# Phase 15: LocationIQ Call-Path Recovery - Discussion Log

> Audit trail only. Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md.

**Date:** 2026-04-16
**Phase:** 15-LocationIQ Call-Path Recovery
**Areas discussed:** Call-path guard

---

## Call-path guard

### Key format gate

| Option | Description | Selected |
|--------|-------------|----------|
| Strict pk. prefix | Only keys starting with `pk.` use LocationIQ path; malformed key logs skip reason. | ✓ |
| Lenient non-empty key | Any non-empty string can trigger LocationIQ path. | |
| Trim + strict pk. | Trim then require `pk.` prefix. | |

**User's choice:** Strict pk. prefix.
**Notes:** Keep provider switch behavior explicit and predictable.

### Skip/failure logging detail

| Option | Description | Selected |
|--------|-------------|----------|
| Reason category + endpoint type | Log reason bucket and endpoint category, never key material. | ✓ |
| Reason category only | Minimal diagnostics. | |
| Reason + HTTP status + body hint | Max verbosity for troubleshooting. | |

**User's choice:** Reason category + endpoint type.
**Notes:** Maintain safety by avoiding key leakage.

### Phase boundary

| Option | Description | Selected |
|--------|-------------|----------|
| Reverse only | Keep strict roadmap scope for Phase 15. | ✓ |
| Reverse + search | Expand patch into location-search path as well. | |

**User's choice:** Reverse only.
**Notes:** Key validation UX and broader behavior remain for later phases.

## the agent's Discretion

- Exact internal log string shape and helper refactor details.
- Immediate fixture values for call-path unit tests until user sends additional address samples.

## Deferred Ideas

- Save-time key validation UX (Phase 16).
- Expanded address corpus regression pass after user provides sample addresses.
