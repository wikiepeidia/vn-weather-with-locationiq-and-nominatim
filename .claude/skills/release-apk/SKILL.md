---
name: release-apk
description: Build and sign the Weather VN release APKs for testing on a phone. Runs the unit tests of both flavors, builds both release flavors, signs them with the owner's keystore and verifies the signature.
disable-model-invocation: true
---

# Release APKs (test, build, sign)

Run from the repo root. Stop at the first failing step and report it; never sign after a failed test.

1. Tests: `./gradlew :app:testBasicDebugUnitTest :app:testFreenetDebugUnitTest --console=plain`. Count failures from `app/build/test-results/*/*.xml`.
2. Build: `./gradlew :app:assembleBasicRelease :app:assembleFreenetRelease --console=plain` (takes several minutes). Never pass `-Pbreezy`.
3. Sign: `python -X utf8 "singature/APK signer.py"`. It signs every `*-release-unsigned.apk` and writes `singature/output/*-release-signed.apk`. Do not open or print anything else in `singature/`.
4. Verify: `apksigner verify` from the newest `build-tools` under the Android SDK on one basic and one freenet APK.
5. Report: the full path of `singature/output/app-basic-arm64-v8a-release-signed.apk` (the one for a modern phone), the universal APK as the fallback, the file times, and the test count.

Do not commit, push or publish the APKs.
