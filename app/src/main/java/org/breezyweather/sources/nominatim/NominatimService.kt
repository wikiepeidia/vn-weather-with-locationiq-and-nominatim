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
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.net.URI
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
        return Companion.isLocationIqKey(value)
    }

    private fun classifyLocationIqKeyState(value: String?): LocationIqKeyState {
        return Companion.classifyLocationIqKeyState(value)
    }

    override fun requestLocationSearch(
        context: Context,
        query: String,
    ): Observable<List<LocationAddressInfo>> {
        migrateLegacyLocationIqEndpointOverrideIfNeeded()
        val configuredInstance = config.getString("instance", null)
        val key = if (isLocationIqKey(configuredInstance)) configuredInstance else null
        val locationIqBaseUrls = getEffectiveLocationIqBaseUrlCandidates()
        val nominatimBaseUrl = resolveNominatimBaseUrl(configuredInstance)

        val nominatimSearchFallback: () -> Observable<List<NominatimLocationResult>> = {
            val api = client.baseUrl(nominatimBaseUrl).build().create(NominatimApi::class.java)
            api.searchLocations(
                acceptLanguage = context.currentLocale.toLanguageTag(),
                userAgent = BreezyWeather.instance.userAgent,
                q = query,
                limit = 20,
                key = null
            )
        }

        val searchRequest: Observable<List<NominatimLocationResult>> = if (key != null) {
            requestLocationIqWithEndpointFallback(locationIqBaseUrls) { _, api ->
                api.searchLocations(
                    acceptLanguage = context.currentLocale.toLanguageTag(),
                    userAgent = BreezyWeather.instance.userAgent,
                    q = query,
                    limit = 20,
                    key = key
                )
            }.onErrorResumeNext { error: Throwable ->
                val reason = classifyLocationIqFailureReason(error)
                Log.d(
                    "NominatimService",
                    "LocationIQ search failure [$reason] endpoints=${locationIqBaseUrls.joinToString()} detail=${error.message ?: error::class.java.simpleName}; falling back to Nominatim"
                )
                nominatimSearchFallback()
            }
        } else {
            nominatimSearchFallback()
        }

        return searchRequest.map { results ->
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
        migrateLegacyLocationIqEndpointOverrideIfNeeded()
        val configuredInstance = config.getString("instance", null)
        val keyState = classifyLocationIqKeyState(configuredInstance)
        val key = if (keyState == LocationIqKeyState.VALID) configuredInstance else null
        val locationIqBaseUrls = getEffectiveLocationIqBaseUrlCandidates()

        when (keyState) {
            LocationIqKeyState.MISSING ->
                Log.d("NominatimService", "LocationIQ skipped: missing key (reverse)")
            LocationIqKeyState.MALFORMED ->
                Log.d("NominatimService", "LocationIQ skipped: malformed key (reverse)")
            LocationIqKeyState.VALID ->
                Log.d(
                    "NominatimService",
                    "LocationIQ enabled: reverse request will be attempted (endpoints=${locationIqBaseUrls.joinToString()})"
                )
        }

        if (key != null) {
            // REL-03: Nominatim client explicitly separated from LocationIQ retry clients
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

            return requestLocationIqWithEndpointFallback(locationIqBaseUrls) { _, api ->
                api.getReverseLocation(
                    acceptLanguage = context.currentLocale.toLanguageTag(),
                    userAgent = BreezyWeather.instance.userAgent,
                    lat = latitude,
                    lon = longitude,
                    zoom = 18,
                    format = "json",
                    key = key
                )
            }.flatMap { liqResult ->
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
                // REL-02: classify LocationIQ failures with endpoint context; then fall through to Nominatim-only path
                val reason = classifyLocationIqFailureReason(e)
                Log.d(
                    "NominatimService",
                    "LocationIQ reverse failure [$reason] endpoints=${locationIqBaseUrls.joinToString()} detail=${e.message ?: e::class.java.simpleName}"
                )
                nominatimFetch
            }.map { result ->
                if (result.isEmpty()) throw InvalidLocationException()
                result
            }
        } else {
            val url = resolveNominatimBaseUrl(configuredInstance)
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

    private fun migrateLegacyLocationIqEndpointOverrideIfNeeded() {
        val configuredInstance = config.getString("instance", null)
        val legacyOverride = Companion.normalizeLegacyLocationIqOverride(configuredInstance) ?: return
        if (config.getString(LOCATIONIQ_BASE_URL_PREF_KEY, null).isNullOrBlank()) {
            locationIqBaseUrlOverride = legacyOverride
        }
        config.edit().remove("instance").apply()
        Log.d(
            "NominatimService",
            "Migrated legacy LocationIQ endpoint from instance to dedicated endpoint setting: $legacyOverride"
        )
    }

    private fun getEffectiveLocationIqBaseUrlCandidates(): List<String> =
        Companion.resolveLocationIqBaseUrlCandidates(locationIqBaseUrlOverride)

    private fun <T : Any> requestLocationIqWithEndpointFallback(
        endpointCandidates: List<String>,
        request: (endpoint: String, api: NominatimApi) -> Observable<T>,
    ): Observable<T> {
        val endpoints = endpointCandidates.distinct()
        if (endpoints.isEmpty()) {
            return Observable.error(IllegalStateException("No LocationIQ endpoint configured"))
        }

        fun attempt(index: Int): Observable<T> {
            val endpoint = endpoints[index]
            val api = client.baseUrl(endpoint).build().create(NominatimApi::class.java)
            return request(endpoint, api).onErrorResumeNext { error ->
                val reason = classifyLocationIqFailureReason(error)
                Log.d(
                    "NominatimService",
                    "LocationIQ endpoint attempt failed [$reason] endpoint=$endpoint detail=${error.message ?: error::class.java.simpleName}"
                )
                if (index + 1 < endpoints.size) {
                    attempt(index + 1)
                } else {
                    Observable.error(error)
                }
            }
        }

        return attempt(0)
    }

    private fun classifyLocationIqFailureReason(error: Throwable): String =
        Companion.classifyLocationIqFailureReason(error)

    private fun resolveNominatimBaseUrl(instanceValue: String?): String =
        Companion.resolveNominatimBaseUrl(instanceValue)

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

    private var locationIqBaseUrlOverride: String?
        set(value) {
            val normalized = normalizeLocationIqBaseUrl(value)
            normalized?.let {
                config.edit().putString(LOCATIONIQ_BASE_URL_PREF_KEY, it).apply()
            } ?: config.edit().remove(LOCATIONIQ_BASE_URL_PREF_KEY).apply()
        }
        get() = normalizeLocationIqBaseUrl(config.getString(LOCATIONIQ_BASE_URL_PREF_KEY, null))

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_weather_source_nominatim_instance,
                summary = { _, content ->
                    when {
                        // GIGL-02: playful description so users/devs see when the rescue system is active
                        isLocationIqKey(content) ->
                            "LocationIQ • Backup address detective on standby"
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
            ),
            EditTextPreference(
                titleId = R.string.settings_weather_source_nominatim_locationiq_endpoint,
                summary = { _, content ->
                    val normalized = normalizeLocationIqBaseUrl(content)
                    when {
                        normalized == null -> "Default endpoint: $LOCATIONIQ_BASE_URL"
                        isLocationIqKey(instance) -> "Active endpoint: $normalized"
                        else -> "Endpoint for pk.* keys: $normalized"
                    }
                },
                content = locationIqBaseUrlOverride,
                placeholder = LOCATIONIQ_BASE_URL,
                regex = null,
                regexError = null,
                keyboardType = KeyboardType.Uri,
                onValueChanged = {
                    locationIqBaseUrlOverride = it.ifEmpty { null }
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
        private const val LOCATIONIQ_BASE_URL = "https://eu1.locationiq.com/v1/"
        private const val LOCATIONIQ_US_BASE_URL = "https://us1.locationiq.com/v1/"
        private const val LOCATIONIQ_BASE_URL_PREF_KEY = "locationiq_base_url"
        private val LOCATIONIQ_DEPRECATED_HOSTS = setOf("ap1.locationiq.com")

        internal enum class LocationIqKeyState {
            MISSING,
            MALFORMED,
            VALID,
        }

        internal fun isLocationIqKey(value: String?): Boolean {
            return value?.startsWith("pk.") == true
        }

        internal fun classifyLocationIqKeyState(value: String?): LocationIqKeyState {
            return when {
                value.isNullOrBlank() -> LocationIqKeyState.MISSING
                isLocationIqKey(value) -> LocationIqKeyState.VALID
                else -> LocationIqKeyState.MALFORMED
            }
        }

        internal fun classifyLocationIqFailureReason(error: Throwable): String {
            val detail = error.message.orEmpty().lowercase()
            return when {
                error is HttpException &&
                    (
                        error.code() == 401 ||
                            error.code() == 403 ||
                            (detail.contains("invalid") && detail.contains("key"))
                        ) -> "invalid key response hint"
                error is HttpException -> "non-success HTTP response (HTTP ${error.code()})"
                error is IOException ||
                    detail.contains("timeout") ||
                    detail.contains("network") ||
                    detail.contains("unable to resolve host") -> "request/network failure"
                detail.contains("invalid") && detail.contains("key") -> "invalid key response hint"
                else -> "request/network failure"
            }
        }

        internal fun normalizeLocationIqBaseUrl(value: String?): String? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return null

            val httpsUrl = when {
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.substring(7)}"
                else -> "https://$trimmed"
            }

            val parsed = try {
                URI(httpsUrl)
            } catch (_: Exception) {
                return null
            }

            val host = parsed.host ?: return null
            val port = when (parsed.port) {
                -1, 443 -> ""
                else -> ":${parsed.port}"
            }

            val canonicalPath = canonicalizeLocationIqBasePath(parsed.path.orEmpty())
            return "https://$host$port$canonicalPath"
        }

        internal fun normalizeLegacyLocationIqOverride(instanceValue: String?): String? {
            if (instanceValue.isNullOrBlank() || isLocationIqKey(instanceValue)) return null
            val normalized = normalizeLocationIqBaseUrl(instanceValue) ?: return null
            return if (isLocationIqHost(normalized)) normalized else null
        }

        internal fun resolveNominatimBaseUrl(instanceValue: String?): String {
            val trimmed = instanceValue?.trim().orEmpty()
            if (trimmed.isEmpty()) return NOMINATIM_BASE_URL
            if (isLocationIqKey(trimmed)) return NOMINATIM_BASE_URL
            if (normalizeLegacyLocationIqOverride(trimmed) != null) return NOMINATIM_BASE_URL
            return trimmed
        }

        private fun isLocationIqHost(value: String): Boolean {
            val host = try {
                URI(value).host?.lowercase()
            } catch (_: Exception) {
                null
            }
            return host == "locationiq.com" || host?.endsWith(".locationiq.com") == true
        }

        private fun canonicalizeLocationIqBasePath(path: String): String {
            val normalized = path.replace('\\', '/')
            val segments = normalized.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return "/v1/"

            val v1Index = segments.indexOfFirst { it.equals("v1", ignoreCase = true) }
            if (v1Index >= 0) {
                val kept = segments.subList(0, v1Index + 1)
                return "/${kept.joinToString("/")}/"
            }

            val endpointTailIndex = segments.indexOfFirst {
                it.equals("reverse", ignoreCase = true) ||
                    it.equals("reverse.php", ignoreCase = true) ||
                    it.equals("search", ignoreCase = true) ||
                    it.equals("search.php", ignoreCase = true)
            }

            return if (endpointTailIndex >= 0) {
                "/v1/"
            } else {
                "/${segments.joinToString("/")}/v1/"
            }
        }

        internal fun resolveLocationIqBaseUrl(overrideValue: String?): String {
            val normalized = normalizeLocationIqBaseUrl(overrideValue) ?: return LOCATIONIQ_BASE_URL
            val normalizedHost = try {
                URI(normalized).host?.lowercase()
            } catch (_: Exception) {
                null
            }
            return if (normalizedHost != null && LOCATIONIQ_DEPRECATED_HOSTS.contains(normalizedHost)) {
                LOCATIONIQ_BASE_URL
            } else {
                normalized
            }
        }

        internal fun resolveLocationIqBaseUrlCandidates(overrideValue: String?): List<String> {
            val primary = resolveLocationIqBaseUrl(overrideValue)
            val primaryHost = try {
                URI(primary).host?.lowercase()
            } catch (_: Exception) {
                null
            }

            val fallbackEndpoints = when (primaryHost) {
                "eu1.locationiq.com" -> listOf(LOCATIONIQ_US_BASE_URL)
                "us1.locationiq.com" -> listOf(LOCATIONIQ_BASE_URL)
                else -> listOf(LOCATIONIQ_BASE_URL, LOCATIONIQ_US_BASE_URL)
            }

            return (listOf(primary) + fallbackEndpoints).distinct()
        }

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
