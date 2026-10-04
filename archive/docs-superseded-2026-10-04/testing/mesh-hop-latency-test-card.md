> Historical/inactive reference archived 2026-10-04; see [disposition index](../README.md). Original claims, hashes and instructions are not current authorization.

# Text and recorded-note mesh latency

This card records the earlier latency baseline. Use the [stability card](mesh-stability-test-card.md) and its matching APKs for the newer connection/custody changes.

Status: implementation locally checked; physical results **UNTESTED**. Users install and operate phones. Do not clear app data: preserve identity, keys, and reconnect history.

## Build identity

Version 1.0.1/code 2, branch `fix/bluetooth-recovery-cluster-bridging-and-sos-ux`, base `b096ecaf88a7d63c6e65d5a58acffbc47ad51f39`. Both APKs include the current uncommitted working tree. Install matching APKs on all participants; never compare mixed builds.

- Preferred build: `app/build/mesh-latency-qa/resqmesh-l2cap-preferred.apk`.
- Diagnostic build: `app/build/mesh-latency-qa/resqmesh-gatt-only.apk`, built with `-PmeshForceGatt=true`. It suppresses L2CAP listener/setup only in debug; GATT still negotiates MTU. Return all phones to the preferred build afterward.

Diagnostic SHA-256: `B4458500B566AF36E240C19AC17E2BB77E09CC7FB0C1AF8A8D440F02B0520099`.
Preferred SHA-256: `12C6FD40F96520F79D105E72C73FDFF66A76EF0B67C5DB9076CC86E3ECA38245`.

## Devices and setup

Use A (sender), B (relay), C (recipient). Record model/API, battery mode, foreground/background, installed hash, and stable node IDs. Keep phones foreground, Bluetooth enabled, and battery restrictions unchanged for the initial comparison.

First test A↔B directly. Then arrange A↔B↔C so A/C have no direct payload-ready link. Use the existing mutual direct-link block procedure if distance cannot isolate them; check both endpoints report a relay route through B. A hidden direct link invalidates the relay result. Wait for READY identities/keys, usable routes, and settled L2CAP setup. Avoid Refresh during timed sends.

Start focused capture on the selected attached devices:

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10
```

Record the role/next hop for every leg. Verify `FRAME_* transport=L2CAP` for preferred payloads; an open socket or a `GATT+L2CAP` UI label alone does not establish the actual transport. Diagnostic payloads must report GATT. Record negotiated `chunkBytes`; 20-byte fallback is allowed when MTU negotiation fails.

## Exact runs

1. **Quiet text:** direct pair, then one relay, in both directions. Send 30 numbered private texts per topology/direction, one at a time after confirmation. Record Send→arrival and Send→DELIVERED separately, missing/duplicate messages, p50/p95/max, dispatch rejection, and unexpected disconnects.
2. **Quiet notes:** each topology/direction, send ten five-second notes and ten ten-second notes in private chat, Radio, and Community wherever recorded notes are supported by the current UI. Start timing when recording ends/Send is pressed, not when recording starts. Wait for arrival before the next note. Record arrival and playback delay separately; check complete audio once, correct destination/channel, and private confirmation.
3. **Mixed load:** through B, send a ten-second note, immediately send five short texts and one SOS. Repeat five times, including private and Radio notes. Check control/text precede queued bulk frames; an active frame must remain intact. No bulk starvation, corrupt notes, false DELIVERED, or ordinary-pressure link retirement.
4. **Temporary relay rejection:** during a note burst, send text and receipt traffic. Observe retained work retrying on capacity/READY for at most ten seconds. Verify accepted named public neighbors are not resent. Repeat both directions.
5. **Relay loss/recovery:** after A accepts a private send, take B offline, then restore it without clearing data. Repeat five times. Check route withdrawal/recovery, bounded attempts, one stored logical message, receipt replay, and no private broadcast. Timers/events from older transmissions must not fail a confirmed message.
6. Install the GATT-only diagnostic build on all three phones and repeat runs 1–3. Record results separately; negotiated GATT may still be slower. Then restore the preferred build. Do not treat diagnostic fallback timings as the L2CAP target.

## Expected results and failure indicators

For settled healthy L2CAP, initial investigation targets are private-text arrival p95 ≤2 seconds and short-note arrival p95 ≤5 seconds after Send. These are test goals, not measured guarantees or release claims. Recording/encoding, queue wait, transfer, relay storage, receipt, and playback are separate costs. Any unmet goal needs the relevant timeline.

SENT proves local acceptance only. A local `FRAME_COMPLETED` is not recipient delivery. Private DELIVERED requires a receipt after recipient persistence. Receipt wait is 15 seconds after origin frame completion; queue wait is capped at 30 seconds and private transmission at 60 seconds from first STARTED. GATT chunk stalls retain the four-second watchdog. Three accepted attempts retain five-second backoff. Relay retention is bounded and may expire during longer outages; it is not a durable relay outbox.

Failures: repeated 20-second retry spikes on healthy quiet links, setup overlapping GATT writes, repeated transport churn, silent forwarding rejection, corrupt/duplicate audio, duplicate logical private rows, failed status overwriting confirmation, starvation, wrong channel, or private broadcast. An active whole note can still delay text/SOS; report its bytes and actual transport before proposing streaming fragmentation.

## Important markers and analysis

Filter `PAYLOAD_SETUP`, `FRAME_QUEUED`, `FRAME_STARTED`, `FRAME_COMPLETED`, `FRAME_FAILED`, `FRAME_RECEIVED`, `RELAY_RETAINED`, `RELAY_RETRY`, `RELAY_EXPIRED`, `RELAY_OVERFLOW`, `PRIVATE_PERSISTED`, `PRIVATE_DISPATCH`, `PRIVATE_RELAY`, `PRIVATE_STORED`, `PRIVATE_RECEIPT`, `PRIVATE_RETRY`, `CONVERSATION_STORED`, READY, and MTU. Timings are monotonic within each device; do not subtract unrelated device clocks. Use synchronized video/manual timestamps for end-to-end timing and local stage durations for attribution. Omit message/audio content, ciphertext previews, keys, and credentials.

Report: APK hash; A/B/C models/API; topology and actual per-leg transport; run/direction/channel; sent/arrived/confirmed/duplicates; p50/p95/max; note bytes/duration; churn/rejections; failure timestamp and capture folder. Mark every run PASS/FAIL/UNTESTED. No emulator or local suite establishes physical BLE performance.

## Local verification

Preferred and diagnostic debug APK builds, all 317 unit tests (zero failures/errors/skips), Android-test Kotlin compilation, Lint (zero errors, 147 warnings, three hints), docs, and diff checks passed on 2026-10-03. This task adds 24 tests beyond the 293-test immediate-dispatch baseline. The initial combined Lint run was cancelled for slow analysis; the final single-worker run with 4 GiB build heap passed. No phone/emulator runtime tests were run for these changes.
