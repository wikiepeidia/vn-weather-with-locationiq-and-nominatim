# Codebase Structure

**Analysis Date:** 2026-03-23

## Directory Layout

```
breezy-weather-VN/
├── app/                        # Main application module
│   └── src/main/java/org/breezyweather/
│       ├── BreezyWeather.kt    # Application class (@HiltAndroidApp)
│       ├── Migrations.kt       # DB migration helpers
│       ├── background/         # WorkManager jobs and background services
│       ├── common/             # Shared utilities, DI, source interfaces
│       ├── data/               # App-layer data classes (Contributors.kt)
│       ├── domain/             # App-layer domain extensions and settings
│       ├── remoteviews/        # Widgets and notification views
│       ├── sources/            # All weather/geocoding source implementations
│       ├── ui/                 # Activities, ViewModels, Fragments (MVVM)
│       └── wallpaper/          # Live wallpaper provider
├── data/                       # :data module — persistence layer
│   └── src/main/
│       ├── java/breezyweather/data/    # Repositories, DB handler, mappers
│       └── sqldelight/breezyweather/data/  # SQLDelight .sq schema files
├── domain/                     # :domain module — pure Kotlin domain models
│   └── src/main/java/breezyweather/domain/
│       ├── location/model/     # Location.kt, LocationAddressInfo.kt
│       ├── source/             # SourceFeature.kt, SourceContinent.kt
│       └── weather/            # model/, reference/, wrappers/
├── ui-weather-view/            # :ui-weather-view module — reusable animated views
│   └── src/main/java/org/breezyweather/ui/theme/
├── weather-unit/               # :weather-unit module — unit conversion
│   └── src/main/java/org/breezyweather/unit/
│       ├── temperature/  speed/  pressure/  precipitation/
│       ├── distance/  pollen/  pollutant/  ratio/  duration/
│       ├── WeatherUnit.kt      # Base sealed class
│       └── WeatherValue.kt     # Value + unit container
├── maps-utils/                 # :maps-utils — vendored Google Maps utils
├── buildSrc/                   # Gradle convention plugins
├── gradle/
│   └── libs.versions.toml      # Version catalog for all dependencies
├── settings.gradle.kts         # Module declarations
├── build.gradle.kts            # Root build config
├── signature/
│   ├── testaddress.py          # Manual lat/lon address lookup test script, require to edit if alrogithm changes, sync with the app to quickly test address without building the app
│   └── testaddress.py.bak
└── .planning/codebase/         # GSD architecture documents (this file)
```

## Directory Purposes

**`app/src/main/java/org/breezyweather/sources/`:**

- Purpose: All pluggable weather and geocoding source implementations
- Contains: One subdirectory per source (50+ sources)
- Each source directory contains: `<Name>Api.kt` (Retrofit interface), `<Name>Service.kt` (logic + interface impls), `json/` (serialization models), optional `xml/`
- Special files directly in `sources/`: `SourceManager.kt` (service registry), `RefreshHelper.kt` (refresh orchestrator), `CommonConverter.kt` (shared conversion utilities)
- Key source: `sources/nominatim/` — unified Nominatim/LocationIQ with VN ward-name regex

**`app/src/main/java/org/breezyweather/common/source/`:**

- Purpose: Source interface contracts
- Contains: `Source.kt`, `HttpSource.kt`, `WeatherSource.kt`, `ReverseGeocodingSource.kt`, `LocationSearchSource.kt`, `ConfigurableSource.kt`, `FeatureSource.kt`, `LocationSource.kt`, `AddressSource.kt`, etc.
- All source services implement one or more of these interfaces

**`app/src/main/java/org/breezyweather/common/di/`:**

- Purpose: Hilt DI modules
- `HttpModule.kt` — OkHttpClient, `@Named("JsonClient")` / `@Named("XmlClient")` Retrofit.Builder singletons
- `DbModule.kt` — DatabaseHandler
- `RxModule.kt` — RxJava schedulers

**`app/src/main/java/org/breezyweather/ui/`:**

- Purpose: All UI — Activities, ViewModels, Fragments, Compose screens
- `main/` — `MainActivity`, `MainActivityViewModel`, `HomeFragment`, `ManagementFragment`, adapters, layouts
- `search/` — `SearchActivity`, `SearchViewModel`, `SearchActivityRepository`
- `settings/` — settings screens (`activities/`, `compose/`, `adapters/`, `preference/`, `dialogs/`)
- `details/` — per-day/hour detail screen
- `alert/` — weather alert screen
- `about/` — about screen
- `common/` — shared UI utilities

**`app/src/main/java/org/breezyweather/background/`:**

- Purpose: Background work (WorkManager)
- `weather/WeatherUpdateJob.kt` — periodic weather refresh worker
- `forecast/` — forecasted-event notification worker
- `updater/` — GitHub release checker
- `interfaces/` — background job interfaces

**`app/src/main/java/org/breezyweather/domain/`:**

- Purpose: App-layer extensions on domain models (NOT the `:domain` module)
- `settings/SettingsManager.kt` — `SharedPreferences`-backed user settings singleton
- `settings/SourceConfigStore.kt` — per-source API key storage
- `location/model/Location.kt` — extension functions (`isDaylight`, `getPlace`, `isCloseTo`, `applyDefaultPreset`)

**`app/src/main/java/org/breezyweather/remoteviews/`:**

- Purpose: Android widgets and notifications
- `presenters/` — one presenter per widget type
- `Notifications.kt` — channel setup and notification builders
- `Widgets.kt` — widget registration and update trigger

**`data/src/main/java/breezyweather/data/`:**

- `location/LocationRepository.kt` — all location CRUD (suspend)
- `location/LocationMapper.kt` — SQLDelight DB row → `Location` domain model
- `weather/WeatherRepository.kt` — all weather CRUD (suspend)
- `weather/WeatherMapper.kt` — DB rows → `Weather` domain model
- `DatabaseHandler.kt` — coroutine wrapper around SQLDelight driver
- `AndroidDatabaseHandler.kt` — Android-specific driver init

**`data/src/main/sqldelight/breezyweather/data/`:**

- `.sq` files define all tables and queries used by SQLDelight codegen
- Files: `locations.sq`, `location_parameters.sq`, `weathers.sq`, `dailys.sq`, `hourlys.sq`, `minutelys.sq`, `alerts.sq`, `normals.sq`

**`domain/src/main/java/breezyweather/domain/`:**

- `location/model/Location.kt` — core location data class
- `location/model/LocationAddressInfo.kt` — geocoding result DTO
- `weather/model/*.kt` — 20+ weather entity data classes (Weather, Current, Daily, Hourly, AirQuality, Alert, Pollen, Temperature, Wind, Precipitation, UV, etc.)
- `weather/reference/` — enum references (WeatherCode, Month, etc.)
- `weather/wrappers/WeatherWrapper.kt` — source output wrapper
- `source/SourceFeature.kt`, `SourceContinent.kt`

## Key File Locations

**Entry Points:**

- `app/src/main/java/org/breezyweather/BreezyWeather.kt` — Application class
- `app/src/main/java/org/breezyweather/ui/main/MainActivity.kt` — Launcher Activity
- `app/src/main/java/org/breezyweather/background/weather/WeatherUpdateJob.kt` — Background refresh

**Source Infrastructure:**

- `app/src/main/java/org/breezyweather/sources/SourceManager.kt` — source registry
- `app/src/main/java/org/breezyweather/sources/RefreshHelper.kt` — refresh orchestrator
- `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` — Nominatim/LocationIQ (VN-enhanced)

**Domain Models:**

- `domain/src/main/java/breezyweather/domain/location/model/Location.kt`
- `domain/src/main/java/breezyweather/domain/location/model/LocationAddressInfo.kt`
- `domain/src/main/java/breezyweather/domain/weather/model/Weather.kt`

**Persistence:**

- `data/src/main/java/breezyweather/data/location/LocationRepository.kt`
- `data/src/main/java/breezyweather/data/weather/WeatherRepository.kt`

**Configuration:**

- `gradle/libs.versions.toml` — all dependency versions
- `settings.gradle.kts` — module includes (`:app`, `:data`, `:domain`, `:maps-utils`, `:ui-weather-view`, `:weather-unit`)
- `app/build.gradle.kts` — app module build config with flavors (`freenet`, `nonfreenet`)

**Testing:**

- `app/src/test/` — unit tests for app module
- `weather-unit/src/test/` — unit tests for unit conversions
- `maps-utils/src/test/` — utility tests
- `signature/testaddress.py` — manual integration test for reverse geocoding APIs (Python script)

## Naming Conventions

**Files:**

- Kotlin classes: `PascalCase.kt` (e.g., `NominatimService.kt`, `WeatherUpdateJob.kt`)
- API interfaces: `<SourceName>Api.kt` (e.g., `NominatimApi.kt`, `OpenMeteoApi.kt`)
- Service classes: `<SourceName>Service.kt`
- ViewModel: `<Screen>ViewModel.kt` (e.g., `MainActivityViewModel.kt`, `SearchViewModel.kt`)
- Repository: `<Entity>Repository.kt`
- Mapper: `<Entity>Mapper.kt`
- SQLDelight schemas: `<tablename>.sq` (plural, lowercase)

**Packages:**

- Source modules use reversed domain: `breezyweather.domain.*`, `breezyweather.data.*`
- App module uses `org.breezyweather.*`
- Sources grouped by provider name: `org.breezyweather.sources.nominatim`, `org.breezyweather.sources.openmeteo`

**Directories:**

- Source subdirectory name = source `id` string (e.g., `nominatim/`, `openmeteo/`, `accu/`)
- JSON models under `json/` subdirectory within each source
- XML models under `xml/` subdirectory if the source uses XML responses

## Where to Add New Code

**New Weather/Geocoding Source:**

- Create `app/src/main/java/org/breezyweather/sources/<sourceid>/` directory
- Add `<Name>Api.kt` (Retrofit interface), `<Name>Service.kt` (implements `HttpSource` + feature interfaces), `json/` for models
- Register in `app/src/main/java/org/breezyweather/sources/SourceManager.kt` constructor list

**New Domain Model Field:**

- Edit `domain/src/main/java/breezyweather/domain/location/model/Location.kt` or relevant weather model
- Update `data/src/main/java/breezyweather/data/*/.*Mapper.kt` for DB serialization
- Add migration in `data/src/main/sqldelight/breezyweather/migrations/`

**New UI Screen:**

- Create Activity + ViewModel under `app/src/main/java/org/breezyweather/ui/<screenname>/`
- ViewModel extends `BreezyViewModel` (`common/activities/BreezyViewModel.kt`), annotated `@HiltViewModel`

**New User Setting:**

- Add property to `app/src/main/java/org/breezyweather/domain/settings/SettingsManager.kt`
- Add UI preference in `app/src/main/java/org/breezyweather/ui/settings/`

**New Unit Type:**

- Add sealed class under `weather-unit/src/main/java/org/breezyweather/unit/<unittype>/`
- Follow pattern of existing units (e.g., `temperature/TemperatureUnit.kt`)

**New Background Job:**

- Add `CoroutineWorker` subclass under `app/src/main/java/org/breezyweather/background/`
- Register with WorkManager in `BreezyWeather.kt` or the relevant trigger point

## Special Directories

**`buildSrc/`:**

- Purpose: Gradle convention plugins (shared build logic)
- Generated: No
- Committed: Yes

**`app/src/res_breezy/`, `res_fork/`, `res_freenet/`, `res_nonfreenet/`:**

- Purpose: Flavor-specific resources (icons, strings, configs for different build variants)
- Generated: No
- Committed: Yes

**`app/src/src_freenet/`, `src_nonfreenet/`:**

- Purpose: Flavor-specific Kotlin source files (e.g., F-Droid vs standard builds differ in update checker, non-free sources)
- Generated: No
- Committed: Yes

**`app/build/`, `data/build/`, `domain/build/`:**

- Purpose: Gradle build outputs, generated code (Hilt, KSP, SQLDelight)
- Generated: Yes
- Committed: No

**`.planning/codebase/`:**

- Purpose: GSD architecture analysis documents
- Generated: By GSD mapper agents
- Committed: Yes (project documentation)

---

*Structure analysis: 2026-03-23*
