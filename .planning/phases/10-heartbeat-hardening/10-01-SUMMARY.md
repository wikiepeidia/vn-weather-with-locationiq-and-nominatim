---
phase: 10-heartbeat-hardening
plan: 01
status: complete
---

# Phase 10 Plan 01 Summary: WakeLock + Dynamic Interval + Diagnostics

## What was done
- **HEART-01:** Added PARTIAL_WAKE_LOCK around performHeartbeat() with 30s timeout — prevents CPU sleep during health checks
- **HEART-02 backend:** Added `watchdogHeartbeatInterval` property to SettingsManager (Int, default 15, range 10-30). Replaced hardcoded `HEARTBEAT_INTERVAL_MS` with dynamic `settingsManager.watchdogHeartbeatInterval * 60 * 1000L`
- **HEART-03:** Added `writeDiagnostic()` method — writes JSON `{timestamp, heartbeat_count, uptime_ms, job_status}` to SharedPreferences `watchdog_diagnostics`
- Added 2 string resources for slider label/summary
- Added `serviceStartTime` field for uptime tracking

## Commits
- `7e490dedd` — feat(10-01): WakeLock + dynamic interval + diagnostics

## Requirements
- **HEART-01:** ✅ WakeLock wraps heartbeat
- **HEART-02:** ✅ Backend ready (setting + dynamic alarm)
- **HEART-03:** ✅ Diagnostic JSON on every heartbeat

## Files modified
- `SettingsManager.kt` — added watchdogHeartbeatInterval property
- `WatchdogService.kt` — WakeLock, dynamic interval, diagnostics, serviceStartTime
- `strings.xml` — 2 new strings
