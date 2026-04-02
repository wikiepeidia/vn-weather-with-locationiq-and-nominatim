---
plan: 07-03
status: complete
commit: 9a7d33b53
---

# Plan 07-03 Summary: BackgroundUpdatesSettingsScreen Watchdog Section

## What was done
Added complete Watchdog Mode section between General and Troubleshoot sections in `BackgroundUpdatesSettingsScreen.kt`.

## Files modified
- `app/src/main/java/org/breezyweather/ui/settings/compose/BackgroundUpdatesSettingsScreen.kt`

## Implementation
- **Imports added:** `android.os.Build`, `org.breezyweather.background.watchdog.WatchdogService`
- **Section structure:** `sectionHeaderItem` → `switchPreferenceItem` → conditional `clickablePreferenceItem` (Xiaomi only) → `sectionFooterItem`
- **Toggle ON:** `WatchdogService.start(context)` + battery opt dialog if not exempt
- **Toggle OFF:** `WatchdogService.stop(context)` — clean DEGRADE-02 path
- **Battery opt dialog:** `remember { mutableStateOf(false) }` correctly scoped inside `@Composable` lambda; navigates to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **HyperOS autostart:** 2-step intent chain (AutoStartManagementActivity → APP_PERM_EDITOR → snackbar)
- **isFirst=false** on `PreferenceViewWithCard` per D-21

## Requirements satisfied
- SETT-01: Watchdog toggle in Background Updates settings
- SETT-02: Battery opt dialog on toggle-ON
- SETT-04: HyperOS/MIUI Autostart deep-link for Xiaomi devices
- DEGRADE-02: Clean disable via WatchdogService.stop
