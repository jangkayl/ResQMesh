# Batch C hygiene smoke card

Prepared: 2026-10-17. Status: user-reported PASS on 2026-10-17 ("it all works"; models, counts, captures and installed APK hash not supplied; prepared build `f3661cc`, APK `1A734DAF11D53997576C9D0C639C89498DFA1FA19C4CA5B4E5F1DF9D407BA9D3`). Branch `refactor/batch-c-hygiene`.

Batch C is meant to change no runtime behavior. It moves the Koin root and splits it into layer modules, moves audio, logging, notification, SOS-alert, setup and migration files to new packages, and moves dependency versions into the catalog without changing any version. The highest risks are startup DI wiring and eager singleton creation. Wire format and Room table names are guarded by unit tests. This card checks for regressions only.

## Build identity

Build `.\gradlew.bat :app:assembleDebug` from the branch head. Record the commit and the APK SHA-256 (`Get-FileHash app\build\outputs\apk\debug\app-debug.apk`). Install the same debug APK over the existing app on every phone. Do not clear app data or keys; this also exercises Room migration ownership. The user installs and operates the phones.

## Devices and setup

At least two phones (A, B), plus C for the relay step if available. Bluetooth on, app in the foreground, screens awake. Start a capture with `.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 10`.

## Steps and expected results

1. **Cold start:** force-stop, then launch on each phone. Expect the app to open without a crash, history intact and the setup gates unchanged. A Koin error or crash on launch is a FAIL.
2. **READY:** A and B reach payload READY, with the usual `BLE_MESH` markers.
3. **Private message:** A sends private text to B. Expect it to arrive on B and show DELIVERED on A.
4. **Voice note:** A records a note over 4 KiB to B. Expect complete playback on B (audio files moved).
5. **Radio voice note:** on a radio channel with the monitor on at B, hold to record on A and release. Expect the note to autoplay on B.
6. **SOS:** A starts SOS, B receives the alarm and silences it locally, then A ends it. Expect the alarm, notification and siren stop as before (SOS alert controller moved).
7. **Notifications:** with B backgrounded, send a private message from A. Expect a MessagingStyle notification on B (notification helper moved).
8. **Incidents and map:** open the incident list, view an incident, and open the offline map. Expect no missing UI.
9. **Relay (optional):** in an A–B–C chain, send text A→C. Record the result; DELIVERY-01 still applies.

## Failure indicators and report

Report FAIL for:
- a crash or Koin `NoBeanDefFoundException`/`InstanceCreationException` in AndroidRuntime
- missing READY, receipts, audio, SOS alarm or notifications
- lost history after the upgrade install

Report, per step: PASS/FAIL/UNTESTED, the APK hash, phone models and API levels, the time of any failure, and the capture folder. Never share message or audio content or keys.
