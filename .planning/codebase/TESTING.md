# Testing Patterns

**Analysis Date:** 2026-05-29

## Test Framework

**Runner:** JUnit 5 (Jupiter) 6.0.3

- Artifact: `org.junit.jupiter:junit-jupiter` via `libs.bundles.test`
- Runtime launcher: `org.junit.platform:junit-platform-launcher` (`testRuntimeOnly`)
- Config: `app/build.gradle.kts` — `testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }`

**Assertion Library:** Kotest Assertions Core 6.1.11 — `io.kotest:kotest-assertions-core`

**Mocking Library:** MockK — `io.mockk:mockk` (part of `libs.bundles.test`)

**Async Test Support:** `kotlinx-coroutines-test` — `kotlinx.coroutines.test.runTest`

**Run Commands:**

```bash
./gradlew test                     # Run all unit tests
./gradlew app:test                 # Run app module unit tests only
./gradlew app:testDebugUnitTest    # Debug variant unit tests
```

No coverage threshold is configured. Coverage tooling is not observed in the build files.

## Test File Organization

**Source roots:**

- Upstream tests: `app/src/test/kotlin/org/breezyweather/`
- Fork-specific tests (valid Kotlin, `java/` source root): `app/src/test/java/org/breezyweather/`

**Naming:** `<SubjectClass>Test.kt` — mirrors the production class name exactly.

**Package mirroring:** Test packages match production packages:

```text
Production: org.breezyweather.sources.nominatim.NominatimService
Test:       org.breezyweather.sources.nominatim.NominatimServiceTest
```

**Current test files:**

```text
app/src/test/java/org/breezyweather/sources/nominatim/
    NominatimServiceTest.kt                    (503 lines — fork-owned, VN logic)

app/src/test/kotlin/org/breezyweather/
    LocationTest.kt                            (stub — empty class body)
    MatchTest.kt
    option/appearance/
        CardDisplayTest.kt
        DailyTrendDisplayTest.kt
    option/utils/
        UtilsTest.kt
    sources/
        CommonConverterTest.kt
```

## Test Structure

**Suite organization:** Flat class body — no `@Nested` classes or `@BeforeEach`/`@AfterEach`
observed. Each `@Test` function is self-contained.

**Section separators** are used inside large test classes to group related tests:

```kotlin
// ─── Phase 15 key gate and diagnostics seams ──────────────────────────────────
@Test
fun `isLocationIqKey - pk prefix is accepted`() { ... }

// ─── TEST-01: pickBestVietnamSubProvincePart ──────────────────────────────────
@Test
fun `pickBestVietnamSubProvincePart - POI prefix is skipped, clean Phuong matched`() { ... }
```

**Class-level helpers** are declared as `private val` or `private fun` inside the test class body
(not in a companion object):

```kotlin
class NominatimServiceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun addressOf(city: String?, countryCode: String = "vn") = LocationAddressInfo(
        countryCode = countryCode,
        city = city,
    )

    private fun httpException(code: Int, message: String): HttpException { ... }
```

**Standard test function pattern** (no coroutines needed for pure logic):

```kotlin
@Test
fun `isLocationIqKey - pk prefix is accepted`() {
    NominatimService.isLocationIqKey("pk.demo-key") shouldBe true
}
```

**Coroutine test pattern** (when the subject is a suspend function or uses coroutines):

```kotlin
@Test
fun getWindDegreeTest() = runTest {
    getWindDegree(null) shouldBe null
    getWindDegree("E") shouldBe 90.0
}
```

## Assertion Style

**Always use Kotest infix `shouldBe`** — never JUnit `assertEquals` or Kotlin `assert`:

```kotlin
result shouldBe listOf(nomInfo)
address.suburb shouldBe "Phường Hoàn Kiếm"
address.hamlet shouldBe null
NominatimService.classifyLocationIqKeyState(null) shouldBe NominatimService.Companion.LocationIqKeyState.MISSING
```

Chain multiple assertions in a single test when they all verify the same object:

```kotlin
result.placeId shouldBe 123456
result.lat shouldBe "21.0285"
result.displayName shouldBe "Phường Hoàn Kiếm, Quận Hoàn Kiếm, Hà Nội, Việt Nam"
result.address?.suburb shouldBe "Phường Hoàn Kiếm"
result.address?.countryCode shouldBe "vn"
result.address?.hamlet shouldBe null
```

## Mocking

**Framework:** MockK

**Pattern:**

```kotlin
val res = mockk<Resources>()
every { res.getStringArray(R.array.dark_modes) } returns
    arrayOf("Automatic", "Follow system", "Always light", "Always dark")
```

**What to mock:** Android framework types (`Context`, `Resources`) that cannot be constructed in
a JVM-only unit test (`isReturnDefaultValues = true` is set, so unmocked Android stubs return
defaults rather than crashing).

**What NOT to mock:** Pure Kotlin logic in companion objects — call these directly. The companion
object pattern (see `NominatimService.kt`) exists precisely so mocking is unnecessary for
business logic tests.

## How to Test NominatimService Logic

The companion object methods are `internal` and callable directly from tests in the same module:

```kotlin
// Pure logic — no mock needed
NominatimService.isLocationIqKey("pk.demo") shouldBe true
NominatimService.pickBestVietnamSubProvincePart(parts) shouldBe "Phường Phú Lương"
NominatimService.mergeVnResults(liqInfo, listOf(nomInfo)) shouldBe listOf(nomInfo)

// Helper to build test inputs without constructing the full service
private fun addressOf(city: String?, countryCode: String = "vn") = LocationAddressInfo(
    countryCode = countryCode,
    city = city,
)
```

Do not try to instantiate `NominatimService` directly in unit tests — it requires Hilt injection
and a live `Retrofit.Builder`. Test only via companion object static methods.

## Fixtures and Test Data

**Inline construction** — no separate fixture files or factory objects:

```kotlin
val parts = listOf(
    "Ủy ban nhân dân Phường Phú Lương",
    "Phường Phú Lương",
    "Quận Hà Đông",
    "Hà Nội",
    "Việt Nam",
)
```

**JSON strings** embedded directly in the test function using `trimIndent()`:

```kotlin
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
```

**Regression coordinate list** kept as a `private val` in the test class body and asserted as a
whole to confirm it hasn't been accidentally modified:

```kotlin
private val manualReverseRegressionCoordinates = listOf(
    21.11157 to 105.79121,
    ...
)

@Test
fun `manual reverse regression coordinates include all TODO vectors`() {
    manualReverseRegressionCoordinates shouldBe listOf(...)
}
```

## Error Path Testing

**HttpException construction helper:**

```kotlin
private fun httpException(code: Int, message: String): HttpException {
    val response = Response.error<String>(
        code,
        message.toResponseBody("text/plain".toMediaType()),
    )
    return HttpException(response)
}

@Test
fun `classifyLocationIqFailureReason - invalid key bucket`() {
    val error = httpException(401, "Invalid key")
    NominatimService.classifyLocationIqFailureReason(error) shouldBe "invalid key response hint"
}

@Test
fun `classifyLocationIqFailureReason - network failure bucket`() {
    NominatimService.classifyLocationIqFailureReason(IOException("timeout")) shouldBe "request/network failure"
}
```

## Test Naming Convention

Test names use backtick strings in the format:

```text
`<subject> - <condition or input> - <expected outcome>`
```

Examples:

- `` `isLocationIqKey - pk prefix is accepted` ``
- `` `classifyLocationIqKeyState - malformed key detected` ``
- `` `pickBestVietnamSubProvincePart - multiple matches - last (innermost) wins` ``
- `` `mergeVnResults XVAL-01 - LIQ dirty, Nominatim clean - Nominatim wins` ``

Test IDs (e.g., `XVAL-01`, `ADDR-03`) embedded in the test name or a comment act as
cross-references to the test plan documented in the class-level KDoc block.

## Test Types

**Unit tests (only type present):**

- Target: pure Kotlin logic — parsing, URL normalization, VN address classification, JSON deserialization
- Scope: companion object static methods and data class deserialization
- Android APIs: avoided; `isReturnDefaultValues = true` prevents crashes if any slip through
- No Robolectric, no instrumented tests, no E2E or UI tests in the custom fork additions

**Integration / instrumented tests:** Not observed in the fork-added test files.

**E2E tests:** Not present.

## Test Coverage — Current State

**Well-covered (NominatimServiceTest.kt — 503 lines, ~40 test cases):**

- `isLocationIqKey` — pk. prefix detection
- `classifyLocationIqKeyState` — MISSING / MALFORMED / VALID states
- `normalizeLocationIqBaseUrl` — protocol upgrade, path canonicalization, query stripping
- `resolveLocationIqBaseUrl` — null fallback, override, deprecated host rejection
- `normalizeLegacyLocationIqOverride` — migration from old endpoint format
- `resolveNominatimBaseUrl` — default, custom instance, LocationIQ key rejection
- `resolveLocationIqBaseUrlCandidates` — default, regional, custom, deprecated ordering
- `classifyLocationIqFailureReason` — network / 401 / 429 HTTP buckets
- `pickBestVietnamSubProvincePart` — POI skip, dirty-only null, single match, last-match ordering,
  Đặc Khu prefix, UBND prefix filtering, empty list
- `isCleanVnCity` — valid Phường/Xã, POI prefix, null, empty
- `mergeVnResults` — XVAL-01/02/03 cross-validation scenarios, null LIQ, both empty
- `NominatimAddress` JSON deserialization — suburb, hamlet, quarter, ISO3166-2-lvl4, `@SerialName`
- `NominatimLocationResult` full VN response, structured-fields priority over dirty display_name

**Coverage gaps:**

- `convertLocation` private method — not directly tested; indirectly exercised through integration
- `requestLocationSearch` / `requestNearestLocation` observable pipelines — not tested (require
  Hilt + live Retrofit); RxJava fallback chains are untested in the test suite
- `getAdmin1CodeForCountry` / `getAdmin2CodeForCountry` / `getNonAmbiguousCountryCode` — not tested
- `migrateLegacyLocationIqEndpointOverrideIfNeeded` — depends on `SourceConfigStore`; not tested
- `WatchdogService`, `WatchdogRestartWorker`, `WatchdogAlarmReceiver` — no tests exist

---

*Testing analysis: 2026-05-29*
