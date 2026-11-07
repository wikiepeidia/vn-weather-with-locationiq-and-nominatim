<!-- refreshed: 2026-05-29 -->
# Codebase Structure

**Analysis Date:** 2026-05-29

## Directory Layout

```
vn-weather-with-locationiq-and-nominatim/   # Project root
├── app/                        # Android application module
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/org/breezyweather/
│       │   │   └── background/watchdog/    # Fork: WatchdogService, WatchdogAlarmReceiver, WatchdogRestartWorker
│       │   ├── kotlin/org/breezyweather/
│       │   │   ├── BreezyWeather.kt        # Application class
│       │   │   ├── Migrations.kt
│       │   │   ├── background/             # Background jobs, services, receivers, widgets
│       │   │   ├── common/                 # Shared utils, DI modules, source interfaces
│       │   │   ├── data/                   # App-level data (Contributors.kt)
│       │   │   ├── domain/                 # App-level settings + source domain helpers
│       │   │   ├── remoteviews/            # Widgets and notifications
│       │   │   ├── sources/                # All weather/geocoding source implementations
│       │   │   ├── ui/                     # All user-facing screens
│       │   │   └── wallpaper/              # Live wallpaper service + config
│       │   └── res/                        # Shared resources (layouts, drawables, strings)
│       ├── src_nonfreenet/                 # basic flavor source set (proprietary sources)
│       │   └── org/breezyweather/sources/  # AccuService (full), and other non-free sources
│       ├── src_freenet/                    # freenet flavor source set (free/open sources)
│       │   └── org/breezyweather/sources/  # AccuService (stub), free-only source implementations
│       ├── res_nonfreenet/                 # basic flavor resources
│       ├── res_freenet/                    # freenet flavor resources
│       ├── res_fork/                       # Fork-specific shared resources (both flavors)
│       ├── res_breezy/                     # Upstream breezy-weather resources (isBreezy builds only)
│       ├── debug/                          # Debug variant resources
│       └── test/                           # Unit tests
├── data/                       # :data Gradle module — SQLDelight repositories
│   └── src/main/kotlin/breezyweather/data/
│       ├── AndroidDatabaseHandler.kt
│       ├── DatabaseAdapter.kt
│       ├── DatabaseHandler.kt
│       ├── location/
│       │   ├── LocationRepository.kt
│       │   └── LocationMapper.kt
│       └── weather/
│           ├── WeatherRepository.kt
│           └── WeatherMapper.kt
├── domain/                     # :domain Gradle module — pure Kotlin models
│   └── src/main/kotlin/breezyweather/domain/
│       ├── location/model/     # Location, LocationAddressInfo
│       ├── source/             # SourceFeature.kt, SourceContinent.kt
│       └── weather/
│           ├── model/          # Weather, Daily, Hourly, AirQuality, Alert, Pollen, etc.
│           ├── index/          # PollenIndex.kt, PollutantIndex.kt
│           ├── reference/      # WeatherCode, Month enums
│           └── wrappers/       # WeatherWrapper (source response wrapper)
├── ui-weather-view/            # :ui-weather-view module — animated weather canvas
├── weather-unit/               # :weather-unit module — unit conversion
├── maps-utils/                 # :maps-utils module — geometry utilities (SphericalUtil)
├── buildSrc/                   # Gradle convention plugins (breezy.android.application, etc.)
├── config/                     # Upstream config (code style, etc.)
├── config-fork/                # Fork-specific config overrides
├── docs/                       # Documentation
├── fastlane/                   # F-Droid / release metadata
├── .planning/codebase/         # GSD planning documents (this file)
├── build.gradle.kts            # Root build file
├── settings.gradle.kts         # Module declarations
└── gradle.properties           # App metadata, source/API key defaults
```

## Key Package Breakdown

### `app/src/main/kotlin/org/breezyweather/background/`

```
background/
├── forecast/
│   ├── ForecastNotificationNotifier.kt
│   ├── TodayForecastNotificationJob.kt
│   └── TomorrowForecastNotificationJob.kt
├── interfaces/
│   └── TileService.kt              # Quick settings tile
├── provider/
│   └── WeatherContentProvider.kt   # Exports weather to other apps
├── receiver/
│   ├── BootReceiver.kt
│   ├── NotificationReceiver.kt
│   └── widget/                     # AppWidgetProvider subclasses (one per widget type)
├── updater/
│   ├── AppUpdateChecker.kt
│   └── AppUpdateNotifier.kt
└── weather/
    ├── WeatherUpdateJob.kt          # WorkManager periodic job
    └── WeatherUpdateNotifier.kt
```

Watchdog (fork-only, legacy `java/` root):
```
app/src/main/java/org/breezyweather/background/watchdog/
├── WatchdogService.kt              # Foreground service, heartbeat monitor
├── WatchdogAlarmReceiver.kt        # AlarmManager wakeup receiver
└── WatchdogRestartWorker.kt        # WorkManager one-shot restart worker
```

### `app/src/main/kotlin/org/breezyweather/sources/`

```
sources/
├── CommonConverter.kt              # Shared DTO-to-domain conversion logic
├── RefreshHelper.kt                # Orchestrates full weather refresh cycle
├── SourceManager.kt                # Hilt singleton registry of all sources
├── nominatim/                      # Fork: unified Nominatim + LocationIQ
│   ├── NominatimService.kt
│   ├── NominatimApi.kt
│   └── json/
│       ├── NominatimLocationResult.kt
│       └── NominatimAddress.kt
├── openmeteo/                      # Default weather source
├── android/                        # Device GPS + Android geocoder
├── openweather/
├── metno/
├── brightsky/
├── [60+ other provider packages]/  # One package per weather/geocoding provider
└── debug/                          # Debug/test source stub
```

### `app/src/main/kotlin/org/breezyweather/ui/`

```
ui/
├── main/
│   ├── MainActivity.kt
│   ├── MainActivityModels.kt
│   ├── MainActivityViewModel.kt
│   ├── fragments/
│   │   ├── HomeFragment.kt         # Primary weather display
│   │   ├── MainModuleFragment.kt
│   │   └── ManagementFragment.kt   # Location list management
│   ├── adapters/
│   ├── dialogs/
│   ├── layouts/
│   ├── utils/
│   └── widgets/
├── search/
│   └── SearchActivity.kt
├── details/
│   └── DetailsActivity.kt          # Daily forecast detail
├── alert/
│   └── AlertActivity.kt
├── settings/
│   ├── activities/                 # Settings sub-screens
│   ├── adapters/
│   ├── compose/                    # Compose-based settings UI
│   ├── dialogs/
│   └── preference/
├── about/
│   └── AboutActivity.kt
├── theme/
└── common/
```

### `app/src/main/kotlin/org/breezyweather/common/`

```
common/
├── di/
│   ├── DbModule.kt                 # Hilt: database + repositories
│   ├── HttpModule.kt               # Hilt: Retrofit clients (JsonClient, XmlClient)
│   └── RxModule.kt                 # Hilt: RxJava3 schedulers
├── source/
│   ├── Source.kt                   # Base interface
│   ├── WeatherSource.kt
│   ├── LocationSource.kt
│   ├── LocationSearchSource.kt
│   ├── ReverseGeocodingSource.kt
│   ├── HttpSource.kt
│   ├── ConfigurableSource.kt
│   ├── NonFreeNetSource.kt
│   ├── TimeZoneSource.kt
│   ├── PollenIndexSource.kt
│   ├── BroadcastSource.kt
│   ├── RefreshError.kt
│   ├── WeatherResult.kt
│   └── LocationResult.kt
├── exceptions/                     # ApiKeyMissingException, InvalidLocationException, etc.
├── extensions/                     # Kotlin extension functions
├── preference/                     # Preference UI helpers (EditTextPreference, etc.)
├── serializer/
├── utils/
│   ├── helpers/
│   ├── ColorUtils.kt
│   ├── UnitUtils.kt
│   └── CrashLogUtils.kt
└── [other utility packages]
```

## Key File Locations

**Entry Points:**
- `app/src/main/kotlin/org/breezyweather/BreezyWeather.kt` — Application class
- `app/src/main/kotlin/org/breezyweather/ui/main/MainActivity.kt` — UI entry point
- `app/src/main/kotlin/org/breezyweather/background/weather/WeatherUpdateJob.kt` — Background entry point

**Source Interface Contracts:**
- `app/src/main/kotlin/org/breezyweather/common/source/` — all `*Source` interfaces

**Fork-Specific Files:**
- `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt`
- `app/src/main/java/org/breezyweather/background/watchdog/WatchdogAlarmReceiver.kt`
- `app/src/main/java/org/breezyweather/background/watchdog/WatchdogRestartWorker.kt`
- `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt`

**Dependency Injection:**
- `app/src/main/kotlin/org/breezyweather/common/di/DbModule.kt`
- `app/src/main/kotlin/org/breezyweather/common/di/HttpModule.kt`
- `app/src/main/kotlin/org/breezyweather/common/di/RxModule.kt`

**Settings:**
- `app/src/main/kotlin/org/breezyweather/domain/settings/SettingsManager.kt`
- `app/src/main/kotlin/org/breezyweather/domain/settings/SourceConfigStore.kt`

**Build Configuration:**
- `app/build.gradle.kts` — flavor declarations, API key `buildConfigField` entries, source set wiring
- `gradle.properties` — app metadata defaults (`app.report_issue`, `app.source_code_link`, etc.)
- `local.properties` — developer-local API keys (`breezy.source.default_geocoding`, `breezy.accu.key`, etc.) — not committed

## Naming Conventions

**Files:**
- Kotlin classes: `PascalCase.kt` (e.g., `NominatimService.kt`, `WeatherUpdateJob.kt`)
- Source packages: lowercase, matching provider name (e.g., `nominatim/`, `openmeteo/`, `brightsky/`)
- JSON DTO files: `[SourcePrefix][Concept].kt` (e.g., `NominatimLocationResult.kt`, `AccuCurrentResult.kt`)
- Test files: `[ClassName]Test.kt`

**Packages:**
- All app code under `org.breezyweather`
- Domain/data modules use `breezyweather.domain` / `breezyweather.data` (no `org.` prefix)

## Where to Add New Code

**New weather source:**
1. Create package: `app/src/main/kotlin/org/breezyweather/sources/[sourceid]/`
2. Implement: `[Name]Service.kt` (implements relevant `*Source` interfaces), `[Name]Api.kt` (Retrofit), `json/` DTOs
3. Register: add `@Inject` constructor to `[Name]Service` and add it to `SourceManager.kt`
4. If source requires a proprietary SDK: place `[Name]Service.kt` in `app/src/src_nonfreenet/` and add a stub to `app/src/src_freenet/`; implement `NonFreeNetSource`

**New background job:**
- Location: `app/src/main/kotlin/org/breezyweather/background/`
- Pattern: extend `CoroutineWorker`, enqueue via WorkManager in `WeatherUpdateJob` or standalone

**New UI screen:**
- Location: `app/src/main/kotlin/org/breezyweather/ui/[screenname]/`
- Pattern: Activity or Fragment + ViewModel; register Activity in `AndroidManifest.xml`

**New domain model:**
- Location: `domain/src/main/kotlin/breezyweather/domain/weather/model/` (weather) or `domain/.../location/model/`
- No Android imports allowed in `:domain`

**New utility:**
- Shared extensions: `app/src/main/kotlin/org/breezyweather/common/extensions/`
- Shared helpers: `app/src/main/kotlin/org/breezyweather/common/utils/helpers/`

**New widget:**
- Config activity: `app/src/main/kotlin/org/breezyweather/remoteviews/config/`
- Presenter: `app/src/main/kotlin/org/breezyweather/remoteviews/presenters/`
- Provider receiver: `app/src/main/kotlin/org/breezyweather/background/receiver/widget/`
- Declare in: `app/src/main/AndroidManifest.xml`

## Special Directories

**`app/src/main/java/`:**
- Purpose: Legacy source root retained for the three watchdog files only
- Generated: No
- Committed: Yes
- Note: All other Kotlin source is under `app/src/main/kotlin/`; both roots are compiled

**`app/src/src_nonfreenet/` and `app/src/src_freenet/`:**
- Purpose: Product flavor source sets; compiled exclusively per flavor build
- Generated: No
- Committed: Yes

**`app/src/res_fork/`:**
- Purpose: Fork-specific resource overrides applied to both flavors
- Generated: No
- Committed: Yes

**`.planning/codebase/`:**
- Purpose: GSD architecture and planning documents
- Generated: Yes (by GSD commands)
- Committed: Yes

**`build/`:**
- Purpose: Gradle build outputs
- Generated: Yes
- Committed: No

---

*Structure analysis: 2026-05-29*
