---
phase: 01-data-model-foundation
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt
autonomous: true
requirements:
  - ADDR-03
must_haves:
  truths:
    - "NominatimAddress data class has suburb, hamlet, quarter, and neighbourhood fields"
    - "All four fields are nullable String? and deserialize correctly from Nominatim JSON"
    - "Existing fields (village, town, municipality, county, state, country, countryCode, ISO fields) are unchanged"
  artifacts:
    - path: "app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt"
      provides: "NominatimAddress data class with all required structured address fields"
      contains: "suburb: String?, hamlet: String?, quarter: String?, neighbourhood: String?"
  key_links:
    - from: "NominatimAddress.kt"
      to: "NominatimService.kt"
      via: "locationResult.address.suburb / hamlet / quarter"
      pattern: "address\\.suburb|address\\.hamlet|address\\.quarter"
---

<objective>
Add the four Nominatim structured address fields that carry Vietnamese ward/commune data to the `NominatimAddress` data class. These fields (`suburb`, `hamlet`, `quarter`, `neighbourhood`) are returned by Nominatim for VN locations but currently unmapped — meaning Nominatim's structured ward data is silently discarded.

Purpose: Make Nominatim's structured VN ward data available to the converter (Plan 02) so it can be preferred over brittle `display_name` regex.
Output: `NominatimAddress.kt` with four new nullable `String?` fields appended to the existing data class.
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
<!-- Current NominatimAddress.kt — executor MUST understand the existing structure before editing. -->

```kotlin
// File: app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt
// Uses kotlinx.serialization — @SerialName("json_key") only needed when JSON key differs from Kotlin property name.
// Fields without @SerialName → JSON key matches property name exactly.

@Serializable
data class NominatimAddress(
    val village: String?,           // JSON: "village"
    val town: String?,              // JSON: "town"
    val municipality: String?,      // JSON: "municipality"
    val county: String?,            // JSON: "county"
    val state: String?,             // JSON: "state"
    val country: String?,           // JSON: "country"
    @SerialName("country_code") val countryCode: String?,    // JSON: "country_code"
    @SerialName("ISO3166-2-lvl3") val isoLvl3: String?,
    @SerialName("ISO3166-2-lvl4") val isoLvl4: String?,
    @SerialName("ISO3166-2-lvl5") val isoLvl5: String?,
    @SerialName("ISO3166-2-lvl6") val isoLvl6: String?,
    @SerialName("ISO3166-2-lvl8") val isoLvl8: String?,
    @SerialName("ISO3166-2-lvl15") val isoLvl15: String?,
)
```

**Key fact:** `suburb`, `hamlet`, `quarter`, `neighbourhood` are all lowercase in Nominatim JSON, matching the Kotlin property names exactly → NO `@SerialName` annotation required for these four fields.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add suburb, hamlet, quarter, neighbourhood fields to NominatimAddress</name>
  <files>app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt</files>

  <read_first>
    - app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt — read the FULL current content first; do not edit from memory
  </read_first>

  <action>
Append four new nullable `String?` fields to `NominatimAddress` data class, positioned BEFORE the existing `village` field (so that most-specific VN fields appear at the top of the property list). No `@SerialName` annotation is needed because each JSON key matches the Kotlin property name exactly.

The four fields to add, in this exact order, inserted as the FIRST four parameters of the data class (before `village`):

```kotlin
val suburb: String?,        // JSON: "suburb"  — Nominatim VN ward/commune (e.g. "Phường Hoàn Kiếm")
val hamlet: String?,        // JSON: "hamlet"  — Nominatim VN hamlet
val quarter: String?,       // JSON: "quarter" — Nominatim VN sub-district
val neighbourhood: String?, // JSON: "neighbourhood" — Nominatim neighbourhood
```

The file MUST still open with the LGPL license header block comment, then the package declaration, then the kotlinx serialization imports, then the existing KDoc comment, then the `@Serializable` annotation, then the updated data class.

All 13 existing fields must remain exactly as they were — only the four new fields are added.
  </action>

  <verify>
    <automated>grep -n "suburb\|hamlet\|quarter\|neighbourhood" "app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt"</automated>
  </verify>

  <acceptance_criteria>
    - `grep "val suburb: String?"` returns a match in NominatimAddress.kt
    - `grep "val hamlet: String?"` returns a match
    - `grep "val quarter: String?"` returns a match
    - `grep "val neighbourhood: String?"` returns a match
    - None of the four new fields has a `@SerialName` annotation (JSON keys match property names — no annotation needed)
    - `grep "val village: String?"` still returns a match (existing fields not removed)
    - `grep "@SerialName(\"country_code\")"` still returns a match (existing annotated fields untouched)
    - File still starts with the LGPL license header (`grep -c "GNU Lesser General Public License"` returns ≥ 1)
  </acceptance_criteria>

  <done>NominatimAddress has all four new fields as nullable String?, positioned before village, with no @SerialName annotations on the new fields, and all existing fields intact.</done>
</task>

</tasks>

<verification>
After task completion:
1. `grep -n "suburb\|hamlet\|quarter\|neighbourhood" app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt` — must show 4 matches
2. `grep -c "val village: String?" app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt` — must return 1 (existing field preserved)
3. Confirm the data class parameter count increased from 13 to 17
</verification>

<success_criteria>
`NominatimAddress` data class has 17 fields total: `suburb`, `hamlet`, `quarter`, `neighbourhood` (new, nullable, no @SerialName) plus the original 13 fields unchanged. Any Nominatim JSON response containing a `"suburb"`, `"hamlet"`, `"quarter"`, or `"neighbourhood"` key will now deserialize into the data class correctly.
</success_criteria>

<output>
After completion, create `.planning/phases/01-data-model-foundation/01-01-SUMMARY.md`
</output>
