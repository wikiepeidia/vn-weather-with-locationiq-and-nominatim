# Milestones

## v1.1 Background Watchdog (Shipped: 2026-04-02)

**Phases completed:** 3 phases (6-8), 7 plans, 477 lines added across 8 files
**Timeline:** 2026-04-02 (single session)
**Git range:** `3d7c34645..87ba3dcd2` (7 commits)

**Key accomplishments:**

1. Persistent foreground WatchdogService with AlarmManager heartbeat monitors WeatherUpdateJob and self-heals after HyperOS/MIUI process kills
2. Watchdog Mode toggle in Background Updates settings with battery-optimization exemption prompt and HyperOS/MIUI autostart deep-link
3. BootReceiver integration resumes Watchdog automatically after device reboot
4. Clean disable path: service stop + alarm cancel + notification dismiss (no zombie resurrection)
5. Graceful degradation: exact alarm fallback to inexact when ROM restricts setExactAndAllowWhileIdle

**Tech debt accepted:**
- No formal VERIFICATION.md or VALIDATION.md for phases 6-8
- `WEATHER_UPDATE_WORK_NAME` hardcoded string literal (fragile if upstream changes)
- HyperOS still kills aggressively even with all settings configured (ROM-V2-03 backlog)

---
