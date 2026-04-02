# Phase 6: WatchdogService Core — Discussion Log

**Date:** 2026-04-02
**Mode:** Autonomous (user unavailable for interactive session)

---

## Agenda

Phase 6 introduces a brand-new persistent Android Service. Four gray areas required decisions before planning could begin:

1. Notification channel — reuse vs. new channel
2. AlarmManager permission strategy
3. Foreground service type declaration
4. Heartbeat interval

---

## Gray Area 1: Notification Channel

**Question:** Reuse existing `CHANNEL_BACKGROUND` (IMPORTANCE_MIN, already silent) or create a new `CHANNEL_WATCHDOG`?

**Analysis:**
- `CHANNEL_BACKGROUND` is used for the weather update progress notification (shown only while a refresh is in progress). Sharing it would mean both types of notifications are bundled under the same "Background Updates" channel in Android system notification settings.
- A dedicated `CHANNEL_WATCHDOG` allows users who find the persistent notification intrusive to disable just the Watchdog channel without losing visibility of weather update progress notifications.
- Both approach use IMPORTANCE_MIN (no sound, no badge, no heads-up).

**Decision:** Create a new `CHANNEL_WATCHDOG` (`"watchdog"`, IMPORTANCE_MIN).

**Rationale:** Better separation of concerns; user control granularity; consistent with how other apps (AdGuard, VPN apps) give users independent channel control for their persistent keepalive notifications.

---

## Gray Area 2: AlarmManager Permission Strategy

**Question:** `setExact()` on Android 12+ requires `SCHEDULE_EXACT_ALARM` which needs user to actively grant it in Settings (high friction). `setExactAndAllowWhileIdle()` does NOT require it — only `WAKE_LOCK`. What strategy?

**Analysis:**
- `SCHEDULE_EXACT_ALARM`: User must navigate to "Alarms & reminders" in Settings and grant permission. This is a user-visible action that would be confusing for a weather widget feature. Many users would not complete this step.
- `setExactAndAllowWhileIdle()`: Works during Doze mode, does not require `SCHEDULE_EXACT_ALARM` user permission. May be throttled on HyperOS but will still fire eventually. Only requires `WAKE_LOCK`.
- Fallback to `setAndAllowWhileIdle()` (inexact, Doze-aware) via try/catch SecurityException provides graceful degradation for the most restricted ROMs.

**Decision:** Use `setExactAndAllowWhileIdle()` as primary; catch SecurityException and fall back to `setAndAllowWhileIdle()`. Add `WAKE_LOCK` permission to manifest. Do NOT request `SCHEDULE_EXACT_ALARM`.

**Rationale:** Maximum compatibility with zero user-facing permission friction. The self-restart mechanism is a best-effort keepalive — exact timing is nice-to-have, not critical. `START_STICKY` on the Service itself handles the common case (OS restarts the service); AlarmManager is the additional safety net for ROMs that don't honor `START_STICKY`.

---

## Gray Area 3: Foreground Service Type

**Question:** What `android:foregroundServiceType` for WatchdogService? Options: `dataSync` (permission already present), `specialUse` (needs Play Store justification).

**Analysis:**
- Android 14 (API 34) requires foreground services to have an explicit `foregroundServiceType` declared in manifest AND the corresponding permission.
- `FOREGROUND_SERVICE_DATA_SYNC` permission is already in the manifest for WorkManager's `SystemForegroundService`.
- WatchdogService monitors weather data sync jobs — "data synchronization that cannot be interrupted" is the Play Store definition. This is a reasonable fit.
- `specialUse` requires submitting a use-case form to Google Play and waiting for review — excessive overhead for a standard background watchdog pattern.

**Decision:** Use `foregroundServiceType="dataSync"`.

**Rationale:** Permission already declared; no additional Play Store form needed; semantically appropriate (monitoring data sync). On the service side, call `startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` on Android Q+.

---

## Gray Area 4: Heartbeat Interval

**Question:** Fixed interval for the AlarmManager heartbeat: 15 min, 30 min, or match user's update interval?

**Analysis:**
- WorkManager's minimum periodic interval is 15 minutes. If the WeatherUpdateJob is killed and re-enqueued, it could be up to 15 min before the first new execution. So checking every 15 min means at worst a 30-min delay (missed one cycle + one watchdog cycle before recovery).
- Matching the user's update interval (which could be 1 hour, 3 hours, etc.) would mean jobs that fail early in the interval aren't caught until the next scheduled time — potentially hours of stale data.
- 30 min is a reasonable middle ground but offers no specific advantage over 15 min.
- The heartbeat is extremely lightweight (only queries WorkManager state, no network), so 15 min battery cost is negligible.

**Decision:** Fixed **15 minutes** heartbeat interval.

**Rationale:** Catches job deaths within one missed cycle. Matches WorkManager's own minimum period. Battery impact negligible (no network, pure local state check). Simpler implementation (no need to read user preference in the alarm receiver).

---

## Summary of All Decisions

| # | Area | Decision |
|---|------|----------|
| 1 | Notification channel | New `CHANNEL_WATCHDOG` (IMPORTANCE_MIN), ID `10` |
| 2 | AlarmManager | `setExactAndAllowWhileIdle()` + SecurityException fallback; add `WAKE_LOCK` |
| 3 | Service type | `foregroundServiceType="dataSync"` |
| 4 | Heartbeat | Fixed 15 minutes |

---

*Phase: 06-watchdogservice-core | Log: 06-DISCUSSION-LOG.md*
