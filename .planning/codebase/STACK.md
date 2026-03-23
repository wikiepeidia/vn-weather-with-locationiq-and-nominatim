# Technology Stack

**Analysis Date:** 2026-03-23

## Languages

**Primary:**
- Kotlin 2.3.20 — all application and library source code
- Java 8 (JVM target) — bytecode compatibility target for Android

**Secondary:**
- Python — test scripts in `signature/testaddress.py` for manual API response testing
- SQL — SQLDelight schema files in `data/src/main/sqldelight/`

## Runtime

**Environment:**
- Android (native APK)
- Minimum SDK: **23** (Android 6.0 Marshmallow)
- Compile/Target SDK: **36** (Android 15)
- Build Tools: **35.0.1**
- JVM Target: Java 8

**Package Manager:**
- Gradle with Kotlin DSL (`build.gradle.kts`)
- Version catalog: `gradle/libs.versions.toml`
- Lockfile: Not used (standard Gradle dependency resolution)

## Frameworks

**Core:**
- Jetpack Compose BOM 2026.03.00 — UI toolkit
- Compose Material3 1.5.0-alpha15 — Material You design system
- AndroidX Activity 1.13.0 — Activity/Fragment integration
- AndroidX Lifecycle 2.10.0 — ViewModel, LiveData, reactive lifecycle
- AndroidX Navigation Compose 2.9.7 — in-app navigation

**Dependency Injection:**
- Dagger Hilt 2.58 — compile-time DI framework
- Hilt Work 1.3.0 — WorkManager + Hilt integration

**Networking:**
- Retrofit 3.0.0 — HTTP client abstraction via Kotlin interfaces
- OkHttp 5.3.2 — underlying HTTP engine (with TLS support for Android < 7)
- OkHttp Logging 5.3.2 — debug HTTP logging interceptor

**Reactive:**
- RxJava3 3.1.12 — reactive streams for async API calls
- RxAndroid 3.0.2 — Android schedulers for RxJava3
- KotlinX Coroutines 1.10.2 — coroutines + flow (for DB, WorkManager tasks)

**Serialization:**
- kotlinx.serialization-json 1.10.0 — JSON parsing (primary format)
- kotlinx.serialization-xml 0.91.3 (xmlutil) — XML parsing (CAP alerts, FMI etc.)
- kotlinx.serialization-csv 3.2.1 — CSV parsing (NCEI normals data)
- org.json 20251224 — legacy JSON fallback

**Database:**
- SQLDelight 2.3.2 — type-safe SQL (schema in `data/src/main/sqldelight/`)
- Android SQLite driver (custom requery fork) — `sqlite-android` 3.49.0
- AndroidX SQLite framework + KTX 2.6.2 — support library wrappers

**Background Work:**
- AndroidX WorkManager 2.11.1 — periodic weather refresh jobs
- Hilt Work 1.3.0 — WorkManager + DI integration

**Charts/UI extras:**
- Vico 2.4.3 — weather chart views (Compose M3 + legacy Views)
- Accompanist Permissions 0.37.3 — Compose runtime permission handling
- AdaptiveIconView — custom adaptive-icon rendering (`com.github.breezy-weather`)
- RecyclerView 1.4.0, CardView, SwipeRefreshLayout — legacy view components
- Material Components 1.14.0-alpha10 — legacy View-based Material widgets
- AboutLibraries 13.2.1 — open-source license screen

**JWT:**
- JJWT 0.13.0 — JWT token signing used by Météo-France (`mf`) API integration

**Astronomy:**
- commons-suncalc 2.14 — sun/moon position and rise/set calculations

**Security:**
- RestrictionBypass — pinned certificate / network restrictions bypass (`com.github.breezy-weather`)

**Testing:**
- JUnit Jupiter 6.0.3 — unit test runner
- Kotest Assertions 6.1.7 — fluent assertion library
- MockK 1.14.9 — Kotlin-native mocking library
- KotlinX Coroutines Test — coroutine test dispatcher

## Key Dependencies

**Critical:**
- `retrofit-core` 3.0.0 — all 40+ weather API integrations depend on this
- `dagger-hilt-core` 2.58 — entire DI graph; changing breaks all injected classes
- `sqldelight-android-driver` 2.3.2 — sole persistence layer
- `kotlinx-serialization-json` 1.10.0 — JSON deserialization for every API response
- `kotlinx-coroutines` 1.10.2 — threading model for DB and background work

**Infrastructure:**
- `breezy-datasharing-lib` (git hash `09c0e4dd`) — Breezy weather broadcast data format
- `restrictionBypass` (git hash `86d4e295`) — Android TLS restriction bypasses

## Configuration

**Environment:**
- API keys set in `local.properties` (git-ignored); never committed
- Keys referenced at build time via `buildConfigField` in `app/build.gradle.kts`
- `gradle.properties` holds project-level links (repo URL, privacy policy, etc.)
- `breezy=true` in `local.properties` enables Breezy branding resources (`res_breezy/`)

**Key `local.properties` properties injected as `BuildConfig` fields:**
- `breezy.openweather.key` → `BuildConfig.OPEN_WEATHER_KEY`
- `breezy.pirateweather.key` → `BuildConfig.PIRATE_WEATHER_KEY`
- `breezy.metoffice.key` → `BuildConfig.MET_OFFICE_KEY`
- `breezy.mf.key` / `breezy.mf.jwtKey` → `BuildConfig.MF_WSFT_KEY` / `MF_WSFT_JWT_KEY`
- `breezy.baiduip.key` → `BuildConfig.BAIDU_IP_LOCATION_AK`
- `breezy.geonames.key` → `BuildConfig.GEO_NAMES_KEY`
- `breezy.cwa.key`, `breezy.eccc.key`, `breezy.bmkg.key`, `breezy.metie.key`
- `breezy.polleninfo.key`, `breezy.infoplaza.key`
- `breezy.atmograndest.key`, `breezy.atmohdf.key`, `breezy.atmosud.key`

**Build:**
- `buildSrc/` — custom Gradle convention plugins (`breezy.android.application`, `breezy.android.application.compose`)
- `buildSrc/src/main/kotlin/breezy/buildlogic/AndroidConfig.kt` — central SDK version constants
- `app/build.gradle.kts` — full application build config; product flavors `basic` / `freenet`
- Kotlin KSP 2.3.6 — annotation processing (Hilt, SQLDelight)
- Spotless 8.4.0 + KtLint 1.8.0 — code formatting and linting

## Product Flavors

**`basic`** (`src/src_nonfreenet/`)
- Full service implementations including those requiring API keys
- Used for standard release builds

**`freenet`** (`src/src_freenet/`)
- Service implementations that don't require external API keys at runtime (or use user-supplied runtime keys)
- Suitable for F-Droid / privacy-focused distribution

## Platform Requirements

**Development:**
- Android SDK (path: `sdk.dir` in `local.properties`)
- JDK 8+ (JVM target is 1.8)
- Gradle daemon enabled, parallel builds disabled (`org.gradle.parallel=false`)
- JVM heap: 4 GB Gradle daemon, 2 GB Kotlin daemon

**Production:**
- Android 6.0+ (API 23) devices
- Target: Android 15 (API 36)
- ABI splits: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` + universal APK

---

*Stack analysis: 2026-03-23*
