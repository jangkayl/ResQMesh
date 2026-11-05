# Pre-demo cleanup: phone card

Status: active procedure. Local unit tests, Lint, detekt, boundary and docs checks pass; device behavior is unverified.

Change: deleted code and resources with zero references (six unused UI component files, unused theme colors, template launcher icons and drawables, `colors.xml`, 56 unused strings, the template example tests) and two unused libraries (`material3-adaptive-navigation-suite`, `koin-test-junit4`). `NotificationHelper` catch blocks log through `AppLogger` instead of `printStackTrace`. No BLE, routing, delivery, wire-format, Room schema or manifest change. The app icon stays `@drawable/resqmesh_logo`.

## Build identity

Branch `refactor/pre-demo-cleanup`. Release variant: copy `keystore.properties.example` to `keystore.properties` (gitignored, `.jks` outside the repository), then run `.\gradlew.bat :app:assembleRelease`. Output is `app/build/outputs/apk/release/app-release.apk`; without `keystore.properties` the build produces an unsigned `app-release-unsigned.apk` that phones refuse. Keep one keystore for every release so upgrades keep data and keys. Record the commit, version and APK hash.

## Devices and setup

The release APK on two phones A and B in direct range, Bluetooth on. The user installs, launches and operates the phones. Release builds keep app Logcat markers debug-only, so use a debug build of the same commit if a capture is needed.

## Steps and expected results

1. **Launch:** open the app on both phones. Expect the ResQMesh icon in the launcher and the usual splash, onboarding or Home with unchanged colors.
2. **Screens:** open Home, Network, Messages, Incidents, Map, Profile and the SOS screen on A. Expect every screen to render with no crash and no missing text or icon.
3. **Link:** wait for B to appear as a direct (green) peer on A's Home.
4. **Text:** send one Community message and one private message from A to B. Expect arrival on B and a returning private receipt on A.
5. **SOS:** raise an SOS on A. Expect the SOS alert and notification on B; cancel it on A.
6. **Notification:** with B's app in the background, send a private message from A. Expect a notification on B that opens the chat.

## Failure indicators

FAIL on any crash (AndroidRuntime FATAL), a blank or missing icon, a screen showing a resource name instead of text, changed theme colors, or a text, receipt or SOS that worked on the previous build but fails here.

## Markers

On a debug build of the same commit, search `AndroidRuntime` and the `Notification` tag in the reported window.

## Report

Build identity, devices and Android versions, per-step pass or fail, and screenshots of any wrong screen.
