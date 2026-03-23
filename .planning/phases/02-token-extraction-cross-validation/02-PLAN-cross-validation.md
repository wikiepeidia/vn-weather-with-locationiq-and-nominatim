---
phase: 02-token-extraction-cross-validation
plan: 02
type: execute
wave: 2
depends_on:
  - 02-PLAN-fix-pick-best-order
files_modified:
  - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt
autonomous: true
requirements:
  - XVAL-01
  - XVAL-02
  - XVAL-03
must_haves:
  truths:
    - "requestNearestLocation zip block for VN applies vnSubProvinceRegex to both LIQ and NOM city values"
    - "If LIQ city is clean → use liqList (LocationIQ priority)"
    - "If LIQ city is dirty AND NOM city is clean → use nomList (Nominatim rescue)"
    - "If both dirty (or NOM absent) → use liqList as last resort"
    - "Non-VN zip logic is unchanged: liqInfo != null → liqList; else nomList; else throw"
    - "isCleanVnCity() is a private helper that runs vnSubProvinceRegex against a city string"
  artifacts:
    - path: "app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt"
      provides: "Cross-validation logic in requestNearestLocation + isCleanVnCity helper"
      contains: "fun isCleanVnCity(city: String?): Boolean"
---

<objective>
Implement real cross-validation in `requestNearestLocation`'s `Observable.zip` block. When both LocationIQ and Nominatim results are available for a VN location, compare the city tokens against `vnSubProvinceRegex` — the cleanest result wins. LocationIQ always takes priority when clean. Nominatim rescues when LocationIQ is dirty. Both-dirty falls back to LocationIQ (last resort). Non-VN behavior is unchanged.

**Priority ladder (for VN):**

1. LocationIQ clean → use LocationIQ
2. LocationIQ dirty + Nominatim clean → use Nominatim (rescue)
3. Both dirty OR Nominatim absent → use LocationIQ (last resort)
4. LocationIQ absent + Nominatim present → use Nominatim (graceful degrade)
5. Both absent → throw InvalidLocationException
</objective>

<context>
**Current zip block (MUST be replaced for VN):**
```kotlin
return Observable.zip(locationIQObs, nominatimObs) { liqList, nomList ->
    val liqInfo = liqList.firstOrNull()
    val nomInfo = nomList.firstOrNull()

    // Strict source priority:
    // 1) LocationIQ if available
    // 2) Nominatim when LocationIQ is absent/failed
    if (liqInfo != null) liqList
    else if (nomInfo != null) nomList
    else throw InvalidLocationException()
}

```

**Target zip block:**
```kotlin
return Observable.zip(locationIQObs, nominatimObs) { liqList, nomList ->
    val liqInfo = liqList.firstOrNull()
    val nomInfo = nomList.firstOrNull()

    // VN cross-validation: prefer whichever result has a clean ward/commune token
    val isVNContext = liqInfo?.countryCode?.equals("vn", ignoreCase = true) == true
        || nomInfo?.countryCode?.equals("vn", ignoreCase = true) == true

    if (isVNContext) {
        val liqClean = isCleanVnCity(liqInfo?.city)
        val nomClean = isCleanVnCity(nomInfo?.city)
        when {
            liqClean          -> liqList                   // LIQ clean — always prefer LocationIQ
            nomClean          -> nomList                   // LIQ dirty, NOM clean — Nominatim rescues
            liqInfo != null   -> liqList                   // Both dirty — LIQ as last resort
            nomInfo != null   -> nomList                   // Only NOM available
            else              -> throw InvalidLocationException()
        }
    } else {
        // Non-VN: preserve existing priority — LocationIQ > Nominatim
        if (liqInfo != null) liqList
        else if (nomInfo != null) nomList
        else throw InvalidLocationException()
    }
}
```

**New private helper to add (near pickBestVietnamSubProvincePart):**

```kotlin
private fun isCleanVnCity(city: String?): Boolean {
    if (city.isNullOrEmpty()) return false
    return vnSubProvinceRegex.matcher(city).matches()
}
```

</context>

<tasks>

<task type="auto">
  <name>Task 1: Add isCleanVnCity helper</name>
  <files>app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt</files>

  <read_first>
    - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt — read FULL file first; confirm Plan 01 changes are present
  </read_first>

  <action>
Add `isCleanVnCity(city: String?): Boolean` immediately after `pickBestVietnamSubProvincePart`.

```kotlin
private fun isCleanVnCity(city: String?): Boolean {
    if (city.isNullOrEmpty()) return false
    return vnSubProvinceRegex.matcher(city).matches()
}
```

  </action>
</task>

<task type="auto">
  <name>Task 2: Replace zip block with VN cross-validation logic</name>
  <files>app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt</files>

  <action>
Replace the entire `Observable.zip` block in `requestNearestLocation` with the VN-aware version. The non-VN fallback path at the bottom must be identical to the current logic (no behaviour change for non-VN).

Replace:

```kotlin
            return Observable.zip(locationIQObs, nominatimObs) { liqList, nomList ->
                val liqInfo = liqList.firstOrNull()
                val nomInfo = nomList.firstOrNull()

                // Strict source priority:
                // 1) LocationIQ if available
                // 2) Nominatim when LocationIQ is absent/failed
                if (liqInfo != null) liqList
                else if (nomInfo != null) nomList
                else throw InvalidLocationException()
            }
```

With:

```kotlin
            return Observable.zip(locationIQObs, nominatimObs) { liqList, nomList ->
                val liqInfo = liqList.firstOrNull()
                val nomInfo = nomList.firstOrNull()

                // VN cross-validation: prefer whichever result has a clean ward/commune token
                val isVNContext = liqInfo?.countryCode?.equals("vn", ignoreCase = true) == true
                    || nomInfo?.countryCode?.equals("vn", ignoreCase = true) == true

                if (isVNContext) {
                    val liqClean = isCleanVnCity(liqInfo?.city)
                    val nomClean = isCleanVnCity(nomInfo?.city)
                    when {
                        liqClean        -> liqList                   // LIQ clean — always prefer LocationIQ
                        nomClean        -> nomList                   // LIQ dirty, NOM clean — Nominatim rescues
                        liqInfo != null -> liqList                   // Both dirty — LIQ as last resort
                        nomInfo != null -> nomList                   // Only NOM available
                        else            -> throw InvalidLocationException()
                    }
                } else {
                    // Non-VN: preserve existing priority — LocationIQ > Nominatim
                    if (liqInfo != null) liqList
                    else if (nomInfo != null) nomList
                    else throw InvalidLocationException()
                }
            }
```

  </action>

  <verify>
    <automated>grep -n "isVNContext\|isCleanVnCity\|liqClean\|nomClean\|Nominatim rescues\|last resort" "app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt"</automated>
  </verify>

  <acceptance_criteria>
    - `isVNContext` check present in zip block
    - `isCleanVnCity` called for both liqInfo and nomInfo
    - `liqClean -> liqList` branch is first (LocationIQ priority)
    - `nomClean -> nomList` rescue branch present
    - `liqInfo != null -> liqList` last-resort branch present
    - Non-VN `else` path at bottom is unchanged
    - `isCleanVnCity` private helper method exists in the file
  </acceptance_criteria>

  <done>Cross-validation implemented: LIQ takes priority when clean; NOM rescues when LIQ dirty; LIQ last resort when both dirty.</done>
</task>

</tasks>
