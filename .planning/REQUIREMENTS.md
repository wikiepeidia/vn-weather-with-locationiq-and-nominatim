# Requirements: Breezy Weather VN — Vietnamese Address Quality

**Defined:** 2026-03-23
**Core Value:** Vietnamese users see a clean ward/commune name — never a POI or government-office prefix — even when LocationIQ or Nominatim individually return garbage.

## v1 Requirements

### Address Quality — VN Token Extraction

- [ ] **ADDR-01**: When LocationIQ's `display_name` returns a POI-prefixed string (e.g. "Ủy ban nhân dân Phường X"), the app must NOT show that prefix — it must extract only the clean "Phường X" token (or fall through to Nominatim)
- [ ] **ADDR-02**: `pickBestVietnamSubProvincePart()` uses `firstOrNull` on LocationIQ results (ward is earliest segment at zoom=18) and `lastOrNull` on Nominatim results (ward is last segment at zoom=13)
- [ ] **ADDR-03**: `NominatimAddress` data class maps `suburb`, `hamlet`, `quarter`, and `neighbourhood` fields so Nominatim's structured ward data is not discarded
- [ ] **ADDR-04**: For `countryCode == "vn"`, the converter tries `address.suburb ?: address.hamlet ?: address.quarter` before falling through to `display_name` regex

### Address Quality — Cross-Validation

- [ ] **XVAL-01**: When both LocationIQ and Nominatim results are available for a VN location, the app prefers whichever produced a clean VN sub-province token (matching `vnSubProvinceRegex`)
- [ ] **XVAL-02**: If LocationIQ result is clean → use LocationIQ result. If LocationIQ dirty but Nominatim clean → use Nominatim result. If both dirty → use LocationIQ as last resort (current fallback behavior preserved)
- [ ] **XVAL-03**: The fallback `parts.firstOrNull()` raw-string assignment is eliminated; when regex fails for LocationIQ, the result is treated as "dirty" and triggers the cross-validation path

### Performance — Lazy Nominatim

- [ ] **PERF-01**: When a LocationIQ API key is configured and LocationIQ returns a clean VN result, Nominatim is NOT called — eliminating the always-parallel second request
- [ ] **PERF-02**: Nominatim is only invoked when LocationIQ result fails the VN regex check (lazy/conditional strategy), reducing API calls per background refresh cycle
- [ ] **PERF-03**: The LocationIQ endpoint is configurable or defaults to a region closer to Vietnam (Asia endpoint) instead of hardcoded `us1.locationiq.com`

### Reliability — Client Freshness & Logging

- [ ] **REL-01**: The stale `mApi by lazy {}` pattern is removed; a fresh API client is constructed per `requestLocationSearch` call (matching the pattern already used in `requestNearestLocation`)
- [ ] **REL-02**: Each `onErrorReturn` handler logs the error at debug level (source name + throwable message) before returning an empty list, so failures are diagnosable
- [ ] **REL-03**: The Nominatim client and LocationIQ client are clearly separated in construction to prevent future JSON schema divergence from being silently ignored

### "Giggles" — Rescue Feedback

- [ ] **GIGL-01**: When cross-validation selects Nominatim's result over LocationIQ's (a "rescue" event), a debug-level log line is emitted: `"[GiggleRescue] Nominatim rescued address for [lat,lon]: was '[dirty]', now '[clean]'"`
- [ ] **GIGL-02**: Settings screen shows a playful description for the Nominatim fallback toggle: something like "Backup address detective (fires when LocationIQ returns garbage)"

### Test Coverage — VN Address Logic

- [ ] **TEST-01**: Kotlin JUnit unit tests cover `pickBestVietnamSubProvincePart()` — including the POI-prefix dirty case, clean Phường/Xã/Đặc khu cases, and the empty-string/null edge cases
- [ ] **TEST-02**: Kotlin unit tests cover the cross-validation merge logic (XVAL-01 through XVAL-03) with mock LocationIQ and Nominatim responses for VN addresses
- [ ] **TEST-03**: Kotlin unit tests cover the `NominatimAddress` structured field mapping (ADDR-03/ADDR-04) with real-shaped Nominatim JSON fixtures
- [ ] **TEST-04**: All new tests run as part of the existing Gradle `./gradlew test` suite and pass in CI

## v2 Requirements

### Performance & Architecture

- **PERF-V2-01**: Full RxJava → Kotlin Coroutines migration for `NominatimService` (using `supervisorScope` + `async/await` instead of `Observable.zip`)
- **PERF-V2-02**: Nominatim rate limiter / request queue (1 req/sec ceiling) as a hard safety net for bulk refresh scenarios

### Security

- **SEC-V2-01**: `SourceConfigStore` migrated to `EncryptedSharedPreferences` for API key storage at rest

### Infrastructure

- **INFRA-V2-01**: `ConfigStore` → Android DataStore migration
- **INFRA-V2-02**: `UpdateStrategy.ALWAYS_UPDATE` logic implemented so locations can opt out of refresh

## Out of Scope

| Feature | Reason |
|---------|--------|
| RxJava → Coroutines full migration | Large independent refactor; doesn't fix address quality directly |
| EncryptedSharedPreferences for API key | Security improvement but orthogonal to VN address quality goal |
| Astro/timezone bugs | Unrelated module; no impact on address display |
| `Release.getDownloadLink` fix | Unrelated to address quality |
| Non-VN location changes | All VN-logic gated on `countryCode == "vn"`; other countries unaffected |
| Nominatim SLA / self-hosted setup | Infrastructure concern; partial mitigation via lazy fallback already in scope |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ADDR-01 | Phase 2 | Pending |
| ADDR-02 | Phase 2 | Pending |
| ADDR-03 | Phase 1 | Pending |
| ADDR-04 | Phase 1 | Pending |
| XVAL-01 | Phase 2 | Pending |
| XVAL-02 | Phase 2 | Pending |
| XVAL-03 | Phase 2 | Pending |
| PERF-01 | Phase 3 | Pending |
| PERF-02 | Phase 3 | Pending |
| PERF-03 | Phase 3 | Pending |
| REL-01 | Phase 3 | Pending |
| REL-02 | Phase 3 | Pending |
| REL-03 | Phase 3 | Pending |
| GIGL-01 | Phase 4 | Pending |
| GIGL-02 | Phase 4 | Pending |
| TEST-01 | Phase 5 | Pending |
| TEST-02 | Phase 5 | Pending |
| TEST-03 | Phase 5 | Pending |
| TEST-04 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 19 total
- Mapped to phases: 19 ✓
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-23*
*Last updated: 2026-03-23 after initialization*
