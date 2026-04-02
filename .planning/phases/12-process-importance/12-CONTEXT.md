---
phase: 12-process-importance
type: feature
generated: auto (autonomous mode)
---

# Phase 12 Context: Process Importance Elevation

## Goal
WatchdogService process is harder to kill on HyperOS/MIUI by elevating its OOM adjustment score.

## Requirements
- **PROC-01:** Transparent zero-pixel Activity binding to elevate OOM adj from "service" to "visible"
- **PROC-02:** Only activates on Xiaomi/Redmi/POCO devices behind watchdog toggle
- **PROC-03:** Process importance logged at each heartbeat when binding is active

## Key Decisions
- Create `WatchdogAnchorActivity` — transparent theme, zero-pixel, finishes immediately after binding
- WatchdogService binds to this Activity via ServiceConnection in onCreate, unbinds in onDestroy
- Activity declared in AndroidManifest with `android:theme="@android:style/Theme.Translucent.NoTitleBar"` and `android:excludeFromRecents="true"`
- Manufacturer check: `Build.MANUFACTURER.lowercase()` in listOf("xiaomi", "redmi", "poco")
- Process importance: `ActivityManager.RunningAppProcessInfo.importance` logged in writeDiagnostic()
- The binding trick works because having a bound Activity moves the process to "visible" importance, which HyperOS treats more favorably than "service"

## Dependencies
- Phase 10 (HEART-03): diagnostic logging to add process_importance field
