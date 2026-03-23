# Testing Patterns

**Analysis Date:** 2026-03-23

## Test Framework

**Runner:**

- JUnit 5 (Jupiter) `6.0.3`
- Config: `testImplementation(libs.bundles.test)` + `testRuntimeOnly(libs.junit.platform)` in `app/build.gradle.kts`
- Version catalog: `gradle/libs.versions.toml`

**Assertion Library:**

- Kotest Assertions Core `6.1.7` — `io.kotest:kotest-assertions-core`
- Uses `shouldBe` infix style (no `assertEquals`)

**Mocking:**

- MockK `1.14.9` — `io.mockk:mockk`

**Coroutines Testing:**

- `kotlinx-coroutines-test` `1.10.2`
- All tests use `= runTest { ... }` even when not strictly async (project convention)

**Run Commands:**

```bash
./gradlew test                  # Run all unit tests
./gradlew app:test              # Run app module unit tests only
./gradlew app:testDebugUnitTest # Run debug variant unit tests
```

---

## Test File Organization

**Location:** `app/src/test/java/org/breezyweather/`

**Mirror structure:** Test packages mirror the main source package structure.

**Naming:** `<ClassUnderTest>Test.kt`

**Current test files:**

```
app/src/test/java/org/breezyweather/
├── LocationTest.kt                         # Empty stub (placeholder only)
├── MatchTest.kt                            # Exploratory/scratch test
├── option/
│   ├── appearance/
│   │   ├── CardDisplayTest.kt              # CardDisplay serialization roundtrip
│   │   └── DailyTrendDisplayTest.kt        # DailyTrendDisplay serialization
│   └── utils/
│       └── UtilsTest.kt                    # UnitUtils.getNameByValue
└── sources/
    └── CommonConverterTest.kt              # getWindDegree parsing
```

**No androidTest directory found** — no instrumented tests; all tests are pure JVM unit tests.

---

## Test Structure

**Standard pattern:**

```kotlin
class CardDisplayTest {

    @Test
    fun toCardDisplayList() = runTest {
        val value = "nowcast&daily_forecast&hourly_forecast&precipitation"
        val list = CardDisplay.toCardDisplayList(value)
        list[0] shouldBe CardDisplay.CARD_NOWCAST
        list[1] shouldBe CardDisplay.CARD_DAILY_FORECAST
    }
}
```

**Key characteristics:**

- No `@BeforeEach`, `@AfterEach`, `@BeforeAll` setup observed — tests are stateless
- No nested `@Nested` classes (flat structure per test class)
- All test functions use `= runTest { ... }` (assigned expression body, not block body with `fun`)
- No explicit `TestDispatcher` or `UnconfinedTestDispatcher` setup — standard `runTest` only

---

## Mocking with MockK

**Creating mocks:**

```kotlin
val res = mockk<Resources>()
val context = mockk<Context>()
```

**Stubbing with `every`:**

```kotlin
every { res.getStringArray(R.array.dark_modes) } returns
    arrayOf("Automatic", "Follow system", "Always light", "Always dark")
every { res.getStringArray(R.array.dark_mode_values) } returns
    arrayOf("auto", "system", "light", "dark")
```

**Application (from `UtilsTest.kt`):**

```kotlin
@Test
fun getNameByValue() = runTest {
    val res = mockk<Resources>()
    every { res.getStringArray(R.array.dark_modes) } returns
        arrayOf("Automatic", "Follow system", "Always light", "Always dark")
    every { res.getStringArray(R.array.dark_mode_values) } returns
        arrayOf("auto", "system", "light", "dark")
    UnitUtils.getNameByValue(res, "auto", R.array.dark_modes, R.array.dark_mode_values) shouldBe "Automatic"
}
```

**Android Context mocking** uses `mockk<Context>().apply { every { getString(...) } returns "..." }`:

```kotlin
val context = mockk<Context>().apply {
    every { getString(any()) } returns "Name"
    every { getString(org.breezyweather.unit.R.string.locale_separator) } returns ", "
}
```

**What is mocked:**

- `android.content.Context`
- `android.content.res.Resources`
- Android resource methods (`getString`, `getStringArray`)

**What is NOT mocked:**

- Pure Kotlin domain objects and data classes
- Static utility functions (called directly)
- Companion object methods (called directly on the class)

---

## Assertion Style

Use **Kotest `shouldBe`** infix assertions exclusively. Do not use `assertEquals`.

**Single value:**

```kotlin
getWindDegree(null) shouldBe null
getWindDegree("E") shouldBe 90.0
getWindDegree("VR") shouldBe -1.0
```

**List element access:**

```kotlin
list[0] shouldBe CardDisplay.CARD_NOWCAST
list[12] shouldBe CardDisplay.CARD_MOON
```

**String equality:**

```kotlin
CardDisplay.toValue(list) shouldBe "nowcast&daily_forecast&hourly_forecast..."
UnitUtils.getNameByValue(...) shouldBe "Automatic"
```

---

## Test Types

**Unit Tests (only type present):**

- Scope: Single class, method, or pure function
- Location: `app/src/test/`
- Dependencies: MockK for Android framework; real objects otherwise

**Integration Tests:** Not present

**E2E / Instrumented Tests:** Not present (`androidTest` not populated)

---

## Coverage

**Requirements:** None enforced — no Jacoco configuration, no coverage threshold found.

**Current state:** Very sparse. Only 5 non-empty test classes covering:

- `CardDisplay` serialization roundtrip (`CardDisplayTest.kt`)
- `DailyTrendDisplay` (similar roundtrip, `DailyTrendDisplayTest.kt`)
- `UnitUtils.getNameByValue` (1 test in `UtilsTest.kt`)
- `getWindDegree` direction parsing (4 assertions in `CommonConverterTest.kt`)
- `MatchTest.kt` is exploratory scratch code with no real assertions

**`LocationTest.kt`** is an empty class — a placeholder with no test methods.

---

## Writing New Tests

**For a new source converter function:**

```kotlin
package org.breezyweather.sources

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MySourceConverterTest {

    @Test
    fun parseTemperature() = runTest {
        parseTemperature("25.0") shouldBe 25.0
        parseTemperature(null) shouldBe null
    }
}
```

**For Vietnamese address parsing (NominatimService):**
NominatimService uses `private` methods, so test the observable output via integration-style test
or extract the parsing logic into a testable `internal` function. Current `pickBestVietnamSubProvincePart`
is `private` — to unit test it, either:

1. Make it `internal` and use `@VisibleForTesting`
2. Test through the public `requestNearestLocation` observable (requires mocking the `NominatimApi`)

**Recommended MockK pattern for Retrofit API mocking:**

```kotlin
val api = mockk<NominatimApi>()
every { api.getReverseLocation(any(), any(), any(), any(), any(), any(), any()) } returns
    Observable.just(fakeResult)
```

---

## Known Test Gaps

| Area | What's Missing | Risk |
|------|---------------|------|
| `NominatimService` VN address parsing | No tests for `pickBestVietnamSubProvincePart` or `convertLocation` | High — regex logic is complex with edge cases |
| `NominatimService` dual-API fallback | No test for LocationIQ+Nominatim zip strategy | High — fallback priority logic untested |
| `CommonConverter` | Only `getWindDegree` tested; many conversion functions uncovered | Medium |
| `BrightSkyService` / all weather sources | Zero test coverage on any weather source | Medium |
| `SourceConfigStore` / configuration | No test for API key detection (`isLocationIqKey`) | Low |
| ViewModel state management | No test for `MainActivityViewModel` | Low (requires Android) |

---

## Test Dependencies (from `gradle/libs.versions.toml`)

```toml
[versions]
junit = "6.0.3"
kotest-assertions = "6.1.7"
kotlinx-coroutines = "1.10.2"
mockk = "1.14.9"

[libraries]
junit = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit" }
junit-platform = { group = "org.junit.platform", name = "junit-platform-launcher" }
kotest-assertions = { group = "io.kotest", name = "kotest-assertions-core", version.ref = "kotest-assertions" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }

[bundles]
test = ["junit", "kotest-assertions", "kotlinx-coroutines-test", "mockk"]
```

---

*Testing analysis: 2026-03-23*
