---
phase: 02-token-extraction-cross-validation
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt
autonomous: true
requirements:
  - ADDR-01
  - ADDR-02
  - XVAL-03
must_haves:
  truths:
    - "pickBestVietnamSubProvincePart() accepts isLocationIQSource: Boolean and uses firstOrNull for LocationIQ, lastOrNull for Nominatim"
    - "The raw parts.firstOrNull()?.trim() fallback block (inside convertLocation for isLocationIQSource) is removed"
    - "When LocationIQ regex fails and structured fields are null, city stays as address.town ?: locationResult.name (no raw assignment)"
    - "Non-VN behavior is unchanged"
  artifacts:
    - path: "app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt"
      provides: "Updated pickBestVietnamSubProvincePart with source-aware firstOrNull/lastOrNull"
      contains: "fun pickBestVietnamSubProvincePart(parts: List<String>, isLocationIQSource: Boolean)"
---

<objective>
Fix `pickBestVietnamSubProvincePart()` to use source-aware ordering: `firstOrNull` for LocationIQ results (ward is earliest at zoom=18), `lastOrNull` for Nominatim results (ward is last at zoom=13). Also remove the raw `parts.firstOrNull()?.trim()` dirty fallback from `convertLocation` so dirty LocationIQ results are not silently accepted and instead surface to the cross-validation layer (Plan 02).
</objective>

<context>
**Current pickBestVietnamSubProvincePart:**
```kotlin
private fun pickBestVietnamSubProvincePart(parts: List<String>): String? {
    return parts.lastOrNull { part ->
        vnSubProvinceRegex.matcher(part).matches()
    }
}
```

**Current convertLocation VN block (after Phase 1) — the raw fallback to remove:**

```kotlin
} else if (isLocationIQSource) {
    // Fallback for LocationIQ when regex also fails: use first display_name segment
    val fallback = parts.firstOrNull()?.trim()
    if (fallback != null) {
        city = fallback
        district = null
    }
}
```

**Target logic:**

- `pickBestVietnamSubProvincePart(parts, isLocationIQSource = true)` → `parts.firstOrNull { ... }`
- `pickBestVietnamSubProvincePart(parts, isLocationIQSource = false)` → `parts.lastOrNull { ... }`
- Remove the `else if (isLocationIQSource)` raw fallback entirely from `convertLocation`
- When regex fails for LocationIQ and no raw fallback, the VN if-block exits without changing `city` or `district` → city stays as `address.town ?: locationResult.name`
</context>

<tasks>

<task type="auto">
  <name>Task 1: Update pickBestVietnamSubProvincePart signature and body</name>
  <files>app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt</files>

  <read_first>
    - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt — read FULL file before editing
  </read_first>

  <action>
1. Change `pickBestVietnamSubProvincePart` signature to accept `isLocationIQSource: Boolean` parameter.
2. Use `firstOrNull` when `isLocationIQSource = true`, `lastOrNull` when `isLocationIQSource = false`.
3. Update the single call site inside `convertLocation` to pass `isLocationIQSource` through.
4. Remove the `else if (isLocationIQSource) { val fallback = parts.firstOrNull()?.trim() ... }` block from `convertLocation`.

**New pickBestVietnamSubProvincePart:**

```kotlin
private fun pickBestVietnamSubProvincePart(parts: List<String>, isLocationIQSource: Boolean): String? {
    return if (isLocationIQSource) {
        // LocationIQ zoom=18: ward/commune is the FIRST matching segment
        parts.firstOrNull { part -> vnSubProvinceRegex.matcher(part).matches() }
    } else {
        // Nominatim zoom=13: ward/commune is the LAST matching segment
        parts.lastOrNull { part -> vnSubProvinceRegex.matcher(part).matches() }
    }
}
```

**Updated call site in convertLocation:**

```kotlin
val cleanPart = pickBestVietnamSubProvincePart(parts, isLocationIQSource)
```

**Remove entirely from convertLocation:**

```kotlin
} else if (isLocationIQSource) {
    // Fallback for LocationIQ when regex also fails: use first display_name segment
    val fallback = parts.firstOrNull()?.trim()
    if (fallback != null) {
        city = fallback
        district = null
    }
}
```

After removal, when `cleanPart == null` and the `else if` block is gone, the inner `if` block simply does nothing for a dirty LocationIQ result. The city stays as `address.town ?: locationResult.name` and the cross-validation plan (Plan 02) rescues it.
  </action>

  <verify>
    <automated>grep -n "pickBestVietnamSubProvincePart\|firstOrNull\|lastOrNull\|Fallback for LocationIQ" "app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt"</automated>
  </verify>

  <acceptance_criteria>
    - `pickBestVietnamSubProvincePart` has `isLocationIQSource: Boolean` parameter
    - Body uses `firstOrNull` when `isLocationIQSource = true`
    - Body uses `lastOrNull` when `isLocationIQSource = false`
    - Call site passes correct `isLocationIQSource` value
    - The raw `parts.firstOrNull()?.trim()` fallback block is gone from `convertLocation`
    - No `else if (isLocationIQSource)` block remains in the VN display_name section
  </acceptance_criteria>

  <done>pickBestVietnamSubProvincePart is source-aware; raw dirty fallback is removed from convertLocation.</done>
</task>

</tasks>
