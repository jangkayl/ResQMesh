# Code smells refactor smoke card

Prepared: 2026-10-31. Status: not yet run. Branch `CodeSmells`. Local checks on the same date: debug and Android-test builds, 665/665 JVM tests with no test file changed, Lint, detekt (baseline regenerated) and boundary check pass. No device evidence.

Round 1 is meant to change no behavior. It splits the incident help workflow, sync, outbox, transfer custody, offline map and SOS code into smaller units. Round 1 does not touch GATT, L2CAP, wire formats or the database schema; round 2 (below) does restructure BLE and GATT code. Unit tests pass unchanged, but only these steps exercise the code on real radios.

## Round 2 rerun

Prepared: 2026-11-04. Status: not yet run. Branch `final-code-smells` ([plan](../plans/code-smells-round-2.md)). Local checks on that date: debug and Android-test builds, 934/934 JVM tests with no test file changed on the branch, Lint, detekt (baseline shrunk) and boundary check pass. No device evidence. Unlike round 1, this round also restructures BLE admission, `BleConfig` constants and GATT client/server callbacks (steps 10–12), so all steps including relay step 8 are **required**. Record the branch head commit and APK hash as below. In captures, also check READY on both CLIENT and SERVER roles, CCCD `READY CCCD indications enabled` markers, and that a peer with no identity is still disconnected after the name handshake timeout.

Merged to `dev` in PR #35 (`07b5e2f`) on 2026-11-05. User report, 2026-11-05: all steps run on **two phones** worked. Build commit, APK hash, phone models, API levels, repeat counts and captures were not supplied. Two phones cannot cover step 5 (late joiner C), step 8 (relay) or the C side of step 6, so those remain **UNTESTED** and the card stays open. Follow-up `refactor/ble-config-constants` moves the default ATT payload (20) and requested MTU (247) into `BleConfig` with unchanged values; build the three-phone rerun from a commit that includes it.

## Build identity

Build `.\gradlew.bat :app:assembleDebug` from the branch head and record the commit and APK SHA-256 (`Get-FileHash app\build\outputs\apk\debug\app-debug.apk`). Install the same debug APK on every phone.

## Devices and setup

Three phones (A, B, C) for all steps; the relay step needs them in a chain. Bluetooth on, app foreground, screens awake. Start a capture with `.\scripts\device\capture_ble_logcat.ps1` on each phone if available.

## Steps and expected results

1. **Public and private text:** A sends public text, then private text to B. Expect arrival on B, and DELIVERED on A for the private message.
2. **Offline private queue:** turn Bluetooth off on B, send private text from A, then turn it back on. Expect Pending, then delivery after reconnect.
3. **Large transfer:** A sends a recorded note over 4 KiB to B. Expect complete playback and the `TRANSFER_RETAINED`, `TRANSFER_REASSEMBLED` and `TRANSFER_CUSTODY_ACK` markers.
4. **Help workflow:** A creates an incident. B offers help, edits the offer, A selects B, B confirms, A resolves. Repeat with B declining, then with B withdrawing after selection. Expect the same state on all phones and no helper offer reopening a resolved incident.
5. **Late joiner sync:** with C's Bluetooth off, complete step 4, then turn C on. Expect C to converge to the same incident states, with `INCIDENT_SYNC` `COMPLETE` markers.
6. **SOS:** A raises SOS, updates location and ends it. Expect the alarm on B and C, the updated location and the end on both.
7. **Offline map:** download or update the map package if a source is available, then open the SOS map offline. Expect the map to display; a failed download must keep the previous package.
8. **Relay (optional):** with A–B–C in a chain, send public and private text A→C. Record the result; DELIVERY-01 still applies.

## Failure indicators and report

Report FAIL for crashes, missing READY, missing receipts, incomplete notes, phones disagreeing on incident state, a missing SOS alarm or a lost map package. Report per step PASS/FAIL/UNTESTED, APK hash, phone models and API levels, repeat counts and capture folder.
