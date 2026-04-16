# VN Weather — Vietnamese Address Quality Fork

## Current Milestone: v1.3 LocationIQ Recovery & Key Validation

**Goal:** Restore LocationIQ as an active, reliable API source and prevent invalid LocationIQ keys from being saved.

**Target features:**

- Fix bug where LocationIQ API key/path is ignored by the app flow
- Validate LocationIQ API key on Save by pinging server and checking invalid JSON response
- Improve settings UX with validation state + error feedback; block saving invalid key

---

## What This Is

A fork of a popular open-source Android weather app optimized for Vietnamese administrative addresses (wards, communes, special zones). It uses LocationIQ and Nominatim APIs in tandem to deliver clean, accurate sub-province address names (e.g. "Phường Phú Lương" instead of "Ủy ban nhân dân Phường Phú Lương") for Vietnamese users, with smart cross-validation, lazy fallback, and "giggles" — delightful feedback when the fallback saves a bad result. Includes a Background Watchdog service that ensures weather refreshes actually happen on aggressive Android ROMs (HyperOS, MIUI).
How it work: LocationIQ is the primary geocoding source for Vietnam. If LocationIQ's result fails a regex check for clean ward/commune tokens, the app falls back to Nominatim's. This is because sometime LocationIQ acting garbage but recently being very good for Vietnam, but we still dont know whether any location, small location still have errors. THats why it fallback to nominatim. Instead of using old Observable.zip parallel API calls, it uses a lazy strategy where Nominatim is only called if LocationIQ fails the regex check. This eliminates unnecessary Nominatim traffic and rate limit risk when LocationIQ is working well. The Background Watchdog is implemented as a foreground service with an AlarmManager heartbeat to survive aggressive ROM killing, and includes a settings toggle and HyperOS autostart integration.

## Core Value

Vietnamese users see a clean ward/commune name — never a POI or government-office prefix — and weather updates reliably happen on background-aggressive ROMs like HyperOS.

## Requirements

### Validated

- ✓ LocationIQ reverse geocoding integrated as primary source for VN — existing
- ✓ Nominatim reverse geocoding integrated as concurrent fallback — existing  
- ✓ `Observable.zip` parallel dual-API strategy for VN country code — existing
- ✓ `vnSubProvinceRegex` regex extracts Phường/Xã/Đặc khu tokens — existing
- ✓ `pickBestVietnamSubProvincePart()` selects best match from `display_name` — existing
- ✓ User-configurable LocationIQ API key in settings — existing
- ✓ User-configurable Nominatim instance URL — existing
- ✓ Python test harness (`testaddress.py`) for regex validation — existing

### Validated (v1.0 — VN Address Quality)

- ✓ Clean VN address — no POI/government-office prefix shown as city name — Phase 2
- ✓ Real cross-validation — prefer whichever API returns a clean VN token — Phase 2
- ✓ Lazy Nominatim — only fire Nominatim when LocationIQ result fails regex check — Phase 3
- ✓ `NominatimAddress` maps `suburb`, `hamlet`, `quarter` fields for VN structured data — Phase 1
- ✓ Correct token ordering — `firstOrNull` for LocationIQ, `lastOrNull` for Nominatim — Phase 2
- ✓ Fresh API client per call — eliminate stale `mApi` after config change — Phase 3
- ✓ Nominatim error logging — debug log which source failed and why — Phase 3
- ✓ LocationIQ endpoint configurable or defaulted to Asia region — Phase 3
- ✓ "Giggles" rescue feedback (debug log + playful settings description) — Phase 4
- ✓ Kotlin unit tests for all VN address parsing logic in Gradle test suite — Phase 5

### Validated (v1.1 — Background Watchdog)

- ✓ `WatchdogService` foreground service with persistent minimal-priority notification — Phase 6
- ✓ AlarmManager `setExactAndAllowWhileIdle()` heartbeat re-arms Watchdog after ROM kill — Phase 6
- ✓ Job monitor: re-enqueues `WeatherUpdateJob` when it is no longer scheduled — Phase 6
- ✓ Settings toggle to enable/disable Watchdog mode (default: off) — Phase 7
- ✓ Watchdog notification shows last-update timestamp — Phase 6
- ✓ HyperOS/MIUI autostart deep-link shown in Background Updates settings — Phase 7
- ✓ `BootReceiver` starts `WatchdogService` after device reboot when Watchdog is enabled — Phase 8
- ✓ Battery optimization prompt shown when user enables Watchdog mode — Phase 7
- ✓ Graceful degradation: exact alarm → inexact fallback when ROM restricts — Phase 6
- ✓ Clean disable: service stop + alarm cancel + notification dismiss — Phase 7 + audit fix

### Validated (v1.2 — ROM Hardening)

- ✓ WakeLock acquired during heartbeat execution to prevent CPU sleep — Phase 10
- ✓ Redundant restart vector: WorkManager periodic job backup to AlarmManager — Phase 11
- ✓ Watchdog health dashboard in Background Updates settings — Phase 14
- ✓ Enhanced watchdog behavior: ephemeral service model and transient worker foreground notification — Phase 13
- ✓ Extract `WEATHER_UPDATE_WORK_NAME` from hardcoded literal to shared constant — Phase 9

### Active (v1.3 — LocationIQ Recovery & Key Validation)

- [ ] LocationIQ API key and request path are actually honored in reverse geocoding flow
- [ ] Saving LocationIQ key triggers server ping validation (detect invalid JSON response)
- [ ] Settings UX blocks invalid key save and shows explicit validation result to user

### Out of Scope

- Invisible Activity binding (OOM adj elevation for HyperOS) — Android 14+ BAL restrictions block it from background; MIUI Security flags it as adware; WorkManager hybrid restart negates need
- RxJava → Coroutines full migration — large refactor, orthogonal to feature work
- Nominatim rate limiter (Semaphore) — lazy fallback strategy eliminates concurrent traffic
- `ConfigStore` → DataStore migration — orthogonal
- EncryptedSharedPreferences for API key — good idea but defer

## Context

- **Forked from:** Breezy Weather (open source Android weather app)
- **Target users:** Vietnamese Android users; device locale = `vn`; special attention to HyperOS/MIUI (Xiaomi, Redmi, POCO devices)
- **Architecture:** Clean Architecture + MVVM, multi-module (app/data/domain). RxJava3 HTTP stack.
- **v1.0 delivered:** VN address quality — cross-validation, lazy Nominatim, structured fields, giggles feedback, Kotlin tests
- **v1.1 delivered:** Background Watchdog — persistent foreground service, AlarmManager heartbeat, settings toggle, HyperOS autostart, boot resume
- **v1.2 delivered:** ROM hardening — WakeLock heartbeat, restart resilience, notification architecture cleanup, health dashboard
- **Known issue (v1.3 target):** LocationIQ key/API path is ignored in parts of current flow, causing LocationIQ settings to appear non-functional
- **AlarmManager vs WorkManager:** WorkManager killed by HyperOS; AlarmManager survives better but alone is not enough. v1.2 adds redundant restart vectors.
- **OOM adj:** Android assigns OOM adjustment scores to processes. Binding to an Activity raises the score, making the process less likely to be killed. AdGuard and similar apps use this technique.

## Constraints

- **Kotlin:** All new code in Kotlin; no new Java
- **No breaking changes:** Existing API surfaces must remain stable
- **Battery:** New features with battery impact must be opt-in
- **Algorithm freeze:** Keep VN address parsing/cross-validation algorithm unchanged; fix LocationIQ call-path and settings validation wiring only

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Lazy Nominatim (conditional, not always-parallel) | Eliminates rate limit risk; faster happy path | ✓ Good |
| Cross-validation: prefer clean regex match over API priority | Correct result = clean VN token regardless of source | ✓ Good |
| `firstOrNull` for LocationIQ, `lastOrNull` for Nominatim | Zoom-level-dependent token ordering | ✓ Good |
| Add `suburb`/`hamlet` to `NominatimAddress` | Nominatim returns VN ward under `suburb` | ✓ Good |
| Drop Phase 12 (invisible Activity OOM trick) | Android 14+ BAL restrictions + MIUI Security adware flag; WorkManager hybrid restart (Phase 11) already reduces kill risk; zero-pixel trick actively counterproductive | ✓ Good |
| WakeLock in WatchdogService heartbeat | WorkManager handles WakeLock for its own workers, but the AlarmManager-triggered heartbeat path (WatchdogService.performHeartbeat) runs in a Service, not a Worker — manual WakeLock still needed there | ✓ Good |
| AlarmManager heartbeat for Watchdog self-heal | WorkManager killed by HyperOS; AlarmManager survives | ✓ Good |
| Delegate weather-fetch to existing WeatherUpdateJob | Watchdog monitors; doesn't duplicate logic | ✓ Good |
| `watchdogEnabled` guard in AlarmReceiver | Prevents zombie resurrection after user disables | ✓ Good (found in audit) |
| Defensive alarm cancel in `stop()` | Covers process-kill scenario where onDestroy never fires | ✓ Good (found in audit) |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):

1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):

1. Full review of all sections
2. Core Value check -> still the right priority?
3. Audit Out of Scope -> reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-16 after milestone v1.3 start*
