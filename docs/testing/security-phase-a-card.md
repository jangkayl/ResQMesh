# Security Phase A card

Prepared: 2026-10-17. Status: UNTESTED. Covers Phase A of the [security hardening plan](../plans/security-hardening.md): input limits, the received-media cap, the notification router, developer mode in debug builds only, and release R8/backup changes. No wire format changes, so Phase A builds interoperate with current `main` builds.

## Build identity

- **Debug:** `scripts\device\manage_devices.ps1`, option 5. Record the package, commit and SHA-256.
- **Release:** `.\gradlew.bat :app:assembleRelease` with `keystore.properties`. Record the APK SHA-256. This is the first R8-minified build, so the release steps below matter.

## Devices and setup

Three phones, A, B and C, with Bluetooth and location on. Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 15` on the debug run.

## Steps and expected results

1. **Message limit:** on A, paste more than 1000 characters into private and public chat. Expected: the text stops at 1000 and a counter appears near the limit. A 1000-character message arrives on B.
2. **Reply near the limit:** reply-quote a message and type up to the limit. Expected: the send succeeds with no "too long" error.
3. **Double tap:** tap Send twice quickly. Expected: one message. A text followed immediately by a voice note sends both.
4. **Other fields:** display name stops at 32 characters, incident description at 2000, area at 120 and search at 100.
5. **Normal traffic:** public, private (DELIVERED), SOS raise and end, an image and a voice note between A, B and C. Expected: unchanged behavior. Media under 2 MB arrives normally.
6. **Notification taps:** with the app in the background, tap a private-message notification on B. It opens the right chat. Raise an SOS on A; B's full-screen or heads-up SOS opens the SOS view.
7. **Developer mode:** in the debug build, Profile shows developer tools with no PIN printed. In the release build, no developer tools section exists.
8. **Release smoke test:** install the release APK on two phones. Check READY, public and private text, SOS and an incident sync. This confirms R8 kept the serialization classes.
9. **Backup:** `adb shell bmgr backupnow edu.cit.resqmesh` reports that backup is not allowed or does nothing.

## Failure indicators and report

Report a FAIL for any of these:
- a crash, especially in the release build or on a notification tap
- a message cut short or changed in ways other than removed control characters
- a valid send dropped
- a notification that opens the wrong screen
- developer tools visible in release
- any `INPUT_REJECT` or `MEDIA_REJECT` marker during normal traffic

Report per step: PASS, FAIL or UNTESTED, the build identities, phone models and the capture folder. Never share message content or keys.
