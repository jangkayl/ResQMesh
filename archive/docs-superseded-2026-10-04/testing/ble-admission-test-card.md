> Historical/inactive reference archived 2026-10-04; see [disposition index](../README.md). Original claims, hashes and instructions are not current authorization.

# BLE identity admission and receipt guards

Status: **UNTESTED on physical phones**. Local checks are recorded below; they do not establish BLE timing, interoperability or security.

## Build identity

- Date: 2026-10-03; version 1.0.1/code 2, debug signing.
- Base commit: `b096ecaf88a7d63c6e65d5a58acffbc47ad51f39`, plus the existing working tree and this batch. APK hashes identify the actual candidate.
- Normal candidate: `app/build/ble-admission-qa/resqmesh-ble-admission-debug.apk`; SHA-256 `B9C5C6FD0BEF67D5285BA3624A7A8E9FCBA20B3182079AFDAE7A77C0E6A0121E`.
- GATT fallback candidate: `app/build/ble-admission-qa/resqmesh-ble-admission-gatt-only.apk`; SHA-256 `6523535944340A9175A99257E41D175207565564A2307D087E8E546D1C8C460F`. Built with `-PmeshForceGatt=true`.
- Local PASS: 381 unit tests (zero failures/errors/skips) on both configurations, debug builds, normal Android-test compilation, Lint (zero errors; 147 warnings), docs and diff checks. Normal configuration was restored. Room receipt instrumentation is compiled, not executed.

## Devices and setup

Use A and B first, then relay B between A and C. Record model, Android/API and installed APK hash for every phone. Install matching candidates, preserve app data/keys, enable Bluetooth and permissions, and keep apps foreground initially. The user installs, joins and operates the phones. Use distinct names and synchronized timestamps. Capture application markers with the existing Logcat script; omit message content and keys.

## Steps and expected results

1. Join A, then B. Record join → GATT READY → IDENTITY_ADMITTED → first text arrival → recipient receipt. While configuring, the peer must not become a usable route or cause private sends to become SENT. Queued private messages should flush after admission. A transport acceptance is not recipient delivery.
2. Send five private texts each direction. Rename B and repeat. Each logical message produces one recipient row, and intended-recipient receipts confirm it despite the rename. Open the recipient thread to check SEEN.
3. Leave and rejoin B ten times without clearing data. Repeat two Bluetooth OFF→ON cycles while joined. Each generation must exchange identity again; retired attempts must not admit or disconnect the replacement. Record every timeout and recovery interval; do not treat a 30-second deadline as a promised connect time.
4. Block A↔B using the existing block flow, then rejoin/Refresh. A blocked identity must not bind or carry ordinary direct traffic. Unblock on both phones and repeat step 2. Keep any separate relay policy unchanged.
5. Send a 10-second recorded note, then one up to 30 seconds, with text sent during the transfer. Verify recipient playback and receipts. Record arrival and confirmation separately, plus whether L2CAP was selected. Report stalls, duplicates, queue rejection and churn.
6. Repeat steps 1–3 and 5 on matching GATT-only candidates. Identity must complete before MTU setup. No L2CAP promotion should appear. Restore matching normal candidates afterward.
7. With A–B–C arranged so A/C depend on B, send five private texts each way and one recorded note. Remove B during a pending send, then restore it. Verify route recovery, one recipient row and directed receipts; no private broadcast. Stop escalation after a reproducible failure.
8. Optional compatibility run: upgraded A and previously installed B. Require `legacy=true` admission, text/receipt exchange and GATT fallback; record old APK identity. This path has no identity acknowledgement and remains unverified until run.

Malformed-key, wrong-recipient/public-flag receipts, stale exchange tokens and missing ATT callbacks have deterministic regression coverage. Exercising them on phones requires a separate test harness; normal UI steps cannot prove those cases.

## Failure indicators and markers

Failures: ordinary traffic before admission; repeated identity timeout; a replaced link retired by an old callback; missing receipt/playback; a public/wrong-recipient receipt confirming private delivery; silent stalls or duplicate rows.

Capture `READY_IDENTITY`, `FRAME_COMPLETED`, `IDENTITY_ADMITTED`, `IDENTITY_TIMEOUT`, `IDENTITY_INVALID`, `IDENTITY_TRAFFIC_REJECTED`, `PAYLOAD_SETUP`, `ATT_QUARANTINED_L2CAP_PRESERVED`, `L2CAP_REJECTED_BEFORE_IDENTITY`, `SERVER_RETIREMENT_ACK/BACKSTOP`, `LINK_RETIRED`, `PEER_PATH_SELECTED`, `PRIVATE_DELIVERED`, `RECEIPT_REJECTED`, and transfer/receipt stages. Optional ATT timeouts quarantine GATT operations. Healthy owned L2CAP remains usable; socket loss then retires the stalled GATT owner. Without healthy L2CAP, retirement may briefly interrupt the link. SERVER quarantine has a 30-second backstop; later address-only callbacks remain an Android ownership limitation to investigate physically.

Report PASS/FAIL/UNTESTED per step, models/API/hashes, topology, timestamps, sender/recipient counts, playback, recovery time, transport and focused captures. Identity acknowledgement and receipt guards are structural checks, not cryptographic authentication. Signed receipts, persistent recipient ledgers and relay/fairness repairs are later stages.
