---
plan: 04-01
phase: 4
name: Giggles Feedback & Settings
requirements: [GIGL-01, GIGL-02]
files_modified:
  - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt
objective: |
  Add [GiggleRescue] debug log when Nominatim supersedes a dirty LocationIQ result,
  and update the Settings EditTextPreference summary to display a playful description
  of the Nominatim fallback system so users and devs see when it fires.
---

# Plan 04-01: Giggles Feedback & Settings

## Goal
Developers and users receive visible confirmation when the fallback system catches a bad
result, and the Settings screen explains the fallback toggle in a human, playful way.

## Requirements
- **GIGL-01**: When Nominatim rescues a dirty LIQ result, emit:
  `[GiggleRescue] Nominatim rescued address for [lat,lon]: was 'dirty', now 'clean'`
- **GIGL-02**: Settings screen description shows playful text e.g.
  `"Backup address detective (fires when LocationIQ returns garbage)"`

## Tasks

### Task 1 — GIGL-01: Add [GiggleRescue] log on rescue

**File**: `NominatimService.kt`  
**Location**: `requestNearestLocation` → `flatMap` block → `nominatimFetch.map` →
  `isCleanVnCity(nomInfo?.city) -> nomList` branch

Change the rescue branch from a single expression to a block that logs before returning:
```kotlin
isCleanVnCity(nomInfo?.city) -> {
    Log.d("NominatimService", "[GiggleRescue] Nominatim rescued address for " +
        "[$latitude,$longitude]: was '${liqInfo?.city}', now '${nomInfo?.city}'")
    nomList
}
```

### Task 2 — GIGL-02: Playful summary in getPreferences()

**File**: `NominatimService.kt`  
**Location**: `getPreferences()` → `EditTextPreference` `summary` lambda

Update the `summary` lambda to include playful Nominatim fallback description:
```kotlin
summary = { _, content ->
    when {
        isLocationIqKey(content) ->
            "LocationIQ \u2022 Backup address detective on standby"
        content.isEmpty() ->
            "Backup address detective (fires when LocationIQ returns garbage)"
        else -> content
    }
}
```

## Verification
- Build succeeds: `./gradlew :app:compileFreenetDebugKotlin`
- `[GiggleRescue]` log line is present in compiled output (grep source)
- Settings summary logic covers all 3 branches (LIQ key, empty, custom URL)
