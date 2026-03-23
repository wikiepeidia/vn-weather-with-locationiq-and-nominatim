# Codebase Concerns

**Analysis Date:** 2026-03-23

---

## Vietnamese Address Parsing

### LocationIQ Fallback Uses `parts.firstOrNull()` — Still Returns Garbage

- **Issue:** When the VN sub-province regex finds no match in LocationIQ data, the code falls back to `parts.firstOrNull()?.trim()`. If LocationIQ puts "Ủy ban nhân dân Phường X" as the FIRST comma-segment (e.g. a POI name), this raw garbage string is assigned directly to `city`.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 210–214
- **Impact:** Users in Vietnam see "Ủy ban nhân dân Phường X" or "Nhà văn hóa Phường X" as their city name instead of the clean "Phường X".
- **Trigger:** Occurs when the logged GPS point resolves to a POI address rather than a standard administrative address. LocationIQ zoom=18 retrieves fine-grained results which can lead with POI names.
- **Fix approach:** When regex fails for a LocationIQ result, skip the LocationIQ fallback entirely and let Nominatim's result take over (since Nominatim result quality for administrative names is more predictable). Alternatively, apply a secondary filter: skip any `firstOrNull()` result that itself contains a VN administrative prefix somewhere internally but is NOT the whole clean token.

---

### No Cross-Validation Between LocationIQ and Nominatim City Values

- **Issue:** The current `Observable.zip` merge logic at line 152–161 picks the LocationIQ result unconditionally if it produced any result, using Nominatim only as a total-failure fallback. The project goal requires both sources to be cross-referenced: if LocationIQ produces a non-clean VN address, Nominatim should save it.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 152–162
- **Impact:** The dual-API benefit is nullified. LocationIQ errors pass through silently while the valid Nominatim result is discarded.
- **Fix approach:** After running `pickBestVietnamSubProvincePart()` on both results (for VN locations), prefer whichever produced a clean VN sub-province match. If LocationIQ result is clean → use it. If not but Nominatim result is clean → use Nominatim. If neither is clean → use LocationIQ as last resort (current behavior). Apply this logic only for `countryCode == "vn"`.

---

### `pickBestVietnamSubProvincePart` Picks LAST Match — May Choose Wrong Level

- **Issue:** The function returns `parts.lastOrNull { vnSubProvinceRegex.matcher(it).matches() }`. In `testaddress.py` the self-test shows `("Phường A, Xã B, blabla", "Xã B")` — the LAST match wins. For LocationIQ zoom=18, the finest-grained component (ward/commune) is typically earlier in `display_name`, not at the end. The "last" may refer to a higher-level administrative entity in some layouts.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 237–240; `signature/testaddress.py` `pick_best_vn_part()`
- **Impact:** Rare but possible: a province-level "Xã" (if any exist with that prefix) could override the correct ward-level "Phường" when both appear in `display_name`.
- **Fix approach:** For LocationIQ zoom=18, the ward is almost always the FIRST valid VN token. Consider using `firstOrNull` for LocationIQ results and `lastOrNull` for Nominatim results (Nominatim zoom=13 is coarser, different segment order). Alternatively, re-examine real `display_name` samples to confirm ordering assumptions.

---

### NominatimAddress Missing `suburb` and `hamlet` Fields

- **Issue:** `NominatimAddress.kt` maps only `village`, `town`, `municipality`, `county`, `state`. Nominatim frequently returns Vietnamese ward/commune data under the `suburb` key (e.g. `"suburb": "Phường Hoàn Kiếm"`). This field is completely unmapped. `address.town` is often null for Vietnamese cities, falling through to `locationResult.name` which is a raw place name from OSM.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt`; `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` line 194
- **Impact:** For Vietnamese locations, Nominatim's structured address parsing is partially blind. The fallback to `display_name` regex mitigates this but is brittle.
- **Fix approach:** Add `suburb: String?`, `hamlet: String?`, `quarter: String?`, `neighbourhood: String?` to `NominatimAddress`. Then in `convertLocation()` for `countryCode == "vn"`, try `address.suburb ?: address.hamlet ?: address.quarter` before falling through to `display_name` regex.

---

## Concurrency / API Reliability

### Nominatim Rate Limit Violation Risk (1 req/s Policy)

- **Issue:** Every call to `requestNearestLocation` with a LocationIQ key issues a simultaneous call to both `us1.locationiq.com` and `nominatim.openstreetmap.org` via `Observable.zip`. Nominatim's usage policy mandates maximum 1 request per second. No throttle, delay, or queue exists in the app's HTTP stack.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 117–152; `app/src/main/java/org/breezyweather/common/di/HttpModule.kt`
- **Impact:** High-frequency reverse geocoding (e.g., background refresh with multiple locations) can violate Nominatim ToS and trigger IP bans which will break the app for all users relying on Nominatim.
- **Fix approach:** Add an in-process rate limiter (e.g., a `Semaphore` or `RxJava`-based delay) specifically for Nominatim calls. Alternatively, de-prioritize the concurrent Nominatim call: fire it only if LocationIQ has already returned a non-clean result (sequential conditional logic instead of always-parallel).

---

### `mApi` Lazy Initialization Goes Stale After Config Change

- **Issue:** `mApi` is created via `by lazy { ... }` (line 77) and captures the `instance` value at first access. If the user saves a new URL/key in Settings, `requestLocationSearch` continues using the old `mApi` (pointing to the old URL). `requestNearestLocation` correctly builds fresh clients on every call, but search does not.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 77–83, 96
- **Impact:** After a config change, location search silently queries the stale endpoint until the app process restarts. Bug is silent — no error, just wrong search results.
- **Fix approach:** Remove `by lazy`. Build the API client freshly in `requestLocationSearch` (same pattern used in `requestNearestLocation`), or use a getter that checks if `instance` has changed and rebuilds.

---

### Nominatim Error Silently Swallowed in Concurrent Mode

- **Issue:** Both `locationIQObs.onErrorReturn { emptyList() }` and `nominatimObs.onErrorReturn { emptyList() }` (lines 138, 150) swallow all errors. If Nominatim fails with a network error, it silently returns an empty list, causing LocationIQ to be used unconditionally without any logging. If both fail, `InvalidLocationException` is thrown but no logging identifies which source failed.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 135–160
- **Impact:** Debugging address resolution failures is very hard. No signal distinguishes "Nominatim timed out", "LocationIQ 401 bad key", or "both failed".
- **Fix approach:** Log the error (at debug level) inside each `onErrorReturn` lambda before returning the empty list. Include source name and throwable message.

---

## Security Considerations

### LocationIQ API Key Stored in Unencrypted SharedPreferences

- **Risk:** The LocationIQ key (pk.xxxx) entered by the user is stored via `SourceConfigStore` → `SharedPreferences` on disk. On rooted devices or via adb backup, this key can be extracted.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 358–366; `app/src/main/java/org/breezyweather/domain/settings/SourceConfigStore.kt`
- **Current mitigation:** Android's per-app private storage provides a baseline. Key rotation is cheap (user re-enters new key).
- **Recommendation:** Consider using Android `EncryptedSharedPreferences` (Jetpack Security) for `SourceConfigStore` to protect API keys at rest. This is especially relevant for `pk.` keys which have billing implications.

### LocationIQ Key Sent as Query Parameter (URL-visible)

- **Risk:** The `key` parameter is passed via `@Query("key")` in `NominatimApi.getReverseLocation`, meaning it appears in the HTTP request URL as a query string. OkHttp logging at `Level.BODY` (enabled in debug mode) prints full URLs to logcat.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimApi.kt` line 47; `app/src/main/java/org/breezyweather/common/di/HttpModule.kt` lines 102–107
- **Impact:** In debug builds, the full key is printed to logcat. In production this is NONE-level logging (acceptable), but the debug log exposure is a risk during development.
- **Recommendation:** This is the LocationIQ API's own convention; no app-level fix is needed. Ensure debug builds are never shared. Document this limitation.

---

## Tech Debt

### RxJava Not Migrated to Coroutines/Suspend Functions

- **Issue:** The entire HTTP stack uses RxJava3 `Observable` rather than Kotlin coroutines with `suspend`. Three `TODO` comments in `HttpModule.kt` explicitly call this out.
- **Files:** `app/src/main/java/org/breezyweather/common/di/HttpModule.kt` lines 133, 162, 191; all `*Service.kt` source files
- **Impact:** RxJava adds significant API complexity, thread management overhead, and a heavier dependency. `Observable.zip` for the concurrent strategy is harder to test and reason about than `async/await` with `supervisorScope`.
- **Fix approach:** Migrate one source at a time, starting with `NominatimService`. Replace `Observable` with `suspend` functions and use `coroutineScope { async { } }` for the concurrent dual-API call.

### `UpdateStrategy` Feature Commented Out

- **Issue:** The per-location `UpdateStrategy.ALWAYS_UPDATE` check is commented out with `TODO: Implement this, it's a good idea`. The surrounding code has dead commented-out logic.
- **Files:** `app/src/main/java/org/breezyweather/background/weather/WeatherUpdateJob.kt` lines 236–240
- **Impact:** All locations are always refreshed regardless of user preference, wasting battery and API quota.

### `ConfigStore` Should Be Read-Only After Migration

- **Issue:** Two TODOs note `ConfigStore` should become read-only and consider migration to Android DataStore.
- **Files:** `app/src/main/java/org/breezyweather/domain/settings/ConfigStore.kt` lines 22–24
- **Impact:** Mutable `ConfigStore` wrappers are scattered across service classes, making data mutation tracking difficult.

### `CommonConverterTest` Is Stub Only

- **Issue:** `CommonConverterTest.kt` has a single wind-degree test with a `TODO: To be completed` comment at the top. All other conversion logic (VN address, weather unit conversions) is untested in this class.
- **Files:** `app/src/test/java/org/breezyweather/sources/CommonConverterTest.kt`
- **Impact:** Regressions in shared conversion logic go undetected until runtime.

---

## Known Bugs

### `Release.getDownloadLink()` FIXME — Silent Wrong-File Fallback

- **Symptoms:** If no APK asset matches the device ABI pattern, the fallback `?: assets[0]` silently downloads the first asset, which may be the wrong architecture or a freenet variant.
- **Files:** `app/src/main/java/org/breezyweather/background/updater/model/Release.kt` line 54
- **Trigger:** Device with an uncommon ABI or when `GITHUB_RELEASE_PREFIX` naming convention changes.
- **Workaround:** None. Update will install an incompatible APK silently.

### Astro Timezone Handling Is Wrong

- **Symptoms:** Sunrise/sunset progress calculation uses a workaround involving `Calendar` but the TODO comment acknowledges the timezone logic is incorrect.
- **Files:** `app/src/main/java/org/breezyweather/domain/weather/model/Astro.kt` line 32
- **Trigger:** Locations in unusual timezones, or during DST transitions.
- **Workaround:** Visual glitches only; core weather data unaffected.

### Notifications FIXME: Timezone

- **Symptoms:** Notification scheduling has a `FIXME: Timezone` comment at line 239.
- **Files:** `app/src/main/java/org/breezyweather/remoteviews/Notifications.kt` line 239
- **Trigger:** Users in non-UTC timezones may see notification timing drift.

---

## Performance Bottlenecks

### Every VN Reverse Geocoding Request Hits Two External APIs Simultaneously

- **Problem:** With a LocationIQ key set, every `requestNearestLocation` call fires two HTTP requests in parallel (LocationIQ + Nominatim). For a user with 5 saved locations, a background refresh fires 10 external API calls per cycle.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 117–152
- **Cause:** Parallel `Observable.zip` with no short-circuit: Nominatim is always called even when LocationIQ connection is fast and clean.
- **Improvement path:** Consider calling Nominatim only when the LocationIQ result fails the VN regex check (lazy fallback). This avoids unnecessary Nominatim requests for locations that already return clean addresses.

---

## Fragile Areas

### `vnSubProvinceRegex` Pattern Compiled via Java `Pattern.compile`

- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` line 75
- **Why fragile:** Uses Java `Pattern` with `(?iu)` flags while Kotlin natively supports `Regex`. The `(?iu)` flag enables Unicode-aware case folding — critical for Vietnamese diacritics. If the pattern is ever copy-pasted or migrated to a plain Kotlin `Regex` without the `UNICODE_CASE` option, Vietnamese diacritic matching will silently break.
- **Safe modification:** Any changes to `vnSubProvinceRegex` MUST preserve `Pattern.UNICODE_CASE or Pattern.CASE_INSENSITIVE` (or `(?iu)` inline). The pattern must also be kept in sync with `testaddress.py`'s `VN_SUB_PROVINCE_REGEX`.
- **Test coverage:** Zero unit tests in Kotlin for this regex. Tests exist only in `signature/testaddress.py` (Python, not run by CI).

### `NominatimApi` Shared Between LocationIQ and Nominatim Endpoints

- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimApi.kt`
- **Why fragile:** The same interface is used for both `nominatim.openstreetmap.org` and `us1.locationiq.com/v1/`. LocationIQ and Nominatim return slightly different JSON structures (e.g., `format=json` vs `format=jsonv2`; different `display_name` conventions). Both are deserialized into `NominatimLocationResult`, which means a future divergence in either API response shape may silently cause null fields without a deserialization error (due to `ignoreUnknownKeys = true` and `explicitNulls = false` in `HttpModule.kt`).
- **Safe modification:** Before adding new JSON fields derived from one service, verify they also exist (or are safely nullable) for the other.

---

## Test Coverage Gaps

### Vietnamese Address Parsing — No Kotlin Unit Tests

- **What's not tested:** `pickBestVietnamSubProvincePart()`, the POI-garbage fallback branch (lines 210–214), the `displayName` split logic, and the dual-API merge `zip` result selection.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt`; no corresponding test file exists under `app/src/test/`
- **Risk:** Changes to the regex, split logic, or priority merging can regress silently. The Python `testaddress.py` covers algorithm logic but is not part of the Gradle test suite.
- **Priority:** High — this is the primary fork-specific feature

### `NominatimService` Concurrent Path — Not Tested

- **What's not tested:** The `Observable.zip` concurrent strategy path (key present), including the error-swallowing fallback, the zip result selection, and the fresh-client construction.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` lines 115–162
- **Risk:** Regression in dual-API logic goes undetected. The `mApi` stale-after-config bug would be caught by a proper unit test.
- **Priority:** High

### Common Source Converter — Stub Test

- **What's not tested:** All weather unit conversions, country code disambiguation logic, address field extraction logic.
- **Files:** `app/src/test/java/org/breezyweather/sources/CommonConverterTest.kt`
- **Priority:** Medium

---

## Dependencies at Risk

### Nominatim Public Instance — No SLA, ToS Restrictions

- **Risk:** `https://nominatim.openstreetmap.org/` is a public free service with no uptime guarantee, a hard 1 req/sec rate limit, and a ToS that prohibits bulk or automated usage without prior contact. The app uses it as a concurrent secondary call alongside LocationIQ.
- **Impact:** IP-level blocks would silently degrade Nominatim fallback for all users of that IP range (shared on mobile carriers).
- **Mitigation plan:** Support user-specified self-hosted Nominatim instances (already partially implemented via `instance` config). Document the ToS concern clearly in user-facing settings help text.

### LocationIQ US-West Endpoint Hardcoded

- **Risk:** `LOCATIONIQ_BASE_URL = "https://us1.locationiq.com/v1/"` is hardcoded to the US-1 region endpoint. Users in Asia (including Vietnam) will experience higher latency and the endpoint may differ in accuracy for VN data.
- **Files:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` line 397
- **Impact:** Suboptimal reverse geocoding accuracy and latency for the primary target userbase (Vietnam).
- **Migration plan:** Make the LocationIQ endpoint region configurable, or auto-select based on device locale/country.

---

*Concerns audit: 2026-03-23*
