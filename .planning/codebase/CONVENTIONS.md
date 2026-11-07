# Coding Conventions

**Analysis Date:** 2026-05-29

## Naming Patterns

**Files:**
- Kotlin source files: `PascalCase.kt` — one top-level class/interface per file, name matches class
  - Examples: `NominatimService.kt`, `NominatimApi.kt`, `NominatimAddress.kt`
- JSON model files: grouped under a `json/` subpackage within each source module
  - `app/src/main/kotlin/org/breezyweather/sources/nominatim/json/NominatimAddress.kt`
  - `app/src/main/kotlin/org/breezyweather/sources/nominatim/json/NominatimLocationResult.kt`
- Test files: `<Subject>Test.kt` placed in a mirror package under the test source set
  - Upstream tests live in `app/src/test/kotlin/`
  - Custom fork tests placed in `app/src/test/java/` (valid Kotlin; different source root for fork isolation)

**Classes:**
- `PascalCase` for all classes, interfaces, and objects
- Service classes implement interfaces and carry a `Source` suffix: `NominatimService`, `HttpSource`
- Enum entries: `SCREAMING_SNAKE_CASE` — e.g., `LocationIqKeyState.MISSING`, `MALFORMED`, `VALID`
- Companion object enums: `internal enum class` inside the companion block of the owning class

**Functions and methods:**
- `camelCase` for regular functions: `isLocationIqKey`, `classifyLocationIqKeyState`, `normalizeLocationIqBaseUrl`
- Backtick-delimited sentence-style names for test functions (JUnit 5 display-name convention):
  ```kotlin
  fun `isLocationIqKey - pk prefix is accepted`()
  fun `pickBestVietnamSubProvincePart - dirty-only list returns null`()
  ```
- Composable functions: `ktlint_function_naming_ignore_when_annotated_with = Composable` permits
  PascalCase without a ktlint warning

**Variables and properties:**
- `camelCase` for local variables and instance properties
- `SCREAMING_SNAKE_CASE` for `const val` and `private const val` in companion objects:
  ```kotlin
  private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"
  private const val LOCATIONIQ_BASE_URL = "https://eu1.locationiq.com/v1/"
  private const val LOCATIONIQ_BASE_URL_PREF_KEY = "locationiq_base_url"
  ```
- `val` preferred over `var`; `var` used only for config-backed mutable properties with explicit getter/setter

**Types:**
- `PascalCase` for all type names
- Data classes for JSON models: `NominatimAddress`, `NominatimLocationResult`
- `@SerialName("snake_case_key")` when a JSON key differs from the Kotlin property name:
  ```kotlin
  @SerialName("country_code") val countryCode: String?
  @SerialName("ISO3166-2-lvl4") val isoLvl4: String?
  @SerialName("place_id") val placeId: Int?
  ```

## Code Style

**Formatter:** Spotless with a ktlint backend.

**Configuration files:**
- `buildSrc/src/main/kotlin/breezy.code.lint.gradle.kts` — applies `com.diffplug.spotless` plugin
  to all modules; targets `**/*.kt`, `**/*.kts`, `**/*.xml`
- `.editorconfig` at project root — authoritative settings for both editors and ktlint

**Key settings (from `.editorconfig`):**
| Setting | Value |
|---------|-------|
| Indent — Kotlin (`*.kt`, `*.kts`) | 4 spaces |
| Indent — XML / SQL | 4 spaces |
| Indent — all other files | 2 spaces |
| Max line length — Kotlin | 120 characters |
| Trailing commas in declarations | Allowed |
| Trailing commas on call sites | Not allowed |
| Star imports | Disabled (`count = 2147483647`) |
| Code style | `intellij_idea` |
| Final newline | Required |
| Trailing whitespace | Trimmed |

**Disabled ktlint rules:**
- `class-signature`, `comment-wrapping`, `discouraged-comment-location`
- `function-expression-body`, `function-signature`
- `type-argument-comment`, `type-parameter-comment`
- `blank-line-between-when-conditions`

**Enforcement commands:**
```bash
./gradlew spotlessCheck   # fails CI if any file is unformatted
./gradlew spotlessApply   # auto-format in place
```

## Import Organization

**Order (enforced by ktlint `intellij_idea` style):**
1. `android.*` imports
2. Third-party library imports (`breezyweather.*`, `dagger.*`, `io.reactivex.*`, etc.)
3. Internal project imports (`org.breezyweather.*`)
4. Standard library (`java.*`, `javax.*`, `kotlin.*`)

Star imports are disabled; each symbol must be imported individually.

**Example from `NominatimService.kt`:**
```kotlin
import android.content.Context
import android.util.Log
import breezyweather.domain.location.model.LocationAddressInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.common.source.HttpSource
import retrofit2.HttpException
import java.io.IOException
import java.net.URI
import javax.inject.Inject
```

## Dependency Injection

**Framework:** Hilt (Dagger/Hilt for Android)

- Constructor injection only — annotate constructors with `@Inject constructor`
- Android qualifiers: `@ApplicationContext context: Context`
- Named bindings for Retrofit clients: `@Named("JsonClient") private val client: Retrofit.Builder`
- No field injection observed in the scanned sources
- KSP (`ksp(libs.kotlin.metadata.jvm)` + `ksp(libs.dagger.hilt.compiler)`) generates Hilt code

```kotlin
class NominatimService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") private val client: Retrofit.Builder,
) : HttpSource(), LocationSearchSource, ReverseGeocodingSource, ConfigurableSource {
```

## Async / Reactive Pattern

**Framework:** RxJava3 — `io.reactivex.rxjava3.core.Observable`

All network operations return `Observable<T>`. Never return raw values or suspend functions from
source interface methods.

```kotlin
override fun requestLocationSearch(context: Context, query: String): Observable<List<LocationAddressInfo>>
override fun requestNearestLocation(context: Context, latitude: Double, longitude: Double): Observable<List<LocationAddressInfo>>
```

**RxJava error handling operators:**
- `.onErrorResumeNext { e -> Observable<T> }` — fall back to another observable on failure
- `.onErrorReturn { e -> T }` — recover to a fallback value (e.g., `emptyList()`)
- `.flatMap { ... }` — chain dependent async calls where the inner call may be conditional

**Coroutine bridge:** `kotlinx-coroutines-rx3` is on the classpath for any interop needs. Tests
use `kotlinx.coroutines.test.runTest` for coroutine-based tests.

## Serialization

**Framework:** `kotlinx.serialization` (JSON)

- Data classes annotated with `@Serializable`
- Field mapping: `@SerialName("json_key")` wherever JSON key differs from property name
- Global opt-in: `ExperimentalSerializationApi` enabled in `app/build.gradle.kts`
  via `freeCompilerArgs`
- Parser config used in tests (mirrors production):
  ```kotlin
  private val json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
  }
  ```

## Companion Object — Testable Logic Pattern

Pure logic that must be unit-testable without the Android runtime is placed in the `companion object`
with `internal` visibility. The class body holds thin private delegation wrappers:

```kotlin
// Instance wrapper — used inside the service
private fun isLocationIqKey(value: String?): Boolean = Companion.isLocationIqKey(value)

// Companion — directly callable by tests
internal fun isLocationIqKey(value: String?): Boolean = value?.startsWith("pk.") == true
```

Tests call `NominatimService.isLocationIqKey(...)` without constructing the full Hilt-injected
service. Apply this pattern to all pure-logic helpers in source classes.

## Error Handling

**Patterns:**
- Exceptions classified into semantic strings via a classify function:
  `classifyLocationIqFailureReason(error: Throwable): String`
- Domain exceptions thrown into the RxJava stream: `throw InvalidLocationException()`
- Android `Log.d("NominatimService", "...")` for diagnostic logging — no crash reporting SDK
- Intentionally ignored catch blocks use the underscore convention: `catch (_: Exception)`

**Null safety:**
- `?.let`, `?:`, and `?.equals(...) == true` patterns throughout
- `!!` used only when null has been ruled out logically (e.g., immediately after a null check on
  `address.countryCode`)
- Function return types are nullable (`String?`, `LocationAddressInfo?`) when absence is a valid result

## Comments

**License header:** LGPL v3 block comment on every production file:
```
/*
 * This file is part of Breezy Weather.
 * ...
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */
```

**KDoc:** Used on abstract classes, interfaces, and non-obvious public methods to explain intent.

**Inline reference comments:** Section separators using `// ─── Section Title ───` in long files.
Work-item codes cross-reference implementation to test specs:
```kotlin
// GIGL-01: [GiggleRescue] log — Nominatim superseded dirty LIQ result
// REL-02: log Nominatim failures for diagnosability
// PERF-01/02: Deferred Nominatim fetch — only subscribed when LIQ result is dirty
```

**TODO comments:** Preserved verbatim and not stripped by spotless. Used in test helpers and
interfaces to track incomplete paths.

## Module Design

**Visibility:**
- `internal` for companion object helpers that tests need to call directly
- `private` for implementation details used only within the class
- `override` for all interface implementations; no redundant `public` keyword

**Package structure for a source module:**
```
sources/nominatim/
    NominatimService.kt          # Main service — implements HttpSource + feature interfaces
    NominatimApi.kt              # Retrofit API interface
    json/
        NominatimAddress.kt      # @Serializable data class
        NominatimLocationResult.kt
```

**Source root layout:**
- Production Kotlin: `app/src/main/kotlin/org/breezyweather/`
- Watchdog custom code (Kotlin files, java/ source root): `app/src/main/java/org/breezyweather/background/watchdog/`
- Upstream tests: `app/src/test/kotlin/org/breezyweather/`
- Fork-specific tests: `app/src/test/java/org/breezyweather/sources/nominatim/`

---

*Convention analysis: 2026-05-29*
