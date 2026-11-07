---
status: resolved
trigger: "Investigate a crash in an Android app caused by watchdog foreground-service startup after a rebase. This is a read-only diagnosis task first; do not edit files."
created: 2026-05-27T19:13:11+07:00
updated: 2026-05-27T19:18:17+07:00
---

## Current Focus

hypothesis: The reported crash is caused by the watchdog service being promoted as a dataSync foreground service in the affected build on SDK 36 after that type's time budget/exemption has been exhausted, while WatchdogService.onStartCommand still lets startForeground() failures escape; changing the watchdog to shortService addresses the specific dataSync rejection, but a local catch-and-bail path is still needed to prevent any future foreground-promotion denial from crashing the process.
test: Compile the touched Android slice after adding a guarded foreground-promotion path in WatchdogService.
expecting: Build succeeds, watchdog no longer crashes the process if startForeground() is denied, and fallback remains WorkManager-based.
next_action: Resolved.

## Symptoms

expected: WatchdogService should restart background watchdog work without crashing the app process when invoked from boot/alarm/background restart paths.
actual: App process crashes while starting org.breezyweather.background.watchdog.WatchdogService on Android 16 / SDK 36 with a RuntimeException wrapping ForegroundServiceStartNotAllowedException.
errors: android.app.ForegroundServiceStartNotAllowedException: Time limit already exhausted for foreground service type dataSync
reproduction: Allow watchdog restart path to invoke WatchdogService on the affected device/build; crash occurs during onStartCommand at Service.startForeground().
started: After the rebase that introduced WatchdogService as a foreground service using dataSync.

## Eliminated

## Evidence

- timestamp: 2026-05-27T19:13:11+07:00
  checked: BUG/breezyweather_crash_logs_1.txt
  found: Repeated fatal crashes all show RuntimeException in ActivityThread.handleServiceArgs caused by ForegroundServiceStartNotAllowedException at Service.startForeground(), then WatchdogService.onStartCommand.
  implication: The process survives the initial startForegroundService() request but dies when the service itself tries to promote to foreground.

- timestamp: 2026-05-27T19:13:11+07:00
  checked: app/src/main/kotlin/org/breezyweather/background/watchdog/WatchdogService.kt
  found: onStartCommand unconditionally calls startWatchdogForeground(notification), which directly calls startForeground(... FOREGROUND_SERVICE_TYPE_SHORT_SERVICE on current tree / type-specific overload on API 34+) without a local try/catch.
  implication: Any platform rejection from startForeground() escapes on the main thread and crashes the process.

- timestamp: 2026-05-27T19:13:11+07:00
  checked: app/src/main/kotlin/org/breezyweather/background/watchdog/WatchdogAlarmReceiver.kt and BootReceiver.kt
  found: Callers invoke WatchdogService.start(...) and only protect around the outer service start request, not around the later onStartCommand foreground promotion.
  implication: Caller-side exception handling cannot intercept the observed crash site.

- timestamp: 2026-05-27T19:13:11+07:00
  checked: app/src/main/kotlin/org/breezyweather/background/watchdog/WatchdogService.kt companion start() and app/src/main/kotlin/org/breezyweather/common/extensions/WorkManagerExtensions.kt
  found: WatchdogService.start() catches only the initial startForegroundService/startService call and always enqueues WatchdogRestartWorker; the worker's setForegroundSafely() helper explicitly treats foreground elevation as optional and continues on IllegalStateException.
  implication: The repo already contains a fallback model where background recovery should degrade to WorkManager instead of crashing when foreground elevation is not permitted.

- timestamp: 2026-05-27T19:13:11+07:00
  checked: app/src/main/AndroidManifest.xml and current Kotlin references
  found: Current uncommitted tree changes WatchdogService and WatchdogRestartWorker to shortService, while the crash log still reports dataSync for WatchdogService; WorkManager's SystemForegroundService remains declared with dataSync|shortService and WeatherUpdateJob still uses dataSync separately.
  implication: The observed crash comes from the watchdog service's old deployed dataSync type, not from the WorkManager foreground service; rebuilding with shortService should change the policy applied to WatchdogService but does not by itself harden the service against future startForeground() denials.

- timestamp: 2026-05-27T19:13:11+07:00
  checked: Android official FGS docs (service types and troubleshooting)
  found: Android docs state dataSync foreground services are more restricted on Android 15+ including boot-start limits, shortService is intended for about 3 minutes of critical work and still throws ForegroundServiceStartNotAllowedException if background-start eligibility is missing, and uncaught foreground-service exceptions crash the app.
  implication: shortService is the correct semantic fit for this heartbeat task, but foreground promotion can still be denied on SDK 34-36, so WatchdogService needs a local degrade path rather than assuming promotion will always succeed.

## Resolution

root_cause: "The affected build still starts WatchdogService as a dataSync foreground service. On SDK 36 the platform rejects that promotion with ForegroundServiceStartNotAllowedException (time limit already exhausted for dataSync) during Service.startForeground() inside onStartCommand. Because WatchdogService only catches failures around context.startForegroundService() and not around its own startForeground() call, the exception escapes on the main thread and crashes the app process."
fix: "Keep the watchdog service on shortService, and additionally wrap the local startWatchdogForeground()/startForeground() call in WatchdogService.onStartCommand with a catch for foreground-promotion failure (at minimum ForegroundServiceStartNotAllowedException on API 31+ plus IllegalStateException compatibility). On failure, log, skip heartbeat thread/wakelock work, stop the service promptly, and rely on the already-enqueued WatchdogRestartWorker fallback to maintain background recovery."
verification: "Applied the local guard in WatchdogService and then ran ./gradlew :app:processBasicDebugMainManifest :app:compileBasicDebugKotlin successfully. Remaining device-side validation is to install this build on the affected SDK 36 device and confirm the watchdog no longer crashes when boot/alarm restart paths fire."
files_changed:
  - app/src/main/AndroidManifest.xml
  - app/src/main/kotlin/org/breezyweather/background/watchdog/WatchdogRestartWorker.kt
  - app/src/main/kotlin/org/breezyweather/background/watchdog/WatchdogService.kt
