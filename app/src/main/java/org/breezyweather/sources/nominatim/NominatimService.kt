/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.sources.nominatim

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.input.KeyboardType
import breezyweather.domain.location.model.LocationAddressInfo
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.BreezyWeather
import org.breezyweather.R
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.preference.EditTextPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationSearchSource
import org.breezyweather.common.source.ReverseGeocodingSource
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.nominatim.json.NominatimAddress
import org.breezyweather.sources.nominatim.json.NominatimLocationResult
import retrofit2.Retrofit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named

/**
 * Nominatim & LocationIQ service (Unified)
 *
 * Supports standard Nominatim instances OR LocationIQ via API Key (pk.xxxx).
 * Search is not possible for standard Nominatim, but enabled if LocationIQ key is provided.
 */
class NominatimService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") private val client: Retrofit.Builder,
) : HttpSource(), LocationSearchSource, ReverseGeocodingSource, ConfigurableSource {

    override val id = "nominatim"
    override val name = "Nominatim / LocationIQ"
    override val locationSearchAttribution =
        "Nominatim/LocationIQ • Data © OpenStreetMap contributors, ODbL 1.0. https://osm.org/copyright"
    override val privacyPolicyUrl = "https://osmfoundation.org/wiki/Privacy_Policy"
    override val continent = SourceContinent.WORLDWIDE

    override val supportedFeatures = mapOf(
        SourceFeature.REVERSE_GEOCODING to locationSearchAttribution
    )
    override val attributionLinks = mapOf(
        name to NOMINATIM_BASE_URL,
        "OpenStreetMap" to "https://osm.org/",
        "https://osm.org/copyright" to "https://osm.org/copyright"
    )

    // Regex for Vietnam Display Name parsing — see companion object for testable static reference
    // Use NominatimService.vnSubProvinceRegex for tests

    private fun isLocationIqKey(value: String?): Boolean {
        return value?.startsWith("pk.") == true
    }

    override fun requestLocationSearch(
        context: Context,
        query: String,
    ): Observable<List<LocationAddressInfo>> {
        val key = if (isLocationIqKey(instance)) instance else null
        // REL-01: fresh client per call — no stale lazy reference after config changes
        val url = if (key != null) LOCATIONIQ_BASE_URL else (instance ?: NOMINATIM_BASE_URL)
        val api = client.baseUrl(url).build().create(NominatimApi::class.java)

        return api.searchLocations(
            acceptLanguage = context.currentLocale.toLanguageTag(),
            userAgent = BreezyWeather.instance.userAgent,
            q = query,
            limit = 20,
            key = key
        ).map { results ->
            results.mapNotNull {
                convertLocation(it)
            }
        }
    }

    override fun requestNearestLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): Observable<List<LocationAddressInfo>> {
        val key = if (isLocationIqKey(instance)) instance else null
        
        if (key != null) {
            // REL-03: LocationIQ and Nominatim clients explicitly separated
            val liqApi = client
                .baseUrl(LOCATIONIQ_BASE_URL)
                .build()
                .create(NominatimApi::class.java)
            val nomApi = client
                .baseUrl(NOMINATIM_BASE_URL)
                .build()
                .create(NominatimApi::class.java)

            // PERF-01/02: Deferred Nominatim fetch — only subscribed when LocationIQ result is dirty or fails
            val nominatimFetch: Observable<List<LocationAddressInfo>> = nomApi.getReverseLocation(
                acceptLanguage = context.currentLocale.toLanguageTag(),
                userAgent = BreezyWeather.instance.userAgent,
                lat = latitude,
                lon = longitude,
                zoom = 13,
                format = "jsonv2",
                key = null
            ).map { result ->
                convertLocation(result, isLocationIQSource = false)?.let { listOf(it) } ?: emptyList()
            }.onErrorReturn { e ->
                // REL-02: log Nominatim failures for diagnosability
                Log.d("NominatimService", "Nominatim reverse geocoding error: ${e.message}")
                emptyList()
            }

            return liqApi.getReverseLocation(
                acceptLanguage = context.currentLocale.toLanguageTag(),
                userAgent = BreezyWeather.instance.userAgent,
                lat = latitude,
                lon = longitude,
                zoom = 18,
                format = "json",
                key = key
            ).flatMap { liqResult ->
                val liqInfo = convertLocation(liqResult, isLocationIQSource = true)
                val isVN = liqInfo?.countryCode?.equals("vn", ignoreCase = true) == true

                if (!isVN || isCleanVnCity(liqInfo?.city)) {
                    // Non-VN or clean VN — LocationIQ result is authoritative; Nominatim never called
                    Observable.just(if (liqInfo != null) listOf(liqInfo) else emptyList())
                } else {
                    // VN but dirty — lazy-fetch Nominatim for potential rescue
                    nominatimFetch.map { nomList ->
                        val nomInfo = nomList.firstOrNull()
                        val rescued = isCleanVnCity(nomInfo?.city)
                        if (rescued) {
                            // GIGL-01: [GiggleRescue] log — Nominatim superseded dirty LIQ result
                            Log.d(
                                "NominatimService",
                                "[GiggleRescue] Nominatim rescued address for " +
                                    "[$latitude,$longitude]: was '${liqInfo?.city}', now '${nomInfo?.city}'"
                            )
                        }
                        mergeVnResults(liqInfo, nomList)
                    }
                }
            }.onErrorResumeNext { e: Throwable ->
                // REL-02: log LocationIQ failures; fall through to Nominatim-only path
                Log.d("NominatimService", "LocationIQ reverse geocoding error: ${e.message}")
                nominatimFetch
            }.map { result ->
                if (result.isEmpty()) throw InvalidLocationException()
                result
            }
        } else {
            val url = instance ?: NOMINATIM_BASE_URL
            val api = client.baseUrl(url).build().create(NominatimApi::class.java)

            return api.getReverseLocation(
                acceptLanguage = context.currentLocale.toLanguageTag(),
                userAgent = BreezyWeather.instance.userAgent,
                lat = latitude,
                lon = longitude,
                zoom = 13,
                format = "jsonv2",
                key = null
            ).map { result ->
                if (result.address?.countryCode == null || result.address.countryCode.isEmpty()) {
                    throw InvalidLocationException()
                }
                listOf(convertLocation(result, isLocationIQSource = false)!!)
            }
        }
    }

    private fun convertLocation(locationResult: NominatimLocationResult, isLocationIQSource: Boolean = isLocationIqKey(instance)): LocationAddressInfo? {
        return if (locationResult.address?.countryCode == null || locationResult.address.countryCode.isEmpty()) {
            null
        } else {
            val countryCode = getNonAmbiguousCountryCode(locationResult.address)
            
            // Vietnam Special Parsing
            var city = locationResult.address.town ?: locationResult.name
            var district = locationResult.address.village

            if (countryCode.equals("vn", ignoreCase = true)) {
                // 1. display_name regex FIRST — lastOrNull gives the innermost clean ward token
                //    (structured fields like suburb can lag the post-2025 admin reform; display_name is fresher)
                val displayName = locationResult.displayName
                var vnCityResolved = false
                if (!displayName.isNullOrEmpty()) {
                    val parts = displayName.split(COMMA_SPLIT_REGEX).map { it.trim() }
                    val cleanPart = pickBestVietnamSubProvincePart(parts)
                    if (cleanPart != null) {
                        city = cleanPart
                        district = null
                        vnCityResolved = true
                    }
                }

                if (!vnCityResolved) {
                    // 2. Structured fields fallback (suburb/hamlet/quarter) when regex finds nothing
                    val structuredCity = locationResult.address?.suburb
                        ?: locationResult.address?.hamlet
                        ?: locationResult.address?.quarter
                    if (structuredCity != null) {
                        city = structuredCity
                        district = null
                    }
                    // Regex failure + no structured field → city stays as address.town ?: locationResult.name
                    // Cross-validation in requestNearestLocation will attempt rescue via the other API
                }
            }

            LocationAddressInfo(
                latitude = locationResult.lat.toDoubleOrNull(),
                longitude = locationResult.lon.toDoubleOrNull(),
                country = locationResult.address.country,
                countryCode = countryCode,
                admin1 = locationResult.address.state,
                admin1Code = getAdmin1CodeForCountry(locationResult.address, countryCode),
                admin2 = locationResult.address.county,
                admin2Code = getAdmin2CodeForCountry(locationResult.address, countryCode),
                admin3 = locationResult.address.municipality,
                city = city,
                cityCode = locationResult.placeId?.toString(),
                district = district
            )
        }
    }

    private fun pickBestVietnamSubProvincePart(parts: List<String>): String? =
        Companion.pickBestVietnamSubProvincePart(parts)

    private fun isCleanVnCity(city: String?): Boolean =
        Companion.isCleanVnCity(city)

    private fun mergeVnResults(
        liqInfo: LocationAddressInfo?,
        nomList: List<LocationAddressInfo>,
    ): List<LocationAddressInfo> = Companion.mergeVnResults(liqInfo, nomList)

    private fun getAdmin1CodeForCountry(
        address: NominatimAddress,
        countryCode: String,
    ): String? {
        return when (countryCode.uppercase()) {
            // Keep the iso code "FR-XX" as the INSEE code is different
            "AR", "AU", "BR", "CA", "CD", "CL", "CN", "EC", "ES", "FM", "FR", "ID",
            "KI", "KZ", "MN", "MX", "MY", "NZ", "PG", "PT", "RU", "UA", "US",
            -> address.isoLvl4
            else -> null
        }
    }

    private fun getAdmin2CodeForCountry(
        address: NominatimAddress,
        countryCode: String,
    ): String? {
        return when (countryCode.uppercase()) {
            "CY" -> address.isoLvl5
            "FR" -> address.isoLvl6?.replace("FR-", "") // Conversion to INSEE code
                ?.let {
                    when (it) {
                        "69M" -> "69"
                        "75C" -> "75"
                        else -> it
                    }
                }
            else -> null
        }
    }

    private fun getNonAmbiguousCountryCode(address: NominatimAddress): String {
        return address.countryCode!!.let {
            with(it) {
                when {
                    equals("CN", ignoreCase = true) -> {
                        with(address.isoLvl3) {
                            when {
                                equals("CN-MO", ignoreCase = true) -> "MO"
                                equals("CN-HK", ignoreCase = true) -> "HK"
                                else -> "CN"
                            }
                        }
                    }
                    equals("FI", ignoreCase = true) -> {
                        with(address.isoLvl3) {
                            when {
                                equals("FI-01", ignoreCase = true) -> "AX"
                                else -> "FI"
                            }
                        }
                    }
                    equals("FR", ignoreCase = true) -> {
                        with(address.isoLvl3) {
                            when {
                                equals("FR-971", ignoreCase = true) -> "GP"
                                equals("FR-972", ignoreCase = true) -> "MQ"
                                equals("FR-973", ignoreCase = true) -> "GF"
                                equals("FR-974", ignoreCase = true) -> "RE"
                                equals("FR-975", ignoreCase = true) -> "PM"
                                equals("FR-976", ignoreCase = true) -> "YT"
                                equals("FR-977", ignoreCase = true) -> "BL"
                                equals("FR-978", ignoreCase = true) -> "MF"
                                equals("FR-986", ignoreCase = true) -> "WF"
                                equals("FR-987", ignoreCase = true) -> "PF"
                                equals("FR-988", ignoreCase = true) -> "NC"
                                equals("FR-BL", ignoreCase = true) -> "BL"
                                equals("FR-CP", ignoreCase = true) -> "CP" // Not official, but reserved
                                equals("FR-MF", ignoreCase = true) -> "MF"
                                equals("FR-NC", ignoreCase = true) -> "NC"
                                equals("FR-PF", ignoreCase = true) -> "PF"
                                equals("FR-PM", ignoreCase = true) -> "PM"
                                equals("FR-TF", ignoreCase = true) -> "TF"
                                equals("FR-WF", ignoreCase = true) -> "WF"
                                else -> "FR"
                            }
                        }
                    }
                    equals("NL", ignoreCase = true) -> {
                        when {
                            address.isoLvl3?.equals("NL-AW", ignoreCase = true) == true -> "AW"
                            address.isoLvl3?.equals("NL-CW", ignoreCase = true) == true -> "CW"
                            address.isoLvl3?.equals("NL-SX", ignoreCase = true) == true -> "SX"
                            address.isoLvl8?.startsWith("BQ", ignoreCase = true) == true -> "BQ"
                            else -> "NL"
                        }
                    }
                    equals("NO", ignoreCase = true) -> {
                        with(address.isoLvl4) {
                            when {
                                equals("NO-21", ignoreCase = true) -> "SJ"
                                equals("NO-22", ignoreCase = true) -> "SJ"
                                else -> "NO"
                            }
                        }
                    }
                    equals("US", ignoreCase = true) -> {
                        when {
                            address.isoLvl4?.equals("US-AS", ignoreCase = true) == true -> "AS"
                            address.isoLvl4?.equals("US-GU", ignoreCase = true) == true -> "GU"
                            address.isoLvl4?.equals("US-MP", ignoreCase = true) == true -> "MP"
                            address.isoLvl4?.equals("US-PR", ignoreCase = true) == true -> "PR"
                            address.isoLvl4?.equals("US-VI", ignoreCase = true) == true -> "VI"
                            address.isoLvl15?.startsWith("UM", ignoreCase = true) == true -> "UM"
                            else -> "US"
                        }
                    }
                    else -> it
                }
            }
        }
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    override val isConfigured = true  // Nominatim requires no API key
    override val isRestricted = false
    private var instance: String?
        set(value) {
            value?.let {
                config.edit().putString("instance", it).apply()
            } ?: config.edit().remove("instance").apply()
        }
        get() = config.getString("instance", null) ?: NOMINATIM_BASE_URL
    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_weather_source_nominatim_instance,
                summary = { _, content ->
                    when {
                        // GIGL-02: playful description so users/devs see when the rescue system is active
                        isLocationIqKey(content) ->
                            "LocationIQ \u2022 Backup address detective on standby"
                        content.isEmpty() ->
                            "Backup address detective (fires when LocationIQ returns garbage)"
                        else -> content
                    }
                },
                content = if (instance != NOMINATIM_BASE_URL) instance else null,
                placeholder = NOMINATIM_BASE_URL,
                regex = null,
                regexError = null,
                keyboardType = KeyboardType.Text,
                onValueChanged = {
                    instance = if (it == NOMINATIM_BASE_URL) null else it.ifEmpty { null }
                }
            )
        )
    }

    // We have no way to distinguish the ones below. Others were deduced with other info in the code above
    override val knownAmbiguousCountryCodes = arrayOf(
        "AU", // Territories: CX, CC, HM (uninhabited), NF
        "NO" // Territories: BV
    )

    companion object {
        private val COMMA_SPLIT_REGEX = Regex("[,，]")
        private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"
        private const val LOCATIONIQ_BASE_URL = "https://ap1.locationiq.com/v1/"

        // VN sub-province regex: matches strings strictly starting with Phường/Xã/Đặc Khu prefix
        // Internal so unit tests can reference it directly
        internal val vnSubProvinceRegex: Pattern =
            Pattern.compile("(?iu)^(?:xã|phường|đặc\\s*khu|xa|phuong|dac\\s*khu)\\s+.*")

        /**
         * Returns the best VN sub-province name from a list of display_name parts.
         * Always picks the LAST matching Phường/Xã/Đặc khu token so that when multiple
         * administrative-level terms appear (e.g. "Đặc khu B, Phường A") the most
         * specific innermost ward wins regardless of API source.
         */
        internal fun pickBestVietnamSubProvincePart(
            parts: List<String>,
        ): String? = parts.lastOrNull { part -> vnSubProvinceRegex.matcher(part).matches() }

        /**
         * Returns true if [city] strictly starts with a Phường/Xã/Đặc khu prefix.
         * Used to decide whether a LocationIQ or Nominatim result is "clean" for VN addresses.
         */
        internal fun isCleanVnCity(city: String?): Boolean {
            if (city.isNullOrEmpty()) return false
            return vnSubProvinceRegex.matcher(city).matches()
        }

        /**
         * Cross-validation merge: given a (potentially dirty) LIQ result and the Nominatim result list,
         * returns the best list — preferring Nominatim when it has a clean city token.
         */
        internal fun mergeVnResults(
            liqInfo: LocationAddressInfo?,
            nomList: List<LocationAddressInfo>,
        ): List<LocationAddressInfo> {
            val nomInfo = nomList.firstOrNull()
            return when {
                isCleanVnCity(nomInfo?.city) -> nomList         // Nominatim rescued
                liqInfo != null              -> listOf(liqInfo) // Both dirty — LIQ last resort
                else                         -> emptyList()
            }
        }
    }
}
