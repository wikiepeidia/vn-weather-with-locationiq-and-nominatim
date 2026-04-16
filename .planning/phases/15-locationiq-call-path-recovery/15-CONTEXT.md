# Phase 15: LocationIQ Call-Path Recovery - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Restore actual LocationIQ reverse-geocoding invocation when a configured key is present, and add actionable diagnostics for skip/failure paths. Do not change VN regex/cross-validation behavior, and do not include save-time key validation UX in this phase.

</domain>

<decisions>
## Implementation Decisions

### LocationIQ Key Gate
- **D-01:** Keep strict key gate: only values starting with `pk.` are treated as LocationIQ keys.
- **D-02:** If key is missing or malformed, remain on Nominatim path and emit explicit skip-reason diagnostics.

### Diagnostics Scope
- **D-03:** Add debug diagnostics with reason category and endpoint type (`reverse`), while never logging key material.
- **D-04:** Failure diagnostics must classify at least: missing key, malformed key, request/network error, and non-success HTTP response.

### Phase 15 Scope Lock
- **D-05:** Apply call-path recovery to reverse geocoding in this phase; do not expand to location-search behavior yet.
- **D-06:** Add focused unit tests for call-path gating and diagnostics while preserving existing VN algorithm expectations.

### the agent's Discretion
- Exact log message string format and helper-function extraction.
- Test fixture coordinates until user-provided address corpus arrives.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and Constraints
- `.planning/ROADMAP.md` — Phase 15 goal, success criteria, and phase boundary.
- `.planning/REQUIREMENTS.md` — LOCIQ-01 and LOCIQ-03 requirement intent.
- `.planning/PROJECT.md` — algorithm freeze constraint and milestone context.

### Existing Call-Path Contracts
- `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` — key gating, reverse flow, fallback, and current logs.
- `app/src/main/java/org/breezyweather/sources/nominatim/NominatimApi.kt` — reverse endpoint contract and key query shape.
- `app/src/main/java/org/breezyweather/ui/settings/preference/composables/EditTextPreference.kt` — current save interaction behavior in settings dialog.

### Testing Baseline
- `app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt` — regression baseline for VN parsing behavior.
- `.planning/codebase/TESTING.md` — project test patterns and conventions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NominatimService.isLocationIqKey(...)` already centralizes key-shape detection.
- `requestNearestLocation(...)` already separates LocationIQ and Nominatim Retrofit clients and uses lazy fallback.
- `NominatimServiceTest` already covers VN token extraction/cross-validation helpers and can host new call-path tests.

### Established Patterns
- Sources layer uses RxJava `Observable` chains with `onErrorReturn` / `onErrorResumeNext` for fallback behavior.
- Source settings are persisted via `SourceConfigStore` and reflected through `EditTextPreference` callbacks.
- Debug diagnostics currently use `Log.d("NominatimService", ...)` and should remain consistent.

### Integration Points
- Reverse geocoding entry: `RefreshHelper.requestReverseGeocoding(...)`.
- Source resolution: `SourceManager.getReverseGeocodingSourceOrDefault(...)` and default reverse source mapping.
- Settings UI injection path: `WeatherSourcesSettingsScreen` rendering of `EditTextPreference` from `NominatimService.getPreferences(...)`.

</code_context>

<specifics>
## Specific Ideas

- User reports LocationIQ is effectively ignored (dashboard shows zero API calls) and wants this fixed as a bug, not a redesign.
- User explicitly requested stronger unit-test coverage to protect VN behavior while this bug fix is implemented.
- User will provide additional address samples later for broader validation.

</specifics>

<deferred>
## Deferred Ideas

- Save-time server key validation UX is Phase 16 scope.
- Extended real-address corpus ingestion (when user sends addresses) can be folded into later regression-focused work.

</deferred>

---

*Phase: 15-locationiq-call-path-recovery*
*Context gathered: 2026-04-16*
