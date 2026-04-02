# STATE.md — VN Weather

## Project Reference

**Core Value:** Vietnamese users see a clean ward/commune name — never a POI or government-office prefix — even when LocationIQ or Nominatim individually return garbage. Weather updates reliably happen on background-aggressive ROMs like HyperOS.

**Milestone:** v1.1 — Background Watchdog  
**Milestone Goal:** Deliver a persistent foreground Watchdog service with AlarmManager heartbeat so weather updates run reliably on HyperOS/MIUI, with a settings toggle, HyperOS autostart guidance, and boot-resume support.

---

## Current Position

**Active Phase:** Phase 6 — WatchdogService Core  
**Active Plan:** none  
**Status:** Roadmap created — ready to plan Phase 6

**Progress:**
```
v1.1: [░░░░░░░░] 0 of 3 phases complete
```

---

## v1.0 Archive (VN Address Quality)

### Phase Checklist

- [x] Phase 1: Data Model Foundation — commit `02c92fed3`
- [x] Phase 2: Token Extraction & Cross-Validation — commit `b6400cfe8`
- [x] Phase 3: Performance & Reliability — commit `481fe82eb`
- [x] Phase 4: Giggles Feedback & Settings — commit `266f80ce8`
- [x] Phase 5: Kotlin Unit Tests — commit `5297198d1`

## v1.1 Phase Checklist

- [ ] Phase 6: WatchdogService Core
- [ ] Phase 7: Settings & Teardown
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
**Last action:** v1.1 roadmap created — Phases 6, 7, 8 defined  
**Next action:** `/gsd-plan-phase 6` to plan WatchdogService Core

---
*STATE.md updated: 2026-04-02 — v1.1 roadmap created*
