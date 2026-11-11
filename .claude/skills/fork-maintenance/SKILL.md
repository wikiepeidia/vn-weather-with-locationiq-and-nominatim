---
name: fork-maintenance
description: Use when merging upstream breezy-weather, resolving merge conflicts, building or signing release APKs, editing the README, gradle.properties, app name, applicationId or branding, or replying to the Breezy Weather developers about the fork or its license.
---

# Weather VN fork maintenance

This is a hobby fork of Breezy Weather (LGPL-3.0 plus `LICENSE_ADDITIONAL`: no trademarks or logos, and modified versions must be marked as different). The upstream developers have objected before to impersonation and to a rebased history, so keep both clean.

## Identity (do not change)

- `applicationId` `io.github.wikiepeidia.vnweather`, `brand_name` "Weather VN" in `app/src/res_fork/values/strings.xml`.
- `gradle.properties`: the `app.*` properties point at this fork. The `breezy.*` ones only apply with `-Pbreezy`, which must never be used.
- README says this is an independent modified fork, not affiliated with Breezy Weather, and sends bug reports here.
- New Kotlin files keep the LGPL header ("This file is part of Breezy Weather ...").
- `fastlane/` still holds Breezy's store text and logo. Do not publish it to a store as is.

## Merging upstream

1. `git fetch upstream`, then **merge** `upstream/main` (never rebase: upstream hashes and authors must stay). If a fetch fails on missing objects, `git fetch --refetch`.
2. Conflicts so far: keep the fork's `applicationId`, app names, README and issue/PR templates; take upstream's version numbers; the fork deletes upstream's GitHub workflows, so modify/delete conflicts are resolved by deleting.
3. Upstream may drop an import the fork still needs (it dropped `android.os.Build` in `Notifications.kt`). Compile both flavors.
4. Verify every line the fork added since the last merge still exists, then run the tests for both flavors.
5. Check `app/build.gradle.kts` still has the fork's `applicationId`, then check the geocoding flow tests (see the `vn-geocoding` skill) and the watchdog tests.

## Build and sign

1. `./gradlew :app:testBasicDebugUnitTest :app:testFreenetDebugUnitTest`.
2. `./gradlew :app:assembleBasicRelease :app:assembleFreenetRelease`.
3. `python -X utf8 "singature/APK signer.py"` (the `-X utf8` avoids a console encoding crash). Signed APKs land in `singature/output/`; install `app-basic-arm64-v8a-release-signed.apk` on a modern phone.

## Secrets

`singature/` (keystore, key notes, scripts) and `.env/` are gitignored and hold secrets. Never read them into the conversation, print them or commit them. Ask the owner to run anything that needs them.

## Talking to upstream

Keep replies short, polite and plain, with no marketing tone. The fork keeps its own Nominatim/LocationIQ changes on purpose.
