# Phase 9: Tech Debt Cleanup - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

v1.1 debt resolved — shared constants extracted and verification gaps documented. Specifically:
1. Extract `"WeatherUpdate-auto"` hardcoded string from WatchdogService into a shared constant accessible from both WatchdogService and WeatherUpdateJob
2. Add VERIFICATION.md for v1.1 phases 6-8 retroactively documenting what was verified and known gaps

</domain>

<decisions>
## Implementation Decisions

### Agent's Discretion
All implementation choices are at the agent's discretion — pure infrastructure/tech debt phase. Use ROADMAP phase goal, success criteria, and codebase conventions to guide decisions.

</decisions>

<code_context>
## Existing Code Insights

### Key Files
- `app/src/main/java/org/breezyweather/background/watchdog/WatchdogService.kt` — has `WEATHER_UPDATE_WORK_NAME = "WeatherUpdate-auto"` hardcoded (line ~217)
- `app/src/main/java/org/breezyweather/background/weather/WeatherUpdateJob.kt` — has `WORK_NAME_AUTO` as `private const val`
- `.planning/milestones/v1.1-phases/` — archived phase SUMMARYs for verification reference

### Established Patterns
- Constants in companion objects as `private const val` or `const val`
- WeatherUpdateJob.WORK_NAME_AUTO is currently private — needs to be made internal/public or extracted to shared location

### Integration Points
- WatchdogService.performHeartbeat() references the work name to check job status
- WeatherUpdateJob uses WORK_NAME_AUTO for WorkManager unique work name

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. The shared constant should be accessible to both classes without creating unnecessary coupling.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
