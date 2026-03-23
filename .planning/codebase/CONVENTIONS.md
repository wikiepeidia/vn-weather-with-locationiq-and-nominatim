# Coding Conventions

**Analysis Date:** 2026-03-23

## Language & Formatting

**Primary Language:** Kotlin (100% of app code — Java interop only via libraries)

**Formatting:**

- Tool: Not detected (no `.editorconfig`, no `ktlint` or `detekt` config at root); follow existing indentation style
- Indentation: 4 spaces
- Line length: ~120 chars (lines wrap in terminal display but no enforced limit found)

**Linting:**

- No `.detekt` or `ktlint` config found; code style is enforced by convention and code review only

---

## File Header

Every source file begins with an LGPL v3 license header block comment. This is mandatory:

```kotlin
/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 * ...
 */
```

---

## Naming Patterns

**Files:**

- Classes: PascalCase filename matching the class name (`NominatimService.kt`, `MainActivityViewModel.kt`)
- Interfaces: PascalCase, named by capability (`WeatherSource`, `LocationSearchSource`, `ReverseGeocodingSource`, `ConfigurableSource`)
- API interfaces: `<ServiceName>Api.kt` (e.g., `NominatimApi.kt`, `BrightSkyApi.kt`)
- JSON models: `<ServiceName>Address.kt`, `<ServiceName>LocationResult.kt` under `json/` subdirectory
- DI modules: `<Area>Module.kt` (e.g., `HttpModule.kt`, `DbModule.kt`, `RxModule.kt`)

**Functions:**

- camelCase
- Suspend functions follow same naming as regular functions
- Observable-returning functions: `requestWeather(...)`, `requestLocationSearch(...)`, `requestNearestLocation(...)`
- Private helpers: descriptive camelCase (e.g., `pickBestVietnamSubProvincePart`, `getNonAmbiguousCountryCode`, `isLocationIqKey`)

**Variables:**

- camelCase
- Private backing properties: underscore-prefixed `_uiState` exposed as public `val uiState`
- Private API field: `mApi` (m-prefix for lazily-initialized API client)
- Constants: `SCREAMING_SNAKE_CASE` in `companion object`

**Types:**

- Interfaces: PascalCase, prefixed with `I` is NOT used; name describes the capability
- Data classes (JSON): PascalCase with `@Serializable`

---

## Import Organization

1. `android.*` / `androidx.*`
2. `breezyweather.domain.*` / `breezyweather.data.*` (domain/data modules)
3. Third-party libraries (`dagger.*`, `io.reactivex.*`, `kotlinx.*`, `retrofit2.*`, `javax.inject.*`)
4. Project-local `org.breezyweather.*`
5. `java.*` / `kotlin.*`

No explicit enforced grouping; IDE auto-sort is assumed. No wildcard imports.

---

## Dependency Injection (Hilt)

The project uses **Hilt** (Dagger Hilt) for dependency injection throughout.

**Key annotations in use:**

- `@HiltViewModel` on ViewModels (e.g., `MainActivityViewModel`, `AlertViewModel`)
- `@Inject constructor(...)` on Services and Repositories
- `@Module` + `@InstallIn(SingletonComponent::class)` on DI modules
- `@Provides` + `@Singleton` for singleton bindings
- `@Named("JsonClient")` / `@Named("XmlClient")` for qualifying multiple Retrofit builders
- `@ApplicationContext` for injecting Context

**DI Module locations:**

- `app/src/main/java/org/breezyweather/common/di/HttpModule.kt` — OkHttp, Retrofit, RxJava3 adapter, serialization converters
- `app/src/main/java/org/breezyweather/common/di/DbModule.kt` — SQLDelight database
- `app/src/main/java/org/breezyweather/common/di/RxModule.kt` — RxJava scheduler module

**Pattern — Service injection:**

```kotlin
class NominatimService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") private val client: Retrofit.Builder,
) : HttpSource(), LocationSearchSource, ReverseGeocodingSource, ConfigurableSource {
```

**Pattern — ViewModel injection:**

```kotlin
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val statementManager: StatementManager,
    private val refreshHelper: RefreshHelper,
    ...
) : BreezyViewModel(application) {
```

---

## Network Layer (Retrofit + RxJava3)

**API interface pattern** — Retrofit interface returning `Observable<T>`:

```kotlin
interface NominatimApi {
    @GET("reverse")
    fun getReverseLocation(
        @Header("Accept-Language") acceptLanguage: String,
        @Header("User-Agent") userAgent: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("zoom") zoom: Int = 13,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("key") key: String? = null,
    ): Observable<NominatimLocationResult>
}
```

**API client instantiation** — lazy property using injected `Retrofit.Builder`:

```kotlin
private val mApi by lazy {
    client
        .baseUrl(instance ?: NOMINATIM_BASE_URL)
        .build()
        .create(NominatimApi::class.java)
}
```

**Named clients provided by `HttpModule`:**

- `@Named("JsonClient")` — kotlinx-serialization JSON converter + RxJava3 adapter
- `@Named("XmlClient")` — kotlinx-xml (xmlutil) converter + RxJava3 adapter

**Note (active TODO in `HttpModule.kt`):** There is a stated migration intent to `suspend` functions (coroutines) for Retrofit calls, but this is not yet implemented. All source-layer network calls are `Observable<T>` (RxJava3).

---

## Reactive Patterns (RxJava3 in Sources Layer)

All `WeatherSource`, `LocationSearchSource`, and `ReverseGeocodingSource` methods return `Observable<T>`.

**Parallel multi-endpoint calls** use `Observable.zip(obs1, obs2, ...) { results... -> ... }`:

```kotlin
return Observable.zip(weather, curWeather, alerts) { brightSkyWeather, brightSkyCurWeather, brightSkyAlerts ->
    WeatherWrapper(...)
}
```

**Per-feature error isolation** with `onErrorResumeNext`:

```kotlin
val weather = mApi.getWeather(...).onErrorResumeNext {
    failedFeatures[SourceFeature.FORECAST] = it
    Observable.just(BrightSkyWeatherResult())
}
```

**Graceful fallback** with `onErrorReturn`:

```kotlin
val locationIQObs = locationIQClient.getReverseLocation(...)
    .map { ... }
    .onErrorReturn { emptyList() }
```

**Concurrent dual-API strategy** (as in `NominatimService`):

```kotlin
return Observable.zip(locationIQObs, nominatimObs) { liqList, nomList ->
    val liqInfo = liqList.firstOrNull()
    if (liqInfo != null) liqList
    else if (nomList.firstOrNull() != null) nomList
    else throw InvalidLocationException()
}
```

---

## Coroutines & Flow (ViewModel/UI Layer)

ViewModels use Kotlin Coroutines + StateFlow. **Do not** use RxJava in the ViewModel or UI layer.

**StateFlow pattern:**

```kotlin
private val _uiState = MutableStateFlow(AlertUiState())
val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()
```

**Launching coroutines from ViewModel:**

```kotlin
viewModelScope.launchIO {
    // background work
}
```

**Coroutine extension helpers** in `app/src/main/java/org/breezyweather/common/extensions/CoroutinesExtensions.kt`:

- `CoroutineScope.launchIO(block)` — launch on `Dispatchers.IO`
- `CoroutineScope.launchUI(block)` — launch on `Dispatchers.Main`
- `suspend fun withIOContext(block)` — `withContext(Dispatchers.IO, block)`
- `suspend fun withUIContext(block)` — `withContext(Dispatchers.Main, block)`

**Collecting StateFlow in Compose UI:**

```kotlin
val alertUiState by alertViewModel.uiState.collectAsState()
```

---

## JSON Serialization

Uses **kotlinx.serialization** (not Gson/Moshi).

**Data class pattern:**

```kotlin
@Serializable
data class NominatimAddress(
    val village: String?,
    val town: String?,
    @SerialName("country_code") val countryCode: String?,
    @SerialName("ISO3166-2-lvl4") val isoLvl4: String?,
)
```

**JSON configuration** (in `HttpModule`):

```kotlin
val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = !BreezyWeather.instance.debugMode
}
```

---

## Error Handling

**Strategy:** Throw domain-specific `Exception` subclasses. No `Result<T>` or sealed class wrappers. Callers handle errors via RxJava3's error channel or `try/catch` in coroutines.

**Exception hierarchy** — all in `app/src/main/java/org/breezyweather/common/exceptions/`:

| Exception | Meaning |
|-----------|---------|
| `InvalidLocationException` | Geocoding returned no usable location |
| `InvalidOrIncompleteDataException` | Parsed data is missing required fields |
| `NoNetworkException` | No network connectivity |
| `ApiKeyMissingException` | Source requires API key not configured |
| `ApiLimitReachedException` | Rate limit hit |
| `ApiUnauthorizedException` | API key rejected |
| `ParsingException` | JSON/XML parse failure |
| `ReverseGeocodingException` | Reverse geocoding failed |
| `WeatherException` | General weather request failure |
| `OutdatedServerDataException` | Server returned stale data |
| `UnsupportedFeatureException` | Requested feature not supported by source |
| `SourceNotInstalledException` | Source module not installed |

**RxJava error side-effects** — `failedFeatures` map collects per-feature errors without aborting the whole request:

```kotlin
val failedFeatures = mutableMapOf<SourceFeature, Throwable>()
val weather = mApi.getWeather(...).onErrorResumeNext {
    failedFeatures[SourceFeature.FORECAST] = it
    Observable.just(BrightSkyWeatherResult())
}
```

---

## Source Implementation Pattern

All weather data sources follow a consistent structure:

1. Class annotated with `@Inject constructor(...)` extending `HttpSource()` and implementing capability interfaces
2. `override val id = "sourceId"` — unique lowercase kebab-case string
3. `override val name = "Display Name"` — human-readable
4. `override val continent = SourceContinent.XXX`
5. `override val supportedFeatures = mapOf(SourceFeature.XXX to attribution)`
6. `private val mApi by lazy { client.baseUrl(...).build().create(Api::class.java) }`
7. `override fun requestWeather(context, location, requestedFeatures): Observable<WeatherWrapper>`
8. Companion object with `BASE_URL`, `USER_AGENT`, and regex/constant definitions

**Stub pattern** for flavor-gated sources: `<ServiceName>Stub.kt` provides a no-op stub when the source is not available in that build flavor.

---

## Vietnamese Address Parsing Pattern

Location: `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt`

**Regex definition** (uses `java.util.regex.Pattern` for `(?iu)` flag support):

```kotlin
private val vnSubProvinceRegex = Pattern.compile(
    "(?iu)^(?:xã|phường|đặc\\s*khu|xa|phuong|dac\\s*khu)\\s+.*"
)
```

**Display name splitting:**

```kotlin
private val COMMA_SPLIT_REGEX = Regex("[,，]")  // handles full-width comma too
val parts = displayName.split(COMMA_SPLIT_REGEX).map { it.trim() }
```

**Part selection** — picks the last matching segment to avoid institutional prefixes like "Ủy ban nhân dân Phường...":

```kotlin
private fun pickBestVietnamSubProvincePart(parts: List<String>): String? {
    return parts.lastOrNull { part ->
        vnSubProvinceRegex.matcher(part).matches()
    }
}
```

**Dual-API strategy** — only active when a LocationIQ key (`pk.xxx`) is configured; otherwise falls back to Nominatim-only:

```kotlin
private fun isLocationIqKey(value: String?): Boolean = value?.startsWith("pk.") == true
```

---

## Source Configuration Pattern

Sources implementing `ConfigurableSource` use `SourceConfigStore` (SharedPreferences wrapper):

```kotlin
private val config = SourceConfigStore(context, id)
override val isConfigured = true
private var instance: String?
    set(value) { value?.let { config.edit().putString("instance", it).apply() } ?: config.edit().remove("instance").apply() }
    get() = config.getString("instance", null) ?: NOMINATIM_BASE_URL

override fun getPreferences(context: Context): List<Preference> = listOf(
    EditTextPreference(
        titleId = R.string.settings_weather_source_nominatim_instance,
        summary = { _, content -> ... },
        onValueChanged = { instance = ... }
    )
)
```

---

## Module/Barrel Organization

- `app/src/main/java/org/breezyweather/common/source/` — all source interfaces (contracts)
- `app/src/main/java/org/breezyweather/common/exceptions/` — all exception types
- `app/src/main/java/org/breezyweather/common/extensions/` — all Kotlin extension functions
- `app/src/main/java/org/breezyweather/common/di/` — Hilt DI modules
- `app/src/main/java/org/breezyweather/sources/<name>/` — each source in its own package
- `app/src/main/java/org/breezyweather/sources/<name>/json/` — JSON data classes for that source

---

## Comments

**When to comment:**

- KDoc block comments on interface methods (see `WeatherSource.kt` inline doc for `testingLocations` and `requestWeather`)
- Inline comments for non-obvious logic (country code disambiguation in `getNonAmbiguousCountryCode`)
- `TODO:` for known deferred work (e.g., `// TODO: We should probably migrate to suspend`)
- Class-level KDoc for complex services

**Style:**

- Block `/** ... */` for public API/interface documentation
- Single-line `// comment` for implementation notes
- Multi-line `/* ... */` for license header only

---

*Convention analysis: 2026-03-23*
