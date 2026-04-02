# Requirements: VN Weather — Background Watchdog (v1.1)

**Defined:** 2026-04-02
**Milestone:** v1.1 — Background Watchdog
**Core Value:** Weather updates reliably happen on aggressive background-kill ROMs (HyperOS, MIUI) via a persistent foreground Watchdog service the user can opt into.

---

## Previous Milestone: v1.0 — VN Address Quality (Complete ✓)

All 19 requirements from v1.0 delivered across Phases 1–5. See archived REQUIREMENTS-v1.0.md for history.

---

## v1.1 Requirements

### Watchdog Service — Core Keepalive

- [ ] **WATCH-01**: A `WatchdogService` (Android `Service`) runs as a foreground service while Watchdog mode is enabled, preventing the ROM from killing the process silently
- [ ] **WATCH-02**: `WatchdogService` posts a persistent notification on a minimal-priority channel (no sound, no popup) when it starts, as required by the Android foreground-service API
- [ ] **WATCH-03**: `WatchdogService` monitors `WeatherUpdateJob` at each heartbeat tick — if the WorkManager job is no longer ENQUEUED or RUNNING, it re-enqueues it via `WeatherUpdateJob.setupTask(context)`
- [ ] **WATCH-04**: `WatchdogService` self-heals: it schedules an `AlarmManager.setExactAndAllowWhileIdle()` alarm before each sleep cycle so the ROM restarts it if the process is killed between heartbeats

### Watchdog Notification

- [ ] **NOTIF-01**: A dedicated `CHANNEL_WATCHDOG` notification channel is created with `IMPORTANCE_MIN` (shows in status bar shade, no sound, no badge, no popup)
- [ ] **NOTIF-02**: The Watchdog notification displays the last successful weather update timestamp (e.g. "Weather last updated 12 min ago") and updates with each completed refresh
- [ ] **NOTIF-03**: The Watchdog notification is non-dismissible while the Watchdog service is running (ongoing = true)

### Settings — Watchdog Toggle

- [ ] **SETT-01**: `BackgroundUpdatesSettingsScreen` includes a "Watchdog Mode" toggle (default: OFF) that starts/stops `WatchdogService`
- [ ] **SETT-02**: When the user enables Watchdog mode and the app is not battery-optimization-exempt, a prompt is shown directing them to the system battery optimization settings (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- [ ] **SETT-03**: `SettingsManager` persists the `watchdogEnabled: Boolean` preference so Watchdog state survives app restarts
- [ ] **SETT-04**: `BackgroundUpdatesSettingsScreen` shows a HyperOS/MIUI "Autostart" deep-link button when the device manufacturer is Xiaomi/Redmi/POCO, directing users to the MIUI autostart permission manager

### Boot & Resume

- [ ] **BOOT-01**: `BootReceiver` starts `WatchdogService` on `ACTION_BOOT_COMPLETED` if `watchdogEnabled` is true, ensuring Watchdog resumes after device reboot without user action
- [ ] **BOOT-02**: `WatchdogService` on start always re-enqueues `WeatherUpdateJob` if it is not already scheduled, acting as an immediate catch-up after boot or process death

### Graceful Degradation

- [ ] **DEGRADE-01**: If `setExactAndAllowWhileIdle()` is unavailable or throws (restricted by ROM policy), `WatchdogService` falls back to `setInexactRepeating()` and logs a debug warning — no crash
- [ ] **DEGRADE-02**: When Watchdog mode is disabled by the user, `WatchdogService` is stopped cleanly, the pending AlarmManager alarm is cancelled, and the Watchdog notification is dismissed

---

## v2 Requirements (deferred)

### Enhanced Watchdog Intelligence

- **WATCH-V2-01**: Watchdog checks actual weather data freshness (compare `weatherUpdateLastTimestamp` vs. expected interval) rather than just WorkManager job state
- **WATCH-V2-02**: Watchdog notification shows current temperature and conditions (e.g. "28°C · Partly cloudy · Updated 5 min ago")
- **WATCH-V2-03**: Watchdog heartbeat interval configurable (5 / 15 / 30 min) instead of fixed

### ROM-Specific Hardening

- **ROM-V2-01**: Detect HyperOS version and auto-suggest "Lock app in Recents" guidance specific to that HyperOS generation
- **ROM-V2-02**: Support Samsung `DeepSleep` whitelist deep-link for Galaxy devices alongside MIUI autostart

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| Floating overlay / always-on-top widget | Too intrusive for a weather app; notification is sufficient keepalive |
| Root-level process protection | Requires root; out of scope for a Play Store app |
| System app installation path | Requires ROM signing; out of scope |
| Full replacement of WorkManager | WorkManager remains primary scheduler; Watchdog is a supplement/safety net |
| Battery usage dashboard / diagnostics screen | Nice-to-have but orthogonal to the keepalive goal; defer to v2 |
| Coroutines migration of WeatherUpdateJob | Large refactor; independent of Watchdog reliability goal |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| WATCH-01 | Phase 6 | Pending |
| WATCH-02 | Phase 6 | Pending |
| WATCH-03 | Phase 6 | Pending |
| WATCH-04 | Phase 6 | Pending |
| NOTIF-01 | Phase 6 | Pending |
| NOTIF-02 | Phase 6 | Pending |
| NOTIF-03 | Phase 6 | Pending |
| SETT-01 | Phase 7 | Pending |
| SETT-02 | Phase 7 | Pending |
| SETT-03 | Phase 7 | Pending |
| SETT-04 | Phase 7 | Pending |
| BOOT-01 | Phase 8 | Pending |
| BOOT-02 | Phase 8 | Pending |
| DEGRADE-01 | Phase 6 | Pending |
| DEGRADE-02 | Phase 7 | Pending |

**Coverage:**
- v1.1 requirements: 15 total
- Mapped to phases: 15 ✓
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-02*
*Last updated: 2026-04-02 after initial definition*
