---
phase: 03
name: Performance & Reliability
status: passed
created: 2025-07-07
updated: 2025-07-07
---

# Phase 03: Performance & Reliability — Verification

## Goal

Nominatim is only invoked when LocationIQ fails the VN regex check (lazily), the API client is always fresh after config changes, every failure produces a diagnosable log line, and the LocationIQ endpoint defaults to Asia instead of the US.

## Requirements Covered

| ID | Description | Status |
|----|-------------|--------|
| PERF-01 | When LIQ returns clean VN result, no Nominatim HTTP request is made | ✓ Verified |
| PERF-02 | Nominatim only invoked when LIQ fails VN regex (lazy strategy) | ✓ Verified |
| PERF-03 | LocationIQ endpoint defaults to Asia (`ap1.locationiq.com`) | ✓ Verified |
| REL-01 | Stale `mApi by lazy {}` removed; fresh client per `requestLocationSearch` call | ✓ Verified |
| REL-02 | `onErrorReturn`/`onErrorResumeNext` log source name + message at debug level | ✓ Verified |
| REL-03 | LIQ and Nominatim clients explicitly separated in construction | ✓ Verified |

## Must-Haves

### PERF-01/02: Lazy Nominatim strategy

| Check | Result |
|-------|--------|
| `nominatimFetch` is a deferred Observable, not subscribed eagerly | ✓ line 123 — defined but not subscribed until needed |
| Non-VN or clean VN → `Observable.just(listOf(liqInfo))` — Nominatim never called | ✓ lines 152–154 |
| VN dirty → `nominatimFetch.map { }` — Nominatim called lazily | ✓ lines 157–163 |
| LIQ error → `onErrorResumeNext` → `nominatimFetch` — Nominatim called as fallback | ✓ lines 165–169 |
| `Observable.zip` removed | ✓ confirmed absent |

### PERF-03: Asia endpoint

| Check | Result |
|-------|--------|
| `LOCATIONIQ_BASE_URL = "https://ap1.locationiq.com/v1/"` | ✓ line 416 |
| `us1.locationiq.com` absent from file | ✓ confirmed absent |

### REL-01: Fresh client per requestLocationSearch

| Check | Result |
|-------|--------|
| `private val mApi by lazy {}` removed from class | ✓ confirmed absent |
| `requestLocationSearch` builds `api` from `client.baseUrl(url).build()` | ✓ lines 88–89 |
| `url` derived from `key != null` check identical to old lazy logic | ✓ line 87 |

### REL-02: Error logging

| Check | Result |
|-------|--------|
| Nominatim `onErrorReturn` logs `"Nominatim reverse geocoding error: ${e.message}"` | ✓ line 135 |
| LocationIQ `onErrorResumeNext` logs `"LocationIQ reverse geocoding error: ${e.message}"` | ✓ line 167 |
| `import android.util.Log` added | ✓ line 19 |

### REL-03: Client separation

| Check | Result |
|-------|--------|
| `liqApi` built from `LOCATIONIQ_BASE_URL` explicitly | ✓ lines 113–116 |
| `nomApi` built from `NOMINATIM_BASE_URL` explicitly | ✓ lines 117–120 |
| No shared client instance between both sources | ✓ confirmed |

## Compilation

- **Build:** `./gradlew :app:compileFreenetDebugKotlin` → **BUILD SUCCESSFUL** (1m 1s)
- **Errors:** 0
- **Warnings:** 0 new

## Commit

- `481fe82eb` — `feat(perf,rel): lazy Nominatim strategy, Asia endpoint, fresh client, error logging (PERF-01..03, REL-01..03)`

## Files Modified

| File | Changes |
|------|---------|
| `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` | Removed `mApi by lazy`; fresh client in `requestLocationSearch`; lazy `flatMap` strategy with `nominatimFetch`; `Log.d` on both error paths; `ap1` endpoint; `android.util.Log` import |

## Verdict: PASSED

All six requirements satisfied. LIQ clean path never touches Nominatim. Asia endpoint reduces VN latency. Stale client eliminated. Both error sources are diagnosable. Build clean. Phase 4 (Giggles Feedback & Settings) may proceed.
