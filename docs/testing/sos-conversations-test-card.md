# SOS and Radio conversation phone card

## Build and devices

Install the same upgraded APK on A, B, and C; mixed versions cannot relay the new protocol reliably. Record models/API, role, and GATT/L2CAP use. The user installs, starts, and operates phones.

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Version: 1.0.1, code 2; Room schema 11; built 2026-10-02.
- Earlier SOS instrumentation APK SHA-256: `0EC7CBFA165F8995858AD9D49CB680A4D884C54DD4A4B7A6C56311FC6D5F0EE8`.
- For the current recovery/SOS UI build, use the identity in the [Bluetooth recovery card](bluetooth-recovery-test-card.md). Earlier emulator results do not validate the latest floating-header/card changes.

## Local evidence and reproduction

On 2026-10-02, debug build, Android-test build, 248 unit tests, Lint (zero errors, 147 warnings, three hints), documentation checks, and diff checks passed. Medium_Phone/x86_64/API 37 passed eleven instrumentation tests: migrations 6→11, 9→11, 10→11 plus sender Back, confirmed End/read-only history, receiver-local silence, compact/large-text controls, reminder layout/navigation, controls surviving height/IME changes, and a Daylight Radio composer with a visibly open keyboard. Historical migration test names retain their original boundaries; each opens the current schema. Screenshot review confirmed the reminder and composer remain clear of the keyboard.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --max-workers=1 --console=plain
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1
adb -s emulator-5554 shell am start -W -n com.example.testresqmesh/.MainActivity
adb -s emulator-5554 shell am instrument -w -r -e class "com.example.testresqmesh.data.local.AppDatabaseMigrationTest,com.example.testresqmesh.feature.sos.SosNavigationTest" com.example.testresqmesh.test/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (11 tests)`; ADB exit zero alone does not establish a pass. A cold run was killed for low memory. The successful final run used 3 GiB emulator RAM, stopped Gradle daemons, and warmed the app before instrumentation. Test windows match the app's edge-to-edge/adjustResize behavior; soft keyboard display is enabled explicitly. Optional screenshots use UIAutomation, avoiding a PixelCopy timeout seen in an earlier attempt. Espresso 3.7.0/AndroidX JUnit 1.3.0 support API 37. Captures: `app/build/sos-ux-final-checks.log`, `app/build/sos-ux-instrumentation-final.log`, `app/build/sos-ux-visuals/`. Recording gestures, audible alerts, and BLE convergence still require the phone steps below.

## Setup

Establish actual READY A–B and B–C links, avoiding a direct A–C link. Verify ordinary public/private text. Tune A to Radio 1, B to 2, C to 3. Keep background mesh opt-in consistent and note whether enabled. Start a focused 10-minute capture with `scripts/capture_ble_logcat.ps1`.

## Steps and expected results

1. A sends text and a recorded note in Radio 1. B/C retain it silently under Radio 1 history. Community stays clean. Switch B to Radio 1: old notes do not autoplay, but manual playback works. New notes autoplay only with monitoring enabled. Check per-channel previews, unread, drafts, and channel changes during queued sends.
2. A sends a test Medical SOS. B/C receive it despite different radio tuning. Open its thread, exchange text replies, and view location. SOS/location/replies stay outside Community and Radio. Record first-hop and C arrival timestamps.
3. A presses Back and uses Messages/Voice. Its SOS remains active and the banner reopens it. B presses Back: only B's sound stops; A's alert stays active. The siren stops within 30 seconds without dismissal. A private voice note must never autoplay on Radio.
4. C creates another SOS. A ends its own SOS using confirmation. Only A's alert ends on B/C; C's alert remains active. Ended threads retain read-only history and matching active notifications disappear.
5. Start a new A SOS, then disconnect C from B while C retains it. A ends its SOS. C remains active until communication resumes. Restore B–C: terminal state converges without creating another alarm. Record time from READY to convergence and neighbor-confirmation feedback. Repeat five times.
6. Restart one app without clearing storage, then revisit histories, drafts, muted alerts, and ended records. Old ended alerts never reactivate. A newly acquired GPS fix after End must not create a new SOS.
7. Repeat steps 2–5 during five recorded notes and simultaneous recordings. Test available GATT-only and L2CAP paths separately. Urgent events may overtake queued notes; an active GATT frame is non-interruptible. Record delay rather than assuming immediate delivery.
8. Review Night/Daylight, largest text, landscape, TalkBack, IME, and Back/map return. With A's SOS active, visit Mission, Messages, Voice, Community, private chat, and Radio history/thread. Main screens reserve the capsule above navigation; conversations reserve a top strip even with the keyboard open. Tap it to reopen A's thread; no message/composer may be covered. In short/large-text SOS threads, use “SOS controls” to reach Map, Activity, and End/Silence. Ended badges must say “ENDED”, not imply rescue completion. Mesh off/searching/checking/connected labels must follow actual state; a queued SOS cannot claim remote confirmation. Earlier unscoped history stays hidden without removing current channel/SOS history. At large text, scroll Voice and cancel a hold by dragging/leaving the screen: no partial note may be sent. A normal hold/release still sends to the channel captured at press time.
9. Test background enabled/disabled and locked-screen notification permission/full-screen fallback separately. A paused/offline mesh cannot receive until it resumes.
10. From the SOS hub, begin creating an alert and cancel or press Back before sending. Expect the hub and no new alert. Send a test SOS; inspect category, active/silenced/ended state, transmission, time, location accuracy, and History in both appearances. Invalid timestamps must not display epoch time; cached coordinates and header time do not prove a fresh GPS fix. Exchange enough replies to scroll beneath the floating header, open/close the keyboard, and change orientation/text size. The header must not cover replies or the composer; latest replies remain visible and ended history stays read-only. Record this scenario as PASS/FAIL/UNTESTED.

## Failure indicators and report

Report wrong-room content, unexpected autoplay, missing/duplicate replies, wrong alert cancellation, SOS ending on Back, ended alert resurrection, notification mismatch, notes sent after a cancelled recording gesture, covered controls, unreadable Daylight badges, misleading connection/confirmation labels, queued stalls, or excessive voice-pressure delay. Include APK hash, device/channel/transport matrix, exact timestamps, five-repeat results, and the relevant window. Important markers: `SOS_EVENT`, `SOS_CANCEL`, `SOS_SYNC`, `SOS_QUEUE`, READY, queue-full, promotion/fallback, and retirement. Never include private content or keys.

Local compilation/unit tests/emulator storage checks do not establish physical BLE reliability.
