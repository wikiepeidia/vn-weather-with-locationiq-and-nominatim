---
name: watchdog
description: Use when changing background weather updates, the Watchdog setting, WatchdogService, WatchdogRestartWorker, WatchdogWeatherUpdateHealth, BootReceiver watchdog start, or battery and OEM kill behaviour (Samsung, Xiaomi) in this fork.
---

# Watchdog (background update recovery)

Files: `app/src/main/java/org/breezyweather/background/watchdog/` (`WatchdogService`, `WatchdogRestartWorker`, `WatchdogAlarmReceiver`, `WatchdogWeatherUpdateHealth`), the Watchdog section of `ui/settings/compose/BackgroundUpdatesSettingsScreen.kt`, `BootReceiver`, and `Migrations` (starts it after an upgrade).

## Design by device

| Device | Mechanism |
|---|---|
| Samsung and every non-Xiaomi phone | Only `WatchdogRestartWorker`, hourly. No alarm, no foreground service, no notification. |
| Xiaomi, Redmi, POCO (`isXiaomiDevice()`), battery-exempt or API below 31 | Exact alarm heartbeat (`setExactAndAllowWhileIdle`) plus a `shortService` foreground service, plus the 30-minute worker as backup. |
| Xiaomi family, not exempt on API 31+, or updates set to Never | Worker only (an inexact alarm cannot start the service). |

The alarm is armed in `start()` before the service starts. The worker re-arms it when `alarm_armed` is false or the next check is overdue.

## Refresh rules (`decideRecoveryPlan`)

Force a refresh only when the weather was already stale at the previous check, at most once per update interval, and only when online, not in Battery Saver, and not below 15% battery while the "ignore updates when battery low" setting is on. With updates set to Never the watchdog cancels itself. A last-update time in the future counts as stale.

## Samsung

No Samsung branch in scheduling. Samsung's Sleeping and Deep sleeping tiers cannot be detected or beaten by an app, so the settings row opens Never sleeping apps (`activity_type=2`, with fallbacks). Do not add `SCHEDULE_EXACT_ALARM`, expedited work or a resident foreground service.

## Changing it

- Pure functions `decideRecoveryPlan`, `usesAlarmChain` and `isCheckOverdue` have tests in `WatchdogWeatherUpdateHealthTest.kt`. Add a case for every change.
- Run `npx gitnexus impact <symbol> --direction upstream` before editing, then `./gradlew :app:testBasicDebugUnitTest :app:testFreenetDebugUnitTest`.
- Real checks happen on a phone: `adb shell dumpsys alarm`, `adb shell dumpsys jobscheduler`, `adb shell am get-standby-bucket <package>`. The owner has run the Samsung design for days without kills; Xiaomi cannot be tested here.
