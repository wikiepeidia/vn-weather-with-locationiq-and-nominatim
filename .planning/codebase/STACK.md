# Technology Stack

**Analysis Date:** 2026-05-29

## Languages

**Primary:**

- Kotlin 2.3.21 - All application and library module source code
- Kotlin DSL (`.kts`) - All Gradle build scripts

**Secondary:**

- Java (JVM 1.8 target) - Kotlin compiles to JVM 1.8 bytecode; source/target compatibility set to Java 8
- SQL (SQLDelight 3.38 dialect) - Database schema files in `data/src/main/sqldelight/`

**Note on source layout:** Upstream migrated all Kotlin files from `app/src/main/java/` to `app/src/main/kotlin/` in the v6.1.4 → v6.2.1 rebase. The custom Watchdog files remain at `app/src/main/java/org/breezyweather/background/watchdog/` as the sole exception. Custom Nominatim+LocationIQ source lives at `app/src/main/kotlin/org/breezyweather/sources/nominatim/`.

## Runtime

**Environment:**

- Android — minimum SDK 23 (Android 6.0), compile/target SDK 36 (Android 16)
- Build tools version: 36.0.0

**Package Manager:**

- Gradle 9.5.0 via wrapper (`gradle/wrapper/gradle-wrapper.properties`)
- Version catalog: `gradle/libs.versions.toml` (single source of truth for all dependency versions)
- Lockfile: Not present

## Frameworks

**Core Android:**

- Android Gradle Plugin (AGP) 9.2.0 - Android build pipeline
- Jetpack Compose BOM 2026.04.01 - Declarative UI; pins all `androidx.compose.*` versions
- Compose Material 3 1.5.0-alpha18 - Material Design 3 UI components
- Dagger Hilt 2.59.2 - Dependency injection (KSP-processed)
- AndroidX Lifecycle 2.10.0 - ViewModel, LiveData, lifecycle-aware coroutines
- AndroidX WorkManager 2.11.2 - Durable background work scheduling
- AndroidX Navigation Compose 2.9.8 - Compose navigation host

**Networking:**

- Retrofit 3.0.0 - HTTP client abstraction with RxJava3 adapter and kotlinx.serialization converter
- OkHttp 5.3.2 - HTTP client (core + `logging-interceptor` + `okhttp-tls`)

**Serialization:**

- `kotlinx-serialization-json` 1.11.0 - JSON parsing for all API responses
- `io.github.pdvrieze.xmlutil` (core-android + serialization) 0.91.3 - XML parsing for weather APIs that return XML

**Reactive / Async:**

- RxJava 3.1.12 + RxAndroid 3.0.2 - Reactive streams (network source layer uses `Observable`)
- `kotlinx-coroutines` 1.10.2 - Coroutines runtime
- `kotlinx-coroutines-rx3` 1.10.2 - Interop bridge between coroutines and RxJava 3

**Database:**

- SQLDelight 2.3.2 - Type-safe SQLite with coroutine extensions; schema in `data/` module
- SQLite (requery/sqlite-android) 3.49.0 - Bundled modern SQLite shared library, replaces Android system SQLite
- `androidx.sqlite` 2.6.2 - Framework and KTX wrappers

**Testing:**

- JUnit Jupiter 6.0.3 - Test runner (JUnit Platform launcher required separately)
- Kotest Assertions 6.1.11 - Fluent assertion DSL
- MockK 1.14.9 - Kotlin-native mocking library
- `kotlinx-coroutines-test` 1.10.2 - Coroutine test utilities

**Build/Dev Tooling:**

- KSP (Kotlin Symbol Processing) 2.3.7 - Annotation processing replacing kapt (used by Hilt)
- Spotless 8.4.0 + ktlint 1.8.0 - Automated code formatting and lint
- AboutLibraries 14.1.0 - Open-source license disclosure plugin and Compose UI
- Convention plugins in `buildSrc/src/main/kotlin/breezy/buildlogic/` - Shared build logic across modules

## Key Dependencies

**Critical:**

- `kotlinx-collections-immutable` 0.4.0 - Immutable collections required by Compose state
- `jjwt` 0.13.0 - JWT token generation (Météo-France source only; `basic` flavor only via `basicImplementation`)
- `breezy-datasharing-lib` commit `09c0e4dd` (JitPack) - Content provider contract for sharing weather data to third-party apps

**Utility:**

- `com.shredzone.commons:commons-suncalc` 2.14 - Astronomical (sun/moon) calculations
- `com.patrykandpatrick.vico` 2.4.4 - Chart rendering library (Compose M3 variant + Views variant)
- `accompanist-permissions` 0.37.3 - Compose runtime permission helpers
- `com.github.breezy-weather:AdaptiveIconView` v0.0.3 (JitPack) - Adaptive icon view component
- `com.github.breezy-weather:RestrictionBypass` commit `86d4e295` (JitPack) - Reflection workaround to pass `Locale` to `RelativeTime` on restricted Android versions

## Build Variants

**Flavor dimension:** `default`

| Flavor    | Description                                               | Extra                              |
| --------- | --------------------------------------------------------- | ---------------------------------- |
| `basic`   | Full source set including non-free-network API sources    | Includes `jjwt` (JWT for MF)       |
| `freenet` | Free-network-only sources; versionName suffix `_freenet`  | No `jjwt`                          |

**Source set directories:**

- `app/src/src_nonfreenet/` - `basic` flavor sources
- `app/src/src_freenet/` - `freenet` flavor sources
- `app/src/res_nonfreenet/` / `app/src/res_freenet/` - Flavor-specific resources
- `app/src/res_fork/` - Fork branding resources (active when `breezy` Gradle property is absent)
- `app/src/res_breezy/` - Upstream Breezy branding (active when `-Pbreezy` is passed)

**Build types:**

- `debug` - applicationId suffix `.debug`; versionName suffix `-r{commitCount}`
- `release` - R8 minification + resource shrinking enabled; ProGuard rules at `app/proguard-rules.pro`; PNG crunch disabled (icons pre-optimized)

## Configuration

**Gradle properties (`gradle.properties`):**

- `android.enableR8.fullMode=true`
- `android.r8.strictFullModeForKeepRules=false` - AGP 9 migration, rules strictness relaxed
- `android.r8.optimizedResourceShrinking=false` - Protects weather icons accessed via reflection
- `android.dependency.useConstraints=true` - AGP 9 migration flag
- `org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200`
- `kotlin.code.style=official`
- `org.gradle.parallel=false`

**Runtime configuration (from `local.properties`, git-ignored):**

- `breezy.source.default_location` (default: `native`)
- `breezy.source.default_location_search` (default: `openmeteo`)
- `breezy.source.default_geocoding` (default: `nominatim`)
- `breezy.source.default_weather` (default: `auto`)
- Per-source API keys: `breezy.accu.key`, `breezy.openweather.key`, `breezy.mf.jwtKey`, etc.

**Fork identity (`gradle.properties`):**

- `app.github.org=wikiepeidia`
- `app.github.repo=vn-weather-with-locationiq-and-nominatim`
- `app.source_code_link=https://github.com/wikiepeidia/vn-weather-with-locationiq-and-nominatim`

**Kotlin stdlib pinning:** Root `build.gradle.kts` forces all `org.jetbrains.kotlin:kotlin-stdlib*` and `kotlin-reflect` variants to 2.3.0 across all configurations to prevent version conflicts from transitive dependencies.

## Platform Requirements

**Development:**

- JDK 17+ recommended (required by AGP 9 / Gradle 9.5)
- Android Studio or command-line Gradle 9.5.0
- `local.properties` optional — sources degrade gracefully without API keys

**Production:**

- Android 6.0+ (API 23 minimum)
- App ID: `io.github.wikiepeidia.vnweather`
- versionCode: `60201`, versionName: `6.2.1`
- ABI splits produced: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` + universal APK

---

*Stack analysis: 2026-05-29*
