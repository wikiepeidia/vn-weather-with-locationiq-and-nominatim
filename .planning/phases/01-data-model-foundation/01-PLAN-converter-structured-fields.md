---
phase: 01-data-model-foundation
plan: 02
type: execute
wave: 2
depends_on:
  - 01-PLAN-nominatim-address-fields
files_modified:
  - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt
autonomous: true
requirements:
  - ADDR-04
must_haves:
  truths:
    - "For VN locations, convertLocation() tries address.suburb ?: address.hamlet ?: address.quarter before display_name regex"
    - "If a structured field is non-null, it is used as city and the display_name regex block is skipped"
    - "If all structured fields are null, the existing display_name regex path runs as before"
    - "Non-VN location behavior is completely unchanged"
  artifacts:
    - path: "app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt"
      provides: "Updated convertLocation() with structured-field-first VN path"
      contains: "address.suburb ?: locationResult.address.hamlet ?: locationResult.address.quarter"
  key_links:
    - from: "convertLocation() VN block"
      to: "NominatimAddress.suburb/hamlet/quarter"
      via: "locationResult.address.suburb ?: locationResult.address.hamlet ?: locationResult.address.quarter"
      pattern: "address\\.suburb"
---

<objective>
Update `convertLocation()` in `NominatimService.kt` so that for Vietnamese locations (`countryCode == "vn"`), the three new structured address fields (`suburb`, `hamlet`, `quarter`) from `NominatimAddress` are checked first. Only if all three are null does the code fall through to the existing `display_name` regex logic.

Purpose: Eliminates brittle regex as the first-resort VN ward resolver when Nominatim provides clean structured data directly.
Output: `NominatimService.kt` with a restructured VN address block inside `convertLocation()`.
</objective>

<execution_context>
@~/.copilot/get-shit-done/workflows/execute-plan.md
@~/.copilot/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md

<interfaces>
<!-- Key contracts the executor must know before editing NominatimService.kt -->

**Current convertLocation() VN block (lines ~194–220):**

```kotlin
// Vietnam Special Parsing
var city = locationResult.address.town ?: locationResult.name
var district = locationResult.address.village

if (countryCode.equals("vn", ignoreCase = true)) {
    // Try to extract Xa/Phuong/Dac Khu from display_name
    val displayName = locationResult.displayName

    if (!displayName.isNullOrEmpty()) {
        // Split display_name by standard/full-width comma and pick the last valid VN component.
        val parts = displayName.split(COMMA_SPLIT_REGEX).map { it.trim() }
        val cleanPart = pickBestVietnamSubProvincePart(parts)

        if (cleanPart != null) {
            city = cleanPart
            district = null // Hide district if we found a better name
        } else if (isLocationIQSource) {
            // Fallback logic for LocationIQ if regex fails: use first part of display_name
            val fallback = parts.firstOrNull()?.trim()
            if (fallback != null) {
                city = fallback
                district = null
            }
        }
    }
}
```

**Target state after this plan's change:**

```kotlin
// Vietnam Special Parsing
var city = locationResult.address.town ?: locationResult.name
var district = locationResult.address.village

if (countryCode.equals("vn", ignoreCase = true)) {
    // 1. Prefer Nominatim's structured fields (ADDR-04) — fastest, cleanest path
    val structuredCity = locationResult.address?.suburb
        ?: locationResult.address?.hamlet
        ?: locationResult.address?.quarter
    if (structuredCity != null) {
        city = structuredCity
        district = null
    } else {
        // 2. Fall through to display_name regex when no structured field available
        val displayName = locationResult.displayName
        if (!displayName.isNullOrEmpty()) {
            val parts = displayName.split(COMMA_SPLIT_REGEX).map { it.trim() }
            val cleanPart = pickBestVietnamSubProvincePart(parts)

            if (cleanPart != null) {
                city = cleanPart
                district = null
            } else if (isLocationIQSource) {
                // Fallback for LocationIQ when regex also fails: use first display_name segment
                val fallback = parts.firstOrNull()?.trim()
                if (fallback != null) {
                    city = fallback
                    district = null
                }
            }
        }
    }
}
```

**Critical constraints:**

- The `if (countryCode.equals("vn", ignoreCase = true))` gate must stay as-is — non-VN paths must not be touched
- `address` is nullable in `NominatimLocationResult` → use safe-call `?.suburb`, `?.hamlet`, `?.quarter`
- The existing `display_name` regex block (including `pickBestVietnamSubProvincePart` and `isLocationIQSource` fallback) must remain fully intact inside the `else` branch — this is unchanged from its current form
- No other method or property in NominatimService.kt should be modified
</interfaces>

</context>

<tasks>

<task type="auto">
  <name>Task 1: Update convertLocation() VN block to prefer structured fields over display_name regex</name>
  <files>app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt</files>

  <read_first>
    - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt — read the FULL file first to locate the exact current VN block text before any edit
    - app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt — confirm suburb/hamlet/quarter fields were added by Plan 01 before proceeding
  </read_first>

  <action>
Inside `convertLocation()`, replace ONLY the `if (countryCode.equals("vn", ignoreCase = true)) { ... }` block with the updated version below. Everything else in the method (the `var city`, `var district` declarations above; the `LocationAddressInfo(...)` constructor call below; all other methods) must remain untouched.

**Replace the current VN block:**

```kotlin
            if (countryCode.equals("vn", ignoreCase = true)) {
                // Try to extract Xa/Phuong/Dac Khu from display_name
                val displayName = locationResult.displayName

                if (!displayName.isNullOrEmpty()) {
                    // Split display_name by standard/full-width comma and pick the last valid VN component.
                    val parts = displayName.split(COMMA_SPLIT_REGEX).map { it.trim() }
                    val cleanPart = pickBestVietnamSubProvincePart(parts)

                    if (cleanPart != null) {
                        city = cleanPart
                        district = null // Hide district if we found a better name
                    } else if (isLocationIQSource) {
                        // Fallback logic for LocationIQ if regex fails: use first part of display_name
                        val fallback = parts.firstOrNull()?.trim()
                        if (fallback != null) {
                            city = fallback
                            district = null
                        }
                    } 
                }
            }
```

**With this exact replacement:**

```kotlin
            if (countryCode.equals("vn", ignoreCase = true)) {
                // 1. Prefer Nominatim's structured fields (ADDR-04) — fastest, cleanest path
                val structuredCity = locationResult.address?.suburb
                    ?: locationResult.address?.hamlet
                    ?: locationResult.address?.quarter
                if (structuredCity != null) {
                    city = structuredCity
                    district = null
                } else {
                    // 2. Fall through to display_name regex when no structured field available
                    val displayName = locationResult.displayName
                    if (!displayName.isNullOrEmpty()) {
                        // Split display_name by standard/full-width comma and pick the last valid VN component.
                        val parts = displayName.split(COMMA_SPLIT_REGEX).map { it.trim() }
                        val cleanPart = pickBestVietnamSubProvincePart(parts)

                        if (cleanPart != null) {
                            city = cleanPart
                            district = null // Hide district if we found a better name
                        } else if (isLocationIQSource) {
                            // Fallback for LocationIQ when regex also fails: use first display_name segment
                            val fallback = parts.firstOrNull()?.trim()
                            if (fallback != null) {
                                city = fallback
                                district = null
                            }
                        }
                    }
                }
            }
```

Indentation: 12 spaces inside the outer `if` (matching the existing file style — 4 spaces per level, body is at 3 levels deep: class body → method body → if body).
  </action>

  <verify>
    <automated>grep -n "structuredCity\|address?.suburb\|address?.hamlet\|address?.quarter" "app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt"</automated>
  </verify>

  <acceptance_criteria>
    - `grep "structuredCity"` returns 2 matches in NominatimService.kt (the `val structuredCity =` assignment and the `if (structuredCity != null)` check)
    - `grep "address?.suburb"` returns at least 1 match
    - `grep "address?.hamlet"` returns at least 1 match  
    - `grep "address?.quarter"` returns at least 1 match
    - `grep "pickBestVietnamSubProvincePart"` still returns a match (existing regex path preserved inside else branch)
    - `grep "isLocationIQSource"` still returns a match (LocationIQ fallback preserved inside else branch)
    - `grep "countryCode.equals(\"vn\""` still returns exactly 1 match (VN gate unchanged)
    - `grep -c "countryCode.equals" "app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt"` returns 1 — only one VN gate, no accidental duplication
    - No changes outside the VN `if` block: `grep "address.town"` still returns its original line, `grep "address.village"` still returns its original line
  </acceptance_criteria>

  <done>convertLocation() for VN locations first checks address.suburb ?: address.hamlet ?: address.quarter; uses it directly if non-null; falls through to existing display_name regex logic if all three are null. All non-VN code paths are bytewise identical to before.</done>
</task>

</tasks>

<verification>
After task completion, verify the overall structure manually by reading lines ~185–230 of NominatimService.kt:
1. VN block opens with `// 1. Prefer Nominatim's structured fields (ADDR-04)`
2. `structuredCity` is assigned via null-coalescing chain
3. `else` branch contains the original display_name regex logic intact
4. `LocationAddressInfo(...)` constructor call below the VN block is unchanged
5. No other method in the file was modified
</verification>

<success_criteria>

1. For a VN location where Nominatim returns `"suburb": "Phường Hoàn Kiếm"`, `convertLocation()` sets `city = "Phường Hoàn Kiếm"` without touching display_name at all.
2. For a VN location where all of suburb/hamlet/quarter are null, the existing display_name regex logic runs exactly as before.
3. For any non-VN location (e.g. `countryCode == "us"`), the code path is byte-for-byte identical to the pre-change behavior.
</success_criteria>

<output>
After completion, create `.planning/phases/01-data-model-foundation/01-02-SUMMARY.md`
</output>
