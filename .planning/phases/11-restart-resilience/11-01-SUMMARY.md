---
phase: 11-restart-resilience
plan: 01
status: complete
---

# Phase 11 Plan 01 Summary: Restart Resilience

## What was done
- **RESTART-01:** Created `WatchdogRestartWorker` — PeriodicWorkRequest every 30min as redundant restart vector. Checks `isRunning` flag, restarts WatchdogService if dead. Enqueued in `start()`, cancelled in `stop()`.
- **RESTART-02:** Added `incrementRestartCount()` to WatchdogService — writes `restart_count`, `last_restart_source`, `last_restart_timestamp` to `watchdog_diagnostics` SharedPreferences.
- **RESTART-03:** `incrementRestartCount()` called at top of `onStartCommand()` before heartbeat. Source defaults to "sticky" for null-intent START_STICKY restarts.
- Simplified WatchdogAlarmReceiver to use `WatchdogService.start(context, "alarm")`
- BootReceiver passes `"boot"` source
- Added `@Volatile var isRunning` companion flag (set in onCreate/onDestroy)
- Added `EXTRA_RESTART_SOURCE` Intent extra for source tracking

## Commits
- `b992d0daf` — feat(11): restart resilience

## Requirements
- **RESTART-01:** ✅ WorkManager 30min periodic backup
- **RESTART-02:** ✅ Persistent restart counter with source
- **RESTART-03:** ✅ Heartbeat fires before next alarm on every restart

## Files modified
- `WatchdogRestartWorker.kt` — NEW: redundant restart worker
- `WatchdogService.kt` — isRunning, EXTRA_RESTART_SOURCE, incrementRestartCount, updated start()/stop()
- `WatchdogAlarmReceiver.kt` — simplified to use WatchdogService.start()
- `BootReceiver.kt` — added "boot" source parameter
