---
phase: 13-notification-enhancement
type: context
generated: 2026-04-05
---

# Phase 13 Context: "Wake, Update, Die" — Ephemeral Execution Model

## Goal

Pivot from a 24/7 persistent foreground service to an ephemeral "wake, do work, die" architecture
that avoids HyperOS/MIUI midnight battery purges. Anchor brief foreground windows to the existing
weather notification (ID_WIDGET) where possible, and ensure the standalone "Watchdog Active"
keepalive notification is never permanently visible.

## Background — Why the Original Phase 13 Plan Was Wrong

The original NOTIF-04/NOTIF-05 approach kept WatchdogService alive 24/7 and added timing info
to its persistent notification. This is COUNTERPRODUCTIVE on HyperOS because:

1. HyperOS 0:00 battery purge targets processes with continuous CPU cycles. A foreground service
   that never dies is the textbook kill target.
2. Android 14+ Background Activity Launch (BAL) restrictions blocked Phase 12's invisible-Activity
   OOM trick — so process importance elevation via PROC-01/02/03 was already defeated.
3. WorkManager (Phase 11 `WatchdogRestartWorker`) already provides the redundant restart vector.
   A 24/7 service ON TOP of WorkManager is redundant and dangerous.

## New Architecture: "Wake, Update, Die"

```
AlarmManager fires (every 15min, user-tunable from Phase 10)
    → WatchdogAlarmReceiver.onReceive()
    → WatchdogService.start(source="alarm")
    → onStartCommand(): foreground promote → heartbeat → scheduleNextAlarm() → stopSelf()
    → Process exits cleanly

WorkManager fires (every 30min, backup from Phase 11)
    → WatchdogRestartWorker.doWork()
    → setForegroundSafely()   ← NEW in Phase 13
    → Check WeatherUpdateJob health directly
    → Re-enqueue if missing
    → return Result.success() → process exits
```

## Key Design Decisions

### WatchdogService becomes ephemeral

- `START_NOT_STICKY` instead of `START_STICKY` — OS will NOT resurrect it if killed between alarms
- `stopSelf()` called after `scheduleNextAlarm()` — service dies immediately after arming next alarm
- AlarmManager is the resurrection mechanism, not `START_STICKY`
- Foreground window lasts only for the duration of the heartbeat (~1–2 seconds)

### WatchdogRestartWorker gets setForegroundSafely()

- Currently just calls `WatchdogService.start()` — no foreground promotion
- After Phase 13: calls `setForegroundSafely()` at the top of `doWork()`
- Uses `ID_WATCHDOG_KEEPALIVE` for ForegroundInfo (not `ID_WIDGET`) to avoid WorkManager
  cancelling the weather notification when the worker finishes
- `CHANNEL_WATCHDOG` is IMPORTANCE_MIN (invisible to user) — brief flash, auto-removed on finish
- Also directly checks WeatherUpdateJob health (same logic as WatchdogService heartbeat) instead
  of depending on WatchdogService.isRunning (which is always false in the new ephemeral model)

### Phase 12 cleanup (WatchdogAnchorActivity)

- `WatchdogAnchorActivity.kt` — DELETED
- `AndroidManifest.xml` — WatchdogAnchorActivity block removed
- `styles.xml` — BreezyWeatherTheme.Transparent removed
- `WatchdogService.kt` — launchAnchorIfNeeded(), LocalBinder, PROC-03 diagnostic block removed

### Notification strategy

- PRIMARY persistent notification: `ID_WIDGET` (weather notification) — owned by WidgetNotificationIMP
- BRIEF foreground window: `ID_WATCHDOG_KEEPALIVE` via CHANNEL_WATCHDOG (IMPORTANCE_MIN on non-Xiaomi,
  IMPORTANCE_LOW on Xiaomi — already set in Phase 10 / Notifications.kt)
- WatchdogService's piggybacking logic (use ID_WIDGET when WidgetNotificationIMP is enabled) is
  kept for the brief foreground window while the service runs

## Files Changed

- `WatchdogAnchorActivity.kt` — DELETED
- `WatchdogService.kt` — Remove Phase 12 refs; `START_NOT_STICKY`; `stopSelf()` after alarm arm
- `WatchdogRestartWorker.kt` — Add `setForegroundSafely()`, `getForegroundInfo()`, direct job check
- `AndroidManifest.xml` — Remove WatchdogAnchorActivity registration
- `styles.xml` — Remove BreezyWeatherTheme.Transparent

## Success Criteria

1. `WatchdogService.onStartCommand()` calls `stopSelf()` after arming the alarm — the service does
   not remain alive permanently between heartbeat intervals
2. `WatchdogRestartWorker.doWork()` calls `setForegroundSafely()` and directly checks
   WeatherUpdateJob health without depending on WatchdogService.isRunning
3. `WatchdogAnchorActivity.kt` does not exist; AndroidManifest has no `WatchdogAnchorActivity`
   registration; `styles.xml` has no `BreezyWeatherTheme.Transparent`
4. Project compiles with no references to `WatchdogAnchorActivity`, `LocalBinder`, or
   `launchAnchorIfNeeded` remaining in any source file
