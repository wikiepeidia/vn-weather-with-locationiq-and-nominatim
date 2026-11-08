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
import android.content.SharedPreferences
import breezyweather.domain.location.model.LocationAddressInfo
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.breezyweather.BreezyWeather
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.extensions.currentLocale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale

/**
 * Runs the real service against canned HTTP responses, so request parameters, the LocationIQ to
 * Nominatim fallback and the Vietnamese ward parsing are all covered together.
 */
class NominatimServiceFlowTest {

    private val requests = mutableListOf<Request>()
    private var responder: (Request) -> Pair<Int, String> = { 500 to "" }
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)

    private val client: Retrofit.Builder = Retrofit.Builder()
        .client(
            OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val (code, body) = responder(request)
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("test")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            ).build()
        )
        // Same settings as HttpModule in a debug build, the stricter of the two
        .addConverterFactory(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = false
            }.asConverterFactory("application/json".toMediaType())
        )
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())

    @BeforeEach
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkStatic("org.breezyweather.common.extensions.LanguageExtensionsKt")
        every { any<Context>().currentLocale } returns Locale.forLanguageTag("vi")
        mockkObject(BreezyWeather.Companion)
        every { BreezyWeather.instance } returns mockk(relaxed = true) {
            every { userAgent } returns "Test"
        }
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun service(instance: String? = KEY): NominatimService {
        every { prefs.getString("instance", any()) } returns instance
        every { prefs.getString("locationiq_base_url", any()) } returns null
        return NominatimService(context, client)
    }

    private fun Request.isNominatim() = url.host == "nominatim.openstreetmap.org"
    private fun Request.isLocationIq() = url.host.endsWith("locationiq.com")

    private fun reverse(instance: String? = KEY): List<LocationAddressInfo> =
        service(instance).requestNearestLocation(context, 10.77, 106.70).blockingFirst()

    private fun search(instance: String? = KEY): List<LocationAddressInfo> =
        service(instance).requestLocationSearch(context, "query").blockingFirst()

    private fun place(
        displayName: String,
        countryCode: String = "vn",
        address: String = "",
        topLevel: String = "",
    ): String {
        val extra = if (address.isEmpty()) "" else ", $address"
        return """{ "place_id": 1, $topLevel "lat": "10.77", "lon": "106.70",
            "display_name": ${JsonPrimitive(displayName)},
            "address": { "country": "X", "country_code": "$countryCode"$extra } }"""
    }

    private fun answer(
        locationIq: Pair<Int, String> = 500 to "",
        nominatim: Pair<Int, String> = 500 to "",
    ) {
        responder = { if (it.isNominatim()) nominatim else locationIq }
    }

    // Each display_name must give the same ward through LocationIQ, the rescue and Nominatim alone
    private fun assertWardEverywhere(displayName: String, expected: String) {
        withClue("LocationIQ: $displayName") {
            answer(locationIq = 200 to place(displayName))
            reverse().first().city shouldBe expected
        }
        withClue("Nominatim rescue: $displayName") {
            answer(locationIq = 200 to place(DIRTY), nominatim = 200 to place(displayName))
            reverse().first().city shouldBe expected
        }
        withClue("Nominatim after LocationIQ failure: $displayName") {
            answer(locationIq = 401 to "", nominatim = 200 to place(displayName))
            reverse().first().city shouldBe expected
        }
        withClue("Nominatim without a key: $displayName") {
            answer(nominatim = 200 to place(displayName))
            reverse(instance = null).first().city shouldBe expected
        }
    }

    // ─── The owner's reference addresses ─────────────────────────────────────────

    @Test
    fun `wards - long real address and its theoretical variants`() {
        listOf(
            "Trụ sở Ủy ban Nhân dân Thành phố Hồ Chí Minh, 86, Lê Thánh Tôn, Khu phố 3, Phường Sài Gòn, " +
                "Thành phố Thủ Đức, Thành phố Hồ Chí Minh, 71006, Việt Nam",
            "Trụ sở Ủy ban Nhân dân Phường  Thủ Đức, Phường Sài Gòn ,  Thành phố Thủ Đức, Thành phố Hồ Chí Minh",
            "Phường  Thủ Đức, Phường Sài Gòn , Thành phố Thủ Đức, Thành phố Hồ Chí Minh",
            "Phường Sài Gòn ,  Thành phố Thủ Đức, Thành phố Hồ Chí Minh",
        ).forEach { assertWardEverywhere(it, "Phường Sài Gòn") }
    }

    @Test
    fun `communes - same shapes as wards`() {
        listOf(
            "Trụ sở Ủy ban Nhân dân Huyện Đan Phượng, 12, Đường Tây Sơn, Thôn 3, Xã Ô Diên, " +
                "Huyện Đan Phượng, Thành phố Hà Nội, 13000, Việt Nam",
            "Trụ sở Ủy ban Nhân dân Xã  Đan Phượng, Xã Ô Diên ,  Huyện Đan Phượng, Thành phố Hà Nội",
            "Xã  Đan Phượng, Xã Ô Diên , Huyện Đan Phượng, Thành phố Hà Nội",
        ).forEach { assertWardEverywhere(it, "Xã Ô Diên") }
    }

    @Test
    fun `special zones - same shapes as wards`() {
        listOf(
            "Trụ sở Ủy ban Nhân dân Tỉnh An Giang, 1, Đường Trần Hưng Đạo, Khu phố 3, Đặc khu Phú Quốc, " +
                "Tỉnh An Giang, 92000, Việt Nam",
            "Trụ sở Ủy ban Nhân dân Đặc khu  Côn Đảo, Đặc khu Phú Quốc ,  Tỉnh An Giang",
            "Đặc khu  Côn Đảo, Đặc khu Phú Quốc , Tỉnh An Giang",
        ).forEach { assertWardEverywhere(it, "Đặc khu Phú Quốc") }
    }

    @Test
    fun `rescue - LocationIQ city level answer is replaced by the Nominatim ward`() {
        listOf(
            "Phường Sài Gòn ,  Thành phố Thủ Đức, Thành phố Hồ Chí Minh" to "Phường Sài Gòn",
            "Xã Ô Diên, Huyện Đan Phượng, Thành phố Hà Nội" to "Xã Ô Diên",
            "Đặc khu Phú Quốc, Tỉnh An Giang" to "Đặc khu Phú Quốc",
        ).forEach { (nominatim, expected) ->
            answer(locationIq = 200 to place(DIRTY), nominatim = 200 to place(nominatim))
            reverse().single().city shouldBe expected
        }
    }

    // ─── Outliers ────────────────────────────────────────────────────────────────

    @Test
    fun `outliers - the last ward wins and prefixes must start the part`() {
        mapOf(
            "Xã A, Phường B, Hà Nội" to "Phường B",
            "Phường B, Xã A, Hà Nội" to "Xã A",
            "Đặc khu B, Phường A, Tỉnh X" to "Phường A",
            "Trường THCS Phường Bến Nghé, Phường Sài Gòn, TP" to "Phường Sài Gòn",
            "Công an Xã Ô Diên, Xã Ô Diên, Hà Nội" to "Xã Ô Diên",
            "Chợ Phường 5, Phường 5, Thành phố Hồ Chí Minh" to "Phường 5",
            "Phường Sài Gòn" to "Phường Sài Gòn",
            "PHƯỜNG SÀI GÒN, TP" to "PHƯỜNG SÀI GÒN",
            "phường sài gòn, tp" to "phường sài gòn",
            "Phuong Sai Gon, TP" to "Phuong Sai Gon",
            "Dac khu Phu Quoc, Tinh An Giang" to "Dac khu Phu Quoc",
            "Đặc Khu Phú Quốc, Tỉnh An Giang" to "Đặc Khu Phú Quốc",
            "Số 1，Phường Sài Gòn，TP" to "Phường Sài Gòn",
            "  Phường Sài Gòn  ,TP" to "Phường Sài Gòn",
            "Phường\tSài Gòn, TP" to "Phường\tSài Gòn",
        ).forEach { (displayName, expected) ->
            withClue(displayName) {
                answer(locationIq = 200 to place(displayName))
                reverse().single().city shouldBe expected
            }
        }
    }

    @Test
    fun `outliers - a bare prefix or lookalike is not a ward`() {
        listOf(
            "Phường, Thành phố Huế",
            "Xã, Hà Nội",
            "Đặc khu, Tỉnh An Giang",
            "Phườngsàigòn, TP",
            "Quận 1, Thành phố Hồ Chí Minh",
            "Thị xã Sơn Tây, Hà Nội",
            "Thị trấn Sa Pa, Tỉnh Lào Cai",
            "Thôn Xã Đàn, Hà Nội",
        ).forEach { displayName ->
            withClue(displayName) {
                answer(locationIq = 200 to place(displayName), nominatim = 200 to place("Phường Cứu, Hà Nội"))
                reverse().single().city shouldBe "Phường Cứu"
            }
        }
    }

    @Test
    fun `known limits - pinned so any change in behaviour is noticed`() {
        // A road named Xa lộ reads as a commune when no real ward follows it, which blocks the rescue
        answer(locationIq = 200 to place("Xa lộ Hà Nội, Thành phố Thủ Đức"), nominatim = 200 to place("Phường X, TP"))
        reverse().single().city shouldBe "Xa lộ Hà Nội"
        requests.count { it.isNominatim() } shouldBe 0

        // Decomposed Unicode is not matched; the services send composed text
        val decomposed = java.text.Normalizer.normalize("Phường Sài Gòn", java.text.Normalizer.Form.NFD)
        answer(locationIq = 200 to place("$decomposed, TP"))
        NominatimService.isCleanVnCity(decomposed) shouldBe false
    }

    @Test
    fun `structured fields - used only when display_name has no ward`() {
        answer(
            locationIq = 200 to place(
                "Số 1, Thành phố Thủ Đức",
                address = """"suburb": "Phường Bến Nghé", "hamlet": "Ấp 1", "quarter": "Khu 2""""
            )
        )
        reverse().single().city shouldBe "Phường Bến Nghé"

        answer(locationIq = 200 to place("Số 1, Tỉnh X", address = """"hamlet": "Xã Hậu", "quarter": "Khu 2""""))
        reverse().single().city shouldBe "Xã Hậu"

        answer(locationIq = 200 to place("Số 1, Tỉnh X", address = """"quarter": "Phường Khu""""))
        reverse().single().city shouldBe "Phường Khu"

        answer(locationIq = 200 to place("Phường Sài Gòn, TP", address = """"suburb": "Phường Khác""""))
        reverse().single().city shouldBe "Phường Sài Gòn"
    }

    // ─── Fallback order and what gets sent ───────────────────────────────────────

    @Test
    fun `reverse - a clean LocationIQ answer never calls Nominatim`() {
        answer(locationIq = 200 to place("Phường Sài Gòn, TP"))
        reverse()
        requests.count { it.isNominatim() } shouldBe 0
        requests.single().url.queryParameter("format") shouldBe "json"
        requests.single().url.queryParameter("key") shouldBe KEY
    }

    @Test
    fun `reverse - both dirty keeps LocationIQ exactly as before`() {
        answer(
            locationIq = 200 to place(DIRTY, address = """"city": "Thành phố Thủ Đức""""),
            nominatim = 200 to place("Hà Nội, Việt Nam"),
        )
        reverse().single().city shouldBe null
    }

    @Test
    fun `reverse - Nominatim failure during rescue keeps LocationIQ`() {
        answer(locationIq = 200 to place(DIRTY), nominatim = 500 to "")
        reverse().single().city shouldBe null
    }

    @Test
    fun `reverse - tries both LocationIQ regions, then Nominatim without the key`() {
        answer(locationIq = 401 to "", nominatim = 200 to place("Phường Sài Gòn, TP"))
        reverse().single().city shouldBe "Phường Sài Gòn"

        requests.map { it.url.host } shouldBe listOf(
            "eu1.locationiq.com",
            "us1.locationiq.com",
            "nominatim.openstreetmap.org",
        )
        val nominatim = requests.last().url
        nominatim.queryParameter("format") shouldBe "jsonv2"
        nominatim.queryParameter("key") shouldBe null
    }

    @Test
    fun `reverse - no LocationIQ place at all (open sea) ends in Nominatim, then a clear failure`() {
        answer(locationIq = 404 to """{ "error": "Unable to geocode" }""", nominatim = 200 to paris)
        reverse().single().city shouldBe "Paris"
        requests.map { it.url.host } shouldBe listOf(
            "eu1.locationiq.com",
            "us1.locationiq.com",
            "nominatim.openstreetmap.org",
        )

        answer(locationIq = 404 to """{ "error": "Unable to geocode" }""", nominatim = 500 to "")
        val thrown = runCatching { reverse() }.exceptionOrNull()
        (thrown is InvalidLocationException || thrown?.cause is InvalidLocationException) shouldBe true
    }

    @Test
    fun `reverse - second region answers when the first is down`() {
        responder = { request ->
            when (request.url.host) {
                "eu1.locationiq.com" -> 500 to ""
                else -> 200 to place("Phường Sài Gòn, TP")
            }
        }
        reverse().single().city shouldBe "Phường Sài Gòn"
    }

    @Test
    fun `reverse - outside Vietnam the ward rules do not apply`() {
        answer(
            locationIq = 200 to place(
                "Phường Sài Gòn, Paris, France",
                countryCode = "fr",
                address = """"city": "Paris""""
            )
        )
        reverse().single().city shouldBe "Paris"
    }

    // ─── Search ──────────────────────────────────────────────────────────────────

    @Test
    fun `search - LocationIQ gets format json and the key, and Nominatim is not called`() {
        answer(locationIq = 200 to "[${place("Phường Sài Gòn, TP")}]")
        search().single().city shouldBe "Phường Sài Gòn"

        val url = requests.single().url
        url.host shouldBe "eu1.locationiq.com"
        url.queryParameter("format") shouldBe "json"
        url.queryParameter("key") shouldBe KEY
    }

    @Test
    fun `search - the real LocationIQ shape parses, including a quoted place_id`() {
        answer(
            locationIq = 200 to """[{
                "place_id": "332768218", "licence": "x", "osm_type": "relation", "osm_id": "1",
                "boundingbox": ["1", "2", "3", "4"], "lat": "10.77", "lon": "106.70",
                "display_name": "Trụ sở, 86, Lê Thánh Tôn, Phường Sài Gòn, Thành phố Hồ Chí Minh, Việt Nam",
                "class": "office", "type": "government", "importance": 0.5,
                "address": { "name": "Trụ sở", "city": "Thành phố Hồ Chí Minh", "country": "Việt Nam",
                    "country_code": "vn" }
            }]"""
        )
        val result = search().single()
        result.city shouldBe "Phường Sài Gòn"
        result.cityCode shouldBe "332768218"
    }

    @Test
    fun `search - a result with no ward and no name still has a city`() {
        answer(
            locationIq = 200 to "[" +
                place("Hà Nội, Việt Nam", address = """"city": "Hà Nội"""") + "," +
                place("Tỉnh An Giang, Việt Nam", address = """"state": "Tỉnh An Giang"""") + "]"
        )
        search().map { it.city } shouldBe listOf("Hà Nội", "Tỉnh An Giang")
    }

    @Test
    fun `reverse - no ward never shows a POI or house number from display_name`() {
        answer(
            locationIq = 200 to place(
                "Trường THCS Tân Lập, 86, Thôn Bình Minh, Huyện Cai Lậy, Tỉnh Tiền Giang",
                address = """"village": "Thôn Bình Minh", "city": "Xã Cũ""""
            ),
            nominatim = 200 to place("Phường Mới, Tỉnh X"),
        )
        // A clean-looking address.city must not skip the rescue
        reverse().single().city shouldBe "Phường Mới"

        answer(
            locationIq = 200 to place("Trường THCS Tân Lập, 86, Thôn Bình Minh", address = """"village": "Thôn Bình Minh""""),
            nominatim = 500 to "",
        )
        val result = reverse().single()
        result.city shouldBe null
        result.district shouldBe "Thôn Bình Minh"
    }

    @Test
    fun `search - an existing name keeps priority in Vietnam, as with Nominatim before`() {
        answer(
            nominatim = 200 to "[" +
                place("Hà Nội, Việt Nam", topLevel = """"name": "Hà Nội", """, address = """"city": "Khác"""") + "]"
        )
        search(instance = null).single().city shouldBe "Hà Nội"
    }

    @Test
    fun `search - falls back to Nominatim with jsonv2 and no key`() {
        answer(locationIq = 400 to "", nominatim = 200 to "[${place("Phường Sài Gòn, TP")}]")
        search().single().city shouldBe "Phường Sài Gòn"

        requests.count { it.isLocationIq() } shouldBe 2
        val nominatim = requests.last().url
        nominatim.host shouldBe "nominatim.openstreetmap.org"
        nominatim.queryParameter("format") shouldBe "jsonv2"
        nominatim.queryParameter("key") shouldBe null
    }

    @Test
    fun `search - without a key only Nominatim is used`() {
        answer(nominatim = 200 to "[${place("Xã Ô Diên, Hà Nội")}]")
        search(instance = null).single().city shouldBe "Xã Ô Diên"
        requests.single().isNominatim() shouldBe true
    }

    // ─── Other countries: LocationIQ first, then Nominatim, with upstream's mapping ────

    private val paris = place(
        "Quartier Saint-Merri, Paris 4e Arrondissement, Paris, Pháp",
        countryCode = "fr",
        address = """"suburb": "Paris 4e Arrondissement", "city": "Paris""""
    )

    @Test
    fun `other countries - Nominatim is asked and its upstream-mapped answer wins`() {
        answer(
            locationIq = 200 to place("LIQ", countryCode = "fr", address = """"city": "From LocationIQ""""),
            nominatim = 200 to paris,
        )
        val result = reverse().single()
        result.city shouldBe "Paris"
        result.district shouldBe "Paris 4e Arrondissement"
        requests.map { it.url.host } shouldBe listOf("eu1.locationiq.com", "nominatim.openstreetmap.org")
    }

    @Test
    fun `other countries - LocationIQ keeps the address alive when Nominatim is unreachable`() {
        answer(locationIq = 200 to paris, nominatim = 500 to "")
        val result = reverse().single()
        result.city shouldBe "Paris"
        result.district shouldBe "Paris 4e Arrondissement"
    }

    @Test
    fun `other countries - LocationIQ is kept when Nominatim has no city`() {
        answer(locationIq = 200 to paris, nominatim = 200 to place("Somewhere", countryCode = "fr"))
        reverse().single().city shouldBe "Paris"
    }

    @Test
    fun `other countries - LocationIQ failure falls back to Nominatim`() {
        answer(locationIq = 401 to "", nominatim = 200 to paris)
        val result = reverse().single()
        result.city shouldBe "Paris"
        result.district shouldBe "Paris 4e Arrondissement"
        requests.last().url.queryParameter("key") shouldBe null
    }

    @Test
    fun `other countries - unusable LocationIQ answer falls back to Nominatim`() {
        listOf(
            place("Somewhere", countryCode = "fr"),
            """{ "place_id": 1, "lat": "1", "lon": "2", "display_name": "No address at all" }""",
        ).forEach { unusable ->
            answer(locationIq = 200 to unusable, nominatim = 200 to paris)
            reverse().single().city shouldBe "Paris"
        }
    }

    @Test
    fun `other countries - both unusable keeps what LocationIQ knows`() {
        answer(locationIq = 200 to place("Somewhere", countryCode = "fr"), nominatim = 500 to "")
        reverse().single().countryCode shouldBe "FR"
    }

    @Test
    fun `other countries - upstream's order is village, town, city`() {
        answer(
            locationIq = 200 to place(
                "X",
                countryCode = "fr",
                address = """"village": "V", "town": "T", "city": "C", "neighbourhood": "N", "suburb": "S""""
            )
        )
        val result = reverse().single()
        result.city shouldBe "V"
        result.district shouldBe "N"
    }

    // ─── Real LocationIQ responses, recorded with the owner's key ────────────────

    private val recorded by lazy {
        Json.parseToJsonElement(
            javaClass.getResource("/nominatim/locationiq_live_responses.json")!!.readText()
        ).jsonObject
    }

    @Test
    fun `recorded - every real reverse response gives the expected place`() {
        recorded.getValue("reverse").jsonArray.map { it.jsonObject }.forEach { entry ->
            withClue(entry.getValue("label").jsonPrimitive.content) {
                val label = entry.getValue("label").jsonPrimitive.content
                answer(locationIq = 200 to entry.getValue("response").toString())
                requests.clear()
                reverse().single().city shouldBe entry.getValue("expected").jsonPrimitive.content
                // Only places abroad ask Nominatim: every Vietnamese one here has a ward
                requests.count { it.isNominatim() } shouldBe if (label == "outlier-paris") 1 else 0
            }
        }
    }

    @Test
    fun `recorded - every real search response gives the expected place`() {
        recorded.getValue("search").jsonArray.map { it.jsonObject }.forEach { entry ->
            withClue(entry.getValue("label").jsonPrimitive.content) {
                answer(locationIq = 200 to entry.getValue("response").toString())
                search().first().city shouldBe entry.getValue("expected").jsonPrimitive.content
            }
        }
    }

    private companion object {
        const val KEY = "pk.test"
        const val DIRTY = "Thành phố Thủ Đức, Thành phố Hồ Chí Minh"
    }
}
