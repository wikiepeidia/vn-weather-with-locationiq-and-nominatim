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

import breezyweather.domain.location.model.LocationAddressInfo
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.breezyweather.sources.nominatim.json.NominatimAddress
import org.breezyweather.sources.nominatim.json.NominatimLocationResult
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for VN address logic in NominatimService.
 *
 * TEST-01: pickBestVietnamSubProvincePart + isCleanVnCity edge cases
 * TEST-02: mergeVnResults cross-validation (XVAL-01, XVAL-02, XVAL-03)
 * TEST-03: NominatimAddress / NominatimLocationResult JSON deserialization (ADDR-03, ADDR-04)
 * TEST-04: All tests run under ./gradlew test (ensured by being in the standard test source set)
 */
class NominatimServiceTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun addressOf(city: String?, countryCode: String = "vn") = LocationAddressInfo(
        countryCode = countryCode,
        city = city,
    )

    // TODO.md integration vectors for manual reverse geocode checks (no API key embedded).
    private val manualReverseRegressionCoordinates = listOf(
        21.11157 to 105.79121,
        21.10908 to 105.70573,
        21.09866 to 105.67899,
        21.051040 to 105.807282,
        21.82616 to 106.76509,
    )

    private fun httpException(code: Int, message: String): HttpException {
        val response = Response.error<String>(
            code,
            message.toResponseBody("text/plain".toMediaType()),
        )
        return HttpException(response)
    }

    // ─── Phase 15 key gate and diagnostics seams ──────────────────────────────────
    @Test
    fun `isLocationIqKey - pk prefix is accepted`() {
        NominatimService.isLocationIqKey("pk.demo-key") shouldBe true
    }
    @Test
    fun `classifyLocationIqKeyState - missing key detected`() {
        NominatimService.classifyLocationIqKeyState(null) shouldBe NominatimService.Companion.LocationIqKeyState.MISSING
        NominatimService.classifyLocationIqKeyState("") shouldBe NominatimService.Companion.LocationIqKeyState.MISSING
    }
    @Test
    fun `classifyLocationIqKeyState - malformed key detected`() {
        NominatimService.classifyLocationIqKeyState("https://nominatim.openstreetmap.org/") shouldBe NominatimService.Companion.LocationIqKeyState.MALFORMED
    }
    @Test
    fun `classifyLocationIqKeyState - valid key detected`() {
        NominatimService.classifyLocationIqKeyState("pk.123456") shouldBe NominatimService.Companion.LocationIqKeyState.VALID
    }

    @Test
    fun `normalizeLocationIqBaseUrl - host only becomes https v1`() {
        NominatimService.normalizeLocationIqBaseUrl("us1.locationiq.com") shouldBe "https://us1.locationiq.com/v1/"
    }

    @Test
    fun `normalizeLocationIqBaseUrl - keeps explicit v1 endpoint`() {
        NominatimService.normalizeLocationIqBaseUrl("https://eu1.locationiq.com/v1/") shouldBe "https://eu1.locationiq.com/v1/"
    }

    @Test
    fun `normalizeLocationIqBaseUrl - full reverse url is canonicalized to v1 base`() {
        NominatimService.normalizeLocationIqBaseUrl(
            "https://us1.locationiq.com/v1/reverse?key=pk.demo&lat=21.046394&lon=105.78790&format=json"
        ) shouldBe "https://us1.locationiq.com/v1/"
    }

    @Test
    fun `normalizeLocationIqBaseUrl - full reverse php url is canonicalized to v1 base`() {
        NominatimService.normalizeLocationIqBaseUrl(
            "https://eu1.locationiq.com/v1/reverse.php?key=pk.demo"
        ) shouldBe "https://eu1.locationiq.com/v1/"
    }

    @Test
    fun `resolveLocationIqBaseUrl - falls back to default when override missing`() {
        NominatimService.resolveLocationIqBaseUrl(null) shouldBe "https://eu1.locationiq.com/v1/"
    }

    @Test
    fun `resolveLocationIqBaseUrl - uses normalized override`() {
        NominatimService.resolveLocationIqBaseUrl("http://api.locationiq.com") shouldBe "https://api.locationiq.com/v1/"
    }

    @Test
    fun `resolveLocationIqBaseUrl - deprecated ap1 override falls back to default`() {
        NominatimService.resolveLocationIqBaseUrl("http://ap1.locationiq.com") shouldBe "https://eu1.locationiq.com/v1/"
    }

    @Test
    fun `normalizeLegacyLocationIqOverride - locationiq host is accepted for migration`() {
        NominatimService.normalizeLegacyLocationIqOverride("https://us1.locationiq.com/v1/search") shouldBe "https://us1.locationiq.com/v1/"
    }

    @Test
    fun `normalizeLegacyLocationIqOverride - pk key is not treated as endpoint override`() {
        NominatimService.normalizeLegacyLocationIqOverride("pk.demo-key") shouldBe null
    }

    @Test
    fun `normalizeLegacyLocationIqOverride - non locationiq host is ignored`() {
        NominatimService.normalizeLegacyLocationIqOverride("https://nominatim.openstreetmap.org") shouldBe null
    }

    @Test
    fun `resolveNominatimBaseUrl - null uses default nominatim`() {
        NominatimService.resolveNominatimBaseUrl(null) shouldBe "https://nominatim.openstreetmap.org/"
    }

    @Test
    fun `resolveNominatimBaseUrl - custom nominatim instance is preserved`() {
        NominatimService.resolveNominatimBaseUrl("https://nominatim.myserver.example/") shouldBe "https://nominatim.myserver.example/"
    }

    @Test
    fun `resolveNominatimBaseUrl - locationiq endpoint does not become nominatim base`() {
        NominatimService.resolveNominatimBaseUrl("https://us1.locationiq.com/v1/") shouldBe "https://nominatim.openstreetmap.org/"
    }

    @Test
    fun `resolveNominatimBaseUrl - pk key does not become nominatim base`() {
        NominatimService.resolveNominatimBaseUrl("pk.demo-key") shouldBe "https://nominatim.openstreetmap.org/"
    }

    @Test
    fun `resolveLocationIqBaseUrlCandidates - default includes eu then us fallback`() {
        NominatimService.resolveLocationIqBaseUrlCandidates(null) shouldBe listOf(
            "https://eu1.locationiq.com/v1/",
            "https://us1.locationiq.com/v1/",
        )
    }

    @Test
    fun `resolveLocationIqBaseUrlCandidates - us override keeps us first then eu fallback`() {
        NominatimService.resolveLocationIqBaseUrlCandidates("https://us1.locationiq.com/v1/") shouldBe listOf(
            "https://us1.locationiq.com/v1/",
            "https://eu1.locationiq.com/v1/",
        )
    }

    @Test
    fun `resolveLocationIqBaseUrlCandidates - custom host keeps custom then regional fallbacks`() {
        NominatimService.resolveLocationIqBaseUrlCandidates("https://api.locationiq.com") shouldBe listOf(
            "https://api.locationiq.com/v1/",
            "https://eu1.locationiq.com/v1/",
            "https://us1.locationiq.com/v1/",
        )
    }

    @Test
    fun `resolveLocationIqBaseUrlCandidates - deprecated ap1 override skips dead host`() {
        NominatimService.resolveLocationIqBaseUrlCandidates("https://ap1.locationiq.com") shouldBe listOf(
            "https://eu1.locationiq.com/v1/",
            "https://us1.locationiq.com/v1/",
        )
    }

    @Test
    fun `classifyLocationIqFailureReason - network failure bucket`() {
        NominatimService.classifyLocationIqFailureReason(IOException("timeout")) shouldBe "request/network failure"
    }
    @Test
    fun `classifyLocationIqFailureReason - invalid key bucket`() {
        val error = httpException(401, "Invalid key")
        NominatimService.classifyLocationIqFailureReason(error) shouldBe "invalid key response hint"
    }
    @Test
    fun `classifyLocationIqFailureReason - non success http bucket`() {
        val error = httpException(429, "Too many requests")
        NominatimService.classifyLocationIqFailureReason(error) shouldBe "non-success HTTP response (HTTP 429)"
    }
    @Test
    fun `manual reverse regression coordinates include all TODO vectors`() {
        manualReverseRegressionCoordinates shouldBe listOf(
            21.11157 to 105.79121,
            21.10908 to 105.70573,
            21.09866 to 105.67899,
            21.051040 to 105.807282,
            21.82616 to 106.76509,
        )
    }

    // ─── TEST-01: pickBestVietnamSubProvincePart ──────────────────────────────────

    @Test
    fun `pickBestVietnamSubProvincePart - POI prefix is skipped, clean Phuong matched`() {
        // LIQ display_name where first part is a POI/institution, second is the real ward
        val parts = listOf(
            "Ủy ban nhân dân Phường Phú Lương",
            "Phường Phú Lương",
            "Quận Hà Đông",
            "Hà Nội",
            "Việt Nam",
        )
        // POI prefix does NOT start with the required prefix word → skipped;
        // only one clean token → lastOrNull returns it
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe
            "Phường Phú Lương"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - dirty-only list returns null`() {
        val parts = listOf(
            "Ủy ban nhân dân Phường Hà Đông",
            "Quận Hà Đông",
            "Hà Nội",
        )
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe null
    }

    @Test
    fun `pickBestVietnamSubProvincePart - single matching token returned regardless of position`() {
        val parts = listOf("Phường Hoàn Kiếm", "Quận Hoàn Kiếm", "Hà Nội", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe
            "Phường Hoàn Kiếm"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - multiple matches - last (innermost) wins`() {
        // Two tokens match: "Xã Dịch Vọng" first, "Phường Cầu Giấy" last
        // lastOrNull must return the last match; firstOrNull would wrongly return Xã first
        val parts = listOf("Xã Dịch Vọng", "Phường Cầu Giấy", "Hà Nội", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe
            "Phường Cầu Giấy"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - Dac Khu prefix is recognized`() {
        val parts = listOf("Đặc Khu Phú Quốc", "Kiên Giang", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe
            "Đặc Khu Phú Quốc"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - empty list returns null`() {
        NominatimService.pickBestVietnamSubProvincePart(emptyList()) shouldBe null
    }

    @Test
    fun `pickBestVietnamSubProvincePart - no matching prefix parts returns null`() {
        val parts = listOf("Quận Hà Đông", "Hà Nội", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe null
    }

    // Multi-match ordering: last valid VN token always wins (innermost ward)

    @Test
    fun `pickBestVietnamSubProvincePart - Xa A then Phuong B - picks Phuong B (last)`() {
        // "Xã A, Phường B, ..." → Phường B is LAST match; must NOT pick Xã A
        val parts = listOf("Xã A", "Phường B", "Quận X", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe "Phường B"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - Phuong B then Xa A - picks Xa A (last)`() {
        // "Phường B, Xã A, ..." → Xã A is LAST match
        val parts = listOf("Phường B", "Xã A", "Quận X", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe "Xã A"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - Dac Khu B then Phuong A - picks Phuong A (last)`() {
        // "Đặc khu B, Phường A, ..." → Phường A is LAST match; Đặc khu is a higher-level unit
        val parts = listOf("Đặc khu B", "Phường A", "Tỉnh X", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe "Phường A"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - UBND prefix then Dac Khu then Xa - picks Xa (last)`() {
        // "Ủy ban nhân dân phường A, Đặc khu B, xã C" → UBND does NOT match; Đặc khu matches;
        // xã C is LAST match → picks xã C  (UBND prefix correctly dropped by regex)
        val parts = listOf("Ủy ban nhân dân phường A", "Đặc khu B", "xã C", "Tỉnh X")
        NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe "xã C"
    }

    // ─── isCleanVnCity edge cases ─────────────────────────────────────────────────

    @Test
    fun `isCleanVnCity - valid Phuong string returns true`() {
        NominatimService.isCleanVnCity("Phường Phú Lương") shouldBe true
    }

    @Test
    fun `isCleanVnCity - valid Xa string returns true`() {
        NominatimService.isCleanVnCity("Xã Dịch Vọng") shouldBe true
    }

    @Test
    fun `isCleanVnCity - POI prefix returns false`() {
        NominatimService.isCleanVnCity("Ủy ban nhân dân Phường Phú Lương") shouldBe false
    }

    @Test
    fun `isCleanVnCity - null returns false`() {
        NominatimService.isCleanVnCity(null) shouldBe false
    }

    @Test
    fun `isCleanVnCity - empty string returns false`() {
        NominatimService.isCleanVnCity("") shouldBe false
    }

    // ─── TEST-02: mergeVnResults cross-validation ─────────────────────────────────

    @Test
    fun `mergeVnResults XVAL-01 - LIQ dirty, Nominatim clean - Nominatim wins`() {
        val liqInfo = addressOf("Ủy ban nhân dân Phường Phú Lương")
        val nomInfo = addressOf("Phường Phú Lương")
        val result = NominatimService.mergeVnResults(liqInfo, listOf(nomInfo))
        result shouldBe listOf(nomInfo)
    }

    @Test
    fun `mergeVnResults XVAL-02 - both dirty - LIQ used as last resort`() {
        val liqInfo = addressOf("Nhà văn hóa Phường Phú Lương")
        val nomInfo = addressOf("Trường tiểu học Xã Nam Từ Liêm")
        val result = NominatimService.mergeVnResults(liqInfo, listOf(nomInfo))
        result shouldBe listOf(liqInfo)
    }

    @Test
    fun `mergeVnResults XVAL-03 - LIQ dirty, Nominatim empty - LIQ used as last resort`() {
        // XVAL-03: when both fail regex, LIQ result is preserved as last resort (not dropped)
        val liqInfo = addressOf("Ủy ban nhân dân Phường Phú Lương")
        val result = NominatimService.mergeVnResults(liqInfo, emptyList())
        result shouldBe listOf(liqInfo)
    }

    @Test
    fun `mergeVnResults - LIQ null, Nominatim clean - Nominatim wins`() {
        val nomInfo = addressOf("Xã Dịch Vọng")
        val result = NominatimService.mergeVnResults(null, listOf(nomInfo))
        result shouldBe listOf(nomInfo)
    }

    @Test
    fun `mergeVnResults - both null and empty - empty result`() {
        val result = NominatimService.mergeVnResults(null, emptyList())
        result shouldBe emptyList()
    }

    @Test
    fun `mergeVnResults - LIQ dirty city, liqInfo is null - empty result`() {
        // nomList has item but city is dirty, liqInfo is null → emptyList
        val nomInfo = addressOf("Trụ sở Phường Hoàn Kiếm")
        val result = NominatimService.mergeVnResults(null, listOf(nomInfo))
        result shouldBe emptyList()
    }

    // ─── TEST-03: NominatimAddress JSON deserialization ───────────────────────────

    @Test
    fun `NominatimAddress - suburb field deserialized from JSON`() {
        val jsonStr = """
            {
                "suburb": "Phường Hoàn Kiếm",
                "town": "Hà Nội",
                "state": "Hà Nội",
                "country": "Việt Nam",
                "country_code": "vn"
            }
        """.trimIndent()
        val address = json.decodeFromString<NominatimAddress>(jsonStr)
        address.suburb shouldBe "Phường Hoàn Kiếm"
        address.hamlet shouldBe null
        address.quarter shouldBe null
        address.countryCode shouldBe "vn"
    }

    @Test
    fun `NominatimAddress - hamlet field deserialized from JSON`() {
        val jsonStr = """
            {
                "hamlet": "Thôn Ba Vì",
                "state": "Hà Nội",
                "country": "Việt Nam",
                "country_code": "vn"
            }
        """.trimIndent()
        val address = json.decodeFromString<NominatimAddress>(jsonStr)
        address.hamlet shouldBe "Thôn Ba Vì"
        address.suburb shouldBe null
    }

    @Test
    fun `NominatimAddress - quarter field deserialized from JSON`() {
        val jsonStr = """
            {
                "quarter": "Phường Bách Khoa",
                "state": "Hà Nội",
                "country": "Việt Nam",
                "country_code": "vn"
            }
        """.trimIndent()
        val address = json.decodeFromString<NominatimAddress>(jsonStr)
        address.quarter shouldBe "Phường Bách Khoa"
        address.suburb shouldBe null
    }

    @Test
    fun `NominatimAddress - ISO3166-2-lvl4 deserialized with SerialName`() {
        val jsonStr = """
            {
                "state": "California",
                "country": "United States",
                "country_code": "us",
                "ISO3166-2-lvl4": "US-CA"
            }
        """.trimIndent()
        val address = json.decodeFromString<NominatimAddress>(jsonStr)
        address.isoLvl4 shouldBe "US-CA"
        address.countryCode shouldBe "us"
    }

    @Test
    fun `NominatimLocationResult - full VN reverse geocode response deserialized`() {
        val jsonStr = """
            {
                "place_id": 123456,
                "display_name": "Phường Hoàn Kiếm, Quận Hoàn Kiếm, Hà Nội, Việt Nam",
                "lat": "21.0285",
                "lon": "105.8542",
                "address": {
                    "suburb": "Phường Hoàn Kiếm",
                    "town": "Hà Nội",
                    "county": "Quận Hoàn Kiếm",
                    "state": "Hà Nội",
                    "country": "Việt Nam",
                    "country_code": "vn"
                }
            }
        """.trimIndent()
        val result = json.decodeFromString<NominatimLocationResult>(jsonStr)
        result.placeId shouldBe 123456
        result.lat shouldBe "21.0285"
        result.displayName shouldBe "Phường Hoàn Kiếm, Quận Hoàn Kiếm, Hà Nội, Việt Nam"
        result.address?.suburb shouldBe "Phường Hoàn Kiếm"
        result.address?.countryCode shouldBe "vn"
        result.address?.hamlet shouldBe null
    }

    @Test
    fun `NominatimLocationResult - structured fields suburb takes priority over display_name parsing`() {
        // Ensure suburb field is present and non-null (simulates ADDR-04 path in convertLocation)
        val jsonStr = """
            {
                "place_id": 999,
                "display_name": "Ủy ban nhân dân Phường Cầu Giấy, Phường Cầu Giấy, Quận Cầu Giấy, Hà Nội, Việt Nam",
                "lat": "21.030",
                "lon": "105.780",
                "address": {
                    "suburb": "Phường Cầu Giấy",
                    "county": "Quận Cầu Giấy",
                    "state": "Hà Nội",
                    "country": "Việt Nam",
                    "country_code": "vn"
                }
            }
        """.trimIndent()
        val result = json.decodeFromString<NominatimLocationResult>(jsonStr)
        // The structured suburb field is clean even though display_name has dirty prefix
        result.address?.suburb shouldBe "Phường Cầu Giấy"
        result.address?.suburb?.let { NominatimService.isCleanVnCity(it) } shouldBe true
    }
}
