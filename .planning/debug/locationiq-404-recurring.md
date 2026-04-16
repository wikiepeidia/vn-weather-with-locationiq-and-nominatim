---
status: resolved
trigger: "Investigate issue: locationiq-404-recurring. User still gets recurring Retrofit HTTP 404 after ap1 endpoint deprecation and endpoint migration."
created: 2026-04-16T22:50:23+07:00
updated: 2026-04-16T23:16:48+07:00
---

# Debug Session: locationiq-404-recurring

## Current Focus

hypothesis: Root cause has been confirmed via git history, blame, call-path inspection, and passing regression tests.
test: Verify patched behavior through targeted unit suites and debug assembly.
expecting: Builds/tests succeed and no direct LocationIQ 404 propagation remains in search/reverse paths for migrated configs.
next_action: Publish final report with commit evidence, patch lines, and command outcomes.

## Symptoms

expected: Switching old ap1 endpoint to us1/eu1 should restore LocationIQ reverse geocoding.
actual: Same Retrofit error keeps happening; LocationIQ appears not properly connected.
errors: retrofit2.HttpException: HTTP 404 (stack includes KotlinExtensions.await and OkHttp callback chain).
reproduction: Configure Nominatim/LocationIQ in app and trigger reverse geocoding/search; failure persists.
started: Used to work in previous commits; started failing recently after endpoint churn (ap1 down).

## Eliminated

- hypothesis: LocationIQ requires .php path suffixes and current @GET("reverse")/@GET("search") path wiring causes 404.
  evidence: Live probes to eu1/us1 and api hosts returned 401 on both /v1/reverse and /v1/reverse.php (and similarly for search variants), proving non-php paths exist.
  timestamp: 2026-04-16T23:01:45+07:00

## Evidence

- timestamp: 2026-04-16T22:51:21+07:00
  checked: .planning/debug/knowledge-base.md
  found: No knowledge base file exists for prior matching patterns.
  implication: Continue with fresh hypothesis-driven investigation from source and git history.

- timestamp: 2026-04-16T22:51:50+07:00
  checked: git history for LocationIQ/Nominatim source files
  found: Recent commit 481fe82eb mentions LocationIQ endpoint reliability changes; git -S for ap1.locationiq.com points only to this commit.
  implication: High-probability regression window around endpoint migration/refactor logic.

- timestamp: 2026-04-16T22:51:50+07:00
  checked: ripgrep availability
  found: rg is not installed in this environment.
  implication: Use grep_search and targeted file reads instead of rg.

- timestamp: 2026-04-16T22:52:58+07:00
  checked: commit scope and source-wide endpoint references
  found: Commit 481fe82eb touches only app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt; endpoint/candidate logic and fallback paths are concentrated there.
  implication: Root cause likely inside this file's normalization, candidate selection, or request path wiring.

- timestamp: 2026-04-16T22:53:59+07:00
  checked: current NominatimService + NominatimApi endpoint wiring
  found: LocationIQ base candidates resolve to eu1/us1 with fallback, but API annotations are @GET("search") and @GET("reverse") while normalization logic explicitly recognizes reverse.php/search.php patterns.
  implication: Even with correct hosts, LocationIQ calls may still hit wrong path variant and return 404 repeatedly.

- timestamp: 2026-04-16T22:53:59+07:00
  checked: endpoint-related tests
  found: Existing tests focus on base URL normalization/candidate order; no direct assertion of emitted reverse/search request path variants for LocationIQ.
  implication: Regression can pass tests while still producing runtime 404.

- timestamp: 2026-04-16T23:01:45+07:00
  checked: live endpoint probes for eu1/us1/api hosts
  found: /v1/reverse and /v1/search (with and without .php) all respond 401 without key; ap1 host no longer resolves (DNS failure).
  implication: Path suffix is not the 404 root cause; deprecated ap1 host is dead and should be sanitized from candidate selection.

- timestamp: 2026-04-16T23:01:45+07:00
  checked: search vs reverse error handling in NominatimService
  found: Reverse path falls back to Nominatim on LIQ errors, but search path has no Nominatim rescue and can bubble raw LIQ HttpException.
  implication: Recurring LIQ 404 can persist in search flow even after endpoint migration attempts.

- timestamp: 2026-04-16T23:03:32+07:00
  checked: implemented fix in NominatimService + tests
  found: Added search onError fallback to Nominatim and sanitized deprecated ap1 host in resolveLocationIqBaseUrl; updated tests to assert ap1 fallback behavior.
  implication: Runtime should no longer propagate LIQ 404 directly from search and should avoid routing primary requests to dead ap1 host.

- timestamp: 2026-04-16T23:05:45+07:00
  checked: Gradle unit tests (freenet/basic debug flavors)
  found: :app:testFreenetDebugUnitTest and :app:testBasicDebugUnitTest passed for org.breezyweather.sources.nominatim.NominatimServiceTest, including new deprecated-ap1 coverage.
  implication: Endpoint resolution changes and VN parsing/cross-validation tests remain stable at unit level.

- timestamp: 2026-04-16T23:15:12+07:00
  checked: git commit comparison for regression window (b6400cfe8 -> 481fe82eb)
  found: b6400cfe8 used LOCATIONIQ_BASE_URL=`https://us1.locationiq.com/v1/` while 481fe82eb changed it to `https://ap1.locationiq.com/v1/` and kept key-mode search URL selection tied to LOCATIONIQ_BASE_URL.
  implication: ap1 deprecation and hardcoded key-mode endpoint selection explain recurring 404 despite endpoint switching attempts.

- timestamp: 2026-04-16T23:16:12+07:00
  checked: patched NominatimService call path and migration logic
  found: requestLocationSearch/requestNearestLocation now run legacy instance migration, use endpoint candidate fallback, and normalize legacy LocationIQ URL values into dedicated endpoint preference.
  implication: dead host routing and legacy misconfigured instance loops are both cut off without modifying VN parsing.

- timestamp: 2026-04-16T23:16:48+07:00
  checked: post-patch verification commands
  found: ./gradlew :app:testFreenetDebugUnitTest --tests org.breezyweather.sources.nominatim.NominatimServiceTest :app:testBasicDebugUnitTest --tests org.breezyweather.sources.nominatim.NominatimServiceTest -> BUILD SUCCESSFUL; ./gradlew assembleDebug -> BUILD SUCCESSFUL.
  implication: Fix compiles and regression tests pass across both debug flavors.

## Resolution

root_cause: Commit 481fe82eb introduced a hardcoded LocationIQ primary endpoint switch to deprecated ap1 plus key-mode search flow that lacked resilient fallback semantics; legacy LocationIQ URLs persisted in the shared instance setting could still be treated as Nominatim base URLs, preserving HTTP 404 loops.
fix: Kept VN parsing intact while adding endpoint-candidate fallback, search onError fallback to Nominatim, deprecated ap1 sanitization, and backward-compatible migration of legacy LocationIQ endpoint values from instance to dedicated endpoint preference.
verification: Passed ./gradlew :app:testFreenetDebugUnitTest --tests org.breezyweather.sources.nominatim.NominatimServiceTest :app:testBasicDebugUnitTest --tests org.breezyweather.sources.nominatim.NominatimServiceTest and ./gradlew assembleDebug (both BUILD SUCCESSFUL).
files_changed: ["app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt", "app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt", "app/src/main/res/values/strings.xml"]
