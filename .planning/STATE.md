---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: — Background Watchdog
status: active
last_updated: "2026-04-02"
progress:
  total_phases: 8
  completed_phases: 1
  total_plans: 3
  completed_plans: 3
---

# STATE.md — VN Weather

## Project Reference

**Core Value:** Vietnamese users see a clean ward/commune name — never a POI or government-office prefix — even when LocationIQ or Nominatim individually return garbage. Weather updates reliably happen on background-aggressive ROMs like HyperOS.

**Milestone:** v1.1 — Background Watchdog  
**Milestone Goal:** Deliver a persistent foreground Watchdog service with AlarmManager heartbeat so weather updates run reliably on HyperOS/MIUI, with a settings toggle, HyperOS autostart guidance, and boot-resume support.

---

## Current Position

Phase: 08 (boot-manifest-wiring) — PENDING

## v1.0 Archive (VN Address Quality)

### Phase Checklist

- [x] Phase 1: Data Model Foundation — commit `02c92fed3`
- [x] Phase 2: Token Extraction & Cross-Validation — commit `b6400cfe8`
- [x] Phase 3: Performance & Reliability — commit `481fe82eb`
- [x] Phase 4: Giggles Feedback & Settings — commit `266f80ce8`
- [x] Phase 5: Kotlin Unit Tests — commit `5297198d1`

## v1.1 Phase Checklist

- [x] Phase 6: WatchdogService Core — commit `c7185e2e2`
- [x] Phase 7: Settings & Teardown — commit `9a7d33b53`
- [ ] Phase 8: Boot & Manifest Wiring

---

### Key Decisions (from PROJECT.md)

| Decision | Rationale |
|----------|-----------|
| Watchdog opt-in (default off) | Foreground service + AlarmManager drains battery; must be user-initiated |
| AlarmManager heartbeat for Watchdog self-heal | WorkManager can't reliably reschedule after HyperOS kills it |
| Delegate weather-fetch to existing WeatherUpdateJob | Watchdog monitors and re-enqueues; doesn't duplicate logic |

### Key Existing Files

- `app/src/main/java/org/breezyweather/background/weather/WeatherUpdateJob.kt` — CoroutineWorker, periodic weather refresh
- `app/src/main/java/org/breezyweather/background/receiver/BootReceiver.kt` — restores workers after reboot
- `app/src/main/java/org/breezyweather/remoteviews/Notifications.kt` — notification channels
- `app/src/main/java/org/breezyweather/ui/settings/compose/BackgroundUpdatesSettingsScreen.kt` — settings UI
- `app/src/main/java/org/breezyweather/domain/settings/SettingsManager.kt` — settings properties

### Constraints to Remember

- Watchdog feature must be fully opt-in; default off to preserve battery
- `setExactAndAllowWhileIdle()` may be restricted on some HyperOS versions — degrade gracefully
- All new code in Kotlin; no new Java
- Existing `WeatherUpdateJob` and `BootReceiver` API surfaces must stay stable

### Todos

*(none yet — will be populated during phase planning)*

### Blockers

*(none)*

---

## Session Continuity

**Last updated:** 2026-04-02  
**Last action:** Phase 6 complete — WatchdogService + WatchdogAlarmReceiver implemented, compiled, committed  
**Next action:** `/gsd-discuss-phase 7` or `/gsd-plan-phase 7` — Settings & Teardown

---
*STATE.md updated: 2026-04-02 — Phase 6 complete*
