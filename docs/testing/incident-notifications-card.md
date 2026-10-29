# Incident notifications card

Prepared: 2026-10-29. Status: pending. Branch `feat/incident-notifications` (stacked on `feat/location-controls`). See D28 in [decisions](../decisions.md).

New incidents of Critical or Serious severity now notify the other phones, and an in-app toggle can turn these alerts off. Reporters are notified about help offers and lead decisions. Selected helpers are notified when they are selected. Resolution or cancellation updates the existing notification. The change adds no wire change, so it interoperates with location-controls builds, but only phones running this build post notifications.

## Build identity

Use `scripts\device\manage_devices.ps1`, option 5, and press Enter to rebuild. Record the printed package, commit and SHA-256.

## Devices and setup

Use two or three phones (A, B, optionally C), all running this build, with notification permission granted and the incident alerts toggle on (default). Turn on background mode on B, then press Home or lock B. Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 15`.

## Steps and expected results

1. **Critical alert:** A reports a Critical incident. Expected: B shows one "Critical incident" notification with the title, type and area. Tapping it opens that incident on B.
2. **Serious alert:** A reports a Serious incident. Expected: B notifies, as in step 1.
3. **Moderate is quiet:** A reports a Moderate incident. Expected: no notification on B, and the incident still appears in B's list.
4. **Own report:** A gets no notification for its own reports.
5. **Toggle off:** on B, turn incident alerts off in Profile, then background B. A reports a Critical incident. Expected: no notification on B. Turn the toggle back on.
6. **Help offer:** B offers help on A's incident, and A is backgrounded. Expected: A is notified that B offered help.
7. **Lead selection:** A selects B as lead, and B is backgrounded. Expected: B is notified that it was selected. When B confirms or declines, A is notified.
8. **Resolution:** A resolves the incident. Expected: B's notification for that incident updates once, without a fresh loud alert per update, and does not duplicate.
9. **No flood on rejoin:** take C offline for over 30 minutes while A reports a Critical incident, then bring C back. Expected: the incident syncs into C's list without a notification.

## Failure indicators and report

Report a FAIL for any of these:
- a crash on receive or on a notification tap
- a tap that opens the wrong screen or incident
- notifications for Moderate incidents, own actions, or with the toggle off
- duplicate notifications for one incident
- a burst of notifications after sync or restart

Report per step: PASS, FAIL or UNTESTED; the build identity; devices and Android versions; and the capture folder. Never share incident descriptions beyond what is needed.
