# Roadmap: Breezy Weather VN — Vietnamese Address Quality

**Milestone:** v1 — VN Address Quality  
**Created:** 2026-03-23  
**Granularity:** Standard (5 phases)  
**Coverage:** 19/19 v1 requirements mapped ✓

---

## Phases

- [ ] **Phase 1: Data Model Foundation** — Map missing Nominatim structured fields so VN ward data is never silently discarded
- [ ] **Phase 2: Token Extraction & Cross-Validation** — Fix firstOrNull/lastOrNull ordering and implement real cross-validation to eliminate POI-prefix garbage
- [ ] **Phase 3: Performance & Reliability** — Lazy Nominatim strategy, fresh API client per call, error logging, Asia endpoint
- [ ] **Phase 4: Giggles Feedback & Settings** — Rescue log + playful settings description so users and devs see the system working
- [ ] **Phase 5: Kotlin Unit Tests** — Full JUnit coverage for all VN address logic in Gradle test suite

---

## Phase Details

### Phase 1: Data Model Foundation
**Goal**: Nominatim's structured VN address fields (`suburb`, `hamlet`, `quarter`, `neighbourhood`) are mapped in `NominatimAddress` and consumed by the converter, so structured ward data is never silently discarded in favour of brittle `display_name` regex.  
**Depends on**: Nothing (first phase — pure data model addition)  
**Requirements**: ADDR-03, ADDR-04  
**Success Criteria** (what must be TRUE):
  1. `NominatimAddress` has `suburb`, `hamlet`, `quarter`, and `neighbourhood` fields with correct `@SerializedName` JSON annotations
  2. For `countryCode == "vn"`, `convertLocation()` tries `address.suburb ?: address.hamlet ?: address.quarter` before falling through to `display_name` regex
  3. Existing non-VN location behavior is unchanged — no regressions for any country outside Vietnam  
**Plans**: TBD

---

### Phase 2: Token Extraction & Cross-Validation
**Goal**: Users see a clean ward/commune name — never a POI or government-office prefix — even when only one API produces a clean result. Both APIs are cross-referenced and the best result wins.  
**Depends on**: Phase 1 (structured fields must be available before cross-validation can compare structured vs. display_name results)  
**Requirements**: ADDR-01, ADDR-02, XVAL-01, XVAL-02, XVAL-03  
**Success Criteria** (what must be TRUE):
  1. A LocationIQ `display_name` of `"Ủy ban nhân dân Phường Phú Lương, Phường Phú Lương, …"` produces `"Phường Phú Lương"` as city — never the full institutional string
  2. `pickBestVietnamSubProvincePart()` uses `firstOrNull` for LocationIQ results (ward is earliest at zoom=18) and `lastOrNull` for Nominatim results (ward is last at zoom=13)
  3. When LocationIQ result fails regex but Nominatim result is clean, the Nominatim clean token is used as city
  4. When both results fail regex, LocationIQ result is used as last resort (existing fallback behavior is preserved, not removed)
  5. The `parts.firstOrNull()?.trim()` raw-string fallback is eliminated; a regex failure is explicitly treated as "dirty" and routes to cross-validation  
**Plans**: TBD

---

### Phase 3: Performance & Reliability
**Goal**: Nominatim is only invoked when LocationIQ fails VN regex check (lazily), the API client is always fresh after config changes, and every failure produces a diagnosable debug log line.  
**Depends on**: Phase 2 (lazy strategy depends on the regex-clean/dirty signal being correct)  
**Requirements**: PERF-01, PERF-02, PERF-03, REL-01, REL-02, REL-03  
**Success Criteria** (what must be TRUE):
  1. When LocationIQ returns a clean VN result, no Nominatim HTTP request is made for that reverse-geocode call
  2. After a user updates the Nominatim base URL in Settings and triggers geocoding, the new URL is used — no app restart required
  3. A Nominatim network failure produces a `Log.d` line containing the source name and error message before the empty list is returned
  4. A LocationIQ network failure similarly produces a `Log.d` line — failures from both sources are individually diagnosable
  5. The LocationIQ endpoint is configurable or defaults to the Asia region endpoint instead of `us1.locationiq.com`  
**Plans**: TBD

---

### Phase 4: Giggles Feedback & Settings
**Goal**: Developers and users receive visible confirmation when the fallback system catches a bad result, and the Settings screen explains the fallback toggle in a human, playful way.  
**Depends on**: Phase 2 (cross-validation rescue logic must exist before the giggles feedback can fire) and Phase 3 (lazy strategy determines when a rescue actually occurs)  
**Requirements**: GIGL-01, GIGL-02  
**Success Criteria** (what must be TRUE):
  1. When Nominatim's result supersedes a dirty LocationIQ result, a debug log line appears in the format `"[GiggleRescue] Nominatim rescued address for [lat,lon]: was '[dirty]', now '[clean]'"` 
  2. The Settings screen Nominatim fallback toggle displays a playful description (e.g. "Backup address detective (fires when LocationIQ returns garbage)") that is visible to users in the UI  
**Plans**: TBD

---

### Phase 5: Kotlin Unit Tests
**Goal**: All VN address parsing logic — token extraction, cross-validation, and structured field mapping — is covered by Kotlin JUnit tests that pass in the existing `./gradlew test` suite.  
**Depends on**: Phases 1–4 (tests validate behaviour delivered in previous phases)  
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04  
**Success Criteria** (what must be TRUE):
  1. `./gradlew test` runs to completion with all VN address tests passing and no new failures
  2. `pickBestVietnamSubProvincePart()` has unit tests covering: POI-prefix dirty input, clean Phường/Xã/Đặc khu inputs, empty string, and null edge cases
  3. The cross-validation merge logic (XVAL-01 through XVAL-03) has tests with mock LocationIQ and Nominatim VN response fixtures
  4. `NominatimAddress` structured field deserialization (ADDR-03/04) has tests with real-shaped Nominatim JSON payloads confirming `suburb` / `hamlet` / `quarter` values are picked up  
**Plans**: TBD

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Data Model Foundation | 0/? | Not started | — |
| 2. Token Extraction & Cross-Validation | 0/? | Not started | — |
| 3. Performance & Reliability | 0/? | Not started | — |
| 4. Giggles Feedback & Settings | 0/? | Not started | — |
| 5. Kotlin Unit Tests | 0/? | Not started | — |

---

## Coverage Map

| Requirement | Phase |
|-------------|-------|
| ADDR-01 | Phase 2 |
| ADDR-02 | Phase 2 |
| ADDR-03 | Phase 1 |
| ADDR-04 | Phase 1 |
| XVAL-01 | Phase 2 |
| XVAL-02 | Phase 2 |
| XVAL-03 | Phase 2 |
| PERF-01 | Phase 3 |
| PERF-02 | Phase 3 |
| PERF-03 | Phase 3 |
| REL-01 | Phase 3 |
| REL-02 | Phase 3 |
| REL-03 | Phase 3 |
| GIGL-01 | Phase 4 |
| GIGL-02 | Phase 4 |
| TEST-01 | Phase 5 |
| TEST-02 | Phase 5 |
| TEST-03 | Phase 5 |
| TEST-04 | Phase 5 |

**Total mapped: 19/19 ✓**

---
*Roadmap created: 2026-03-23*
