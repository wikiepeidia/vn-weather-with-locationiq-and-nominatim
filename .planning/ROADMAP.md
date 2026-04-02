# Roadmap: VN Weather

**Milestones:**
- ✅ **v1.0 — VN Address Quality** (Phases 1-5) — Shipped 2026-03-23
- ✅ **v1.1 — Background Watchdog** (Phases 6-8) — Shipped 2026-04-02
- 🔄 **v1.2 — ROM Hardening** (Phases 9-14) — Active

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

- [ ] **Phase 9: Tech Debt Cleanup** — Resolve v1.1 debt: extract shared constants, document verification gaps
- [ ] **Phase 10: Heartbeat Hardening** — CPU-sleep-proof heartbeat with WakeLock, configurable interval, diagnostic logging
- [ ] **Phase 11: Restart Resilience** — Redundant WorkManager restart vector, persisted restart tracking, immediate heartbeat on restart
- [ ] **Phase 12: Process Importance Elevation** — Invisible Activity binding to raise OOM adj score on HyperOS/MIUI
- [ ] **Phase 13: Notification Enhancement** — Enhanced keepalive notification with timing info and elevated priority on HyperOS/MIUI
- [ ] **Phase 14: Health Dashboard** — User-facing health section in settings with live status, restart count, and reset action

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
**Plans**: TBD

#### Phase 11: Restart Resilience
**Goal**: WatchdogService survives AlarmManager failures through redundant restart paths and tracks its own restarts
**Depends on**: Phase 10 (heartbeat logic must exist for immediate-heartbeat-on-restart)
**Requirements**: RESTART-01, RESTART-02, RESTART-03
**Success Criteria** (what must be TRUE):
  1. A WorkManager periodic job independently verifies WatchdogService is running and restarts it within 30 minutes if the AlarmManager heartbeat fails to fire
  2. Cumulative restart count is persisted to SharedPreferences, survives process death, and is accessible for the health dashboard
  3. On each restart (boot, alarm, or WorkManager), WatchdogService immediately performs a heartbeat before scheduling the next alarm
**Plans**: TBD

#### Phase 12: Process Importance Elevation
**Goal**: WatchdogService process is harder to kill on HyperOS/MIUI by elevating its OOM adjustment score
**Depends on**: Phase 10 (heartbeat diagnostic logging needed for PROC-03)
**Requirements**: PROC-01, PROC-02, PROC-03
**Success Criteria** (what must be TRUE):
  1. On Xiaomi/Redmi/POCO devices with Watchdog enabled, WatchdogService binds to a transparent zero-pixel Activity that raises the process OOM adj from "service" to "visible"
  2. The Activity binding only activates when manufacturer matches Xiaomi/Redmi/POCO — does nothing on Samsung, Pixel, or other OEMs
  3. Process importance level is logged in each heartbeat's diagnostic entry when the Activity binding is active
**Plans**: TBD

#### Phase 13: Notification Enhancement
**Goal**: Watchdog notification gives users confidence the system is working with timing information
**Depends on**: Phase 10 (heartbeat timing data needed for notification content)
**Requirements**: NOTIF-04, NOTIF-05
**Success Criteria** (what must be TRUE):
  1. Keepalive notification displays "Last updated: X min ago" and "Next refresh: ~Y min" — both values update on each heartbeat
  2. On HyperOS/MIUI devices, notification channel importance is IMPORTANCE_LOW instead of IMPORTANCE_MIN (slightly more visible, reduces kill probability)
**Plans**: TBD

#### Phase 14: Health Dashboard
**Goal**: Users can inspect Watchdog health at a glance from Background Updates settings
**Depends on**: Phase 10 (HEART-03 diagnostic logging), Phase 11 (RESTART-02 restart count)
**Requirements**: HEALTH-01, HEALTH-02, HEALTH-03
**Success Criteria** (what must be TRUE):
  1. Background Updates settings shows a "Watchdog Health" section (only visible when Watchdog enabled) displaying: service status (Running/Stopped), last heartbeat time, restart count, and next scheduled refresh
  2. Health data refreshes in real-time while the settings screen is open (via SharedPreferences listener or periodic poll)
  3. A "Reset Stats" action clears restart count and heartbeat log, returning all counters to zero
**Plans**: TBD
**UI hint**: yes

### Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 9. Tech Debt Cleanup | 0/? | Not started | - |
| 10. Heartbeat Hardening | 0/? | Not started | - |
| 11. Restart Resilience | 0/? | Not started | - |
| 12. Process Importance Elevation | 0/? | Not started | - |
| 13. Notification Enhancement | 0/? | Not started | - |
| 14. Health Dashboard | 0/? | Not started | - |

---
*Last updated: 2026-04-02 — v1.2 ROM Hardening roadmap created*
