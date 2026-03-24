---
status: awaiting_human_verify
trigger: "After changing userAgent from hardcoded constant to BreezyWeather.instance.userAgent, Nominatim/LocationIQ reverse geocoding fails completely"
created: 2026-03-24T00:00:00Z
updated: 2026-03-24T00:00:00Z
---

## Current Focus

hypothesis: CONFIRMED — Vietnamese locale overrides brand_name to "Thời Tiết VN" (non-ASCII chars ờ=U+1EDD, ế=U+1EBF), OkHttp 5.3.2 rejects non-ASCII in header values with IllegalArgumentException, which falls through to defaultRefreshError=REVERSE_GEOCODING_FAILED
test: Checked values-vi/strings.xml line 96 — brand_name = "Thời Tiết VN", verified chars are > 0x7E
expecting: Fix: remove Vietnamese brand_name override so userAgent stays ASCII-only
next_action: Apply fix

## Symptoms

expected: App should reverse-geocode coordinates to an address using Nominatim and/or LocationIQ APIs
actual: App shows error "Nguồn cung cấp thời tiết không tìm được vị trí chính xác" meaning it failed to find address
errors: "Nominatim / LocationIQ (Tìm địa chỉ): Nguồn cung cấp thời tiết không tìm được vị trí chính xác" shown in UI snackbar
reproduction: Any location lookup in the app fails. Started after commit bef29ec85 which changed userAgent from hardcoded USER_AGENT constant to BreezyWeather.instance.userAgent
started: After changing userAgent approach

## Eliminated

- hypothesis: BreezyWeather.instance not initialized when NominatimService is constructed
  evidence: Hilt singletons are lazily created; instance is set in onCreate() before any service call
  timestamp: 2026-03-24

- hypothesis: isConfigured=false blocking the call
  evidence: isConfigured is hardcoded to true in current code
  timestamp: 2026-03-24

- hypothesis: Empty userAgent for this fork
  evidence: brand_name "Weather VN" doesn't contain "breezy", so condition passes and userAgent is non-empty in English locale
  timestamp: 2026-03-24

## Evidence

- timestamp: 2026-03-24
  checked: values-vi/strings.xml line 96
  found: brand_name = "Thời Tiết VN" — Vietnamese locale override exists
  implication: On Vietnamese device, getString(R.string.brand_name) returns "Thời Tiết VN" instead of "Weather VN"

- timestamp: 2026-03-24
  checked: Character analysis of "Thời Tiết VN"
  found: Contains ờ (U+1EDD) and ế (U+1EBF), both > U+007E
  implication: OkHttp 5.3.2 rejects header values with chars > 0x7E

- timestamp: 2026-03-24
  checked: OkHttp version = 5.3.2, Headers.checkValue() source
  found: checkValue requires chars to be \t or in U+0020..U+007E range
  implication: IllegalArgumentException thrown when User-Agent contains Vietnamese chars

- timestamp: 2026-03-24
  checked: RefreshErrorType.getTypeFromThrowable()
  found: IllegalArgumentException falls through to else branch -> defaultRefreshError = REVERSE_GEOCODING_FAILED
  implication: Matches the user's exact error message

## Resolution

root_cause: Vietnamese locale overrides brand_name to "Thời Tiết VN" (non-ASCII). BreezyWeather.instance.userAgent uses getString(R.string.brand_name) which on Vietnamese devices returns the localized string. OkHttp 5.3.2 rejects non-ASCII characters in HTTP header values with IllegalArgumentException. This unhandled exception falls through to REVERSE_GEOCODING_FAILED error.
fix: Removed the Vietnamese brand_name override ("Thời Tiết VN") from values-vi/strings.xml. The brand_name in res_fork/values/strings.xml is marked translatable="false" and stays "Weather VN" (ASCII-only) in all locales. The app_name "Thời Tiết VN" is preserved for UI display (launcher label etc.).
verification: Static analysis confirms no other locale overrides brand_name. OkHttp 5.3.2 will no longer throw IllegalArgumentException since "Weather VN" is ASCII-safe.
files_changed: [app/src/main/res/values-vi/strings.xml]
