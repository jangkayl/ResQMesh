# Location controls card

Prepared: 2026-10-29 on `feat/location-controls`. Status: local checks only; no device evidence yet. Build the debug APK from the branch head and record its hash here before running.

The change adds user control over GPS:
- **Auto-detect location**: a switch on the Home location card and in Settings. It defaults to ON. It keeps a recent, balanced-power fix ready while the app is open, and nothing is sent by itself. Turning it off stops updates and forgets the cached fix.
- **Include my location**: a row on the SOS screen. It is ON for each new SOS, and one tap leaves the location out.
  - When excluded, the SOS has no coordinates and no high-accuracy refine runs.
  - When included and auto-detect is off, a one-shot fix is taken on send.
  - A denied permission or phone location being off shows **Allow** or **Turn on**, and the slider still sends.

Not changed: the wire format, Room, BLE, receiver wording ("No Location Attached"), background location (there is none) and the chat share-location action.

## Setup

Two phones, A and B, with Bluetooth on and the app in the foreground. Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 15` on a debug build.

## Steps

1. **Default:** on a fresh install of A, Home shows "Location found" with "±N m · updated …" and the switch ON.
2. **Included SOS:** A opens SOS. The row reads "Location included" with accuracy and age. Slide to send. Expected: B alarms and its map shows a pin, and the accuracy refines within seconds without a duplicate alert.
3. **Excluded SOS:** end the SOS. Start a new one: the row is ON again. Tap the row so it reads "Location not included", then send.
   - Expected on B: "No Location Attached" on the map, and no later location update in SOS_SYNC.
   - Expected on A: the thread header reads "Location not shared", not "Acquiring".
4. **Auto-detect off:** turn it off in Settings. Home reads "Auto-detect off" and the switch is off. Send an SOS with location included. The row says "A fix will be taken when you send", and B gets a pin after the one-shot fix. Turn auto-detect back on from the Home card.
5. **Problems never block:** turn phone location off, then open SOS. The row shows **Turn on**, and tapping it shows the system dialog. Then revoke the location permission. The row shows **Allow**. In both states the slider still sends.
6. **Rotation:** on the SOS screen, exclude location and rotate the phone. The row stays excluded.

## Failure indicators

- B receives coordinates for an excluded SOS.
- Any location update after an excluded SOS.
- The slider is disabled or hidden by a location problem.
- Home or the SOS row shows a fix while auto-detect is off and nothing was sent.
- The switch state is lost on rotation.

## Report

APK hash, phone models, which steps passed, and for failures the time plus SOS_SYNC markers. Never share real coordinates.
