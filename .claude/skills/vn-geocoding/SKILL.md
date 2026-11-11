---
name: vn-geocoding
description: Use when touching NominatimService, LocationIQ, reverse geocoding, location search, address or city/district display, or the Vietnamese ward (Phường, Xã, Đặc khu) logic and its tests in this fork. Holds the frozen algorithm, reference addresses and how to verify changes.
---

# Vietnamese geocoding (Nominatim + LocationIQ)

The owner's core feature. The Vietnamese behaviour is frozen: change it only if the owner asks.

Code: `app/src/main/kotlin/org/breezyweather/sources/nominatim/NominatimService.kt`.
Tests: `NominatimServiceTest.kt` (helpers) and `NominatimServiceFlowTest.kt` (canned HTTP through the real service, plus real LocationIQ responses recorded in `app/src/test/resources/nominatim/locationiq_live_responses.json`).

## Rules

**Vietnam.** LocationIQ first. A "clean" ward is a `display_name` part starting with Phường, Xã or Đặc khu (`vnSubProvinceRegex`); the last matching part wins. No clean ward: ask Nominatim, and its clean ward wins. Still none: keep the LocationIQ result as it is. The starting city is `address.town ?: name`, then ward parsing overrides it. Never show a POI or house number taken from `display_name`.

**Other countries.** LocationIQ first, then Nominatim is always asked and its answer wins, using upstream's mapping (`village ?: town ?: city`, district chain). If Nominatim fails or has no city, keep LocationIQ's.

**No place at all** (LocationIQ 404, e.g. open sea): Nominatim, then `InvalidLocationException`.

**Requests.** LocationIQ search and reverse send `format=json` (`jsonv2` gets HTTP 400). Nominatim sends `jsonv2`. Search without a key uses Nominatim only.

**Key.** The `pk.` key lives in the source's "instance" setting inside the app. It is not in the build. Never read `.env/`, `singature/` or key files and never print a key; ask the owner to run live checks.

**Identity.** `isConfigured` must stay `REPORT_ISSUE.isNotEmpty() && userAgent.isNotEmpty()` and every call uses `BreezyWeather.instance.userAgent`. No hardcoded User-Agent (Nominatim's policy and the upstream developers' complaint).

## Reference addresses (must keep passing)

- `Trụ sở Ủy ban Nhân dân Thành phố Hồ Chí Minh, 86, Lê Thánh Tôn, Khu phố 3, Phường Sài Gòn, Thành phố Thủ Đức, Thành phố Hồ Chí Minh, 71006, Việt Nam` shows `Phường Sài Gòn`.
- LocationIQ `Thành phố Thủ Đức, Thành phố Hồ Chí Minh` plus Nominatim `Phường Sài Gòn, Thành phố Thủ Đức, ...` shows `Phường Sài Gòn`.
- The same shapes for Xã and Đặc khu.

## Environment note

The owner's ISP resets connections to every `openstreetmap.org` host, so Nominatim often fails for them. LocationIQ alone must always give a usable answer.

## Changing this code

1. `npx gitnexus impact <symbol> --direction upstream` first (the project's CLAUDE.md requires it; `requestNearestLocation` and `requestLocationSearch` rate CRITICAL).
2. Keep both test files passing: `./gradlew :app:testBasicDebugUnitTest :app:testFreenetDebugUnitTest`.
3. Add a flow test for any new case and, when the owner supplies a real response, record it in the fixture file.
