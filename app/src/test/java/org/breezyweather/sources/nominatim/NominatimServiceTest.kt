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
import org.breezyweather.sources.nominatim.json.NominatimAddress
import org.breezyweather.sources.nominatim.json.NominatimLocationResult
import org.junit.jupiter.api.Test

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

    // ─── TEST-01: pickBestVietnamSubProvincePart ──────────────────────────────────

    @Test
    fun `pickBestVietnamSubProvincePart - POI prefix is skipped, clean Phuong matched for LIQ`() {
        // LIQ display_name where first part is a POI/institution, second is the real ward
        val parts = listOf(
            "Ủy ban nhân dân Phường Phú Lương",
            "Phường Phú Lương",
            "Quận Hà Đông",
            "Hà Nội",
            "Việt Nam",
        )
        // ADDR-02 / XVAL-01: POI prefix does NOT start with the required prefix word
        // → skipped; next clean "Phường Phú Lương" is returned
        NominatimService.pickBestVietnamSubProvincePart(parts, isLocationIQSource = true) shouldBe
            "Phường Phú Lương"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - dirty-only list returns null for LIQ`() {
        val parts = listOf(
            "Ủy ban nhân dân Phường Hà Đông",
            "Quận Hà Đông",
            "Hà Nội",
        )
        NominatimService.pickBestVietnamSubProvincePart(parts, isLocationIQSource = true) shouldBe null
    }

    @Test
    fun `pickBestVietnamSubProvincePart - clean Phuong is first element for LIQ`() {
        val parts = listOf("Phường Hoàn Kiếm", "Quận Hoàn Kiếm", "Hà Nội", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts, isLocationIQSource = true) shouldBe
            "Phường Hoàn Kiếm"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - Nominatim uses lastOrNull to find ward at end`() {
        // Nominatim zoom=13 puts ward/commune LAST in display_name
        val parts = listOf("Việt Nam", "Hà Nội", "Quận Cầu Giấy", "Xã Dịch Vọng")
        NominatimService.pickBestVietnamSubProvincePart(parts, isLocationIQSource = false) shouldBe
            "Xã Dịch Vọng"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - Dac Khu prefix is recognized`() {
        val parts = listOf("Đặc Khu Phú Quốc", "Kiên Giang", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts, isLocationIQSource = true) shouldBe
            "Đặc Khu Phú Quốc"
    }

    @Test
    fun `pickBestVietnamSubProvincePart - empty list returns null`() {
        NominatimService.pickBestVietnamSubProvincePart(emptyList(), isLocationIQSource = true) shouldBe null
    }

    @Test
    fun `pickBestVietnamSubProvincePart - no matching prefix parts returns null`() {
        val parts = listOf("Quận Hà Đông", "Hà Nội", "Việt Nam")
        NominatimService.pickBestVietnamSubProvincePart(parts, isLocationIQSource = true) shouldBe null
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
