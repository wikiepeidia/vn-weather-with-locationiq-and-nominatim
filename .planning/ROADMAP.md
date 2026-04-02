# Roadmap: VN Weather

**Milestones:**
- ✅ **v1.0 — VN Address Quality** (Phases 1-5) — Shipped 2026-03-23
- ✅ **v1.1 — Background Watchdog** (Phases 6-8) — Shipped 2026-04-02

---

## Milestones

<details>
<summary>✅ v1.0 VN Address Quality (Phases 1-5) — Shipped 2026-03-23</summary>

- [x] Phase 1: Data Model Foundation — Map missing Nominatim structured fields (2 plans)
- [x] Phase 2: Token Extraction & Cross-Validation — Fix token ordering, eliminate POI-prefix garbage
- [x] Phase 3: Performance & Reliability — Lazy Nominatim, fresh API client, error logging, Asia endpoint
- [x] Phase 4: Giggles Feedback & Settings — Rescue log + playful settings description
- [x] Phase 5: Kotlin Unit Tests — Full JUnit coverage for VN address logic

See: `.planning/milestones/v1.0-ROADMAP.md` (if archived)

</details>

<details>
<summary>✅ v1.1 Background Watchdog (Phases 6-8) — Shipped 2026-04-02</summary>

- [x] Phase 6: WatchdogService Core — Foreground service, AlarmManager heartbeat, job monitor, graceful alarm fallback (3 plans)
- [x] Phase 7: Settings & Teardown — Watchdog toggle, battery-opt prompt, MIUI/HyperOS autostart deep-link, clean disable (3 plans)
- [x] Phase 8: Boot & Manifest Wiring — BootReceiver integration, alarm receiver, manifest registrations (1 plan)

**Stats:** 3 phases, 7 plans, 7 commits, 477 lines added across 8 files
**Audit:** 15/15 requirements, 14/14 integrations, 4/4 flows — `tech_debt` status (7 items accepted)

See: `.planning/milestones/v1.1-ROADMAP.md`, `.planning/milestones/v1.1-REQUIREMENTS.md`

</details>

## Active

*(No active milestone — run `/gsd-new-milestone` to start v1.2)*

---
*Last updated: 2026-04-02*
