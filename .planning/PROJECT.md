# Breezy Weather VN — Vietnamese Address Quality Fork

## What This Is

A fork of the Breezy Weather Android app optimized for Vietnamese administrative addresses (wards, communes, special zones). It uses LocationIQ and Nominatim APIs in tandem to deliver clean, accurate sub-province address names (e.g. "Phường Phú Lương" instead of "Ủy ban nhân dân Phường Phú Lương") for Vietnamese users, with smart cross-validation, lazy fallback, and "giggles" — delightful feedback when the fallback saves a bad result.

## Core Value

Vietnamese users see a clean ward/commune name — never a POI or government-office prefix — even when LocationIQ or Nominatim individually return garbage.

## Requirements

### Validated

- ✓ LocationIQ reverse geocoding integrated as primary source for VN — existing
- ✓ Nominatim reverse geocoding integrated as concurrent fallback — existing  
- ✓ `Observable.zip` parallel dual-API strategy for VN country code — existing
- ✓ `vnSubProvinceRegex` regex extracts Phường/Xã/Đặc khu tokens — existing
- ✓ `pickBestVietnamSubProvincePart()` selects best match from `display_name` — existing
- ✓ User-configurable LocationIQ API key in settings — existing
- ✓ User-configurable Nominatim instance URL — existing
- ✓ Python test harness (`testaddress.py`) for regex validation — existing

### Active

- [ ] Clean VN address — no POI/government-office prefix shown as city name
- [ ] Real cross-validation — prefer whichever API returns a clean VN token
- [ ] Lazy Nominatim — only fire Nominatim when LocationIQ result fails regex check
- [ ] `NominatimAddress` maps `suburb`, `hamlet`, `quarter` fields for VN structured data
- [ ] Correct token ordering — use `firstOrNull` for LocationIQ, `lastOrNull` for Nominatim
- [ ] Fresh API client per call — eliminate stale `mApi` after config change
- [ ] Nominatim error logging — debug log which source failed and why
- [ ] LocationIQ endpoint configurable or defaulted to Asia region
- [ ] "Giggles" — in-app feedback (Snackbar/debug log) when Nominatim rescues a bad LocationIQ result
- [ ] Kotlin unit tests for all VN address parsing logic integrated into Gradle test suite

### Out of Scope

- RxJava → Coroutines full migration — large refactor, orthogonal to VN address quality; defer
- Nominatim rate limiter (Semaphore) — lazy fallback strategy already eliminates concurrent traffic
- `UpdateStrategy` commenting — unrelated to this feature area
- `ConfigStore` → DataStore migration — orthogonal; defer
- Astro/timezone bug fixes — unrelated to VN address
- `Release.getDownloadLink` fix — unrelated to VN address
- EncryptedSharedPreferences for API key — good idea but out of scope for this focus milestone

## Context

- **Forked from:** Breezy Weather (open source Android weather app)
- **Target users:** Vietnamese Android users; device locale = `vn`
- **The core problem:** Vietnamese administrative address reorganization effective July 1, 2025 creates mismatches between what APIs return and what users expect (clean ward names). LocationIQ zoom=18 often returns POI-prefixed strings. Nominatim returns structured data under `suburb` which isn't mapped.
- **Architecture:** Clean Architecture + MVVM, multi-module (app/data/domain). RxJava3 HTTP stack. Key file: `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt`.
- **Test harness:** `signature/testaddress.py` exercises regex logic in Python; needs a parallel Kotlin JUnit test suite.
- **"Giggles":** When cross-validation detects that Nominatim rescued a bad LocationIQ result, show a brief Snackbar message or debug toast so developers and users notice the system working as intended.

## Constraints

- **Compatibility:** Must not break non-VN locations; all VN-specific logic gated on `countryCode == "vn"` checks
- **API ToS:** Nominatim public instance — 1 req/sec policy; lazy fallback strategy must not exceed this
- **Kotlin:** All new code in Kotlin; no new Java
- **No breaking changes:** Existing API surface of `NominatimService` must remain stable for other callers

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Lazy Nominatim (conditional, not always-parallel) | Eliminates rate limit risk; faster happy path; Nominatim only fires when LocationIQ regex fails | — Pending |
| Cross-validation: prefer clean regex match over API priority | The "correct" result is whichever has a clean VN token, not which API responded first | — Pending |
| `firstOrNull` for LocationIQ, `lastOrNull` for Nominatim | LocationIQ zoom=18 has ward as FIRST segment; Nominatim zoom=13 has it LAST | — Pending |
| Add `suburb`/`hamlet` to `NominatimAddress` | Nominatim returns VN ward under `suburb` key; currently unmapped, causing regex fallback brittleness | — Pending |
| Skip RxJava migration | High-risk refactor; independent of address quality goal | ✓ Accepted |

---
*Last updated: 2026-03-23 after initialization*
