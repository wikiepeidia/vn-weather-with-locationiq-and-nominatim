---
phase: 01
name: Data Model Foundation
status: passed
created: 2025-07-07
updated: 2025-07-07
---

# Phase 01: Data Model Foundation — Verification

## Goal

Nominatim's structured VN address fields (`suburb`, `hamlet`, `quarter`, `neighbourhood`) are mapped in `NominatimAddress` and consumed by the converter, so structured ward data is never silently discarded in favour of brittle `display_name` regex.

## Requirements Covered

| ID | Description | Status |
|----|-------------|--------|
| ADDR-03 | `NominatimAddress` has suburb/hamlet/quarter/neighbourhood fields | ✓ Verified |
| ADDR-04 | `convertLocation()` tries structured fields before display_name regex | ✓ Verified |

## Must-Haves

### ADDR-03: NominatimAddress structured fields

| Check | Result |
|-------|--------|
| `NominatimAddress` has `suburb: String?` field | ✓ line 27 |
| `NominatimAddress` has `hamlet: String?` field | ✓ line 28 |
| `NominatimAddress` has `quarter: String?` field | ✓ line 29 |
| `NominatimAddress` has `neighbourhood: String?` field | ✓ line 30 |
| None of the four new fields has `@SerialName` | ✓ JSON keys match property names exactly |
| Existing fields (`village`, `town`, `country_code`, ISO fields) unchanged | ✓ verified |
| File retains LGPL license header | ✓ verified |

### ADDR-04: convertLocation() structured-fields-first VN path

| Check | Result |
|-------|--------|
| `structuredCity = address?.suburb ?: address?.hamlet ?: address?.quarter` guard added | ✓ lines 199–201 |
| Non-null `structuredCity` sets `city` and nulls `district` | ✓ lines 202–204 |
| Existing `display_name` regex block preserved intact in `else` branch | ✓ lines 206–221 |
| `isLocationIQSource` fallback preserved | ✓ lines 214–219 |
| Non-VN code path (`countryCode != "vn"`) unchanged | ✓ verified |

## Compilation

- **Build:** `./gradlew :app:compileFreenetDebugKotlin` → **BUILD SUCCESSFUL** (1m 12s)
- **Errors:** 0
- **Warnings:** Pre-existing deprecation warnings only — none introduced by this phase

## Commit

- `02c92fed3` — `feat(addr): add Nominatim structured VN ward fields and prefer them in convertLocation (ADDR-03, ADDR-04)`

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt` | Added 4 nullable String? fields before `village` |
| `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` | Restructured VN block in `convertLocation()` to check structured fields first |

## Verdict: PASSED

All must-haves satisfied. Build clean. Non-VN behavior is unchanged. Phase 2 (Token Extraction & Cross-Validation) may proceed.
