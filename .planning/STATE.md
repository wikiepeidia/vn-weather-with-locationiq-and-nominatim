# STATE.md — VN Weather

## Project Reference

**Core Value:** Vietnamese users see a clean ward/commune name — never a POI or government-office prefix — even when LocationIQ or Nominatim individually return garbage.

**Milestone:** v1 — VN Address Quality  
**Milestone Goal:** All 19 requirements covering token extraction, cross-validation, lazy Nominatim, reliability, giggles feedback, and Kotlin unit tests delivered and passing.

---

## Current Position

**Active Phase:** Phase 4 — Giggles Feedback & Settings  
**Active Plan:** none — awaiting `/gsd-execute-phase 4`  
**Status:** Phase 3 complete ✓ — Phase 4 not yet planned  

```
Progress: [██████░░░░] 60% (3/5 phases complete)
```

### Phase Checklist

- [x] Phase 1: Data Model Foundation — commit `02c92fed3`
- [x] Phase 2: Token Extraction & Cross-Validation — commit `b6400cfe8`
- [x] Phase 3: Performance & Reliability — commit `481fe82eb`
- [ ] Phase 4: Giggles Feedback & Settings
- [ ] Phase 3: Performance & Reliability
- [ ] Phase 4: Giggles Feedback & Settings
- [ ] Phase 5: Kotlin Unit Tests

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Phases defined | 5 |
| Requirements total | 19 |
| Requirements mapped | 19 |
| Phases complete | 0 |
| Plans executed | 0 |

---

## Accumulated Context

### Key Decisions (from PROJECT.md)

| Decision | Rationale |
|----------|-----------|
| Lazy Nominatim (conditional, not always-parallel) | Eliminates rate limit risk; faster happy path |
| Cross-validation: prefer clean regex match over API priority | Correct result = whichever has a clean VN token |
| `firstOrNull` for LocationIQ, `lastOrNull` for Nominatim | LocationIQ zoom=18: ward is FIRST; Nominatim zoom=13: ward is LAST |
| Add `suburb`/`hamlet` to `NominatimAddress` | Nominatim returns VN ward under `suburb` — currently unmapped |
| Skip RxJava migration | High-risk refactor; independent of address quality goal |

### Key Files to Touch

- `app/src/main/java/org/breezyweather/sources/nominatim/json/NominatimAddress.kt` — Phase 1
- `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` — Phases 1–4
- `app/src/main/res/` (strings) + settings preference XML — Phase 4
- `app/src/test/java/org/breezyweather/sources/` — Phase 5

### Constraints to Remember

- All VN-specific logic MUST be gated on `countryCode == "vn"` — non-VN locations must be unchanged
- Nominatim 1 req/sec policy — lazy strategy is the mitigation (no rate limiter needed)
- Keep `NominatimService` API surface stable — other callers depend on it
- All new code in Kotlin; no new Java

### Todos

*(none yet — will be populated during phase planning)*

### Blockers

*(none)*

---

## Session Continuity

**Last updated:** 2026-03-23  
**Last action:** Phase 1 planned — 2 PLAN.md files written to .planning/phases/01-data-model-foundation/  
**Next action:** Run `/gsd-execute-phase 1` to execute Phase 1 — Data Model Foundation

---
*STATE.md initialized: 2026-03-23*
