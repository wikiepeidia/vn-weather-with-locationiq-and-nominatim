# VN Weather — Vietnamese Address Quality Fork

## Current Milestone: v1.2 ROM Hardening

**Goal:** Harden WatchdogService survival on HyperOS/MIUI through multiple restart vectors, WakeLock during heartbeat, process importance elevation, and user-visible health diagnostics.

**Target features:**
- WakeLock during heartbeat execution to prevent CPU sleep during weather check
- Redundant restart vectors: WorkManager periodic backup alongside AlarmManager
- Process importance elevation via invisible Activity binding (raise OOM adj score)
- Watchdog health dashboard in settings (last heartbeat time, restart count, service status)
- Enhanced keepalive notification with last-update time + next scheduled refresh
- v1.1 tech debt cleanup: extract `WEATHER_UPDATE_WORK_NAME` constant, add verification

---

## What This Is

A fork of a popular open-source Android weather app optimized for Vietnamese administrative addresses (wards, communes, special zones). It uses LocationIQ and Nominatim APIs in tandem to deliver clean, accurate sub-province address names (e.g. "Phường Phú Lương" instead of "Ủy ban nhân dân Phường Phú Lương") for Vietnamese users, with smart cross-validation, lazy fallback, and "giggles" — delightful feedback when the fallback saves a bad result. Includes a Background Watchdog service that ensures weather refreshes actually happen on aggressive Android ROMs (HyperOS, MIUI).

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

### Active (v1.2 — ROM Hardening)

- [ ] WakeLock acquired during heartbeat execution to prevent CPU sleep
- [ ] Redundant restart vector: WorkManager periodic job as backup to AlarmManager
- [ ] Process importance elevation via invisible Activity binding on aggressive ROMs
- [ ] Watchdog health dashboard in Background Updates settings (status, last heartbeat, restarts)
- [ ] Enhanced keepalive notification with last-update + next-scheduled info
- [ ] Extract `WEATHER_UPDATE_WORK_NAME` from hardcoded string literal to shared constant

### Out of Scope

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
- **Known issue (ROM-V2-03):** HyperOS kills the app even with battery-opt disabled + autostart + memory exclusion. Users report needing 2-3 refreshes. **v1.2 target.**
- **AlarmManager vs WorkManager:** WorkManager killed by HyperOS; AlarmManager survives better but alone is not enough. v1.2 adds redundant restart vectors.
- **OOM adj:** Android assigns OOM adjustment scores to processes. Binding to an Activity raises the score, making the process less likely to be killed. AdGuard and similar apps use this technique.

## Constraints

- **Kotlin:** All new code in Kotlin; no new Java
- **No breaking changes:** Existing API surfaces must remain stable
- **Battery:** New features with battery impact must be opt-in

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Lazy Nominatim (conditional, not always-parallel) | Eliminates rate limit risk; faster happy path | ✓ Good |
| Cross-validation: prefer clean regex match over API priority | Correct result = clean VN token regardless of source | ✓ Good |
| `firstOrNull` for LocationIQ, `lastOrNull` for Nominatim | Zoom-level-dependent token ordering | ✓ Good |
| Add `suburb`/`hamlet` to `NominatimAddress` | Nominatim returns VN ward under `suburb` | ✓ Good |
| Watchdog opt-in (default off) | Foreground service + AlarmManager drains battery | ✓ Good |
| AlarmManager heartbeat for Watchdog self-heal | WorkManager killed by HyperOS; AlarmManager survives | ✓ Good |
| Delegate weather-fetch to existing WeatherUpdateJob | Watchdog monitors; doesn't duplicate logic | ✓ Good |
| `watchdogEnabled` guard in AlarmReceiver | Prevents zombie resurrection after user disables | ✓ Good (found in audit) |
| Defensive alarm cancel in `stop()` | Covers process-kill scenario where onDestroy never fires | ✓ Good (found in audit) |

---
*Last updated: 2026-04-02 after v1.2 ROM Hardening milestone started*
