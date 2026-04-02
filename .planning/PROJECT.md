# VN Weather — Vietnamese Address Quality Fork

## Current Milestone: v1.1 Background Watchdog

**Goal:** Weather updates run reliably on aggressive-kill ROMs (HyperOS, MIUI) via a persistent foreground Watchdog service with AlarmManager heartbeat backup.

**Target features:**
- `WatchdogService`: persistent foreground service with minimal-priority "keepalive" notification
- AlarmManager `setExactAndAllowWhileIdle()` heartbeat re-arms service after ROM kills it
- Job monitor: detects dead `WeatherUpdateJob` and re-enqueues it
- Settings toggle to enable/disable Watchdog mode
- Notification shows "last updated X min ago" with current conditions
- HyperOS/MIUI autostart deep-link in BackgroundUpdates settings screen
- `BootReceiver` starts Watchdog on device reboot (when enabled)

---

## What This Is

A fork of a popular open-source Android weather app optimized for Vietnamese administrative addresses (wards, communes, special zones). It uses LocationIQ and Nominatim APIs in tandem to deliver clean, accurate sub-province address names (e.g. "Phường Phú Lương" instead of "Ủy ban nhân dân Phường Phú Lương") for Vietnamese users, with smart cross-validation, lazy fallback, and "giggles" — delightful feedback when the fallback saves a bad result. Now with a Background Watchdog to ensure weather refreshes actually happen on aggressive Android ROMs.

## Core Value

Vietnamese users see a clean ward/commune name — never a POI or government-office prefix — even when LocationIQ or Nominatim individually return garbage.

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

### Active (v1.1 — Background Watchdog)

- [ ] `WatchdogService` foreground service with persistent minimal-priority notification
- [ ] AlarmManager `setExactAndAllowWhileIdle()` heartbeat re-arms Watchdog after ROM kill
- [ ] Job monitor: re-enqueues `WeatherUpdateJob` when it is no longer scheduled
- [ ] Settings toggle to enable/disable Watchdog mode (default: off)
- [ ] Watchdog notification shows last-update timestamp and current weather conditions summary
- [ ] HyperOS/MIUI autostart deep-link shown in Background Updates settings
- [ ] `BootReceiver` starts `WatchdogService` after device reboot when Watchdog is enabled
- [ ] Battery optimization prompt shown when user enables Watchdog mode

### Out of Scope

- RxJava → Coroutines full migration — large refactor, orthogonal to VN address quality; defer
- Nominatim rate limiter (Semaphore) — lazy fallback strategy already eliminates concurrent traffic
- `UpdateStrategy` commenting — unrelated to this feature area
- `ConfigStore` → DataStore migration — orthogonal; defer
- Astro/timezone bug fixes — unrelated to VN address
- `Release.getDownloadLink` fix — unrelated to VN address
- EncryptedSharedPreferences for API key — good idea but out of scope for this focus milestone

## Context

- **Forked from:** Breezy Weather (open source Android weather app)
- **Target users:** Vietnamese Android users; device locale = `vn`; primary pain point for this milestone is HyperOS/MIUI aggressive background-kill (Xiaomi, Redmi, POCO devices)
- **The core problem (v1.0):** Vietnamese administrative address reorganization effective July 1, 2025 creates mismatches between what APIs return and what users expect (clean ward names). LocationIQ zoom=18 often returns POI-prefixed strings. Nominatim returns structured data under `suburb` which isn't mapped. *(Delivered in v1.0)*
- **The core problem (v1.1):** WorkManager periodic tasks are silently cancelled on HyperOS/MIUI by the ROM's app lifecycle manager. Users configured a 30-min refresh but see weather last updated 6 hours ago because the OS killed the worker. A persistent foreground service + AlarmManager heartbeat is the proven mitigation pattern (as used by AdGuard, NetSpeedMonitor, system-utility apps).
- **Architecture:** Clean Architecture + MVVM, multi-module (app/data/domain). RxJava3 HTTP stack. Key background files: `WeatherUpdateJob`, `BootReceiver`, `BackgroundUpdatesSettingsScreen`, `Notifications`.
- **AlarmManager vs WorkManager:** WorkManager is recommended by Google but routinely killed on MIUI/HyperOS. `AlarmManager.setExactAndAllowWhileIdle()` is more reliable on these ROMs. The Watchdog service uses AlarmManager as its own heartbeat while delegating actual weather-fetch to `WeatherUpdateJob`.
- **"Giggles":** When cross-validation detects that Nominatim rescued a bad LocationIQ result, show a brief Snackbar message or debug toast so developers and users notice the system working as intended. *(Delivered in v1.0)*

## Constraints

- **Compatibility:** Must not affect weather update behavior when Watchdog is disabled (feature-flagged)
- **Battery:** Foreground service + AlarmManager will consume more battery; Watchdog must be opt-in, not default
- **Kotlin:** All new code in Kotlin; no new Java
- **No breaking changes:** Existing `WeatherUpdateJob`, `BootReceiver`, `Notifications` API surface must remain stable
- **HyperOS policy:** Some MIUI/HyperOS versions restrict `setExactAndAllowWhileIdle()`; the implementation must degrade gracefully (fall back to inexact alarm or WorkManager-only) rather than crash

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Lazy Nominatim (conditional, not always-parallel) | Eliminates rate limit risk; faster happy path; Nominatim only fires when LocationIQ regex fails | ✓ Accepted |
| Cross-validation: prefer clean regex match over API priority | The "correct" result is whichever has a clean VN token, not which API responded first | ✓ Accepted |
| `firstOrNull` for LocationIQ, `lastOrNull` for Nominatim | LocationIQ zoom=18 has ward as FIRST segment; Nominatim zoom=13 has it LAST | ✓ Accepted |
| Add `suburb`/`hamlet` to `NominatimAddress` | Nominatim returns VN ward under `suburb` key; currently unmapped, causing regex fallback brittleness | ✓ Accepted |
| Skip RxJava migration | High-risk refactor; independent of address quality goal | ✓ Accepted |
| Watchdog opt-in (default off) | Foreground service + AlarmManager drains battery; should only activate when user needs it (aggressive ROM) | — Pending |
| AlarmManager heartbeat for Watchdog self-heal | WorkManager cannot reliably reschedule itself after HyperOS kills it; AlarmManager survives better | — Pending |
| Delegate weather-fetch to existing WeatherUpdateJob | Watchdog monitors and re-enqueues jobs; doesn't duplicate weather-fetch logic | — Pending |

---
*Last updated: 2026-04-02 after milestone v1.1 Background Watchdog started*
