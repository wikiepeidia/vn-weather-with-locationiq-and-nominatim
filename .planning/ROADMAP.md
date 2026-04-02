# Roadmap: VN Weather — Vietnamese Address Quality + Background Watchdog

**Milestones:** v1.0 — VN Address Quality (complete ✓) · v1.1 — Background Watchdog (active)  
**Created:** 2026-03-23 · **v1.1 added:** 2026-04-02  
**Granularity:** Standard  
**Coverage:** v1.0 19/19 ✓ · v1.1 15/15 ✓

---

## Phases

- [x] **Phase 1: Data Model Foundation** — Map missing Nominatim structured fields so VN ward data is never silently discarded ✓
- [x] **Phase 2: Token Extraction & Cross-Validation** — Fix firstOrNull/lastOrNull ordering and implement real cross-validation to eliminate POI-prefix garbage ✓
- [x] **Phase 3: Performance & Reliability** — Lazy Nominatim strategy, fresh API client per call, error logging, Asia endpoint ✓
- [x] **Phase 4: Giggles Feedback & Settings** — Rescue log + playful settings description so users and devs see the system working ✓
- [x] **Phase 5: Kotlin Unit Tests** — Full JUnit coverage for all VN address logic in Gradle test suite ✓

### v1.1 — Background Watchdog

- [x] **Phase 6: WatchdogService Core** — Foreground service with keepalive notification, AlarmManager heartbeat, job monitor, and graceful alarm fallback ✓
- [ ] **Phase 7: Settings & Teardown** — Watchdog toggle UI, SettingsManager persistence, battery-opt prompt, MIUI/HyperOS autostart deep-link, and clean disable path
- [ ] **Phase 8: Boot & Manifest Wiring** — BootReceiver integration, WATCH_ALARM BroadcastReceiver, AndroidManifest registrations so the service survives reboot and ROM alarm delivery

---

## Phase Details

### Phase 1: Data Model Foundation

**Goal**: Nominatim's structured VN address fields (`suburb`, `hamlet`, `quarter`, `neighbourhood`) are mapped in `NominatimAddress` and consumed by the converter, so structured ward data is never silently discarded in favour of brittle `display_name` regex.  
**Depends on**: Nothing (first phase — pure data model addition)  
**Requirements**: ADDR-03, ADDR-04  
**Success Criteria** (what must be TRUE):

  1. `NominatimAddress` has `suburb`, `hamlet`, `quarter`, and `neighbourhood` fields with correct `@SerializedName` JSON annotations
  2. For `countryCode == "vn"`, `convertLocation()` tries `address.suburb ?: address.hamlet ?: address.quarter` before falling through to `display_name` regex
  3. Existing non-VN location behavior is unchanged — no regressions for any country outside Vietnam  
**Plans**: 2 plans

Plans:

- [x] 01-PLAN-nominatim-address-fields.md — Add suburb/hamlet/quarter/neighbourhood fields to NominatimAddress
  - [x] 01-PLAN-converter-structured-fields.md — Update convertLocation() VN path to use structured fields first

---

### Phase 2: Token Extraction & Cross-Validation

**Goal**: Users see a clean ward/commune name — never a POI or government-office prefix — even when only one API produces a clean result. Both APIs are cross-referenced and the best result wins.  
**Depends on**: Phase 1 (structured fields must be available before cross-validation can compare structured vs. display_name results)  
**Requirements**: ADDR-01, ADDR-02, XVAL-01, XVAL-02, XVAL-03  
**Success Criteria** (what must be TRUE):

  1. A LocationIQ `display_name` of `"Ủy ban nhân dân Phường Phú Lương, Phường Phú Lương, …"` produces `"Phường Phú Lương"` as city — never the full institutional string
  2. `pickBestVietnamSubProvincePart()` uses `firstOrNull` for LocationIQ results (ward is earliest at zoom=18) and `lastOrNull` for Nominatim results (ward is last at zoom=13)
  3. When LocationIQ result fails regex but Nominatim result is clean, the Nominatim clean token is used as city
  4. When both results fail regex, LocationIQ result is used as last resort (existing fallback behavior is preserved, not removed)
  5. The `parts.firstOrNull()?.trim()` raw-string fallback is eliminated; a regex failure is explicitly treated as "dirty" and routes to cross-validation  
**Plans**: TBD

---

### Phase 3: Performance & Reliability

**Goal**: Nominatim is only invoked when LocationIQ fails VN regex check (lazily), the API client is always fresh after config changes, and every failure produces a diagnosable debug log line.  
**Depends on**: Phase 2 (lazy strategy depends on the regex-clean/dirty signal being correct)  
**Requirements**: PERF-01, PERF-02, PERF-03, REL-01, REL-02, REL-03  
**Success Criteria** (what must be TRUE):

  1. When LocationIQ returns a clean VN result, no Nominatim HTTP request is made for that reverse-geocode call
  2. After a user updates the Nominatim base URL in Settings and triggers geocoding, the new URL is used — no app restart required
  3. A Nominatim network failure produces a `Log.d` line containing the source name and error message before the empty list is returned
  4. A LocationIQ network failure similarly produces a `Log.d` line — failures from both sources are individually diagnosable
  5. The LocationIQ endpoint is configurable or defaults to the Asia region endpoint instead of `us1.locationiq.com`  
**Plans**: TBD

---

### Phase 4: Giggles Feedback & Settings

**Goal**: Developers and users receive visible confirmation when the fallback system catches a bad result, and the Settings screen explains the fallback toggle in a human, playful way.  
**Depends on**: Phase 2 (cross-validation rescue logic must exist before the giggles feedback can fire) and Phase 3 (lazy strategy determines when a rescue actually occurs)  
**Requirements**: GIGL-01, GIGL-02  
**Success Criteria** (what must be TRUE):

  1. When Nominatim's result supersedes a dirty LocationIQ result, a debug log line appears in the format `"[GiggleRescue] Nominatim rescued address for [lat,lon]: was '[dirty]', now '[clean]'"`
  2. The Settings screen Nominatim fallback toggle displays a playful description (e.g. "Backup address detective (fires when LocationIQ returns garbage)") that is visible to users in the UI  
**Plans**: TBD

---

### Phase 5: Kotlin Unit Tests

**Goal**: All VN address parsing logic — token extraction, cross-validation, and structured field mapping — is covered by Kotlin JUnit tests that pass in the existing `./gradlew test` suite.  
**Depends on**: Phases 1–4 (tests validate behaviour delivered in previous phases)  
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04  
**Success Criteria** (what must be TRUE):

  1. `./gradlew test` runs to completion with all VN address tests passing and no new failures
  2. `pickBestVietnamSubProvincePart()` has unit tests covering: POI-prefix dirty input, clean Phường/Xã/Đặc khu inputs, empty string, and null edge cases
  3. The cross-validation merge logic (XVAL-01 through XVAL-03) has tests with mock LocationIQ and Nominatim VN response fixtures
  4. `NominatimAddress` structured field deserialization (ADDR-03/04) has tests with real-shaped Nominatim JSON payloads confirming `suburb` / `hamlet` / `quarter` values are picked up  
**Plans**: TBD

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Data Model Foundation | 0/? | Not started | — |
| 2. Token Extraction & Cross-Validation | 0/? | Not started | — |
| 3. Performance & Reliability | 0/? | Not started | — |
| 4. Giggles Feedback & Settings | 0/? | Not started | — |
| 5. Kotlin Unit Tests | 0/? | Not started | — |
| 6. WatchdogService Core | 0/3 | Planning complete | — |
| 7. Settings & Teardown | 0/? | Not started | — |
| 8. Boot & Manifest Wiring | 0/? | Not started | — |

---

## v1.1 Phase Details

### Phase 6: WatchdogService Core

**Goal**: `WatchdogService` runs as a persistent foreground service, posts a non-dismissible keepalive notification, monitors `WeatherUpdateJob` at each heartbeat, and self-heals via AlarmManager — with graceful fallback if exact alarms are restricted by the ROM.  
**Depends on**: Phase 5 (v1.0 complete; existing `WeatherUpdateJob`, `Notifications`, and `BootReceiver` surfaces are stable)  
**Requirements**: WATCH-01, WATCH-02, WATCH-03, WATCH-04, NOTIF-01, NOTIF-02, NOTIF-03, DEGRADE-01  
**Success Criteria** (what must be TRUE):

  1. Starting `WatchdogService` causes a persistent, non-dismissible notification to appear in the status bar shade with no sound, badge, or popup (IMPORTANCE_MIN)
  2. The notification text shows "Weather last updated X min ago" and refreshes after each successful weather update
  3. When `WeatherUpdateJob` is no longer ENQUEUED or RUNNING at heartbeat time, it is automatically re-enqueued without user action
  4. On a device where `setExactAndAllowWhileIdle()` is permitted, an exact alarm is armed before the service sleeps; on a device where it is restricted, the service falls back to `setInexactRepeating()` and writes a `Log.d` warning — no crash or ANR
  5. Killing the `WatchdogService` process between heartbeats (via `adb shell am kill`) causes the AlarmManager alarm to restart the service within the next alarm interval

**Plans**: 3 plans

Plans:
- [ ] 06-01-PLAN.md — Add CHANNEL_WATCHDOG and ID_WATCHDOG_KEEPALIVE to Notifications.kt
- [ ] 06-02-PLAN.md — Add WAKE_LOCK permission, WatchdogService and WatchdogAlarmReceiver to AndroidManifest
- [ ] 06-03-PLAN.md — Implement WatchdogService and WatchdogAlarmReceiver core functionality

**UI hint**: yes

---

### Phase 7: Settings & Teardown

**Goal**: Users can enable/disable Watchdog mode through the existing Background Updates settings screen; enabling prompts for battery-optimization exemption and surfaces the MIUI/HyperOS autostart deep-link; disabling cleanly stops the service, cancels the alarm, and dismisses the notification.  
**Depends on**: Phase 6 (WatchdogService must exist before the toggle can start/stop it)  
**Requirements**: SETT-01, SETT-02, SETT-03, SETT-04, DEGRADE-02  
**Success Criteria** (what must be TRUE):

  1. `BackgroundUpdatesSettingsScreen` shows a "Watchdog Mode" toggle defaulting to OFF; toggling it ON starts `WatchdogService` and toggling it OFF stops it
  2. When Watchdog is enabled and the app is not battery-optimization-exempt, the system battery-optimization settings screen is opened automatically (or a prompt shown directing the user there)
  3. On a Xiaomi/Redmi/POCO device, a clearly visible "MIUI Autostart" button appears in Background Updates settings and taps through to the MIUI autostart permission manager
  4. Disabling Watchdog mode via the toggle results in: service stopped, AlarmManager alarm cancelled, Watchdog notification gone — no orphaned notification or dangling alarm remains
  5. After an app restart, the Watchdog toggle reflects the persisted `watchdogEnabled` state — it does not reset to OFF if the user had left it ON

**Plans**: TBD  
**UI hint**: yes

---

### Phase 8: Boot & Manifest Wiring

**Goal**: The full Watchdog lifecycle is registered in `AndroidManifest.xml` and wired into `BootReceiver` so that the service automatically resumes after device reboot, and the `WATCH_ALARM` intent from AlarmManager is handled by a dedicated `BroadcastReceiver` that restarts the service after a ROM kill.  
**Depends on**: Phase 6 (WatchdogService core) and Phase 7 (SettingsManager `watchdogEnabled` preference)  
**Requirements**: BOOT-01, BOOT-02  
**Success Criteria** (what must be TRUE):

  1. After a full device reboot with Watchdog mode enabled, `WatchdogService` starts automatically — the Watchdog notification appears in the status bar within a few seconds of the home screen becoming interactive
  2. After a full device reboot with Watchdog mode disabled, `WatchdogService` does NOT start — existing WorkManager-only behavior is completely unchanged
  3. On boot, `WatchdogService` immediately re-enqueues `WeatherUpdateJob` if it is not already scheduled, providing catch-up coverage for any refresh that was missed during the off period
  4. The `WATCH_ALARM` broadcast intent (fired by AlarmManager) causes `WatchdogService` to restart even if the process was killed — confirmed by killing the process and waiting for the alarm interval
  5. `./gradlew assembleDebug` succeeds with all new service, receiver, and permission declarations present in `AndroidManifest.xml`

**Plans**: TBD

---

## Coverage Map

### v1.0 Requirements

| Requirement | Phase |
|-------------|-------|
| ADDR-01 | Phase 2 |
| ADDR-02 | Phase 2 |
| ADDR-03 | Phase 1 |
| ADDR-04 | Phase 1 |
| XVAL-01 | Phase 2 |
| XVAL-02 | Phase 2 |
| XVAL-03 | Phase 2 |
| PERF-01 | Phase 3 |
| PERF-02 | Phase 3 |
| PERF-03 | Phase 3 |
| REL-01 | Phase 3 |
| REL-02 | Phase 3 |
| REL-03 | Phase 3 |
| GIGL-01 | Phase 4 |
| GIGL-02 | Phase 4 |
| TEST-01 | Phase 5 |
| TEST-02 | Phase 5 |
| TEST-03 | Phase 5 |
| TEST-04 | Phase 5 |

**v1.0 Total mapped: 19/19 ✓**

### v1.1 Requirements

| Requirement | Phase |
|-------------|-------|
| WATCH-01 | Phase 6 |
| WATCH-02 | Phase 6 |
| WATCH-03 | Phase 6 |
| WATCH-04 | Phase 6 |
| NOTIF-01 | Phase 6 |
| NOTIF-02 | Phase 6 |
| NOTIF-03 | Phase 6 |
| DEGRADE-01 | Phase 6 |
| SETT-01 | Phase 7 |
| SETT-02 | Phase 7 |
| SETT-03 | Phase 7 |
| SETT-04 | Phase 7 |
| DEGRADE-02 | Phase 7 |
| BOOT-01 | Phase 8 |
| BOOT-02 | Phase 8 |

**v1.1 Total mapped: 15/15 ✓**

---
*Roadmap created: 2026-03-23 · v1.1 phases added: 2026-04-02*
