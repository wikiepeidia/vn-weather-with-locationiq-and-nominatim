# External Integrations

**Analysis Date:** 2026-05-29

## Geocoding / Location Search

**Nominatim + LocationIQ (unified, custom fork source):**

- Purpose: Forward location search and reverse geocoding
- Implementation: `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt`
- API interface: `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimApi.kt`
- Endpoints used: `GET /search` (forward), `GET /reverse` (reverse)
- Default base URL: `https://nominatim.openstreetmap.org/`
- LocationIQ primary endpoint: `https://eu1.locationiq.com/v1/`
- LocationIQ US fallback: `https://us1.locationiq.com/v1/`
- Auth: Optional `pk.*` API key passed as `@Query("key")` — no key required for standard Nominatim
- Key configured: at runtime via `SourceConfigStore` (SharedPreferences, key: `instance`); separate endpoint override stored under `locationiq_base_url`
- Fallback strategy: If LocationIQ key is present, tries EU endpoint then US endpoint; on failure falls back to plain Nominatim
- Vietnam special logic: dual-API cross-validation for VN addresses — Nominatim rescues dirty LocationIQ ward names via `[GiggleRescue]` path
- Data attribution: OpenStreetMap contributors, ODbL 1.0

## Weather Data APIs

Weather source integrations are registered as injectable `HttpSource` implementations. All network calls go through Retrofit + OkHttp. API keys are baked in at compile time from `local.properties`.

**AccuWeather:**

- Key env: `breezy.accu.key` → `BuildConfig.ACCU_WEATHER_KEY`
- Flavor: `basic` only

**Open-Meteo:**

- Key env: None (free, no key required)
- Used as default location-search source (`DEFAULT_LOCATION_SEARCH_SOURCE=openmeteo`)

**OpenWeather:**

- Key env: `breezy.openweather.key` → `BuildConfig.OPEN_WEATHER_KEY`
- Flavor: `basic` only

**Météo-France (MF):**

- Key env: `breezy.mf.key` → `BuildConfig.MF_WSFT_KEY`; JWT signing key: `breezy.mf.jwtKey` → `BuildConfig.MF_WSFT_JWT_KEY`
- Auth: JWT token generated at runtime using `jjwt` 0.13.0
- Flavor: `basic` only (JWT dependency excluded from `freenet`)

**Pirate Weather:**

- Key env: `breezy.pirateweather.key` → `BuildConfig.PIRATE_WEATHER_KEY`
- Flavor: `basic` only

**CWA (Central Weather Administration, Taiwan):**

- Key env: `breezy.cwa.key` → `BuildConfig.CWA_KEY`

**AEMET (Spain):**

- Key env: `breezy.aemet.key` → `BuildConfig.AEMET_KEY`

**BMKG (Indonesia):**

- Key env: `breezy.bmkg.key` → `BuildConfig.BMKG_KEY`

**ECCC (Canada):**

- Key env: `breezy.eccc.key` → `BuildConfig.ECCC_KEY`

**Met Éireann (Ireland):**

- Key env: `breezy.metie.key` → `BuildConfig.MET_IE_KEY`

**Met Office (UK):**

- Key env: `breezy.metoffice.key` → `BuildConfig.MET_OFFICE_KEY`

**GeoNames:**

- Key env: `breezy.geonames.key` → `BuildConfig.GEO_NAMES_KEY`

**Atmo regional sources (France air quality):**

- Keys: `breezy.atmoaura.key`, `breezy.atmofrance.key`, `breezy.atmograndest.key`, `breezy.atmohdf.key`, `breezy.atmosud.key`

**PollenInfo:**

- Key env: `breezy.polleninfo.key` → `BuildConfig.POLLENINFO_KEY`

**Infoplaza:**

- Key env: `breezy.infoplaza.key` → `BuildConfig.INFOPLAZA_KEY`

**Baidu IP Location:**

- Key env: `breezy.baiduip.key` → `BuildConfig.BAIDU_IP_LOCATION_AK`

## Data Storage

**Database:**

- Type: SQLite (bundled requery/sqlite-android 3.49.0, bypasses system SQLite)
- ORM/client: SQLDelight 2.3.2 with coroutine extensions
- Schema location: `data/src/main/sqldelight/`
- Module: `data/` (`:data` Gradle project)
- Connection: no external connection string; local on-device file

**Preferences:**

- Android `SharedPreferences` via `androidx.preference:preference-ktx` 1.2.1
- Per-source config stored in `SourceConfigStore` (wraps SharedPreferences, keyed by source ID)
- No DataStore (dependency commented out in `libs.versions.toml`)

**File Storage:**

- Local filesystem only (no cloud file storage)
- Weather icon packs loaded via `AdaptiveIconView` from installed APK resources

## Authentication & Identity

**Auth Provider:** None — no user account system

**API key management:** All third-party API keys are compiled into `BuildConfig` from `local.properties` at build time. Keys are not stored on-device at runtime; they are embedded as string constants in the release APK.

**LocationIQ key:** Stored at runtime in `SharedPreferences` (entered by user in settings UI, not compiled in). Pattern: must start with `pk.`.

## Data Sharing (Outgoing Content Provider)

**Provider contract:**

- Library: `breezy-datasharing-lib` commit `09c0e4dd` (JitPack)
- Permission: `${applicationId}.READ_PROVIDER` (declared in `AndroidManifest.xml`, `protectionLevel="dangerous"`)
- Purpose: Exposes current weather data to third-party Android apps via content provider

## Background Services

**WorkManager jobs:**

- Library: AndroidX WorkManager 2.11.2
- Hilt integration: `hilt-work` 1.3.0
- Used for: scheduled weather refresh background tasks

**Custom Watchdog:**

- Files: `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt`, `WatchdogRestartWorker.kt`, `WatchdogAlarmReceiver.kt`
- Mechanism: `AlarmManager` wakeup (`WAKE_LOCK` permission) + WorkManager restart worker
- Note: These three files remain in `java/` source set (not migrated to `kotlin/`)

## Monitoring & Observability

**Error Tracking:** None (no Sentry, Firebase Crashlytics, or equivalent)

**Logs:** `android.util.Log` calls in custom sources (e.g., `NominatimService` uses `Log.d` with tagged messages like `[GiggleRescue]`)

**LeakCanary:** Dependency present in `libs.versions.toml` but commented out — not active

## Update Checker

**GitHub Releases:**

- Mechanism: Built-in GitHub release checker using `app.github.org` / `app.github.repo` / `app.github.release_prefix` from `gradle.properties`
- Org: `wikiepeidia`, Repo: `vn-weather-with-locationiq-and-nominatim`, Prefix: `vn-weather-with-locationiq-and-nominatim`

## CI/CD & Deployment

**Hosting:** GitHub (`https://github.com/wikiepeidia/vn-weather-with-locationiq-and-nominatim`)

**CI Pipeline:** Not detected (no `.github/workflows/` files observed in scope)

**Releases:** GitHub Releases; APK assets prefixed `vn-weather-with-locationiq-and-nominatim`

## Environment Configuration

**Required for build (set in `local.properties`, git-ignored):**

- None strictly required — all API keys default to empty string; app builds without them

**Required for full functionality at runtime:**

- LocationIQ: user enters `pk.*` key in Settings UI (stored in SharedPreferences)
- AccuWeather, OpenWeather, MF, etc.: keys must be set in `local.properties` before building

**Secrets location:**

- `local.properties` (git-ignored, developer machine only)
- No secrets committed to repository

## Webhooks & Callbacks

**Incoming:** None

**Outgoing:** None (no webhook dispatch)

---

*Integration audit: 2026-05-29*
