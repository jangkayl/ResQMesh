# Adaptive location card

Prepared: 2026-10-31 on `feat/adaptive-location`. Status: local checks only; no device evidence yet. Build the debug APK from the branch head and record its hash here before running.

The change:
- While the app is visible, a short GPS fix runs every 5 minutes, skipped when a good fix is under 2 minutes old. Nothing is sent.
- An open map follows you every 5 s. Updates stop when the map closes or the app leaves the screen.
- Your own active SOS follows you every 15 s, then every 60 s after 30 minutes. It publishes only after real movement or a clearly better fix: at most once a minute, then once per 5 minutes after 10 minutes.
- Sharing location in chat uses a recent fix at once. A failed lock now shows on your phone only.
- Map and SOS requests no longer cancel each other.

Not changed: the wire format, Room, BLE routing and background location. SOS updates pause while the app is not on screen.

## Setup

Two phones, A and B, including your slowest phone, with airplane mode on, then Bluetooth and location turned on. No Wi-Fi or mobile data. Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 30` on a debug build. A needs an offline map package.

## Steps

1. **Cold fix:** reboot A and open the app outside. Time how long Home takes to report a fix.
2. **Warm fix:** leave the app open for 6 minutes, then share location in public chat. Time it from tap to message.
3. **Failed lock:** stay deep indoors with no recent fix, ideally after a reboot, and share location. Expected: a message on A only, and nothing on B.
4. **Live map:** on A, open the map for a pin and walk 50 m. The marker and distance follow you without the camera jumping or the map stuttering.
5. **Map closes:** close the map. Expected: the location indicator in Android's status bar goes away once no SOS is active.
6. **Lost fix:** on A, raise an SOS and open a map straight away. Expected: B still receives the refined position.
7. **Moving SOS:** walk 100 m with the app on screen. Expected: B's pin moves, and `SOS_QUEUE` shows new revisions at most about once a minute.
8. **Stationary SOS:** stand still for 5 minutes with the app on screen. Expected: no new revisions after the first refinement settles.
9. **No re-alarm:** B alarms once for the SOS, with no repeat on later location updates.
10. **Excluded SOS:** raise an SOS without location and walk 50 m. Expected: no coordinates and no later location revision.
11. **Auto-detect off:** turn it off, raise an SOS with location and walk 50 m. Expected: one fix on send, then no further location revisions.
12. **Mesh still works:** during steps 7–8, send text both ways and record whether it arrives normally.

## Failure indicators

- A failed lock appears on B as a chat message.
- More than one location revision a minute.
- Any revision while stationary after the fix settles, beyond a clearly better fix.
- A repeat alarm on B.
- Location revisions for an excluded SOS or with auto-detect off.
- The status-bar location indicator stays on after the map closes with no active SOS.
- Visible stutter on the map, or chat delay during an active SOS.

## Report

APK hash, phone models, cold and warm fix times in seconds, which steps passed, approximate battery change over the test, and for failures the time plus `SOS_QUEUE` and `SOS_SYNC` markers. Never share real coordinates.
