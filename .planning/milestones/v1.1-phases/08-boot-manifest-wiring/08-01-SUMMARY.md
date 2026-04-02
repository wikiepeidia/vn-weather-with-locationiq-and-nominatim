---
plan: 08-01
status: complete
commit: 250bc35e3
---

# Plan 08-01 Summary: BootReceiver Watchdog Wiring

## What was done
Added WatchdogService start logic to `BootReceiver.onReceive()` for `ACTION_BOOT_COMPLETED`.

## Files modified
- `app/src/main/java/org/breezyweather/background/receiver/BootReceiver.kt`

## Implementation
- Import `WatchdogService` and `SettingsManager`
- After existing WorkManager query and notification-widget restoration:
  - Check `SettingsManager.getInstance(context).watchdogEnabled`
  - Call `WatchdogService.start(context)` if true
- `WatchdogService.onStartCommand()` already calls `performHeartbeat()` which re-enqueues `WeatherUpdateJob` if not scheduled (BOOT-02)

## Requirements satisfied
- BOOT-01: WatchdogService resumes on boot if watchdogEnabled == true
- BOOT-02: Immediate heartbeat on start re-enqueues WeatherUpdateJob as catch-up
