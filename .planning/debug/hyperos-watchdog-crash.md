---
status: awaiting_human_verify
trigger: "App shows 'weather vn keep crashing' dialog on HyperOS out of nowhere (background / midnight-kill type). WatchdogService is enabled. No logcat available. Developer suspects HyperOS is nuking the WatchdogService and the crash is a side effect."
created: 2026-04-05T00:00:00Z
updated: 2026-04-05T00:10:00Z
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

hypothesis: CONFIRMED — Three bugs found. Primary crash: uncaught ForegroundServiceStartNotAllowedException/IllegalStateException from WatchdogAlarmReceiver with no try/catch around WatchdogService.start(). Secondary design flaw: onDestroy() calls cancelAlarm() which destroys the self-healing loop on every normal stopSelf(). Tertiary: performHeartbeat() blocks the main thread with .get() potentially causing ANR.
test: Applying code fixes to WatchdogAlarmReceiver.kt and WatchdogService.kt.
expecting: Fix eliminates uncaught exceptions from alarm receiver; self-healing loop works correctly; blocking I/O moved off main thread.
next_action: Apply three targeted fixes, then await human verification.

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected: App runs silently in background on HyperOS. WatchdogService fires heartbeats via AlarmManager. WorkManager backup runs every 30 min. No crash dialog ever shown.
actual: User sees Android "weather vn keeps stopping" crash dialog out of nowhere, especially in background (midnight / screen-off period). Crash appears random while WatchdogService is already enabled.
errors: "weather vn keep crashing" system dialog. No logcat captured.
reproduction: Unclear — happens on HyperOS in background, not reliably reproducible. Seems tied to midnight battery purge or aggressive app kill by HyperOS.
started: After WatchdogService was introduced (v1.1/v1.2). Suspected: HyperOS killing causes crash rather than a clean process death.

## Eliminated
<!-- APPEND only - prevents re-investigating -->

## Evidence
<!-- APPEND only - facts discovered -->

- timestamp: 2026-04-05T00:00:00Z
  checked: codebase_context supplied by developer
  found: Four key files identified. Suspicion ranking: WatchdogAlarmReceiver (no exception guard) > WatchdogService.onStartCommand early-return path > WatchdogRestartWorker still calling start() > stop() throwing in unexpected state.
  implication: Need to confirm each file's actual content.

- timestamp: 2026-04-05T00:05:00Z
  checked: WatchdogAlarmReceiver.kt
  found: onReceive() calls WatchdogService.start(context, "alarm") with zero exception handling. No try/catch, no goAsync().
  implication: Any exception from startForegroundService() (ForegroundServiceStartNotAllowedException, IllegalStateException, SecurityException) propagates uncaught → system crash dialog.

- timestamp: 2026-04-05T00:05:00Z
  checked: WatchdogService.onStartCommand()
  found: startForeground() IS called before any risky work — not a missing-startForeground bug. But performHeartbeat() calls workManager.getWorkInfos(workQuery).get() (blocking) on the main thread AFTER startForeground(). WakeLock timeout set to 30s, potentially causing ANR if WorkManager is slow on cold start.
  implication: ANR risk on HyperOS after cold process restart due to cold WorkManager room-DB init.

- timestamp: 2026-04-05T00:05:00Z
  checked: WatchdogService.onDestroy() + scheduleNextAlarm()
  found: CRITICAL DESIGN BUG — onDestroy() calls cancelAlarm() which cancels the PendingIntent set by scheduleNextAlarm(). Since the service calls stopSelf() (ephemeral design), onDestroy() always fires immediately after scheduleNextAlarm(), cancelling the alarm before it ever fires independently. Self-healing AlarmManager loop is broken every single normal run. Only way service re-runs is via BootReceiver or user re-toggle.
  implication: The watchdog silently stops looping after the first boot/toggle. When HyperOS midnight-kills the now-dormant service and fires the alarm, the receiver calls startForegroundService() from a cold process in restricted background state → exception.

- timestamp: 2026-04-05T00:05:00Z
  checked: WatchdogRestartWorker.kt
  found: CONFIRMED — does NOT call WatchdogService.start(). Only checks WeatherUpdateJob health. So the 30-min worker backup does not restart the WatchdogService heartbeat loop.
  implication: Once onDestroy() kills the alarm, WatchdogService is effectively dead until next reboot.

- timestamp: 2026-04-05T00:05:00Z
  checked: WatchdogService.start() companion
  found: startForegroundService() called with no try/catch. IllegalStateException subtypes (including ForegroundServiceStartNotAllowedException, API 31+) and SecurityException are not handled.
  implication: Even if WatchdogAlarmReceiver had a catch, the exception originally propagates from inside start().

- timestamp: 2026-04-05T00:05:00Z
  checked: AndroidManifest.xml
  found: WatchdogService declared with android:foregroundServiceType="dataSync" and android:exported="false". WatchdogAlarmReceiver declared with android:exported="false" (correct for explicit PendingIntent). No issues with manifest declarations.
  implication: Manifest is fine. Bug is entirely in the Kotlin code.

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: Three compounding bugs: (1) WatchdogAlarmReceiver.onReceive() has no try/catch around WatchdogService.start() — when HyperOS fires the alarm into a cold/restricted process, startForegroundService() throws ForegroundServiceStartNotAllowedException (or IllegalStateException/SecurityException) which propagates uncaught → "weather vn keeps stopping" crash dialog. (2) WatchdogService.onDestroy() calls cancelAlarm() which immediately destroys the self-healing AlarmManager loop after every ephemeral stopSelf() run — so the watchdog stops looping silently. (3) performHeartbeat() blocks the service main thread with workManager.getWorkInfos().get(), risking ANR on cold WorkManager init.
fix: (A) WatchdogAlarmReceiver — wrap WatchdogService.start() in try/catch(Exception). (B) WatchdogService.start() companion — wrap startForegroundService() in try/catch(Exception). (C) WatchdogService.onDestroy() — remove cancelAlarm() call (stop() companion already handles it). (D) WatchdogService.onStartCommand() — move WakeLock+heartbeat+scheduleNextAlarm+stopSelf into a background Thread to unblock main thread.
verification:
files_changed:

- app/src/main/java/org/breezyweather/background/watchdog/WatchdogAlarmReceiver.kt
- app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt
