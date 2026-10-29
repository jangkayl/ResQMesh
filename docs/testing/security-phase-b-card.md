# Security Phase B card

Prepared: 2026-10-18. Status: not run. At the branch tip, the local unit, lint, detekt, androidTest-compile, R8 release, docs and boundary checks pass, and the golden wire tests are unmodified. Covers Phase B of the [security hardening plan](../plans/security-hardening.md): bounded framing, GATT server and L2CAP reads, the inbound validator, the expiring dedup cache, origin and link rate limits, single-flight sync handlers, the per-peer transfer quota, bounded maps, topology and key checks, and strict advertisements. Built from branch `feat/security-phase-b`. The wire format is unchanged, so Phase B builds should interoperate with candidate #2 (`42f1660`). Step 9 checks that.

This branch is not part of the demo freeze. Do not install it on demo phones until the freeze ends or the user approves.

## Build identity

- **Debug:** `scripts\device\manage_devices.ps1`, option 5, from `feat/security-phase-b`. Record the commit and APK SHA-256.
- **Release:** `.\gradlew.bat :app:assembleRelease` with `keystore.properties`. Record the SHA-256. New classes go through R8, so step 8 matters.

## Devices and setup

At least three phones, A, B and C, in a line (A–B–C, with A and C out of direct range if possible). A fourth phone D on candidate #2 is optional for step 9. Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 20` on the debug run.

## Steps and expected results

1. **Normal traffic, direct:** A↔B public text, private text (DELIVERED), an image, and a voice note of about 60 s. Expected: everything arrives, with no `INBOUND_REJECT`, `RL_DROP` or `LINK_SHAPE` markers.
2. **Relayed traffic:** the same set A→C through B. Expected: it arrives on C, and B logs no `RL_DROP` or `RL_COOLDOWN`.
3. **Chat burst:** on A, send 25 messages as fast as possible in public, then private. Expected: the counter drops to 0, Send shows "Wait N s" and refills about +1 per 3 s, every message A sends arrives on B, B logs no `RL_DROP class=CHAT` or `RL_COOLDOWN`, and A stays READY on B.
4. **SOS:** raise an SOS on A, let the location refine, then end it. Expected: B and C show raise, update and end. Then raise a new SOS on C. Expected: it arrives on A even though A just raised one. Then, with B out of range, raise an SOS on A and bring B back. Expected: B alarms once after sync (notification and full-screen alarm).
5. **SOS re-raise:** on A, raise and end 5 times quickly. Expected: B (and C through B) alarms for all 5 live, with no `RL_DROP class=SOS_NEW` or `class=SOS_UPDATE`. The hub counter on A then shows "0 of 5 left", and the raise button is disabled and reads "Wait N s". After about 12 s it shows "1 of 5 left" and can be raised. Report it either way.
6. **Reconnect and MAC rotation:** turn Bluetooth off and on on B, and also leave B idle for at least 15 minutes. Expected: A and C re-admit B under the same name, with no `ADV_EVICT_BLOCKED` loop and no lasting duplicate peer.
7. **App restart:** force-stop and reopen B. Expected: routes through B return within the usual time, and no `TOPO_REJECT` appears on A or C.
8. **Release smoke test:** release APK on two phones: READY, public and private text, an SOS, a voice note and an incident sync.
9. **Mixed builds (optional):** D on candidate #2 exchanges public and private text and an SOS with A. Expected: both directions work.

## Failure indicators and report

Report a FAIL for any of these:
- a crash
- a valid message, receipt, SOS, image or voice note that never arrives
- `INBOUND_REJECT`, `RL_COOLDOWN`, `L2CAP_READ_TIMEOUT`, `L2CAP_QUEUE_STALL`, `TRANSFER_OVERFLOW`, `KEY_REJECT`, `TOPO_REJECT` or `ADV_EVICT_BLOCKED` during normal traffic
- a peer that stays READY-less after reconnect

Markers to grep: `INBOUND_REJECT`, `RL_DROP`, `RL_COOLDOWN`, `LINK_SHAPE`, `L2CAP_READ_TIMEOUT`, `L2CAP_QUEUE_STALL`, `TRANSFER_OVERFLOW`, `SYNC_COALESCED`, `KEY_REJECT`, `TOPO_REJECT`, `ADV_EVICT_BLOCKED`.

Report per step: PASS, FAIL or UNTESTED, the build identities, phone models, the topology, and the capture folder. Never share message content or keys.

Adversarial cases (floods, spoofed origins, malformed frames) need a modified client and are not covered here. Local unit tests are the only evidence for them so far.
