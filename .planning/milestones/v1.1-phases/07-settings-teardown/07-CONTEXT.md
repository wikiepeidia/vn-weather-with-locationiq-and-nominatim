# Phase 7: Settings & Teardown — Context

**Gathered:** 2026-04-02  
**Mode:** auto (user unavailable — all decisions made autonomously)  
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire the ON/OFF switch for WatchdogService — everything a user needs to control the Watchdog feature:

1. Add `watchdogEnabled: Boolean` to `SettingsManager` (persisted preference, default `false`)
2. Add a **"Watchdog Mode" section** to `BackgroundUpdatesSettingsScreen` with:
   - Toggle that starts/stops `WatchdogService`
   - Battery optimization prompt (AlertDialog) when user enables Watchdog and app is not exempt
   - HyperOS/MIUI "Autostart" deep-link button (Xiaomi devices only)
3. Implement clean disable path (DEGRADE-02): toggle OFF → `WatchdogService.stop()` → `onDestroy()` cancels alarm → notification dismissed

This phase does NOT touch BootReceiver (Phase 8). The toggle starts the service inline via `WatchdogService.start(context)` — Phase 8 handles resume after reboot.

</domain>

<decisions>
## Implementation Decisions

### SettingsManager — watchdogEnabled Preference
- **D-01:** Add `var watchdogEnabled: Boolean` to `SettingsManager` with:
  - SharedPreferences key: `"watchdog_enabled"`
  - Default: `false` (opt-in, battery-safe default)
  - Setter calls `notifySettingsChanged()` (consistent with all other setting properties)
- **D-02:** Rationale: default-off is mandatory per project constraints — foreground service + AlarmManager has measurable battery impact on users who don't need it

### BackgroundUpdatesSettingsScreen — New "Watchdog" Section
- **D-03:** Insert a new **"Watchdog Mode" section** between the existing General section and the Troubleshoot section:
  ```
  [GENERAL section]
    Update interval
    Skip when battery low
  [large separator]
  [WATCHDOG section — NEW]
    Watchdog Mode toggle
    (Xiaomi only) HyperOS Autostart deep-link
  [large separator]
  [TROUBLESHOOT section]
    Battery optimization
    Don't Kill My App
    Worker info
  ```
- **D-04:** Rationale: separate section makes Watchdog a distinct, named feature — users can find and identify it. Placing it between General and Troubleshoot puts it logically between "what updates" and "how to fix it."
- **D-05:** Section header string: `settings_background_updates_section_watchdog` = `"Watchdog Mode"`
- **D-06:** Section footer: use `sectionFooterItem` with a brief explanation string `settings_background_updates_section_watchdog_footer` = `"Keeps weather updates alive on ROMs that aggressively kill background tasks (HyperOS, MIUI)."`

### Watchdog Toggle
- **D-07:** Use `switchPreferenceItem` / `SwitchPreferenceView` — same pattern as `ignoreUpdatesWhenBatteryLow`
- **D-08:** Toggle reads initial state from `SettingsManager.getInstance(context).watchdogEnabled` (no new parameter to BackgroundSettingsScreen function — self-contained, consistent with `ignoreUpdatesWhenBatteryLow` which doesn't need activity-level state)
- **D-09:** `isFirst = true`, `isLast = true` when device is NOT Xiaomi (only item in section); `isFirst = true`, `isLast = false` when device IS Xiaomi (autostart button follows)
- **D-10:** Toggle string resources:
  - Title: `settings_background_updates_watchdog_switch` = `"Watchdog keepalive"`
  - Summary ON: `settings_enabled` (reuse existing)
  - Summary OFF: `settings_disabled` (reuse existing)
- **D-11:** Toggle ON logic:
  1. `SettingsManager.getInstance(context).watchdogEnabled = true`
  2. `WatchdogService.start(context)`
  3. If `!context.powerManager.isIgnoringBatteryOptimizations(context.packageName)` → show battery opt AlertDialog
- **D-12:** Toggle OFF logic (DEGRADE-02):
  1. `SettingsManager.getInstance(context).watchdogEnabled = false`
  2. `WatchdogService.stop(context)` — triggers `onDestroy()` which cancels alarm and removes notification

### Battery Optimization Prompt (SETT-02)
- **D-13:** Show an `AlertDialog` when user enables Watchdog and `!powerManager.isIgnoringBatteryOptimizations(packageName)`:
  - Title: `settings_background_updates_watchdog_battery_opt_title` = `"Optimize battery settings"`
  - Message: `settings_background_updates_watchdog_battery_opt_message` = `"For Watchdog to work reliably on your device, disable battery optimization for this app. This allows it to run in the background without interruption."`
  - Confirm button: `settings_background_updates_battery_optimization` (reuse existing key = `"Disable battery optimization"`)
  - Dismiss button: `action_later` (need to add or reuse `android.R.string.cancel`)
  - On confirm: launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent (same as existing clickable item in Troubleshoot)
  - On dismiss: dialog closes, Watchdog still starts (user chose to skip for now)
- **D-14:** Wrap the AlertDialog state in `remember { mutableStateOf(false) }` inside the `switchPreferenceItem` lambda — `dialogBatteryOptState`
- **D-15:** Do NOT re-prompt on every toggle-on — only show when not already exempt. If already exempt (user previously granted it), no dialog.

### HyperOS/MIUI Autostart Deep-link (SETT-04)
- **D-16:** Detect Xiaomi device: `Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)` — covers Xiaomi, Redmi, POCO (all share Xiaomi as manufacturer)
- **D-17:** Show `clickablePreferenceItem` only when `isXiaomiDevice = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)` — hidden on non-Xiaomi
- **D-18:** Button string: `settings_background_updates_watchdog_hyperios_autostart` = `"Enable HyperOS/MIUI Autostart"`; summary: `settings_background_updates_watchdog_hyperios_autostart_summary` = `"Required on Xiaomi/Redmi/POCO devices to prevent the app from being killed"`
- **D-19:** Intent strategy (try in order, catch `ActivityNotFoundException` at each step):
  1. Primary: `Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")`
  2. Fallback: `Intent("miui.intent.action.APP_PERM_EDITOR")` with extra `"extra_pkgname"` = `context.packageName`
  3. Last resort: `SnackbarHelper.showSnackbar(context.getString(R.string.settings_background_updates_watchdog_hyperios_autostart_not_found))`
- **D-20:** Snackbar fallback string: `settings_background_updates_watchdog_hyperios_autostart_not_found` = `"Could not open Autostart settings. Go to Security app → Permissions → Autostart manually."`
- **D-21:** Item positioning: `isFirst = false`, `isLast = true` (follows toggle in Watchdog section)

### Clean Disable Path (DEGRADE-02)
- **D-22:** `WatchdogService.stop(context)` (already implemented in Phase 6 companion) calls `context.stopService()` — this triggers `onDestroy()` which:
  1. Cancels the AlarmManager PendingIntent
  2. Calls `stopForeground(true)` / `stopSelf()` — removes notification
- **D-23:** No additional cleanup needed in UI layer — service handles its own teardown

### Agent's Discretion
- `isXiaomiDevice` local val computed once inside the composable lambda (not a composable state)
- `@SuppressLint("BatteryLife")` on the battery opt intent block (existing pattern from Troubleshoot section)
- The `addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` on battery opt and autostart intents (required for starting activities from non-Activity context)
- `sectionHeaderItem` + `sectionFooterItem` wrapping (matches existing General and Troubleshoot sections)
- Use `largeSeparatorItem()` before and after Watchdog section (matches existing spacing)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Target Files to Modify
- `app/src/main/java/org/breezyweather/domain/settings/SettingsManager.kt` — Add `watchdogEnabled: Boolean` at line ~172 (after `ignoreUpdatesWhenBatteryLow`); follow exact property pattern from lines 167–173
- `app/src/main/java/org/breezyweather/ui/settings/compose/BackgroundUpdatesSettingsScreen.kt` — Add Watchdog section between `sectionFooterItem(R.string.settings_background_updates_section_general)` (line 232) and `largeSeparatorItem()` (line 234)
- `app/src/main/res/values/strings.xml` — Add ~7 new string resources for Watchdog UI

### Reference Files (read, do not modify)
- `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt` — `WatchdogService.start(context)` and `WatchdogService.stop(context)` companion methods implemented in Phase 6
- `app/src/main/java/org/breezyweather/ui/settings/preference/composables/SwitchPreference.kt` — `SwitchPreferenceView` signature; `withState`, `isFirst`, `isLast` params
- `app/src/main/java/org/breezyweather/ui/settings/preference/PreferenceItems.kt` — `switchPreferenceItem`, `clickablePreferenceItem`, `sectionHeaderItem`, `sectionFooterItem`, `largeSeparatorItem` DSL functions
- `app/src/main/java/org/breezyweather/common/extensions/PowerManagerExtensions.kt` (or similar) — `context.powerManager` extension used in existing battery opt check (line 244 of BackgroundUpdatesSettingsScreen)
- `app/src/main/java/org/breezyweather/ui/settings/activities/SettingsActivity.kt` — No changes needed; `watchdogEnabled` doesn't affect other settings' enabled state, so no new `mutableStateOf` is required in the Activity

### Existing String Resources to Reuse
- `R.string.settings_enabled` / `R.string.settings_disabled` — toggle summary ON/OFF
- `R.string.settings_background_updates_battery_optimization` — reuse in AlertDialog confirm button
- `android.R.string.cancel` — AlertDialog dismiss button

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Patterns
- **SettingsManager boolean property** (lines 167–173): `var ignoreUpdatesWhenBatteryLow: Boolean` — exact template for `watchdogEnabled`
- **SwitchPreferenceView** (lines 218–231 of BackgroundUpdatesSettingsScreen): `checked = SettingsManager.getInstance(context).ignoreUpdatesWhenBatteryLow` — no Activity-level mutableStateOf needed for self-contained toggle
- **AlertDialog pattern** (lines 139–215): `dialogNeverRefreshOpenState` + `LaunchedEffect` — template for battery opt dialog (simpler: no countdown timer needed)
- **Battery opt intent block** (lines 244–265): Exact pattern for launching `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `ActivityNotFoundException` catch
- **`PreferenceViewWithCard` clickable** (lines 238–265): Pattern for HyperOS autostart button

### Integration Points
- `BackgroundUpdatesSettingsScreen.kt` line 233–234: Insert new Watchdog section block after existing `sectionFooterItem(R.string.settings_background_updates_section_general)` and before existing `largeSeparatorItem()`
- `SettingsManager.kt` line ~172: Insert `watchdogEnabled` after `ignoreUpdatesWhenBatteryLow`
- `strings.xml`: Add 7 new string entries in the Background Updates settings string block

### Key Observation — No Activity State Needed
`updateInterval` is passed as a parameter from SettingsActivity because it affects the `enabled` state of the battery-low toggle. `watchdogEnabled` has no cross-preference dependencies, so it can be read inline: `checked = SettingsManager.getInstance(context).watchdogEnabled` — no new parameter on `BackgroundSettingsScreen`.

</code_context>

<specifics>
## Specific Implementation Notes

- **Toggle initial state from SettingsManager**: `SwitchPreferenceView` with `withState = true` (default) initializes its internal state from the `checked` parameter at first composition. Since BackgroundSettingsScreen is re-created on each navigation, the pref value will always be fresh from SettingsManager.
- **"Watchdog Mode" vs "Watchdog keepalive"**: Section header is "Watchdog Mode" (descriptive of the feature), toggle title is "Watchdog keepalive" (descriptive of what it does — matches the notification channel name used in Phase 6).
- **Device detection guard**: `val isXiaomiDevice = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)` — compute once outside the `if` block, reuse for `isLast = !isXiaomiDevice` on the toggle and conditional rendering of the autostart button.

</specifics>

<deferred>
## Deferred Ideas

- Samsung DeepSleep whitelist deep-link (ROM-V2-02) — deferred to v2
- HyperOS version-specific autostart guidance (ROM-V2-01) — deferred to v2
- "Watchdog Status" indicator showing whether service is currently running — nice-to-have, not required
- Watchdog heartbeat interval selector (WATCH-V2-03) — deferred to v2

</deferred>

---

*Phase: 07-settings-teardown*  
*Context gathered: 2026-04-02 — auto mode*
