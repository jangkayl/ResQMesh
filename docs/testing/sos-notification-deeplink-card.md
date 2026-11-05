# SOS notification deep link: phone card

Status: active procedure. Local parser tests pass; device behavior is unverified.

Fix: notification links cut 65-character SOS ids to 64, so a tap opened a thread that matched no SOS and stayed on `Loading SOS`. SOS ids are now kept whole, and over-length ids fall back to the SOS hub. This also repairs step 5 of the [SOS message notifications card](sos-message-notifications-card.md).

## Build identity

Branch `fix/sos-notification-deeplink`. Record the commit, version and APK hash used.

## Devices and setup

Debug builds on phones A and B, notifications allowed, background mesh on for B. The user installs, launches and operates the phones.

## Steps and expected results

1. **Chat, warm:** raise an SOS on A. On B, silence it and press Home. Send text from A, then tap the `SOS chat` notification on B. Expect the SOS chat titled with A's name and type, with the SOS header.
2. **Chat, cold:** swipe B's app away while the mesh service keeps running, send again from A, then tap the notification. Expect the same chat.
3. **Alarm:** raise a new SOS on A while B is on the home screen. Tap the alert notification on B. Expect that SOS thread, not `Loading SOS`.
4. **Map action:** repeat step 3 and tap `VIEW TACTICAL MAP`. Expect the same SOS thread.

## Failure indicators

FAIL if `Loading SOS` stays on screen, the header is empty, a different SOS opens, or the app crashes.

## Markers

Search `SOS_EVENT`, `CONVERSATION` and `AndroidRuntime` in the reported window.

## Report

Build identity, devices, Android versions, per-step pass or fail, and any bounded excerpt with no private content.
