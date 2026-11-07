# Codebase Concerns

**Analysis Date:** 2026-05-29

---

## Source Layout Split

### Watchdog Files Remain in `java/` While All Other Code is in `kotlin/`

- **Issue:** The three watchdog files (`WatchdogService.kt`, `WatchdogRestartWorker.kt`, `WatchdogAlarmReceiver.kt`) live under `app/src/main/java/org/breezyweather/background/watchdog/`. Every other Kotlin source file in the app is under `app/src/main/kotlin/`. The `java/` directory contains only these three files.
- **Files:** `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt`, `app/src/main/java/org/breezyweather/background/watchdog/WatchdogRestartWorker.kt`, `app/src/main/java/org/breezyweather/background/watchdog/WatchdogAlarmReceiver.kt`
- **Impact:** Developers looking at `app/src/main/kotlin/org/breezyweather/background/watchdog/` will find nothing and assume the feature doesn't exist. Tooling (lint, test coverage) configured against the `kotlin/` root may silently skip these files. IDE navigation from callers in `kotlin/` to these files may require an extra step.
- **Fix approach:** Move all three files to `app/src/main/kotlin/org/breezyweather/background/watchdog/` and delete the now-empty `java/` subtree. No code changes required — package declarations remain identical.

---

## Watchdog Service — Remaining Risks

### `shortService` 3-Minute Hard Kill on HyperOS

- **Issue:** `WatchdogService` uses `FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` (Android 14+). The Android platform enforces a ~3-minute time budget for this FGS type. On HyperOS, cold WorkManager DB init (`Room` initialization) inside `performHeartbeat()` via `.get()` can be slow. If the cumulative wall time from `onStartCommand` to `stopSelf()` exceeds ~3 minutes, the platform force-kills the service with `ForegroundServiceStartNotAllowedException: Time limit already exhausted for foreground service type shortService`.
- **Files:** `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt` (lines 100–116: `Thread { performHeartbeat(); scheduleNextAlarm(); stopSelf() }`)
- **Impact:** Confirmed crash. See `BUG/breezyweather_crash_logs_1.txt` line 4: `ForegroundServiceStartNotAllowedException: Time limit already exhausted for foreground service type dataSync` (from `dataSync`-era build; same issue applies to `shortService` under load). The service gets killed mid-heartbeat and `scheduleNextAlarm()` never runs, breaking the self-healing loop.
- **Current mitigation:** Heartbeat and all blocking I/O runs on a background `Thread` (not main thread). `WakeLock` has a 30-second timeout. `WatchdogRestartWorker` provides a 30-minute fallback.
- **Remaining risk:** The 30-second `WakeLock` does not cap total service lifetime — it only prevents CPU sleep during the wakelock window. If Room init takes longer than 3 minutes total wall time, the OS kill still applies.
- **Fix approach:** Add a timeout to `workManager.getWorkInfos(workQuery).get()` call (e.g., `.get(10, TimeUnit.SECONDS)`) to bound heartbeat time. Consider removing the `Thread { }` wrapper entirely and using a `CoroutineScope` with a strict `withTimeout(60_000)` block, stopping the service on timeout with a log.
- **Awaiting:** Human device verification on SDK 36 / HyperOS that `shortService` change resolves the original crash.

### `WatchdogRestartWorker` Does Not Restart `WatchdogService` — Only Checks Job Health

- **Issue:** `WatchdogRestartWorker.doWork()` only checks `WeatherUpdateJob` health and re-enqueues it if needed. It does NOT call `WatchdogService.start()`. If the AlarmManager loop breaks (e.g., after process kill with no alarm pending), no mechanism restarts the `WatchdogService` heartbeat chain until next device boot or user re-toggle.
- **Files:** `app/src/main/java/org/breezyweather/background/watchdog/WatchdogRestartWorker.kt` (lines 52–76)
- **Impact:** Watchdog heartbeat loop is silently dead after a process kill if the alarm was not yet scheduled. Only `WeatherUpdateJob` health is covered, not the watchdog's own continuity.
- **Fix approach:** After re-enqueuing `WeatherUpdateJob`, also call `WatchdogService.start(applicationContext, "worker_restart")` inside `doWork()` to restart the AlarmManager chain.

### `WatchdogService` Uses Raw `Thread {}` Instead of Structured Concurrency

- **Issue:** `onStartCommand` spawns a plain `Thread { }` for the heartbeat. This thread is not tracked, not cancellable, and not lifecycle-aware. If the service is re-started (overlapping `onStartCommand` calls), multiple threads can run concurrently.
- **Files:** `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt` (lines 106–116)
- **Impact:** Low probability but possible: duplicate `WeatherUpdateJob.setupTask()` calls from concurrent heartbeat threads. More importantly, the thread has no way to be cancelled if the service is stopped early.
- **Fix approach:** Use `CoroutineScope(Dispatchers.IO + Job())` scoped to the service lifecycle, cancelled in `onDestroy()`. Or use a `HandlerThread` with proper cleanup.

---

## LocationIQ / Nominatim — Geocoding Risks

### `zoom` Parameter Removed from `NominatimApi.getReverseLocation()`

- **Issue:** Upstream removed the `zoom` query parameter from `NominatimApi.getReverseLocation()`. Previous versions of this fork passed `zoom=18` for LocationIQ calls to request fine-grained (address-level) reverse geocoding results. The parameter is now absent from the API interface.
- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimApi.kt` (interface has no `zoom` param)
- **Impact:** LocationIQ calls no longer request building-level precision. For Vietnamese ward/commune matching, this may return coarser results, making the `vnSubProvinceRegex` match less reliable and increasing the frequency of "dirty" results requiring Nominatim rescue. Accuracy degradation is subtle — no runtime error, just different (possibly less precise) `display_name` values.
- **Fix approach:** Add `@Query("zoom") zoom: Int? = null` back to `getReverseLocation()` in `NominatimApi.kt`. In `NominatimService`, pass `zoom = 18` for LocationIQ-keyed calls only. Do not pass `zoom` for plain Nominatim calls (where the default is fine).

### `pickBestVietnamSubProvincePart` Uses `lastOrNull` — May Pick Wrong Admin Level

- **Issue:** `pickBestVietnamSubProvincePart()` returns `parts.lastOrNull { vnSubProvinceRegex.matcher(it).matches() }`. In `display_name` returned by LocationIQ without `zoom=18`, the segment order may differ from Nominatim. If both a ward ("Phường X") and a higher-level "Xã Y" appear, `lastOrNull` returns the last match, which may be the higher-level entity rather than the ward.
- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` (line 709: Companion `pickBestVietnamSubProvincePart`)
- **Impact:** Rare but possible wrong city name (province-level instead of ward-level).
- **Fix approach:** Investigate whether `firstOrNull` is more reliable for LocationIQ results (where the finest-grained token typically appears earlier). Possibly use `firstOrNull` when `isLocationIQSource=true` and `lastOrNull` for Nominatim.

### Nominatim Rate Limit Violation Risk

- **Issue:** Every `requestNearestLocation` call with a LocationIQ key starts a deferred Nominatim fetch that fires whenever LocationIQ returns a dirty VN result. There is no throttle, queue, or delay applied to Nominatim calls. Nominatim's usage policy mandates a maximum of 1 request/second.
- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` (lines 165–179: `nominatimFetch` Observable; lines 198–211: conditional subscription)
- **Impact:** In the current lazy-fetch design (Nominatim is only called when LocationIQ result is dirty), the rate is lower than the old always-parallel `zip` design. However, on a user with multiple VN locations doing simultaneous background refresh, multiple Nominatim calls can fire concurrently.
- **Fix approach:** Add a global `Semaphore(1)` or a timestamp-gated rate limiter applied to all Nominatim HTTP calls. Alternatively, add a configurable minimum delay between Nominatim calls using `RxJava`'s `.delay()` operator.

### LocationIQ Key Stored in Unencrypted `SharedPreferences`

- **Risk:** The user's LocationIQ key (`pk.xxxx`) is stored via `SourceConfigStore` → `SharedPreferences`. On rooted devices or via ADB backup, this key is extractable.
- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` (key read/write via `config`); `app/src/main/kotlin/org/breezyweather/domain/settings/SourceConfigStore.kt`
- **Current mitigation:** Android per-app private storage provides a baseline. Key rotation is cheap (user re-enters).
- **Recommendation:** Migrate `SourceConfigStore` to `EncryptedSharedPreferences` (Jetpack Security Crypto) for entries holding API keys. This is especially relevant for `pk.` keys which have billing implications.

---

## Build System — AGP 9 Upgrade Blockers

### Three Unresolved `# TODO: AGP 9 upgrade` Flags in `gradle.properties`

- **Issue:** Three properties in `gradle.properties` are explicitly flagged as requiring review before upgrading to Android Gradle Plugin 9.
  1. `android.dependency.useConstraints=true` — Line 31: "Check if we need this"
  2. `android.r8.strictFullModeForKeepRules=false` — Line 34: "Check impacts on our rules"
  3. `android.r8.optimizedResourceShrinking=false` — Line 37: "Check the impacts on our weather icons which are accessed through reflection. Note: a package.keep.xml was added to protect them, but unsure if it works"
- **Files:** `gradle.properties` (lines 30–38)
- **Impact:** Upgrading to AGP 9 without resolving these may silently break: (1) dependency resolution behavior, (2) R8 keep rules causing ProGuard stripping of custom code, (3) reflection-accessed weather icon resources being removed by optimized resource shrinking.
- **Fix approach:** Before any AGP 9 bump: test a debug build with `android.r8.optimizedResourceShrinking=true` and verify all weather icon resources load at runtime. Review upstream Breezy Weather's AGP 9 migration commits and apply the same changes.

---

## Tech Debt

### RxJava3 Not Migrated to Coroutines / Suspend Functions

- **Issue:** The entire HTTP layer uses RxJava3 `Observable` rather than Kotlin coroutines. Three `TODO` comments in `HttpModule.kt` explicitly flag this. The fork-specific `NominatimService` uses `flatMap`, `onErrorResumeNext`, and deferred `Observable` chaining — patterns that are hard to test and reason about compared to `async/await`.
- **Files:** `app/src/main/kotlin/org/breezyweather/common/di/HttpModule.kt` (lines 133, 162, 191); `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` (all request methods)
- **Impact:** RxJava adds dependency weight and complexity. The fork-specific dual-API logic (`flatMap` → conditional `nominatimFetch`) is harder to unit test than a `suspend` + `withContext` equivalent.
- **Fix approach:** Migrate one source at a time. `NominatimService` is the best candidate first since it has an existing test suite. Replace `Observable` returns with `suspend` and use `coroutineScope { async {} }` for the conditional deferred fetch.

### `UpdateStrategy` Feature is Commented Out

- **Issue:** Per-location `UpdateStrategy.ALWAYS_UPDATE` skip logic is commented out with `TODO: Implement this, it's a good idea`.
- **Files:** `app/src/main/kotlin/org/breezyweather/background/weather/WeatherUpdateJob.kt` (line 236)
- **Impact:** All locations are always refreshed regardless of user preference, wasting battery and API quota.

### `ConfigStore` Mutable — Should Be Read-Only After Setup

- **Issue:** Two TODOs note `ConfigStore` should become read-only and migrate to Android `DataStore`.
- **Files:** `app/src/main/kotlin/org/breezyweather/domain/settings/ConfigStore.kt` (lines 22–24)
- **Impact:** Mutable `ConfigStore` wrappers scattered across service classes make data mutation tracking difficult. Settings migrations are error-prone.

### `TileService` Has an Acknowledged Memory Leak

- **Issue:** `TileService.kt` has a `TODO: Memory leak` comment at its class declaration. It implements `CoroutineScope` directly on the service class without a backing `Job` that is cancelled in `onTileRemoved()` or `onDestroy()`.
- **Files:** `app/src/main/kotlin/org/breezyweather/background/interfaces/TileService.kt` (line 44)
- **Impact:** Coroutines launched from `TileService` may outlive the tile's lifecycle, holding references to the service instance and any captured lambdas.
- **Fix approach:** Replace `CoroutineScope` implementation with a `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)` field, cancelling `scope` in `onTileRemoved()`.

### `BreezyViewModel` Has Open Kotlin Conversion Issue

- **Issue:** `BreezyViewModel.kt` has `// TODO: Issue with getter on application when converted to Kotlin`.
- **Files:** `app/src/main/kotlin/org/breezyweather/common/activities/BreezyViewModel.kt` (line 22)
- **Impact:** The class is likely a partial Java→Kotlin conversion. The application getter may behave differently from the original Java version.

---

## Known Bugs

### `Release.getDownloadLink()` FIXME — Silent Wrong-File Fallback

- **Symptoms:** If no APK asset name matches the device ABI pattern, the fallback `?: assets[0]` silently downloads the first available asset, which may be the wrong architecture or a freenet/basic flavor mismatch.
- **Files:** `app/src/main/kotlin/org/breezyweather/background/updater/model/Release.kt` (line 54: `?: assets[0] // FIXME`)
- **Trigger:** Device with an uncommon ABI, or when the `app.github.release_prefix` naming convention changes.
- **Workaround:** None. The auto-updater silently installs an incompatible APK.

### Astro Timezone Handling Acknowledged as Wrong

- **Symptoms:** Sunrise/sunset progress calculation uses a `Calendar`-based workaround that the TODO comment acknowledges handles timezones incorrectly.
- **Files:** `app/src/main/kotlin/org/breezyweather/domain/weather/model/Astro.kt` (line 32: `TODO: Works but the way timezones are handled is wrong`)
- **Trigger:** Locations in non-local or unusual timezones, or during DST transitions.
- **Workaround:** Visual glitches only; core weather data unaffected.

### Notification Timezone Scheduling Incorrect

- **Symptoms:** Notification scheduling has a `FIXME: Timezone` comment.
- **Files:** `app/src/main/kotlin/org/breezyweather/remoteviews/Notifications.kt` (line 257)
- **Trigger:** Users in non-UTC timezones may see notification timing drift.

---

## Performance Bottlenecks

### `workManager.getWorkInfos().get()` Blocks Background Thread Without Timeout

- **Problem:** `performHeartbeat()` calls `workManager.getWorkInfos(workQuery).get()` (blocking) on the background `Thread` spawned by `onStartCommand`. Room DB initialization on first access after a cold process kill (HyperOS midnight-kill scenario) can block this call for many seconds or minutes.
- **Files:** `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt` (line 235)
- **Cause:** `ListenableFuture.get()` with no timeout blocks indefinitely until Room's executor completes the query.
- **Improvement path:** Replace `.get()` with `.get(10L, TimeUnit.SECONDS)` and catch `TimeoutException` to skip the health check gracefully on slow startup rather than blocking the watchdog thread.

---

## Fragile Areas

### `vnSubProvinceRegex` Must Preserve `(?iu)` Unicode Flags

- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` (line 698: `Companion.vnSubProvinceRegex`; compiled via Java `Pattern.compile` with `UNICODE_CASE | CASE_INSENSITIVE`)
- **Why fragile:** The regex uses `(?iu)` inline flags for Unicode-aware case folding — critical for Vietnamese diacritics. If the pattern is migrated to a plain Kotlin `Regex` without `RegexOption.UNICODE_CASE`, diacritic matching silently breaks. The pattern must also stay in sync with `signature/testaddress.py`'s `VN_SUB_PROVINCE_REGEX` (Python version used for manual regression testing, not part of CI).
- **Safe modification:** Any changes to `vnSubProvinceRegex` MUST keep `Pattern.UNICODE_CASE or Pattern.CASE_INSENSITIVE` flags (or `(?iu)` inline). Add a unit test for a Vietnamese diacritic variant (e.g., `"Phường Hoàng Mai"`) to catch flag removal.
- **Test coverage:** Unit tests in `NominatimServiceTest.kt` cover `pickBestVietnamSubProvincePart()` for ASCII-equivalent inputs but do not explicitly test diacritic folding.

### `NominatimApi` Interface Shared Between LocationIQ and Nominatim

- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimApi.kt`
- **Why fragile:** The same Retrofit interface is used for both `nominatim.openstreetmap.org` and `us1/eu1.locationiq.com/v1/`. The two services return different JSON structures. Both are deserialized into `NominatimLocationResult` with `ignoreUnknownKeys = true`, meaning field divergence between the two APIs is silently absorbed as `null`.
- **Safe modification:** Before adding new JSON fields from one service, verify they are safely nullable for the other. Prefer separate response model classes if the schemas diverge further.

---

## Test Coverage Gaps

### `requestNearestLocation` Lazy Nominatim Fetch — Not Integration Tested

- **What's not tested:** The `flatMap` → conditional `nominatimFetch` path, including: (1) the "clean LIQ result, Nominatim never called" branch; (2) the "dirty LIQ result, Nominatim rescue" branch (GiggleRescue); (3) the "LIQ failure, fall through to Nominatim" branch; (4) `mergeVnResults()` output when both sources provide data.
- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` (lines 158–224); `app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt`
- **Risk:** Changes to the lazy-fetch logic or `mergeVnResults()` regress silently. The GiggleRescue path has never been integration-tested.
- **Priority:** High

### `vnSubProvinceRegex` Diacritic Folding — Not Explicitly Tested

- **What's not tested:** No unit test passes a Vietnamese diacritic string directly to `vnSubProvinceRegex` to confirm `(?iu)` is active. Tests use ASCII-equivalent strings.
- **Files:** `app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt`
- **Risk:** If a refactor strips the Unicode flags, all VN address resolution silently fails for names with diacritics (the common case).
- **Priority:** High

### `WatchdogService` / `WatchdogRestartWorker` — Zero Unit Tests

- **What's not tested:** `performHeartbeat()` logic, `scheduleNextAlarm()` alarm setup, `startWatchdogForeground()` failure fallback branch, `WatchdogRestartWorker.doWork()` re-enqueue path.
- **Files:** `app/src/main/java/org/breezyweather/background/watchdog/` (no corresponding test file exists under `app/src/test/`)
- **Risk:** Any change to watchdog startup, alarm scheduling, or fallback logic can break background job recovery undetected.
- **Priority:** Medium

### `CommonConverterTest` Is Stub Only

- **What's not tested:** Weather unit conversions, country code disambiguation, address field extraction.
- **Files:** `app/src/test/java/org/breezyweather/sources/CommonConverterTest.kt`
- **Priority:** Medium

---

## Dependencies at Risk

### Nominatim Public Instance — No SLA, ToS Restrictions

- **Risk:** `https://nominatim.openstreetmap.org/` is a public free service with no uptime SLA, a hard 1 req/sec rate limit, and a ToS that prohibits bulk or automated usage. The app uses it as a conditional secondary call for VN reverse geocoding rescue.
- **Impact:** IP-level blocks would silently degrade Nominatim fallback for all users sharing that carrier's IP range.
- **Mitigation plan:** Support is already partially implemented for user-specified self-hosted Nominatim via `instance` config. Document the ToS concern in user-facing settings help text.

### LocationIQ Regional Endpoint — No Auto-Selection

- **Risk:** LocationIQ requests fall back through `eu1` → `us1` candidates. There is no Asia-Pacific (`ap1`) option since `ap1.locationiq.com` was deprecated (DNS failure confirmed during debugging). For Vietnamese users, both `eu1` and `us1` have higher latency than an APAC endpoint.
- **Files:** `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt` (endpoint candidate resolution logic)
- **Impact:** Suboptimal reverse geocoding latency for the primary target userbase (Vietnam). If LocationIQ introduces a new APAC endpoint, the app won't use it without a code change.
- **Migration plan:** Make the endpoint region configurable in Settings, or auto-select based on device locale.

---

*Concerns audit: 2026-05-29*
