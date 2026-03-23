# Architecture

**Analysis Date:** 2026-03-23

## Pattern Overview

**Overall:** Clean Architecture + MVVM

**Key Characteristics:**
- Strict multi-module separation: `domain` (pure models) → `data` (persistence) → `app` (business logic + UI)
- ViewModels expose `StateFlow` / `LiveData`; UI observes reactively
- Source abstraction: 50+ pluggable weather/geocoding sources all implement shared interfaces
- Async strategy: RxJava3 `Observable` for HTTP (source layer), Kotlin Coroutines for persistence and WorkManager jobs
- DI via Dagger Hilt with singleton-scoped HTTP clients and typed Retrofit builders

## Modules

**`:domain`** (`data/src/main/java/breezyweather/domain/`):
- Purpose: Pure Kotlin data models, zero Android dependencies
- Contains: Domain entities, source feature enums
- Depends on: Nothing (bottom of dependency graph)
- Used by: `:data`, `:app`, `:ui-weather-view`, `:weather-unit`
- Key files:
  - `domain/location/model/Location.kt` — core location entity (lat/lon, admin levels 1–4, city, district, source config)
  - `domain/location/model/LocationAddressInfo.kt` — reverse-geocoding result DTO
  - `domain/weather/model/Weather.kt`, `Daily.kt`, `Hourly.kt`, etc. — weather domain objects
  - `domain/source/SourceFeature.kt` — enum of capabilities (REVERSE_GEOCODING, FORECAST, AIR_QUALITY, etc.)
  - `domain/source/SourceContinent.kt` — continent grouping enum

**`:data`** (`data/src/main/java/breezyweather/data/`):
- Purpose: Persistence layer using SQLDelight
- Contains: Repository classes, database handler, SQLDelight schema files
- Depends on: `:domain`
- Used by: `:app`
- Key files:
  - `data/location/LocationRepository.kt` — CRUD for saved locations
  - `data/weather/WeatherRepository.kt` — CRUD for cached weather
  - `data/DatabaseHandler.kt`, `AndroidDatabaseHandler.kt` — coroutine-aware SQLDelight access
  - `data/location/LocationMapper.kt`, `data/weather/WeatherMapper.kt` — DB ↔ domain mapping
- SQLDelight schemas: `sqldelight/breezyweather/data/*.sq` (locations, weathers, dailys, hourlys, minutelys, alerts, normals, location_parameters)

**`:app`** (`app/src/main/java/org/breezyweather/`):
- Purpose: All application logic: sources, UI, background jobs, DI wiring
- Contains: Activities, ViewModels, Fragments, source implementations, DI modules, background workers
- Depends on: `:domain`, `:data`, `:maps-utils`, `:ui-weather-view`, `:weather-unit`

**`:ui-weather-view`** (`ui-weather-view/src/main/java/org/breezyweather/ui/`):
- Purpose: Reusable custom weather animation views and theming
- Contains: `theme/` — animated weather drawing components
- Depends on: `:domain`, `:weather-unit`

**`:weather-unit`** (`weather-unit/src/main/java/org/breezyweather/unit/`):
- Purpose: Unit conversion library (temperature, speed, pressure, precipitation, distance, pollen, pollutant, ratio, duration)
- Contains: Sealed classes per unit type with conversion logic
- Depends on: `:domain`
- Key file: `unit/WeatherUnit.kt`, `unit/WeatherValue.kt`

**`:maps-utils`** (`maps-utils/src/`):
- Purpose: Bundled Google Maps Android Utils (SphericalUtil, LatLng) — forked/vendored for offline use
- Depends on: Nothing

## Layers (within `:app`)

**UI Layer** (`app/src/main/java/org/breezyweather/ui/`):
- Pattern: MVVM — Activities/Fragments observe `StateFlow` from `@HiltViewModel` ViewModels
- Sub-packages: `main/`, `search/`, `settings/`, `details/`, `alert/`, `about/`, `common/`, `theme/`
- Key files:
  - `ui/main/MainActivity.kt` — main entry, hosts fragments
  - `ui/main/MainActivityViewModel.kt` — primary ViewModel, central state holder
  - `ui/main/MainActivityModels.kt` — UI state data classes (`Indicator`, `DayNightLocation`, `PermissionsRequest`)
  - `ui/search/SearchActivity.kt` + `SearchViewModel.kt` + `SearchActivityRepository.kt`
  - `ui/main/fragments/HomeFragment.kt`, `ManagementFragment.kt`

**Source Abstraction Layer** (`app/src/main/java/org/breezyweather/common/source/`):
- Purpose: Interface contracts all data sources must implement
- Key interfaces:
  - `Source` — base: `id`, `name`
  - `HttpSource` — adds `privacyPolicyUrl`, `continent`; all network sources extend this abstract class
  - `WeatherSource` — `requestWeather()`
  - `ReverseGeocodingSource` — `requestNearestLocation()`  
  - `LocationSearchSource` — `requestLocationSearch()`
  - `ConfigurableSource` — exposes `Preference` list for settings UI
  - `FeatureSource` — `supportedFeatures: Map<SourceFeature, String>`
  - `LocationSource` — GPS-based location (Android system)

**Source Implementations** (`app/src/main/java/org/breezyweather/sources/`):
- 50+ source directories (accu, openmeteo, metno, nominatim, etc.)
- Each source: API interface (Retrofit), Service class (implements Source interfaces), JSON models
- `SourceManager.kt` — Hilt-injected registry; constructs and exposes all sources as typed lists
- `RefreshHelper.kt` — orchestrates weather/geocoding refresh, called by ViewModel and WorkManager job

**Background Layer** (`app/src/main/java/org/breezyweather/background/`):
- `weather/WeatherUpdateJob.kt` — `CoroutineWorker` (WorkManager) for periodic background refresh
- `forecast/` — forecast notification worker
- `updater/` — in-app update checker (GitHub release check)

**Dependency Injection** (`app/src/main/java/org/breezyweather/common/di/`):
- `HttpModule.kt` — provides `OkHttpClient`, `@Named("JsonClient") Retrofit.Builder`, `@Named("XmlClient") Retrofit.Builder`
- `DbModule.kt` — provides `DatabaseHandler`
- `RxModule.kt` — provides RxJava scheduler wiring
- Entry point: `BreezyWeather.kt` (`@HiltAndroidApp`)

**Settings / Domain Extensions** (`app/src/main/java/org/breezyweather/domain/`):
- `settings/SettingsManager.kt` — singleton SharedPreferences wrapper for all user prefs
- `settings/SourceConfigStore.kt` — per-source API key storage
- `domain/location/model/Location.kt` — app-layer extension functions on the domain `Location`

## Data Flow

**Weather Refresh:**
1. `MainActivityViewModel` triggers refresh → calls `RefreshHelper.getWeather()`
2. `RefreshHelper` resolves source IDs from `Location` fields (forecastSource, airQualitySource, etc.) via `SourceManager`
3. For each `SourceFeature`, calls the appropriate source method returning `Observable<WeatherWrapper>`
4. Observables merged, results converted to domain models, saved via `WeatherRepository`
5. `StateFlow` in ViewModel updated → UI recomposes

**Reverse Geocoding (VN-optimized):**
1. Device GPS → `AndroidLocationService.requestCurrentLocation()`
2. `RefreshHelper.requestReverseGeocodingLocation()` → calls `ReverseGeocodingSource.requestNearestLocation()`
3. `NominatimService` (unified Nominatim/LocationIQ):
   - If LocationIQ key present: fires `Observable.zip()` of LocationIQ + Nominatim calls concurrently
   - For VN (`countryCode == "vn"`): applies `vnSubProvinceRegex` to `display_name` to extract Phường/Xã/Đặc Khu ward names from comma-separated segments
   - If no key: pure Nominatim fallback only
4. Result (`LocationAddressInfo`) stored in `Location.district`/`city` fields
5. Saved via `LocationRepository`

**Location Search:**
1. User types in `SearchActivity` → `SearchViewModel` calls `SearchActivityRepository`
2. Repository calls `LocationSearchSource.requestLocationSearch()` on selected source
3. Returns `Observable<List<LocationAddressInfo>>` → mapped to `Location` → display in list

## Key Abstractions

**`Source` / `HttpSource`:**
- Base building block for all data providers
- `HttpSource` receives a `@Named("JsonClient") Retrofit.Builder` injected by Hilt
- Implementations: `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt`, `sources/openmeteo/OpenMeteoService.kt`, etc.

**`SourceManager`:**
- Location: `app/src/main/java/org/breezyweather/sources/SourceManager.kt`
- Pattern: Hilt `@Singleton`; holds `ImmutableList<Source>` of all registered sources
- Provides typed filtered views: `getWeatherSources()`, `getReverseGeocodingSources()`, etc.

**`RefreshHelper`:**
- Location: `app/src/main/java/org/breezyweather/sources/RefreshHelper.kt`
- Pattern: Application-scoped orchestrator; coordinates multi-source parallel refresh using coroutines
- Entry point for both background jobs and foreground ViewModel refreshes

**`LocationRepository` / `WeatherRepository`:**
- Location: `data/src/main/java/breezyweather/data/location/LocationRepository.kt`, `breezyweather/data/weather/WeatherRepository.kt`
- Pattern: Repository over SQLDelight `DatabaseHandler`; all operations are `suspend` functions

**`NominatimService` (VN-enhanced):**
- Location: `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt`
- Dual-API: acts as unified Nominatim (free, no key) + LocationIQ (API key `pk.*`) service
- VN regex: `vnSubProvinceRegex` — `(?iu)^(?:xã|phường|đặc\s*khu|xa|phuong|dac\s*khu)\s+.*`
- Concurrent strategy: `Observable.zip(locationIQObs, nominatimObs)` when key available

## Entry Points

**Application:**
- `app/src/main/java/org/breezyweather/BreezyWeather.kt` — `@HiltAndroidApp Application`; initializes WorkManager, dark mode, logging

**Main UI:**
- `app/src/main/java/org/breezyweather/ui/main/MainActivity.kt` — launcher Activity; hosts `HomeFragment` and `ManagementFragment`

**Background Refresh:**
- `app/src/main/java/org/breezyweather/background/weather/WeatherUpdateJob.kt` — periodic WorkManager `CoroutineWorker`

## Error Handling

**Strategy:** RxJava `onErrorReturn` for non-fatal source failures; custom exception hierarchy for fatal errors

**Exceptions** (`app/src/main/java/org/breezyweather/common/exceptions/`):
- `InvalidLocationException` — no valid geocode result
- `ReverseGeocodingException` — geocoding step failed
- `ApiKeyMissingException` — source config incomplete
- `NoNetworkException`, `WeatherException`, `LocationException`

**Source error wrapping:** `RefreshError` sealed class propagated up to ViewModel, surfaced as snackbar in UI

---

*Architecture analysis: 2026-03-23*
