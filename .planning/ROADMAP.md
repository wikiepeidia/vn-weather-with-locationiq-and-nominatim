# Roadmap: VN Weather

**Milestones:**

- ✅ **v1.0 — VN Address Quality** (Phases 1-5) — Shipped 2026-03-23
- ✅ **v1.1 — Background Watchdog** (Phases 6-8) — Shipped 2026-04-02
- ✅ **v1.2 — ROM Hardening** (Phases 9-14, Phase 12 dropped) — Shipped 2026-04-05
- 🔄 **v1.3 — LocationIQ Recovery & Key Validation** (Phases 15-17) — Planned

---

## Active: v1.3 LocationIQ Recovery & Key Validation

### Phases

- [ ] **Phase 15: LocationIQ Call-Path Recovery** — Restore actual LocationIQ HTTP call path when configured
- [ ] **Phase 16: Save-Time Key Validation UX** — Validate key on Save and block invalid persistence
- [ ] **Phase 17: Regression Lock & Fallback Assurance** — Confirm no algorithm rewrite and preserve fallback behavior

### Phase Details

#### Phase 15: LocationIQ Call-Path Recovery

**Goal**: Ensure LocationIQ is actually invoked in VN reverse geocoding when configured, with explicit diagnostics on skip/failure reasons.
**Depends on**: Nothing (first phase of v1.3)
**Requirements**: LOCIQ-01, LOCIQ-03
**Success Criteria** (what must be TRUE):

1. With configured LocationIQ key, VN reverse geocoding triggers at least one LocationIQ request (provider is not silently ignored)
2. If LocationIQ is not called, debug logs clearly state why (missing key, disabled path, config issue)
3. If LocationIQ call fails, debug logs classify failure (invalid key response, network/request failure, non-success response)

**Plans**: 1 plan

Plans:

- [ ] 15-01-PLAN.md — Trace and fix LocationIQ call-path wiring + diagnostics

#### Phase 16: Save-Time Key Validation UX

**Goal**: Validate LocationIQ key when user taps Save and only persist valid keys.
**Depends on**: Phase 15 (ensures LocationIQ client path is operational)
**Requirements**: KEYVAL-01, KEYVAL-02, KEYVAL-03, KEYVAL-04
**Success Criteria** (what must be TRUE):

1. Save action performs server validation ping for entered LocationIQ key before persistence
2. Invalid-key response shows explicit user error and blocks save
3. Valid-key response shows success feedback and persists key
4. Network/server validation failure shows retryable error and does not overwrite existing valid key

**Plans**: 1 plan

Plans:

- [ ] 16-01-PLAN.md — Implement save-time key validation flow and settings UX states

#### Phase 17: Regression Lock & Fallback Assurance

**Goal**: Guarantee bug-fix-only scope and preserve VN fallback behavior.
**Depends on**: Phase 15 and Phase 16
**Requirements**: LOCIQ-02, REG-01, REG-02
**Success Criteria** (what must be TRUE):

1. VN regex/token extraction and cross-validation outputs remain unchanged from pre-v1.3 behavior on existing VN test paths
2. Code changes remain limited to LocationIQ call-path wiring, validation flow, and settings UX (no algorithm rewrite)
3. Nominatim fallback remains functional when LocationIQ is unavailable or invalid

**Plans**: 1 plan

Plans:

- [ ] 17-01-PLAN.md — Regression validation and fallback assurance

### Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 15. LocationIQ Call-Path Recovery | 0/1 | ○ Pending | — |
| 16. Save-Time Key Validation UX | 0/1 | ○ Pending | — |
| 17. Regression Lock & Fallback Assurance | 0/1 | ○ Pending | — |

---
*Last updated: 2026-04-16 — v1.3 roadmap approved (3 phases, 9 requirements mapped)*
