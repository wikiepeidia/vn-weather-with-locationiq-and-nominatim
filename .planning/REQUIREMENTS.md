# Requirements: v1.2 ROM Hardening

**Milestone:** v1.2 — ROM Hardening
**Goal:** Harden WatchdogService survival on HyperOS/MIUI through multiple restart vectors, WakeLock, and health diagnostics. (Process importance elevation via invisible Activity dropped — see Out of Scope.)
**Created:** 2026-04-02

---

## v1.2 Requirements

### Heartbeat Hardening

- [x] **HEART-01**: WatchdogService acquires a partial WakeLock before executing heartbeat logic and releases it after completion, preventing CPU sleep during weather-job health checks
- [x] **HEART-02**: Heartbeat interval is configurable between 10-30 minutes via a settings slider (default: 15 minutes)
- [x] **HEART-03**: Each heartbeat logs a timestamped diagnostic entry (heartbeat count, service uptime, job status) accessible from the health dashboard

### Restart Resilience

- [x] **RESTART-01**: A WorkManager periodic job runs independently of AlarmManager as a redundant restart vector — if AlarmManager fails to fire, WorkManager re-starts WatchdogService within 30 minutes
- [x] **RESTART-02**: WatchdogService tracks cumulative restart count (persisted across process deaths) and surfaces it in the health dashboard
- [x] **RESTART-03**: On each restart, WatchdogService immediately performs a heartbeat (checks + re-enqueues WeatherUpdateJob if missing) before scheduling the next alarm

### Health Dashboard

- [x] **HEALTH-01**: Background Updates settings shows a "Watchdog Health" section (visible only when Watchdog is enabled) displaying: service status (Running/Stopped), last heartbeat time, restart count, and next scheduled refresh
- [x] **HEALTH-02**: Health data refreshes in real-time when the settings screen is open (via SharedPreferences listener or periodic poll)
- [x] **HEALTH-03**: A "Reset Stats" action clears the restart count and heartbeat log

### Notification Enhancement

- [x] **NOTIF-04**: WatchdogRestartWorker calls `setForegroundSafely()` anchored to `ID_WATCHDOG_KEEPALIVE` (IMPORTANCE_MIN, `CHANNEL_WATCHDOG`) for the brief execution window only; no permanent standalone watchdog notification — the weather notification (`ID_WIDGET`) is the sole persistent status indicator
- [x] **NOTIF-05**: Notification priority is elevated from IMPORTANCE_MIN to IMPORTANCE_LOW on HyperOS/MIUI devices (makes it slightly more visible, reduces kill chance)

### Tech Debt Cleanup

- [x] **DEBT-01**: Extract `"WeatherUpdate-auto"` hardcoded string from WatchdogService into a shared constant accessible from both WatchdogService and WeatherUpdateJob
- [x] **DEBT-02**: Add VERIFICATION.md for v1.1 phases 6-8 retroactively documenting what was verified and known gaps

---

## Future Requirements (Deferred)

- Dual-process guardian architecture (separate process monitors primary) — high complexity, defer to v1.3 if v1.2 mitigations insufficient
- EncryptedSharedPreferences for API keys — orthogonal to ROM hardening
- ConfigStore → DataStore migration — orthogonal

## Out of Scope

- **PROC-01 / PROC-02 / PROC-03 — Invisible Activity OOM elevation** — Android 14+ Background Activity Launch (BAL) restrictions block background-service-to-Activity `startActivity` calls from background; MIUI Security Center flags transparent overlays as adware; Phase 11's WorkManager hybrid restart makes constant OOM elevation redundant — the process sleeps between scheduled wake-ups
- RxJava → Coroutines full migration — large refactor, orthogonal
- Full MIUI/HyperOS ROM compatibility layer — too broad, focus on specific kill mitigation
- User-facing "kill protection" tutorial/wizard — defer to UX milestone

---

## Traceability

*(Filled by roadmapper)*

| Requirement | Phase |
|-------------|-------|
| HEART-01 | Phase 10 |
| HEART-02 | Phase 10 |
| HEART-03 | Phase 10 |
| RESTART-01 | Phase 11 |
| RESTART-02 | Phase 11 |
| RESTART-03 | Phase 11 |
| ~~PROC-01~~ | ~~Phase 12~~ DROPPED |
| ~~PROC-02~~ | ~~Phase 12~~ DROPPED |
| ~~PROC-03~~ | ~~Phase 12~~ DROPPED |
| HEALTH-01 | Phase 14 |
| HEALTH-02 | Phase 14 |
| HEALTH-03 | Phase 14 |
| NOTIF-04 | Phase 13 |
| NOTIF-05 | Phase 13 |
| DEBT-01 | Phase 9 |
| DEBT-02 | Phase 9 |

---
*Requirements created: 2026-04-02*
*Last updated: 2026-04-05 — PROC-01/02/03 moved to Out of Scope (Phase 12 dropped); phases 9-11 complete*
