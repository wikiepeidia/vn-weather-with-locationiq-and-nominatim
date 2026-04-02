# Phase 7: Settings & Teardown — Discussion Log

**Gathered:** 2026-04-02  
**Mode:** auto (user unavailable)

## Gray Areas Identified and Resolved

### Area 1: Toggle Placement
**Decision:** New dedicated "Watchdog Mode" section between General and Troubleshoot.

**Rationale:** The Watchdog feature is a distinct capability, not a tweak to general update settings. Its own named section makes it discoverable and signals to users that it's a separate opt-in feature. Appending to General would bury it; Troubleshoot is for diagnostics, not feature enablement.

---

### Area 2: Battery Opt Dialog — Trigger and UX
**Decision:** AlertDialog on Watchdog toggle-ON when not already exempt. Confirm → launch battery settings. Dismiss → skip (Watchdog still starts). No re-prompt if already exempt.

**Rationale:** This mirrors the AdGuard UX that the user referenced. The prompt is advisory, not mandatory — user can dismiss and Watchdog will still run (just less reliably). Forcing the user to grant battery exemption before enabling would be too much friction.

---

### Area 3: MIUI Autostart Deep-link Detection
**Decision:** `Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)` — covers Xiaomi, Redmi, and POCO devices (all share Xiaomi as manufacturer regardless of brand). Two-step intent strategy with SnackbarHelper fallback.

**Rationale:** Build.MANUFACTURER is more reliable than Build.BRAND for manufacturer detection. POCO and Redmi both report "xiaomi" as manufacturer even though their brand differs. Two-step intent covers older MIUI versions.

---

### Area 4: SettingsManager Parameter Passing
**Decision:** No new parameter on `BackgroundSettingsScreen`. Read `watchdogEnabled` inline from `SettingsManager.getInstance(context)` in the composable, consistent with `ignoreUpdatesWhenBatteryLow`.

**Rationale:** `updateInterval` is a parameter because it affects the `enabled` state of another preference. `watchdogEnabled` has no cross-preference dependencies in this phase — it controls only WatchdogService start/stop. Adding a parameter would be premature complexity.

---

### Area 5: Clean Disable Path (DEGRADE-02)
**Decision:** Toggle OFF → `watchdogEnabled = false` + `WatchdogService.stop(context)`. Service's `onDestroy()` already cancels alarm and removes notification (implemented in Phase 6).

**Rationale:** The clean disable behavior is already fully implemented in WatchdogService. UI just needs to call `stop()`. No additional cleanup at the Activity/UI layer.

---

*Discussion log created: 2026-04-02*
