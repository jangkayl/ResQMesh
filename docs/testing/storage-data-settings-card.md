# Storage & data settings: phone card

Status: active procedure. Confirms Settings → Storage & data (D17). Local JVM tests do not prove device behavior.

## Build identity

Branch `feat/storage-data-settings`. Record the commit, version and APK hash used.

## Devices and setup

Phones A and B on the same debug build, payload READY, with some chat history, at least one voice note and image each, and an offline map installed on A. The user installs, launches and operates the phones. Reset is destructive: use test phones only.

## Clear cache (A)

1. Open Settings → Storage & data. Expect "Clear cache" to show a size (KB/MB), not stay on "Calculating…".
2. Record and send a voice note, then return to Settings. Expect the size to be the same or larger.
3. Tap Clear cache → Cancel. Expect nothing to change.
4. Tap Clear cache → Clear. Expect "Cache cleared (… freed)" and the size near 0 B.
5. Open chats. Expect all messages, voice notes and images still to play and display; the offline map still to render; the node name unchanged.
6. Record and send another voice note. Expect it to work normally.

## Reset blocked during SOS (A)

7. Raise an SOS on A, then open Reset app. Expect the red SOS warning, the text field disabled and "Delete everything" disabled.
8. End the SOS, reopen Reset app. Expect the field enabled.

## Reset (A, observed from B)

9. Note A's node ID/name as shown on B. In Reset app type `reset` (lowercase). Expect the button disabled; type `RESET`, expect it enabled.
10. Tap Delete everything. Expect a "Resetting ResQMesh…" dialog with a spinner that Back and outside taps cannot dismiss, then the app to close about 1.5 s later.
11. Relaunch A. Expect the identity setup screen and first-launch guide; no chats, incidents, blocked list or offline map.
12. Finish setup and start the mesh. On B, expect A to appear as a new peer (different node ID); the old A entry goes grey/offline.
13. Send a public text both ways. Expect delivery. Attempt a private message both ways; record whether it works or fails closed (TRUST-01, key change).

## Failure indicators

FAIL for a crash, cache clear removing messages/maps/identity, reset possible during an active SOS, the app staying open with old data after reset, or the old node ID returning after reset.

## Markers

`AndroidRuntime` and `MediaHelper` in the reported window; on B, peer identity/key-change lines for A.

## Report

Build identity, phone models and Android versions, cache sizes before/after, per-step pass or fail, A's old and new node ID prefixes, private-message result in step 13.
