# Batch A cleanup smoke card

Prepared: 2026-10-16. Status: UNTESTED. Branch `refactor/batch-a-cleanup`.

Batch A is meant to change no mesh behavior: it removes unused incident UI files, adds two thread-safety fixes, logs previously empty catches (class name only), makes Logcat debug-only and names existing timer values. This card checks for regressions only.

## Build identity

Build `.\gradlew.bat :app:assembleDebug` from the branch head and record the commit and APK SHA-256 (`Get-FileHash app\build\outputs\apk\debug\app-debug.apk`). Install the same debug APK on every phone. The user installs and operates phones; do not clear app data or keys.

## Devices and setup

Two phones (A, B) minimum; a third (C) for the relay step if available. Bluetooth on, app foreground, screens awake. Start a capture with `.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10`.

## Steps and expected results

1. **READY:** A and B reach payload READY. Expect the usual connection state and `BLE_MESH` markers in Logcat.
2. **Private message:** A sends private text to B. Expect arrival on B and DELIVERED on A.
3. **Large transfer:** A sends a recorded note over 4 KiB to B. Expect complete playback on B (exercises the renamed transfer timers).
4. **Disconnect/reconnect:** turn Bluetooth off on B for 20 seconds, then on. Expect reconnection to READY and a successful follow-up text. New `... ignored: <ExceptionClass>` lines in Logcat are acceptable; crashes are not.
5. **Block/unblock:** A blocks B, then unblocks. Expect the direct link to drop and recover as before (exercises the block retry map).
6. **Incidents screen:** open the incident list, use quick filters and create/view an incident. Expect no missing UI (unused legacy files were removed).
7. **Relay (optional):** with A–B–C in a chain, send text A→C. Record result; DELIVERY-01 still applies.

## Failure indicators and report

Report FAIL for crashes, missing READY, missing receipts, incomplete notes, missing incident UI or missing Logcat markers on the debug build. Report per step PASS/FAIL/UNTESTED, APK hash, phone models/API, times of any failure and the capture folder. Never share message/audio content or keys.
