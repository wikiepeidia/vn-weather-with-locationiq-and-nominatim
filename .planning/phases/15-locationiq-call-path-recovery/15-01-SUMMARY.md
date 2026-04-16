---
phase: 15-locationiq-call-path-recovery
plan: 01
status: completed
completed_at: 2026-04-16
requirements:
  - LOCIQ-01
  - LOCIQ-03
files_modified:
  - app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt
  - app/src/test/java/org/breezyweather/sources/nominatim/NominatimServiceTest.kt
verification:
  - ./gradlew :app:testFreenetDebugUnitTest --tests org.breezyweather.sources.nominatim.NominatimServiceTest
---

# 15-01 Summary

## Objective

Recover LocationIQ reverse call-path reliability and diagnostics without changing VN parsing/cross-validation behavior.

## Implemented

- Hardened LocationIQ endpoint normalization to safely accept full pasted reverse URLs (including query params) and canonicalize them to a Retrofit-safe base URL ending in `/v1/`.
- Prevented malformed base URL construction that could produce 404s when users paste full links like `/v1/reverse?...` into endpoint settings.
- Added regression tests for full `reverse` and `reverse.php` URL canonicalization.
- Fixed test references for companion-scoped `LocationIqKeyState` enum.

## Validation

- Confirmed endpoint behavior externally: both `https://us1.locationiq.com/v1/reverse` and `https://eu1.locationiq.com/v1/reverse` return HTTP 200 with valid query parameters.
- Executed targeted unit suite successfully:
  - `./gradlew :app:testFreenetDebugUnitTest --tests org.breezyweather.sources.nominatim.NominatimServiceTest`

## Notes

- VN regex/token extraction and merge logic were not modified.
- No API key material was added to logs or source.
