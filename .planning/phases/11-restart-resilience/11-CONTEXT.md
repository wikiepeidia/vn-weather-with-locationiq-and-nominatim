---
phase: 11-restart-resilience
type: feature
generated: auto (autonomous mode)
---

# Phase 11 Context: Restart Resilience

## Goal
WatchdogService survives AlarmManager failures through redundant restart paths and tracks its own restarts.

## Requirements
- **RESTART-01:** WorkManager periodic job as backup restart vector — restarts WatchdogService within 30min if AlarmManager fails
- **RESTART-02:** Cumulative restart count persisted to SharedPreferences, survives process death
- **RESTART-03:** On each restart, immediate heartbeat before scheduling next alarm

## Key Decisions
- WorkManager job: New `WatchdogRestartWorker` as PeriodicWorkRequest (30min). Checks if WatchdogService is running, restarts if needed.
- Restart tracking: Use existing `watchdog_diagnostics` SharedPreferences — add `restart_count` and `last_restart_source` keys
- RESTART-03 is already satisfied: `onStartCommand()` calls `performHeartbeat()` before `scheduleNextAlarm()`. Just need to increment restart counter on each `onStartCommand()`.
- WorkManager job enabled/disabled alongside watchdog toggle (start when watchdogEnabled, cancel when disabled)
- Worker enqueue in `WatchdogService.start()` companion method — alongside service start

## Dependencies
- Phase 10 (HEART): heartbeat logic with WakeLock and diagnostics
