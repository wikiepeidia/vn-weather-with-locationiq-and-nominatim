# Phase 6: WatchdogService Core — Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Create `WatchdogService` — a new Android `Service` (not a WorkManager Worker) that:
1. Runs as a persistent foreground service while Watchdog mode is enabled
2. Posts a non-dismissible, silent keepalive notification
3. Monitors `WeatherUpdateJob` at each heartbeat tick and re-enqueues it if dead
4. Uses AlarmManager `setExactAndAllowWhileIdle()` to self-restart after ROM process kills
5. Degrades gracefully on ROMs that restrict exact alarms

This phase does NOT include the Settings toggle (Phase 7) or BootReceiver wiring (Phase 8).
The service is built as a complete, startable unit — Phase 7 wires the ON/OFF switch.

</domain>

<decisions>
## Implementation Decisions

### Notification Channel
- **D-01:** Create a **new dedicated `CHANNEL_WATCHDOG`** (channel ID: `"watchdog"`) with `IMPORTANCE_MIN`, `setShowBadge(false)`, grouped under `GROUP_BREEZY_WEATHER`
- **D-02:** Rationale: separating it from `CHANNEL_BACKGROUND` lets users independently disable Watchdog notifications in system notification settings without affecting background update progress notifications
- **D-03:** Notification ID: add `ID_WATCHDOG_KEEPALIVE = 10` to `Notifications.kt` (next available ID after `ID_UPDATING_AWAKE = 9`)
- **D-04:** Notification text format: `"Weather last updated X min ago"` (relative time), updated via `updateNotification()` method on the service
- **D-05:** Notification is `setOngoing(true)` — non-dismissible while service is running

### AlarmManager Strategy
- **D-06:** Use **`setExactAndAllowWhileIdle()`** as primary alarm method — this does NOT require `SCHEDULE_EXACT_ALARM` user-facing permission, only `WAKE_LOCK` in manifest
- **D-07:** Add `<uses-permission android:name="android.permission.WAKE_LOCK" />` to `AndroidManifest.xml`
- **D-08:** Wrap `setExactAndAllowWhileIdle()` in a try/catch `SecurityException` — if restricted, fall back to `setAndAllowWhileIdle()` (inexact but Doze-aware) with a `Log.d` warning
- **D-09:** The alarm fires `WatchdogAlarmReceiver` (new `BroadcastReceiver`) which calls `context.startForegroundService(Intent(context, WatchdogService::class.java))` to restart the service
- **D-10:** Do NOT request `SCHEDULE_EXACT_ALARM` — it requires user to navigate to a system settings screen (too much friction for an opt-in feature)

### Foreground Service Type
- **D-11:** Declare `WatchdogService` with `android:foregroundServiceType="dataSync"` in `AndroidManifest.xml`
- **D-12:** Rationale: `FOREGROUND_SERVICE_DATA_SYNC` permission is already declared in the manifest; `dataSync` type is valid for Android 14+ with no additional Play Store justification needed. `specialUse` would require a Play Store FGS use case form — unnecessary overhead.
- **D-13:** Call `startForeground(ID_WATCHDOG_KEEPALIVE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` on Android Q+; plain `startForeground(ID, notification)` on older

### Heartbeat Interval
- **D-14:** Fixed heartbeat: **15 minutes** via AlarmManager
- **D-15:** Rationale: 15 min matches WorkManager's minimum periodic interval; shorter intervals add battery cost without benefit (Watchdog only checks job state, not doing network calls); longer risks missing more updates on aggressive ROMs
- **D-16:** At each heartbeat: call `context.workManager.isRunning(WeatherUpdateJob.TAG)` — if not running AND not enqueued, call `WeatherUpdateJob.setupTask(context)` to restore

### Job Monitor Logic
- **D-17:** Watchdog checks `WorkInfo.State.RUNNING` OR `WorkInfo.State.ENQUEUED` as healthy states — either means the job is alive
- **D-18:** Only if NEITHER state is present does Watchdog call `WeatherUpdateJob.setupTask(context)` — avoids redundant re-queuing
- **D-19:** After re-enqueuing, log `Log.d("Watchdog", "Re-enqueued WeatherUpdateJob — was not found in RUNNING/ENQUEUED state")`

### Service Structure
- **D-20:** New file: `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt`
- **D-21:** New file: `app/src/main/java/org/breezyweather/background/watchdog/WatchdogAlarmReceiver.kt`
- **D-22:** `WatchdogService` is NOT a `@HiltAndroidApp` entry point — use manual `SettingsManager.getInstance(this)` and `this.workManager` extension, consistent with other non-Hilt parts of the codebase
- **D-23:** `WatchdogService.onStartCommand()` returns `START_STICKY` — OS restarts service after kill if possible
- **D-24:** `WatchdogService` schedules the alarm in `onStartCommand()` and cancels it in `onDestroy()`

### Agent's Discretion
- Internal coroutine scope for the service (use `CoroutineScope(Dispatchers.IO + SupervisorJob())`, cancelled in `onDestroy()`)
- Exact PendingIntent flags: `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`
- AlarmManager alarm type: `AlarmManager.ELAPSED_REALTIME_WAKEUP` (not RTC — more reliable, not affected by time zone changes)
- Notification timestamp display: use `DateUtils.getRelativeTimeSpanString()` from `weatherUpdateLastTimestamp` in `SettingsManager`

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Background Components
- `app/src/main/java/org/breezyweather/background/weather/WeatherUpdateJob.kt` — WorkManager worker; TAG, WORK_NAME_AUTO, setupTask(), isRunning() pattern to monitor
- `app/src/main/java/org/breezyweather/background/receiver/BootReceiver.kt` — Pattern for BroadcastReceiver structure in this codebase
- `app/src/main/java/org/breezyweather/common/extensions/WorkManagerExtensions.kt` — `isRunning(tag)` extension method used for job state check

### Notifications
- `app/src/main/java/org/breezyweather/remoteviews/Notifications.kt` — Channel definitions, IDs, `createChannels()` — add CHANNEL_WATCHDOG and ID_WATCHDOG_KEEPALIVE here
- `app/src/main/java/org/breezyweather/common/extensions/NotificationExtensions.kt` — `buildNotificationChannel()`, `notificationBuilder()` DSL patterns

### Settings
- `app/src/main/java/org/breezyweather/domain/settings/SettingsManager.kt` — Boolean preference pattern; `weatherUpdateLastTimestamp` for "last updated X min ago" text

### Manifest
- `app/src/main/AndroidManifest.xml` — Add: `WAKE_LOCK` permission, WatchdogService declaration (foregroundServiceType="dataSync"), WatchdogAlarmReceiver declaration

### Application
- `app/src/main/java/org/breezyweather/BreezyWeather.kt` — `setupNotificationChannels()` in onCreate() — CHANNEL_WATCHDOG will be created here automatically via Notifications.createChannels()

No external specs — requirements fully captured in decisions above.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CHANNEL_BACKGROUND` (IMPORTANCE_MIN): Pattern for the new CHANNEL_WATCHDOG — same importance, same group, same `setShowBadge(false)` setup
- `context.workManager` extension: Direct WorkManager access without injection
- `WorkManager.isRunning(tag)`: Use `WeatherUpdateJob.TAG` to check job health
- `WeatherUpdateJob.setupTask(context)`: Call this to restore a dead periodic job
- `notificationBuilder(channelId) { ... }` DSL: Use for building keepalive notification

### Established Patterns
- **Non-Hilt services:** Use `SettingsManager.getInstance(context)` and `context.workManager` directly — no @AndroidEntryPoint needed
- **Foreground service start:** `startForeground(ID, notification, type)` called promptly in `onStartCommand()` — Android requires this within 5 seconds
- **Error handling:** Wrap alarm operations in try/catch; log with `Log.d` tag format matching rest of codebase
- **Notification building:** `notificationBuilder(CHANNEL_WATCHDOG) { setOngoing(true); setSmallIcon(...); setContentTitle(...) }`

### Integration Points
- `Notifications.createChannels()` → add `buildNotificationChannel(CHANNEL_WATCHDOG, IMPORTANCE_MIN) { ... }` to the list
- `Notifications.kt` constants → add `const val CHANNEL_WATCHDOG = "watchdog"` and `const val ID_WATCHDOG_KEEPALIVE = 10`
- `AndroidManifest.xml` → new `<service>` and `<receiver>` entries
- Phase 7 will call `context.startForegroundService(Intent(context, WatchdogService::class.java))` to start it

</code_context>

<specifics>
## Specific Ideas

- **AdGuard/NetSpeedMonitor pattern:** Persistent notification as keepalive anchor — exactly matches D-05 (setOngoing=true) + D-01 (IMPORTANCE_MIN silent channel)
- **HyperOS kill scenario:** WorkManager periodic work silently disappears; Watchdog's 15-min heartbeat catches this and re-enqueues — the key recovery loop
- **"Last updated X min ago":** Pull `SettingsManager.getInstance(this).weatherUpdateLastTimestamp` and format with `DateUtils.getRelativeTimeSpanString()` for human-readable notification text

</specifics>

<deferred>
## Deferred Ideas

- Watchdog notification showing current temperature/conditions (WATCH-V2-02) — deferred to v2
- Configurable heartbeat interval (WATCH-V2-03) — deferred to v2; fixed 15 min for now
- HyperOS version detection for contextual guidance (ROM-V2-01) — deferred to v2

</deferred>

---

*Phase: 06-watchdogservice-core*
*Context gathered: 2026-04-02*
