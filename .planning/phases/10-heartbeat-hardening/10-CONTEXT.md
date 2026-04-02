---
phase: 10-heartbeat-hardening
type: feature
generated: auto (autonomous mode — infrastructure-adjacent)
---

# Phase 10 Context: Heartbeat Hardening

## Goal
Heartbeat execution is CPU-sleep-proof, user-tunable, and self-documenting.

## Requirements
- **HEART-01:** WakeLock around heartbeat logic — prevent CPU sleep during health checks
- **HEART-02:** Configurable interval 10-30min via settings slider (default 15min)
- **HEART-03:** Timestamped diagnostic entries (heartbeat count, uptime, job status) in SharedPreferences

## Key Decisions
- WakeLock: Use PowerManager.PARTIAL_WAKE_LOCK with timeout (30s max) — acquire before performHeartbeat(), release in finally block
- Interval setting: Add `watchdogHeartbeatInterval` to SettingsManager (Int, minutes), slider in BackgroundUpdatesSettingsScreen below watchdog toggle
- Diagnostics: Write to SharedPreferences JSON key `watchdog_diagnostics` — simple JSON with timestamp, heartbeat_count, uptime_ms, job_status
- AlarmManager reschedule: When interval changes, cancel existing alarm and reschedule with new interval
- Existing `HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L` becomes dynamic: `settingsManager.watchdogHeartbeatInterval * 60 * 1000L`

## Dependencies
- Phase 9 (DEBT-01): `WeatherUpdateJob.WORK_NAME_AUTO` is now `internal` — used in heartbeat
