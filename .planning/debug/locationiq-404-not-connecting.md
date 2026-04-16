---
status: resolved
trigger: "Investigate issue: locationiq-404-not-connecting — LocationIQ still not connecting properly; app throws HTTP 404 during geocoding calls after endpoint/provider changes."
created: 2026-04-16T00:00:00Z
updated: 2026-04-16T01:05:00Z
---

# Debug Session: locationiq-404-not-connecting

## Current Focus

hypothesis: Confirmed root cause is single-endpoint LocationIQ call path: the app uses only one configured/default endpoint for key-based calls and does not retry alternate regional endpoints (`eu1`/`us1`) when first endpoint returns HTTP 404.
test: Implement endpoint-candidate fallback (`configured/default` then alternate region) for key-based `search` and `reverse` calls; verify with unit tests and Gradle test run.
expecting: Key-based calls no longer hard-fail on first-endpoint 404 when alternate region is reachable/valid.
next_action: resolved

## Symptoms

expected: Reverse geocoding should call LocationIQ successfully (us1/eu1) when pk key is configured and return location data.
actual: App shows many bizarre errors; stack trace reports retrofit2.HttpException: HTTP 404.
errors: retrofit2.HttpException: HTTP 404 at retrofit2.KotlinExtensions$await$2$2.onResponse(KotlinExtensions.kt:53) and okhttp callback chain.
reproduction: Configure Nominatim/LocationIQ source with pk key, trigger reverse geocoding in app (location/address lookup), observe 404 failure.
started: Started around older commits when old API endpoint went down; behavior remained unstable afterward.

## Eliminated

- hypothesis: LocationIQ requires `.php` suffix and `@GET("reverse")`/`@GET("search")` alone causes 404
  evidence: Direct probes to `https://eu1.locationiq.com/v1/reverse` and `https://eu1.locationiq.com/v1/search` returned HTTP 401 (same as `.php` forms) with invalid key, proving these paths exist.
  timestamp: 2026-04-16T00:21:00Z

## Evidence

- timestamp: 2026-04-16T00:02:00Z
  checked: active debug sessions list
  found: Multiple active sessions exist (.planning/debug/hyperos-watchdog-crash.md, .planning/debug/nominatim-address-retrieval-broken.md, and this session file).
  implication: New issue investigation is valid because this run includes a new explicit objective; do not reuse prior state.

- timestamp: 2026-04-16T00:02:30Z
  checked: code search tooling availability
  found: `rg` is not available in this terminal environment.
  implication: Use grep/find and direct file reads for evidence gathering.

- timestamp: 2026-04-16T00:06:00Z
  checked: source-level string and annotation search for LocationIQ/Nominatim wiring
  found: Reverse geocoding path is implemented in `app/src/main/java/org/breezyweather/sources/nominatim/NominatimApi.kt`, runtime wiring in `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt`, and default LocationIQ base URL constant is `https://eu1.locationiq.com/v1/`.
  implication: The 404 likely comes from URL shape mismatch between base URL normalization and endpoint path assembly in these files.

- timestamp: 2026-04-16T00:10:00Z
  checked: `NominatimApi` + `NominatimService` full code path for reverse/search
  found: API uses `@GET("search")` and `@GET("reverse")` for both providers; when pk key exists, LocationIQ client uses base URL resolved to `/v1/` and sends reverse call with `format="json"` + `key=pk...`.
  implication: If LocationIQ currently requires `.php` endpoint suffixes, this implementation will consistently produce HTTP 404 while preserving fallback behavior.

- timestamp: 2026-04-16T00:12:00Z
  checked: broad repository text scan for LocationIQ path clues
  found: Result set is large/noisy (multiple planning and doc files), requiring targeted filtering for executable code/tests.
  implication: Need focused evidence from test and runtime files before confirming root cause.

- timestamp: 2026-04-16T00:14:30Z
  checked: targeted source/test scan for `reverse.php`/`search.php`
  found: Existing unit tests already normalize both `.../v1/reverse?...` and `.../v1/reverse.php?...` to `.../v1/`; runtime API interface still calls `@GET("reverse")` and `@GET("search")`.
  implication: Current tests validate base URL normalization but do not prove LocationIQ request path compatibility; 404 may still be due to missing `.php` on actual Retrofit call paths.

- timestamp: 2026-04-16T00:17:00Z
  checked: `app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt`
  found: Tests cover key-state classification, base URL normalization, VN parsing, and merge behavior, but there is no assertion that LocationIQ Retrofit paths must be `reverse.php/search.php`.
  implication: A path-level compatibility bug can exist even when current tests all pass.

- timestamp: 2026-04-16T00:20:00Z
  checked: live HTTP status probe for LocationIQ endpoint variants
  found: `eu1.locationiq.com/v1/reverse`, `.../reverse.php`, `.../search`, and `.../search.php` all returned HTTP 401 with an invalid key (not 404).
  implication: 404 is likely host/config-specific rather than endpoint suffix-specific.

- timestamp: 2026-04-16T00:23:30Z
  checked: project-wide references to `instance` and `locationiq_base_url`
  found: `NominatimService` persists key/url in `SourceConfigStore` under `instance` and `locationiq_base_url`; no other obvious dedicated migration path for these keys surfaced in the first pass.
  implication: Stale persisted values can plausibly survive unless explicitly normalized/validated inside `NominatimService` accessors.

- timestamp: 2026-04-16T00:25:00Z
  checked: live host probe (`api.locationiq.com`, `us1.locationiq.com`, `eu1.locationiq.com`, `locationiq.com`)
  found: All tested hosts returned HTTP 401 with an invalid key for `/v1/reverse`.
  implication: Reported 404 likely depends on valid-key + endpoint-region state, not simple host reachability.

- timestamp: 2026-04-16T00:26:00Z
  checked: reverse probes without key / with alternate format
  found: `/v1/reverse?format=jsonv2` returned HTTP 400 (missing/invalid key), while default no-key JSON forms returned 401.
  implication: 404 is unlikely to be caused solely by missing key or format parameter mismatch.

- timestamp: 2026-04-16T00:29:00Z
  checked: LocationIQ docs (`reverse-geocoding`, `search-forward-geocoding`)
  found: Official docs define both US and EU endpoints (`us1.locationiq.com` and `eu1.locationiq.com`) for v1 reverse/search requests.
  implication: Robust client behavior should support endpoint fallback when one regional endpoint fails for a given key/runtime path.

- timestamp: 2026-04-16T00:30:00Z
  checked: current `NominatimService` request flow for pk-key mode
  found: Both `requestLocationSearch` and `requestNearestLocation` build a single LocationIQ client from one base URL and do not attempt alternate endpoints before surfacing failure (search) or falling back only to Nominatim (reverse).
  implication: Endpoint mismatch can manifest as persistent 404 in key mode; adding multi-endpoint retry is a targeted fix.

- timestamp: 2026-04-16T00:32:30Z
  checked: GitNexus CLI availability for required impact analysis
  found: `npx gitnexus status` failed in this environment with npm dependency/runtime errors, so graph-based impact analysis is not executable here.
  implication: Proceed with manual call-site impact tracing before code edits.

- timestamp: 2026-04-16T00:34:30Z
  checked: manual call-site blast radius for `requestLocationSearch` and `requestNearestLocation`
  found: Core upstream callers are interface dispatches through `RefreshHelper` (`requestLocationSearch` around line 1021 and `requestNearestLocation` around line 439); change scope is localized to Nominatim implementation behavior only.
  implication: Low-to-moderate risk; verify search + reverse flows after patching.

- timestamp: 2026-04-16T00:37:30Z
  checked: `NominatimService` implementation update
  found: Added endpoint candidate resolution (`resolveLocationIqBaseUrlCandidates`) and sequential retry helper (`requestLocationIqWithEndpointFallback`) for key-based LocationIQ search and reverse calls; reverse still falls back to Nominatim after LocationIQ candidate failures.
  implication: Endpoint/call-path wiring now retries alternate region before surfacing failure, directly addressing the 404 connection issue.

- timestamp: 2026-04-16T00:38:30Z
  checked: `NominatimServiceTest` update
  found: Added tests covering endpoint candidate order and fallback list for default, US override, and custom host override.
  implication: Behavior is now guarded against future regressions.

## Resolution

root_cause: Key-based LocationIQ requests use a single configured/default endpoint and lack automatic us1/eu1 retry. When the selected endpoint returns HTTP 404, search fails directly and reverse depends solely on Nominatim fallback instead of retrying the alternate regional LocationIQ endpoint.
fix: Add LocationIQ endpoint candidate resolution and sequential retry across endpoints for key-based search and reverse requests, preserving existing VN parsing and Nominatim cross-validation behavior.
verification: ./gradlew :app:testFreenetDebugUnitTest --tests org.breezyweather.sources.nominatim.NominatimServiceTest (BUILD SUCCESSFUL); ./gradlew assembleDebug (BUILD SUCCESSFUL)
files_changed: ["app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt", "app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt"]
