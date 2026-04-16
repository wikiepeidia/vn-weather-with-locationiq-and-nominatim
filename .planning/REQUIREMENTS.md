# Requirements: v1.3 LocationIQ Recovery & Key Validation

**Milestone:** v1.3 — LocationIQ Recovery & Key Validation
**Goal:** Fix the bug where LocationIQ is ignored and add save-time key validation UX without changing the existing VN address algorithm.
**Defined:** 2026-04-16
**Core Value:** Vietnamese users see a clean ward/commune name and weather updates remain reliable.

---

## v1.3 Requirements

### LocationIQ Call Path Recovery

- [ ] **LOCIQ-01**: When a LocationIQ key is configured and VN reverse geocoding runs, the app must issue LocationIQ HTTP requests (not silently skip LocationIQ)
- [ ] **LOCIQ-02**: Fix is limited to call-path/wiring; VN parsing and cross-validation algorithm behavior must remain unchanged
- [ ] **LOCIQ-03**: Debug logs must clearly report why LocationIQ was not called or failed (missing key, invalid key, request failure, non-success response)

### LocationIQ Key Validation UX

- [ ] **KEYVAL-01**: When user taps Save in settings, app pings LocationIQ to validate the entered API key
- [ ] **KEYVAL-02**: If response indicates invalid key, app shows explicit invalid-key message and blocks saving
- [ ] **KEYVAL-03**: If key is valid, app shows success feedback and persists the new key
- [ ] **KEYVAL-04**: If validation cannot complete (network/server error), app shows retryable error and does not overwrite existing valid key

### Regression Safety

- [ ] **REG-01**: Existing VN regex/token extraction and cross-validation flow remains intact (no algorithm rewrite)
- [ ] **REG-02**: Nominatim fallback behavior remains functional when LocationIQ is unavailable or invalid

---

## Future Requirements (Deferred)

- Optional live key validation while typing (debounced)
- Provider diagnostics screen with per-provider success/failure counters
- EncryptedSharedPreferences for API key storage

## Out of Scope

| Feature | Reason |
|---------|--------|
| Rewriting VN address extraction algorithm | Explicitly excluded by milestone scope; current algorithm is heavily tuned |
| Removing "giggles" behavior | Not part of bug-fix objective |
| Watchdog/ROM hardening changes | Separate concern from LocationIQ bug |

---

## Traceability

*(Filled by roadmapper after phase planning.)*

| Requirement | Phase | Status |
|-------------|-------|--------|
| LOCIQ-01 | TBD | Pending |
| LOCIQ-02 | TBD | Pending |
| LOCIQ-03 | TBD | Pending |
| KEYVAL-01 | TBD | Pending |
| KEYVAL-02 | TBD | Pending |
| KEYVAL-03 | TBD | Pending |
| KEYVAL-04 | TBD | Pending |
| REG-01 | TBD | Pending |
| REG-02 | TBD | Pending |

**Coverage:**
- v1.3 requirements: 9 total
- Mapped to phases: 0
- Unmapped: 9

---
*Requirements defined: 2026-04-16*
*Last updated: 2026-04-16 after milestone scope clarification*
