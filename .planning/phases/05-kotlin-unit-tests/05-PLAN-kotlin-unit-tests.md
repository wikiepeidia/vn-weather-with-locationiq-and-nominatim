---
plan: 05-01
phase: 5
name: Kotlin Unit Tests
requirements: [TEST-01, TEST-02, TEST-03, TEST-04]
files_modified:
  - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt
  - app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt
objective: |
  Make VN address logic testable by promoting pure functions to the companion object as
  `internal`, then write JUnit 5 / kotest-assertions tests covering pickBestVietnamSubProvincePart,
  isCleanVnCity, mergeVnResults cross-validation, and NominatimAddress JSON deserialization.
---

# Plan 05-01: Kotlin Unit Tests

## Goal

All VN address parsing logic is covered by Kotlin JUnit tests that pass in `./gradlew test`.

## Requirements

- **TEST-01**: Tests for `pickBestVietnamSubProvincePart()` — POI-prefix dirty, clean Phường/Xã/Đặc khu, empty, null
- **TEST-02**: Tests for cross-validation merge logic (XVAL-01–03) with mock LIQ and Nominatim fixtures
- **TEST-03**: Tests for `NominatimAddress` structured field deserialization (suburb/hamlet/quarter) with real JSON
- **TEST-04**: All tests pass in `./gradlew test`

## Tasks

### Task 1 — Refactor NominatimService for testability

Move pure functions to companion object as `internal` so test files can call them:

1. Move `vnSubProvinceRegex` from instance field to companion object as `internal val`
2. Move `pickBestVietnamSubProvincePart` to companion object as `internal fun`
3. Move `isCleanVnCity` to companion object as `internal fun`
4. Add `mergeVnResults(liqInfo, nomList)` to companion object as `internal fun` (extracted from flatMap)
5. Update `requestNearestLocation` flatMap branch to call `mergeVnResults` + log conditionally

### Task 2 — Create NominatimServiceTest.kt

File: `app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt`

Tests:

- `pickBestVietnamSubProvincePart`: 7 cases (POI dirty, LIQ first, Nom last, Đặc khu, empty, no match)
- `isCleanVnCity`: 4 cases (valid, dirty, null, empty)
- `mergeVnResults`: 5 cases (Nom wins, LIQ fallback, both dirty, null LIQ, all empty)
- `NominatimAddress` deserialization: 5 cases (suburb, hamlet, quarter, ISO4, full VN result)

## Verification

- `./gradlew :app:testFreenetDebugUnitTest` succeeds
- All 20+ test cases pass
