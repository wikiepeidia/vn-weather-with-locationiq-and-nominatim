<!-- refreshed: 2026-05-29 -->
# Architecture

**Analysis Date:** 2026-05-29

## System Overview

```text
┌────────────────────────────────────────────────────────────────────────┐
│                           UI Layer                                      │
│  MainActivity · HomeFragment · ManagementFragment · SearchActivity      │
│  `app/src/main/kotlin/org/breezyweather/ui/`                           │
└───────────────────────┬────────────────────────────────────────────────┘
                        │ ViewModels (Hilt + StateFlow)
                        ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Application / Sources Layer                          │
│  RefreshHelper  ·  SourceManager  ·  WeatherUpdateJob                  │
│  `app/src/main/kotlin/org/breezyweather/sources/`                      │
│  `app/src/main/kotlin/org/breezyweather/background/`                   │
└──────────┬────────────────────────────┬───────────────────────────────┘
           │ Source interfaces           │ Repositories
           ▼                             ▼
┌──────────────────────┐   ┌─────────────────────────────────────────────┐
│  Sources (70+)        │   │  Data Module                                │
│  nominatim · openmeteo│   │  LocationRepository · WeatherRepository     │
│  accu · metno · …    │   │  SQLDelight database                        │
│  `sources/[name]/`   │   │  `data/src/main/kotlin/breezyweather/data/` │
└──────────────────────┘   └──────────────────────────────────┬──────────┘
                                                               │
                                                               ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Domain Module                                    │
│  Location · Weather · WeatherCode · SourceFeature (pure Kotlin)        │
│  `domain/src/main/kotlin/breezyweather/domain/`                        │
└────────────────────────────────────────────────────────────────────────┘
```

## Gradle Modules

| Module | Role | Package root |
|--------|------|--------------|
| `:app` | Android application — UI, background, sources | `org.breezyweather` |
| `:domain` | Pure Kotlin domain models, zero Android deps | `breezyweather.domain` |
| `:data` | SQLDelight repositories and DB mappers | `breezyweather.data` |
| `:ui-weather-view` | Animated weather canvas view library | — |
| `:weather-unit` | Unit conversion (temperature, wind, pressure…) | — |
| `:maps-utils` | Forked Google Maps geometry utilities | — |

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `BreezyWeather` | Application entry point, Hilt init | `app/.../BreezyWeather.kt` |
| `MainActivity` | Single-Activity host, fragment navigation | `app/.../ui/main/MainActivity.kt` |
| `MainActivityViewModel` | UI state, location list, refresh triggers | `app/.../ui/main/MainActivityViewModel.kt` |
| `RefreshHelper` | Orchestrates multi-source weather + geocoding refresh | `app/.../sources/RefreshHelper.kt` |
| `SourceManager` | Registry of all 70+ `Source` implementations | `app/.../sources/SourceManager.kt` |
| `CommonConverter` | Converts raw source DTOs to domain Weather model | `app/.../sources/CommonConverter.kt` |
| `WeatherUpdateJob` | WorkManager periodic background refresh | `app/.../background/weather/WeatherUpdateJob.kt` |
| `WatchdogService` | Foreground service monitoring WeatherUpdateJob on HyperOS/MIUI | `app/src/main/java/.../background/watchdog/WatchdogService.kt` |
| `WatchdogAlarmReceiver` | AlarmManager receiver that wakes WatchdogService | `app/src/main/java/.../background/watchdog/WatchdogAlarmReceiver.kt` |
| `WatchdogRestartWorker` | WorkManager one-shot worker to restart WatchdogService | `app/src/main/java/.../background/watchdog/WatchdogRestartWorker.kt` |
| `WeatherContentProvider` | Exports weather data to other apps via ContentProvider | `app/.../background/provider/WeatherContentProvider.kt` |
| `NominatimService` | Unified Nominatim + LocationIQ geocoding and search | `app/.../sources/nominatim/NominatimService.kt` |
| `LocationRepository` | CRUD for persisted locations | `data/.../data/location/LocationRepository.kt` |
| `WeatherRepository` | CRUD for cached weather data | `data/.../data/weather/WeatherRepository.kt` |
| `SettingsManager` | App-wide settings access layer | `app/.../domain/settings/SettingsManager.kt` |
| `SourceConfigStore` | Per-source API key and config persistence | `app/.../domain/settings/SourceConfigStore.kt` |

## Pattern Overview

**Overall:** Clean Architecture with a Source Plugin Pattern

**Key Characteristics:**
- Strict module boundaries: `domain` has zero Android dependencies; `data` depends only on `domain`; `:app` depends on all
- All weather sources implement interfaces from `app/.../common/source/` — adding a new source means implementing the right interfaces and registering in `SourceManager`
- Dependency injection via Hilt throughout `:app`; `:domain` and `:data` are plain Kotlin modules
- UI is a single-Activity architecture (Compose + Fragment hybrid) with MVVM ViewModels
- Background refresh driven by WorkManager (`WeatherUpdateJob`); supplemented by `WatchdogService` on MIUI-class ROMs

## Layers

**Domain Layer:**
- Purpose: Core business models with no platform dependencies
- Location: `domain/src/main/kotlin/breezyweather/domain/`
- Contains: `location/model/Location.kt`, `weather/model/*.kt` (Weather, Daily, Hourly, AirQuality, Pollen, Alert, etc.), `source/SourceFeature.kt`, `source/SourceContinent.kt`
- Depends on: Nothing (pure Kotlin)
- Used by: `:data`, `:app`

**Data Layer:**
- Purpose: Persistence — SQLDelight database, repository implementations
- Location: `data/src/main/kotlin/breezyweather/data/`
- Contains: `location/LocationRepository.kt`, `location/LocationMapper.kt`, `weather/WeatherRepository.kt`, `weather/WeatherMapper.kt`, `AndroidDatabaseHandler.kt`
- Depends on: `:domain`, SQLDelight, Android context
- Used by: `:app`

**Sources Layer (in :app):**
- Purpose: All external data provider integrations — weather APIs, geocoding, location
- Location: `app/src/main/kotlin/org/breezyweather/sources/`
- Contains: One sub-package per provider (e.g., `nominatim/`, `openmeteo/`, `metno/`, `accu/`); each has a `*Service.kt` implementing source interfaces, `*Api.kt` Retrofit interface, and `json/` DTOs
- Depends on: `:domain`, Retrofit, RxJava3/Coroutines, Hilt
- Used by: `RefreshHelper`, `SourceManager`

**Background Layer (in :app):**
- Purpose: Background work scheduling, notification delivery, widget updates
- Location: `app/src/main/kotlin/org/breezyweather/background/`
- Contains: `weather/WeatherUpdateJob.kt`, `weather/WeatherUpdateNotifier.kt`, `forecast/` (notification jobs), `receiver/` (broadcast receivers), `interfaces/TileService.kt`, `provider/WeatherContentProvider.kt`
- Fork additions: `app/src/main/java/org/breezyweather/background/watchdog/` — three files for MIUI/HyperOS recovery (not yet migrated to `kotlin/`)
- Depends on: WorkManager, AlarmManager, Hilt

**UI Layer (in :app):**
- Purpose: All user-facing screens
- Location: `app/src/main/kotlin/org/breezyweather/ui/`
- Contains: `main/` (MainActivity, HomeFragment, ManagementFragment, ViewModels), `search/`, `details/`, `alert/`, `settings/`, `about/`, `theme/`, `common/`
- Pattern: MVVM — Activities/Fragments observe `StateFlow` from ViewModels; Jetpack Compose used in settings and secondary screens; View system in main weather display
- Depends on: `:domain`, `:ui-weather-view`, `:weather-unit`, Hilt ViewModels

**Common Layer (in :app):**
- Purpose: Shared utilities, source interface contracts, Hilt DI modules
- Location: `app/src/main/kotlin/org/breezyweather/common/`
- Contains: `source/` — all source interface definitions; `di/` — Hilt modules (`DbModule`, `HttpModule`, `RxModule`); `utils/`, `extensions/`, `exceptions/`, `preference/`

**Remote Views Layer (in :app):**
- Purpose: Home screen widgets and persistent notification presenters
- Location: `app/src/main/kotlin/org/breezyweather/remoteviews/`
- Contains: `presenters/`, `config/` (widget config activities), `trend/` (chart views), `Notifications.kt`, `Widgets.kt`

## Source Interface Contracts

All source capability declarations live in `app/src/main/kotlin/org/breezyweather/common/source/`:

| Interface | Capability |
|-----------|-----------|
| `Source` | Base: `id`, `name` — every source implements this |
| `WeatherSource` | Provides forecast weather data |
| `LocationSource` | Provides device GPS/network location |
| `LocationSearchSource` | Searches locations by query string |
| `ReverseGeocodingSource` | Converts coordinates to address |
| `HttpSource` | Marks source as using HTTP (Retrofit client injection) |
| `ConfigurableSource` | Source has user-configurable preferences (API keys, base URLs) |
| `NonFreeNetSource` | Source only available in `basic` flavor (non-free dependencies) |
| `TimeZoneSource` | Source provides timezone lookup for location |
| `PollenIndexSource` | Source provides pollen data |
| `BroadcastSource` | Source can broadcast weather data to external apps (GadgetBridge etc.) |

## NominatimService (Fork-Specific)

`app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` is the fork's unified geocoding source.

- Implements: `HttpSource`, `LocationSearchSource`, `ReverseGeocodingSource`, `ConfigurableSource`
- Supports standard Nominatim instances (reverse geocoding only) OR LocationIQ (search + reverse) when a `pk.xxxx` API key is configured
- Source ID: `"nominatim"`, displayed name: `"Nominatim / LocationIQ"`
- Default geocoding source: set via `BuildConfig.DEFAULT_GEOCODING_SOURCE = "nominatim"` (in `app/build.gradle.kts`)
- JSON models: `sources/nominatim/json/NominatimLocationResult.kt`, `NominatimAddress.kt`
- No separate LocationIQ module — all logic is inside `NominatimService`

## Product Flavors and Source Sets

Two product flavors with `flavorDimension = "default"` (defined in `app/build.gradle.kts`):

| Flavor | Source Set Added | Purpose |
|--------|-----------------|---------|
| `basic` | `app/src/src_nonfreenet/` + `app/src/res_nonfreenet/` | Full source list including proprietary API sources |
| `freenet` | `app/src/src_freenet/` + `app/src/res_freenet/` | Free/open sources only (F-Droid compatible) |

Both flavors also add `app/src/res_fork/` (fork-specific resources).

Some `*Service.kt` files exist in both flavor source sets with different implementations (e.g., `AccuService.kt` has a stub in `src_freenet/` and a full implementation in `src_nonfreenet/`).

## Data Flow

### Weather Refresh

1. User triggers refresh or WorkManager fires `WeatherUpdateJob` (`app/.../background/weather/WeatherUpdateJob.kt`)
2. `WeatherUpdateJob` calls `RefreshHelper.getWeather()` (`app/.../sources/RefreshHelper.kt`)
3. `RefreshHelper` resolves active sources via `SourceManager`, calls source HTTP APIs in parallel (coroutines + RxJava3)
4. Raw API responses converted by `CommonConverter.kt` (`app/.../sources/CommonConverter.kt`) into domain `Weather` model
5. `WeatherRepository.save()` persists to SQLDelight DB (`data/.../weather/WeatherRepository.kt`)
6. `LocationRepository` updates cached location metadata
7. `MainActivityViewModel` `StateFlow` emits updated state → `HomeFragment` redraws

### Location Search

1. User types query in `SearchActivity` (`app/.../ui/search/SearchActivity.kt`)
2. Query dispatched to configured `LocationSearchSource` (default: `openmeteo`; or `nominatim` when LocationIQ key is set)
3. `NominatimService.requestLocationSearch()` calls Nominatim/LocationIQ REST API via `NominatimApi` Retrofit interface
4. Results mapped to `Location` domain models and displayed in search list

### WatchdogService Health Check (HyperOS/MIUI)

1. `WatchdogService` starts as foreground service on boot (via `BootReceiver`) or settings toggle
2. `WatchdogService` schedules a repeating `AlarmManager` alarm (~15-minute interval)
3. `WatchdogAlarmReceiver` fires; starts `WatchdogService` if process was killed
4. `WatchdogService.onStartCommand()` queries WorkManager for `WeatherUpdateJob` state
5. If not RUNNING or ENQUEUED, re-enqueues the job via `WatchdogRestartWorker`

All three watchdog files reside under `app/src/main/java/org/breezyweather/background/watchdog/` (legacy `java/` source root, not yet migrated).

## Dependency Injection

Hilt throughout `:app`. Key DI modules:

- `HttpModule.kt` (`app/.../common/di/HttpModule.kt`) — provides named Retrofit clients: `@Named("JsonClient")`, `@Named("XmlClient")`
- `DbModule.kt` (`app/.../common/di/DbModule.kt`) — provides SQLDelight database, `LocationRepository`, `WeatherRepository`
- `RxModule.kt` (`app/.../common/di/RxModule.kt`) — provides RxJava3 `Scheduler` instances

Each `*Service.kt` source uses `@Inject constructor` and is registered in `SourceManager` which is itself a Hilt singleton.

## Error Handling

**Strategy:** Typed error results surfaced through `WeatherResult` and `LocationResult` wrappers

**Patterns:**
- Network/API errors mapped to `RefreshError` enum (`app/.../common/source/RefreshError.kt`)
- Typed exceptions for known failure modes: `ApiKeyMissingException`, `InvalidLocationException`, `NoNetworkException`, `ReverseGeocodingException`, `WeatherException` in `app/.../common/exceptions/`
- RxJava3 `onErrorReturn` / `onErrorResumeNext` in source calls; coroutine `try/catch` in `RefreshHelper`
- UI displays per-source error state through `WeatherResult` observable in `MainActivityViewModel`

## Architectural Constraints

- **Threading:** WorkManager manages background thread pool; UI on main thread via `StateFlow`; source HTTP calls on RxJava3 IO scheduler or coroutine `Dispatchers.IO`
- **Global state:** `SettingsManager` is a Hilt singleton (`app/.../domain/settings/SettingsManager.kt`); `SourceConfigStore` is a `SharedPreferences`-backed singleton keyed per source ID
- **Watchdog source location:** The three watchdog Kotlin files remain under `app/src/main/java/` — both `java/` and `kotlin/` source roots are compiled; the split is a navigation hazard when grepping
- **Flavor boundary:** `NonFreeNetSource` marker interface gates sources to `basic` flavor; `SourceManager` checks this at runtime and omits non-free sources in `freenet` builds

## Anti-Patterns

### Mixed java/kotlin source roots for watchdog

**What happens:** `WatchdogService.kt`, `WatchdogAlarmReceiver.kt`, and `WatchdogRestartWorker.kt` live under `app/src/main/java/` while all other Kotlin source is under `app/src/main/kotlin/`
**Why it's wrong:** Breaks the convention established by the upstream Kotlin migration; tools and greps targeting `kotlin/` will miss these files
**Do this instead:** Move all three to `app/src/main/kotlin/org/breezyweather/background/watchdog/`

### Direct SharedPreferences access outside SettingsManager

**What happens:** Some call sites read preferences directly via `getSharedPreferences()` instead of going through `SettingsManager`
**Why it's wrong:** Preference key changes require hunting all call sites; no single source of truth
**Do this instead:** Route all app preference access through `SettingsManager` (`app/.../domain/settings/SettingsManager.kt`) or `SourceConfigStore` for source-specific keys

## Cross-Cutting Concerns

**Logging:** `android.util.Log` used directly, tagged per class; no centralized logging facade
**Validation:** Source input validated in `RefreshHelper`; API key presence checked via `ConfigurableSource.isConfigured()`
**Authentication:** Per-source API keys stored in `SourceConfigStore` (SharedPreferences), injected at Retrofit call time via `@Named` Retrofit builder

---

*Architecture analysis: 2026-05-29*
