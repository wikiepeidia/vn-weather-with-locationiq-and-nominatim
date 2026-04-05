# Roadmap: VN Weather

**Milestones:**

- ✅ **v1.0 — VN Address Quality** (Phases 1-5) — Shipped 2026-03-23
- ✅ **v1.1 — Background Watchdog** (Phases 6-8) — Shipped 2026-04-02
- 🔄 **v1.2 — ROM Hardening** (Phases 9-14, Phase 12 dropped) — In Progress

---

## Milestones

<details>
<summary>✅ v1.0 VN Address Quality (Phases 1-5) — Shipped 2026-03-23</summary>

- [x] Phase 1: Data Model Foundation — Map missing Nominatim structured fields (2 plans)
- [x] Phase 2: Token Extraction & Cross-Validation — Fix token ordering, eliminate POI-prefix garbage
- [x] Phase 3: Performance & Reliability — Lazy Nominatim, fresh API client, error logging, Asia endpoint
- [x] Phase 4: Giggles Feedback & Settings — Rescue log + playful settings description
- [x] Phase 5: Kotlin Unit Tests — Full JUnit coverage for VN address logic

See: `.planning/milestones/v1.0-ROADMAP.md` (if archived)

</details>

<details>
<summary>✅ v1.1 Background Watchdog (Phases 6-8) — Shipped 2026-04-02</summary>

- [x] Phase 6: WatchdogService Core — Foreground service, AlarmManager heartbeat, job monitor, graceful alarm fallback (3 plans)
- [x] Phase 7: Settings & Teardown — Watchdog toggle, battery-opt prompt, MIUI/HyperOS autostart deep-link, clean disable (3 plans)
- [x] Phase 8: Boot & Manifest Wiring — BootReceiver integration, alarm receiver, manifest registrations (1 plan)

**Stats:** 3 phases, 7 plans, 7 commits, 477 lines added across 8 files
**Audit:** 15/15 requirements, 14/14 integrations, 4/4 flows — `tech_debt` status (7 items accepted)

See: `.planning/milestones/v1.1-ROADMAP.md`, `.planning/milestones/v1.1-REQUIREMENTS.md`

</details>

## Active: v1.2 ROM Hardening

### Phases

- [x] **Phase 9: Tech Debt Cleanup** — Resolve v1.1 debt: extract shared constants, document verification gaps ✓
- [x] **Phase 10: Heartbeat Hardening** — CPU-sleep-proof heartbeat with WakeLock, configurable interval, diagnostic logging ✓
- [x] **Phase 11: Restart Resilience** — Redundant WorkManager restart vector, persisted restart tracking, immediate heartbeat on restart ✓
- ~~**Phase 12: Process Importance Elevation**~~ — **DROPPED**: Background Activity Launches are blocked on Android 14+; MIUI Security Center flags transparent overlays as adware/malware; the hybrid WorkManager/AlarmManager model (Phase 11) makes 24/7 OOM-score elevation unnecessary
- [x] **Phase 13: Notification Enhancement** — WatchdogRestartWorker upgraded to ephemeral foreground worker; WatchdogService made START_NOT_STICKY; WatchdogAnchorActivity deleted; no standalone watchdog notification ✓
- [x] **Phase 14: Health Dashboard** — Watchdog Health section in Background Updates settings; alarm-based status (Scheduled/Overdue/Starting up); next refresh + heartbeat + restart counters; 5s live poll; Reset Stats ✓

### Phase Details

#### Phase 9: Tech Debt Cleanup

**Goal**: v1.1 debt resolved — shared constants extracted and verification gaps documented
**Depends on**: Nothing (first phase of v1.2; builds on v1.1 codebase)
**Requirements**: DEBT-01, DEBT-02
**Success Criteria** (what must be TRUE):

  1. `WEATHER_UPDATE_WORK_NAME` is defined as a single shared constant and referenced from both WatchdogService and WeatherUpdateJob — no hardcoded `"WeatherUpdate-auto"` strings remain in the codebase
  2. VERIFICATION.md exists in `.planning/` documenting what was verified in v1.1 phases 6-8 and known gaps
**Plans**: 1 plan

Plans:

- [ ] 09-01-PLAN.md — Extract shared WORK_NAME_AUTO constant + create retroactive v1.1 VERIFICATION.md

#### Phase 10: Heartbeat Hardening

**Goal**: Heartbeat execution is CPU-sleep-proof, user-tunable, and self-documenting
**Depends on**: Phase 9 (shared constant needed for job-status checks)
**Requirements**: HEART-01, HEART-02, HEART-03
**Success Criteria** (what must be TRUE):

  1. WatchdogService acquires a partial WakeLock before heartbeat logic and releases it after — heartbeat completes even when device enters Doze
  2. User can adjust heartbeat interval (10–30 min) via a slider in Background Updates settings, and the next alarm fires at the chosen interval
  3. Each heartbeat writes a timestamped diagnostic entry (heartbeat count, service uptime, job status) to SharedPreferences that the health dashboard can later read
**Plans**: 2 plans

Plans:

- [ ] 10-01-PLAN.md — WakeLock + dynamic interval backend + diagnostic logging (HEART-01, HEART-02 backend, HEART-03)
- [ ] 10-02-PLAN.md — Heartbeat interval slider UI (HEART-02 frontend)

#### Phase 11: Restart Resilience

**Goal**: WatchdogService survives AlarmManager failures through redundant restart paths and tracks its own restarts
**Depends on**: Phase 10 (heartbeat logic must exist for immediate-heartbeat-on-restart)
**Requirements**: RESTART-01, RESTART-02, RESTART-03
**Success Criteria** (what must be TRUE):

  1. A WorkManager periodic job independently verifies WatchdogService is running and restarts it within 30 minutes if the AlarmManager heartbeat fails to fire
  2. Cumulative restart count is persisted to SharedPreferences, survives process death, and is accessible for the health dashboard
  3. On each restart (boot, alarm, or WorkManager), WatchdogService immediately performs a heartbeat before scheduling the next alarm
**Plans**: 1 plan

Plans:

- [ ] 11-01-PLAN.md — WatchdogRestartWorker + restart counter + source tracking (RESTART-01, RESTART-02, RESTART-03)

#### ~~Phase 12: Process Importance Elevation~~ — DROPPED

> **Why dropped (2026-04-05):** Android 14+ Background Activity Launch restrictions block transparent-Activity spawning from background services. MIUI Security Center actively flags hidden overlays as adware. The hybrid WorkManager + AlarmManager restart architecture from Phase 11 makes constant OOM-score elevation redundant — the process legitimately sleeps between scheduled wake-ups, which is what HyperOS expects. PROC-01, PROC-02, PROC-03 moved to Out of Scope.

#### Phase 13: Notification Enhancement ✓ COMPLETE

**Goal**: Wake-Update-Die architecture — WatchdogService is ephemeral (START_NOT_STICKY + stopSelf); WatchdogRestartWorker directly checks WeatherUpdateJob health via WorkQuery and promotes to foreground via setForegroundSafely(ID_WATCHDOG_KEEPALIVE)
**Depends on**: Phase 10 (heartbeat timing data needed for notification content)
**Requirements**: NOTIF-04, NOTIF-05
**Pivoted from original goal (2026-04-05)**: Original goal was to add timing info to keepalive notification. Pivoted to eliminating the always-on keepalive entirely — WatchdogService dies after each heartbeat; ID_WIDGET (weather notification) is the sole persistent status indicator; CHANNEL_WATCHDOG is used only for brief foreground window during WatchdogRestartWorker execution.
**Success Criteria** (met):

  1. WatchdogService returns START_NOT_STICKY and calls stopSelf() — process exits legitimately after heartbeat
  2. WatchdogRestartWorker bypasses WatchdogService.isRunning check (always false in ephemeral model); directly queries RUNNING/ENQUEUED WeatherUpdateJob via WorkQuery; re-enqueues if empty
  3. WatchdogRestartWorker calls setForegroundSafely() with ID_WATCHDOG_KEEPALIVE (IMPORTANCE_MIN) — brief, invisible; WorkManager cancels it cleanly on finish without touching ID_WIDGET
  4. WatchdogAnchorActivity.kt deleted; manifest + styles.xml cleaned; all dead imports removed
  5. BUILD SUCCESSFUL with zero errors (confirmed 2026-04-05)
**Plans**: Phase 13 executed autonomously (no separate PLAN.md)

#### Phase 14: Health Dashboard ✓ COMPLETE

**Goal**: Users can inspect Watchdog health at a glance from Background Updates settings
**Depends on**: Phase 10 (HEART-03 diagnostic logging), Phase 11 (RESTART-02 restart count)
**Requirements**: HEALTH-01, HEALTH-02, HEALTH-03
**Pivoted status field (2026-04-05)**: Original spec used `WatchdogService.isRunning` for status — unreliable in ephemeral model (service dies after each heartbeat). Replaced with alarm-based status derived from `next_alarm_timestamp` in SharedPreferences: "Scheduled" (alarm in future + 5 min grace), "Alarm overdue" (alarm missed), "Starting up" (first run not yet happened).
**Success Criteria** (met):

  1. Background Updates settings shows a "Watchdog Health" section (only visible when Watchdog enabled) with: status (Scheduled/Overdue/Starting up), last heartbeat (relative time), next scheduled refresh (relative time), heartbeat count, restart count with last restart time
  2. Health data polls every 5 seconds via LaunchedEffect while screen is open
  3. Reset Stats clears heartbeat_count, restart_count, last_restart_timestamp, last_heartbeat_timestamp, last_diagnostic, last_restart_source
  4. WatchdogService.scheduleNextAlarm() persists next_alarm_timestamp (wall-clock) to watchdog_diagnostics
  5. BUILD SUCCESSFUL with zero errors (confirmed 2026-04-05)
**Plans**: Phase 14 executed autonomously (no separate PLAN.md)

### Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 9. Tech Debt Cleanup | 1/1 | ✓ Complete | 2025-07-21 |
| 10. Heartbeat Hardening | 2/2 | ✓ Complete | 2025-07-21 |
| 11. Restart Resilience | 1/1 | ✓ Complete | 2025-07-21 |
| ~~12. Process Importance Elevation~~ | — | DROPPED | — |
| 13. Notification Enhancement | 0/0 | ✓ Complete | 2026-04-05 |
| 14. Health Dashboard | 0/0 | ✓ Complete | 2026-04-05 |

---
*Last updated: 2026-04-05 — All v1.2 phases complete (9-11 from previous work, 12 dropped, 13-14 executed 2026-04-05)*
