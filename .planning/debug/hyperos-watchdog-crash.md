---
status: awaiting-device-validation
trigger: "App shows 'weather vn keep crashing' dialog on HyperOS out of nowhere (background / midnight-kill type). WatchdogService is enabled. No logcat available. Developer suspects HyperOS is nuking the WatchdogService and the crash is a side effect."
created: 2026-04-05T00:00:00Z
updated: 2026-06-01T00:00:00Z
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

hypothesis: CONFIRMED — The old dataSync crash was real, but the current disappearing-notification symptom is primarily a design/behavior mismatch after migrating WatchdogService to shortService. The service now intentionally self-terminates after each heartbeat, so a persistent watchdog notification is no longer a viable expectation on Android 14+/SDK 34-36.
test: Explicitly remove the standalone watchdog foreground notification on normal teardown, align comments/settings copy with the short-service design, and validate the basicDebug build.
expecting: Standalone watchdog notification disappears immediately on normal short-service exit, the UI no longer promises an uninterrupted keepalive, and the old dataSync foreground-service crash is not reintroduced.
next_action: Treat current `main` and nearby backup branches as unsafe lifecycle references on SDK 36, because they still self-stop and still use `dataSync`; only use them as historical context, not as a direct revert target.

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected: App runs silently in background on HyperOS. WatchdogService fires heartbeats via AlarmManager. WorkManager backup runs every 30 min. No crash dialog ever shown.
actual: User sees Android "weather vn keeps stopping" crash dialog out of nowhere, especially in background (midnight / screen-off period). Crash appears random while WatchdogService is already enabled.
errors: "weather vn keep crashing" system dialog. No logcat captured.
reproduction: Unclear — happens on HyperOS in background, not reliably reproducible. Seems tied to midnight battery purge or aggressive app kill by HyperOS.
started: After WatchdogService was introduced (v1.1/v1.2). Suspected: HyperOS killing causes crash rather than a clean process death.

## Eliminated
<!-- APPEND only - prevents re-investigating -->

- hypothesis: The current disappearing notification proves HyperOS/LMK killed the app while WatchdogService should have stayed alive
  evidence: Current WatchdogService code promotes to `FOREGROUND_SERVICE_TYPE_SHORT_SERVICE`, runs one heartbeat on a background thread, schedules the next alarm, calls `stopSelf()`, and returns `START_NOT_STICKY`. Settings default the watchdog heartbeat to 15 minutes and the UI only allows 10-30 minutes, so a notification vanishing around 2 minutes does not match the configured watchdog cadence and is not evidence that the service was supposed to remain persistent.
  timestamp: 2026-06-01T00:00:00Z

- hypothesis: Reverting WatchdogService to a long-lived `dataSync` foreground keepalive is a safe fix on current SDKs
  evidence: The newly supplied May 26-27 crash logs show repeated `ForegroundServiceStartNotAllowedException: Time limit already exhausted for foreground service type dataSync` when starting WatchdogService, and repo memory already records that `dataSync` time budgets on SDK 34-36 can exhaust and crash this path.
  timestamp: 2026-06-01T00:00:00Z

- hypothesis: The current `main` branch contains a truly persistent watchdog lifecycle that can be copied back as-is
  evidence: `main` and `backup/fork-main-20260504` both still call `stopSelf()` after each heartbeat and still declare WatchdogService as `foregroundServiceType="dataSync"`. The code path differs from the current branch mainly in using `dataSync`, not in keeping the service alive indefinitely.
  timestamp: 2026-06-01T00:00:00Z

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

- timestamp: 2026-06-01T00:00:00Z
  checked: latest user report + older May 26-27 crash excerpts
  found: User now reports the watchdog notification disappears after about 2 minutes on the current build. Older logs separately show repeated `ForegroundServiceStartNotAllowedException: Time limit already exhausted for foreground service type dataSync` while starting WatchdogService.
  implication: The historical crash and the current UX symptom are related but not identical problems; the old crash constrains the fix space, while the current symptom needs current-code-path analysis.

- timestamp: 2026-06-01T00:00:00Z
  checked: current WatchdogService, AndroidManifest, and watchdog settings path
  found: WatchdogService is now declared as `android:foregroundServiceType="shortService"`; on Android 14+ it calls `startForeground(..., FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)`, runs one heartbeat, schedules the next alarm, then calls `stopSelf()`. The heartbeat interval defaults to 15 minutes and the settings slider only exposes 10-30 minutes.
  implication: The current implementation is intentionally ephemeral, so a persistent keepalive notification is not the expected behavior under the shortService migration.

- timestamp: 2026-06-01T00:00:00Z
  checked: current teardown path for standalone watchdog notification
  found: Before this patch, onDestroy() only called `stopForeground(STOP_FOREGROUND_DETACH)` when piggybacking on the widget notification and otherwise relied on the system to remove the standalone watchdog notification after service teardown.
  implication: The lingering then disappearing standalone notification could look like an LMK symptom even though the service had already stopped itself normally.

- timestamp: 2026-06-01T00:00:00Z
  checked: post-fix validation
  found: Updated WatchdogService to explicitly remove the standalone watchdog foreground notification on teardown and updated base strings/comments to describe the feature as a short-lived background guard. `./gradlew :app:processBasicDebugResources :app:compileBasicDebugKotlin --console=plain` completed successfully.
  implication: The code now matches the short-service lifecycle more explicitly and the touched slice compiles on the basicDebug variant.

- timestamp: 2026-06-01T00:00:00Z
  checked: `main` and `backup/fork-main-20260504` versions of WatchdogService and AndroidManifest
  found: Both reference branches still promote WatchdogService as `FOREGROUND_SERVICE_TYPE_DATA_SYNC`, declare it as `android:foregroundServiceType="dataSync"`, and still call `stopSelf()` after scheduling the next alarm.
  implication: The perceived "never-kill" behavior of older builds is not backed by a materially different watchdog lifecycle in these nearby code references. Reverting to those branches would mainly reintroduce the `dataSync` crash risk on SDK 36.

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: The old crash root cause was real: starting WatchdogService as a `dataSync` foreground service could exhaust Android's foreground-service time budget and crash with `ForegroundServiceStartNotAllowedException`. The current symptom is different: after migrating to `shortService`, WatchdogService is intentionally ephemeral and always stops itself after each heartbeat, so the standalone watchdog notification disappearing is expected under the current design rather than confirmed evidence of an LMK/HyperOS kill. Nearby branch references (`main`, `backup/fork-main-20260504`) do not provide a safe persistent watchdog implementation to revert to: they still self-stop and mainly differ by using the unsafe `dataSync` foreground type. The real remaining issue was a design/UX mismatch: code and strings still implied a persistent keepalive even though modern Android limits no longer allow that implementation safely.
fix: Keep the short-service design, explicitly remove the standalone watchdog notification on service teardown so its disappearance matches the actual lifecycle, and update base watchdog copy/comments to describe the feature as a short-lived background guard instead of an uninterrupted keepalive.
verification: `./gradlew :app:processBasicDebugResources :app:compileBasicDebugKotlin --console=plain` (BUILD SUCCESSFUL); IDE diagnostics on touched files report no errors.
files_changed: ["app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt", "app/src/main/res/values/strings.xml"]
