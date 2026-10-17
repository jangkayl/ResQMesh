# Layer boundaries smoke card

Prepared: 2026-10-17. Status: awaiting user phone run. Branch `refactor/layer-boundaries` ([plan](../plans/layer-boundaries.md)).

This change is meant to leave runtime behavior unchanged. Feature screens now read `core/model` copies of Room rows through core repository interfaces and data adapters. Incident rules moved to `core/domain/incident`. SOS raise and private send moved into use cases, the SOS alarm is driven through a core notifier, and location clients come from a factory. No BLE, routing, crypto, wire or Room schema code changed. The highest risks are:
- Koin wiring at startup
- the SOS screens (alert flows, ownership and history)
- the incident screens (list, detail, offers, permissions)
- SOS location refinement
- the private-send key prompt

## Build identity

Build `.\gradlew.bat :app:assembleDebug` from the branch head. Record the commit and the APK SHA-256 (`Get-FileHash app\build\outputs\apk\debug\app-debug.apk`). Install over the existing app on every phone. Do not clear data or keys. The user installs and operates the phones.

## Devices and setup

At least two phones (A, B), plus C for the relay step if available. Turn Bluetooth and location on, keep the app in the foreground and the screens awake. Start a capture with `.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 15`.

## Steps and expected results

1. **Cold start:** force-stop, then launch each phone. Expected: no crash, history intact, setup gates unchanged.
2. **READY and chat:** A and B reach payload READY. A sends public text and B receives it.
3. **Private send:** A sends private text to B. Expected: it arrives and shows DELIVERED on A. If a key-change prompt is pending, the verification prompt appears as before instead of a generic failure.
4. **SOS raise:** A starts SOS with location on. Expected: B gets the alarm and notification, and the alert has a location. A few seconds later the location refines (the accuracy changes) without a duplicate alert.
5. **SOS thread:** on both phones open the SOS thread and its activity history. Expected: the rows match the previous build. On A only, the alert is treated as own (end control shown); on B it is not.
6. **SOS silence and end:** B silences locally, then A ends. Expected: the siren stops on B, and the alert ends on both phones within about 30 seconds. Ending before refinement must not bring the alert back.
7. **Incident create:** A creates an incident with location. Expected: it appears on A and syncs to B.
8. **Incident offer flow:** B offers help, A selects B, B starts and resolves. Expected: buttons appear only for the allowed role at each state (reporter versus helper), as before.
9. **Map and distance:** open the incident and SOS maps and the location card. Expected: the own location and distances show.
10. **Setup and profile:** open the profile. Expected: the display name and #nodeId are unchanged.
11. **Relay (optional):** with A–B–C in a chain, send text from A to C and record the result. DELIVERY-01 still applies.

## Failure indicators and report

Report a FAIL for any of these:
- a crash, or a Koin `NoBeanDefFoundException` / `InstanceCreationException` in AndroidRuntime
- missing SOS alarm, location, history or end
- wrong own-SOS or incident role controls
- an empty incident list or incident detail
- a generic failure where the key prompt is expected
- lost history

Markers: `BLE_MESH`, `AndroidRuntime`, `SosAlert`.

Report per step: PASS, FAIL or UNTESTED; the APK hash; phone models and API levels; the time of any failure; and the capture folder. Never share message content, audio or keys.
