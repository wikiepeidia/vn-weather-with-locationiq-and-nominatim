---
status: awaiting-device-validation
trigger: "watchdog feature after merging with latest form completely not updating the weather (weather no longer updating in background but watchdog i working and not crash.)"
created: 2026-06-01T00:00:00Z
updated: 2026-06-02T00:00:00Z
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

hypothesis: CONFIRMED — the prior watchdog fix was only partially correct. It did detect stale periodic work better, but it trusted a timestamp written when WeatherUpdateJob starts, not when weather is actually refreshed, and its immediate recovery path still used `ExistingWorkPolicy.KEEP` for manual refresh work. On HyperOS-style devices, that can leave watchdog satisfied by worker starts or by a stale pending manual request while visible weather data remains hours old.
test: Tightened WeatherUpdateJob so `weatherUpdateLastTimestamp` is only recorded after at least one location completes the refresh path, and changed immediate watchdog retries to replace pending manual refresh work instead of silently keeping it. Validated with focused unit tests.
expecting: Watchdog should keep treating weather as stale until a real refresh completes, and each stale heartbeat can replace a stuck ENQUEUED manual refresh request instead of getting trapped behind it.
next_action: Validate on HyperOS 3 that worker info no longer sits on an aging pending manual refresh and that the main weather freshness advances after the next watchdog repair cycle.

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected: Background weather should continue updating after the watchdog feature runs, without crashes, similar to behavior before the merge.
actual: Watchdog appears to run and no longer crashes, but weather data no longer updates in background.
errors: No concrete logs or errors supplied. User unavailable for follow-up during this run.
reproduction: After the merge with the latest upstream/form changes, leave the app in background with watchdog enabled and wait for the normal background refresh path. Watchdog still appears active, but weather timestamps/data do not refresh.
started: Reported after merging with the latest form/upstream changes. Background updates reportedly worked before.

## Eliminated
<!-- APPEND only - prevents re-investigating -->

- The `WeatherUpdateJob.setupTask()` guard `pollingRate > 15.minutes` is not the regression trigger here. The app only exposes 30 minutes or slower as background refresh intervals, so the watchdog was not silently cancelling a valid 15-minute weather schedule.

- The prior assumption that `weather_update_last_timestamp` already represented a successful weather refresh was false. `WeatherUpdateJob` wrote it at worker start, so failed or killed runs could still look healthy to the watchdog.

## Evidence
<!-- APPEND only - facts discovered -->

- timestamp: 2026-06-01T00:05:00Z
 checked: app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt and app/src/main/java/org/breezyweather/background/watchdog/WatchdogRestartWorker.kt
 found: Both watchdog paths considered WeatherUpdateJob healthy solely when unique periodic work existed in WorkManager with state RUNNING or ENQUEUED.
 implication: A periodic worker that stays ENQUEUED but never actually executes will never be repaired by watchdog.

- timestamp: 2026-06-01T00:10:00Z
 checked: app/src/main/kotlin/org/breezyweather/domain/settings/SettingsManager.kt and app/src/main/kotlin/org/breezyweather/background/weather/WeatherUpdateJob.kt
 found: The app already persists `weather_update_last_timestamp` on each weather worker run, and the settings screen already shows it as worker info.
 implication: There was already a local signal available to detect stale background updates, but watchdog was not using it.

- timestamp: 2026-06-01T00:15:00Z
 checked: app/src/main/kotlin/org/breezyweather/common/options/UpdateInterval.kt
 found: The smallest supported automatic refresh interval is 30 minutes.
 implication: The earlier suspicion about `pollingRate > 15.minutes` cancelling a valid 15-minute weather job was falsified.

- timestamp: 2026-06-01T00:25:00Z
 checked: app/src/main/java/org/breezyweather/background/watchdog/WatchdogWeatherUpdateHealth.kt and app/src/test/kotlin/org/breezyweather/background/watchdog/WatchdogWeatherUpdateHealthTest.kt
 found: The fix centralizes watchdog repair logic so stale ENQUEUED work now triggers `WeatherUpdateJob.startNow()` and missing periodic work is re-scheduled before the next heartbeat. Targeted unit tests passed for stale, missing+stale, and healthy cases.
 implication: The watchdog handoff now reaches an actual refresh path instead of only confirming worker presence.

- timestamp: 2026-06-01T00:40:00Z
 checked: app/src/main/kotlin/org/breezyweather/background/weather/WeatherUpdateJob.kt and app/src/main/kotlin/org/breezyweather/domain/settings/SettingsManager.kt
 found: `weatherUpdateLastTimestamp` is only written in `WeatherUpdateJob.doWork()`, and it was written immediately after `setForegroundSafely()` before any location/weather refresh succeeded.
 implication: The previous session's assumption that watchdog was using a last-successful-update signal was false. A worker start, retry, or early failure could still mask stale weather data.

- timestamp: 2026-06-01T00:45:00Z
 checked: app/src/main/kotlin/org/breezyweather/background/weather/WeatherUpdateJob.kt
 found: `WeatherUpdateJob.startNow()` only blocked duplicate work when a weather job was already RUNNING, but it enqueued manual refreshes with unique work policy `KEEP`. A stale ENQUEUED/BLOCKED manual refresh therefore prevented watchdog from replacing it with a fresh retry.
 implication: On HyperOS-like behavior where one-time work can remain pending for long periods, watchdog could keep requesting a refresh without ever replacing the stuck manual request.

- timestamp: 2026-06-01T01:00:00Z
 checked: app/src/test/kotlin/org/breezyweather/background/weather/WeatherUpdateJobTest.kt and app/src/test/kotlin/org/breezyweather/background/watchdog/WatchdogWeatherUpdateHealthTest.kt
 found: Focused unit tests passed after changing the timestamp semantics and immediate refresh enqueue policy.
 implication: The reopened fix now covers both the false-success timestamp and the stuck manual-refresh retry path at the watchdog-to-worker handoff.

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: The earlier watchdog repair fix used the wrong health signal and an incomplete retry path. `weatherUpdateLastTimestamp` was treated as if it meant last successful weather refresh, but the app wrote it when `WeatherUpdateJob` started. That let failed or killed workers look fresh. Separately, the watchdog recovery hop into `WeatherUpdateJob.startNow()` still used unique work policy `KEEP`, so a stale pending manual refresh could block all later retries on HyperOS-style WorkManager behavior.
fix: Kept the watchdog health logic, but tightened the worker handoff. `WeatherUpdateJob` now records `weatherUpdateLastTimestamp` only after at least one location completes the weather refresh path, and `startNow()` now replaces pending ENQUEUED/BLOCKED manual refresh work instead of silently keeping it. This makes watchdog keep retrying until a real refresh succeeds and avoids getting trapped behind a stale manual request.
verification: `./gradlew :app:testBasicDebugUnitTest --tests org.breezyweather.background.weather.WeatherUpdateJobTest --tests org.breezyweather.background.watchdog.WatchdogWeatherUpdateHealthTest` passed.
files_changed:

- app/src/main/java/org/breezyweather/background/watchdog/WatchdogWeatherUpdateHealth.kt
- app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt
- app/src/main/java/org/breezyweather/background/watchdog/WatchdogRestartWorker.kt
- app/src/main/kotlin/org/breezyweather/background/weather/WeatherUpdateJob.kt
- app/src/test/kotlin/org/breezyweather/background/weather/WeatherUpdateJobTest.kt
- app/src/test/kotlin/org/breezyweather/background/watchdog/WatchdogWeatherUpdateHealthTest.kt
