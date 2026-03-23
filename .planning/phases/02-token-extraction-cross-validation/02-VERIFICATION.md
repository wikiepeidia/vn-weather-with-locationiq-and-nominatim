---
phase: 02
name: Token Extraction & Cross-Validation
status: passed
created: 2025-07-07
updated: 2025-07-07
---

# Phase 02: Token Extraction & Cross-Validation — Verification

## Goal

Users see a clean ward/commune name — never a POI or government-office prefix — even when only one API produces a clean result. Both APIs are cross-referenced and the best result wins. LocationIQ is the primary API; Nominatim rescues dirty results.

## Requirements Covered

| ID | Description | Status |
|----|-------------|--------|
| ADDR-01 | POI-prefixed LIQ display_name → extract clean token; never show prefix | ✓ Verified |
| ADDR-02 | `pickBestVietnamSubProvincePart` uses `firstOrNull` for LIQ, `lastOrNull` for Nominatim | ✓ Verified |
| XVAL-01 | Both results compared; clean VN sub-province token wins | ✓ Verified |
| XVAL-02 | LIQ clean → LIQ; LIQ dirty + NOM clean → NOM; both dirty → LIQ last resort | ✓ Verified |
| XVAL-03 | Raw `parts.firstOrNull()` dirty fallback eliminated; regex failure routes to cross-validation | ✓ Verified |

## Must-Haves

### ADDR-02 + XVAL-03: pickBestVietnamSubProvincePart & dirty fallback

| Check | Result |
|-------|--------|
| `pickBestVietnamSubProvincePart` accepts `isLocationIQSource: Boolean` | ✓ line 254 |
| Uses `firstOrNull` when `isLocationIQSource = true` | ✓ line 257 |
| Uses `lastOrNull` when `isLocationIQSource = false` | ✓ line 260 |
| Call site in `convertLocation` passes `isLocationIQSource` | ✓ line 225 |
| Raw `parts.firstOrNull()?.trim()` fallback block removed from `convertLocation` | ✓ confirmed absent |
| Regex failure leaves city as `address.town ?: locationResult.name` | ✓ comment at line 218 |

### XVAL-01 + XVAL-02: Cross-validation in requestNearestLocation zip block

| Check | Result |
|-------|--------|
| `isVNContext` check present | ✓ line 157 |
| `isCleanVnCity(liqInfo?.city)` and `isCleanVnCity(nomInfo?.city)` called | ✓ lines 161-162 |
| `liqClean -> liqList` branch is FIRST (LocationIQ priority confirmed) | ✓ line 164 |
| `nomClean -> nomList` rescue branch present | ✓ line 165 |
| `liqInfo != null -> liqList` last-resort branch present | ✓ line 166 |
| Non-VN else path unchanged | ✓ lines 169-173 |
| `isCleanVnCity` private helper exists | ✓ lines 264-267 |

### ADDR-01: "Ủy ban nhân dân Phường X" scenario

- Input: `display_name = "Ủy ban nhân dân Phường Phú Lương, Phường Phú Lương, Quận Hà Đông, Hà Nội, Việt Nam"`
- Parts after split: `["Ủy ban nhân dân Phường Phú Lương", "Phường Phú Lương", "Quận Hà Đông", "Hà Nội", "Việt Nam"]`
- `pickBestVietnamSubProvincePart(parts, isLocationIQSource = true)` → `firstOrNull` over matching → "Ủy ban nhân dân..." does NOT match regex, "Phường Phú Lương" DOES match → returns `"Phường Phú Lương"` ✓
- Result: `city = "Phường Phú Lương"` — never the POI prefix ✓

## LocationIQ Priority Ladder (Confirmed)

| Priority | Condition | Result |
|----------|-----------|--------|
| 1st | LIQ city matches `vnSubProvinceRegex` | Use LIQ (clean, authoritative) |
| 2nd | LIQ dirty + NOM city matches regex | Use NOM (Nominatim rescue) |
| 3rd | Both dirty, LIQ available | Use LIQ (last resort) |
| 4th | Only NOM available | Use NOM (graceful degrade) |
| 5th | Both absent | `InvalidLocationException` |

## Compilation

- **Build:** `./gradlew :app:compileFreenetDebugKotlin` → **BUILD SUCCESSFUL** (21s)
- **Errors:** 0
- **Warnings:** 0 new (pre-existing deprecation warnings only)

## Commit

- `b6400cfe8` — `feat(xval): source-aware token extraction, remove dirty fallback, VN cross-validation (ADDR-01, ADDR-02, XVAL-01, XVAL-02, XVAL-03)`

## Files Modified

| File | Changes |
|------|---------|
| `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt` | `pickBestVietnamSubProvincePart` source-aware; raw fallback removed; `isCleanVnCity` helper added; zip block cross-validation implemented |

## Verdict: PASSED

All five requirements satisfied. LocationIQ priority confirmed at every level. Build clean. Non-VN paths unchanged. Phase 3 (Performance & Reliability) may proceed.
