> Historical/inactive reference archived 2026-10-04; see [disposition index](../README.md). Original claims, hashes and instructions are not current authorization.

# DIRECT and mesh-hop private timing/recovery phone card

For current APK hashes, transport-completion deadlines, and text/note comparison, use the [mesh latency card](mesh-hop-latency-test-card.md). This card retains earlier immediate-dispatch/reconnect cases; recorded hashes/check counts below are historical.

## Build and devices

Build: debug, version 1.0.1/code 2, branch `fix/bluetooth-recovery-cluster-bridging-and-sos-ux`, working-tree private reconnect and immediate-dispatch fix.

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Base commit: `b096ecaf88a7d63c6e65d5a58acffbc47ad51f39`; this fix is an uncommitted working-tree change.
- Previous batching APK SHA-256: `D126778B55950FE8C19EABDE8B8DE4ECB25BA7B90F493AA7CD67105A3675117E` (comparison baseline only).
- Immediate-dispatch baseline SHA-256: `CF61EBFA86DE5EF815D8BBDE934E447B9157B628DE98391826DBFC825BD85793`.
- New local checks: 293 unit tests passed (zero failures/errors/skips), debug APK and Android-test compilation passed, Lint passed (zero errors/147 existing warnings), docs/diff checks passed. Six added private timing tests and one public backoff test cover zero-delay direct/mesh dispatch, concurrent wakeups, deadline isolation, and exact retry timing.
- Earlier persistence fix: eight Room/encrypted-envelope tests passed on selected `Medium_Phone`, API 37. These precede the timing refinement. Physical BLE results remain pending.

Use two physical phones A/B with the same APK. Record models, Android/API, APK SHA-256, and test start time. Installation and phone operation belong to the user. Upgrade without clearing data; retain identities, key approvals, messages, and blocks. Both peers must be locally unblocked and use private chats for the same stable identities. Keep a third phone offline for the two-phone baseline.

## Quiet-link latency comparison

1. If the previous matching batching APK is already installed, measure it before upgrading. Retain its APK if available. Do not downgrade the new app or clear data just to obtain a baseline; label comparison unavailable when missing. Use the same phones, distance, foreground state, and short text size. Stop voice/image traffic and let offline queues drain.
2. DIRECT A–B: wait for READY and trusted keys, then send 30 numbered short texts each way, one at a time after confirmation. Record tap-to-recipient-display and tap-to-DELIVERED/READ separately. Record median/p95 per direction and all retries/rejections. A video showing both screens provides a common clock; do not subtract timestamps or monotonic clocks from different phones.
3. After upgrading both phones to the matching new hash, repeat the DIRECT run without clearing data. Expect removal of the deliberate 1–2.5-second sender wait. Require one recipient row per message and eventual DELIVERED/READ. Any remaining quiet-link 1–5-second delay needs a trace; do not call it resolved from a faster average alone.
4. Three phones: record C's model/API/hash and arrange A–C–B with no A–B READY link. Verify the recorded directed path before testing; a third connected phone alone does not establish relay. Repeat 30 texts each way on matching APKs. Compare the old relay baseline only if previously recorded under the same arrangement. Expect immediate origin dispatch, actual relay forwarding, one recipient row, and confirmation. Radio/hop transmission time remains.
5. While an older private message is waiting for retry, send a new short text on a usable route. Expect the new message to dispatch immediately; the older one must retain its five-second backoff. Also queue a Community/Radio message before private sends: public recovery keeps its own jitter and must not create a private-send pause. Record spontaneous occurrences; do not intentionally create unexplained key changes.

`PRIVATE_PERSISTED` → `PRIVATE_DISPATCH` elapsed deltas and `sincePersistMs` measure local scheduling/encryption/admission, not physical delivery. `PRIVATE_RETRY` → `PRIVATE_RETRY_ELIGIBLE` should retain five-second backoff. `PRIVATE_STORED` reports local persistence time and INSERTED/DUPLICATE. `PRIVATE_RELAY` records each next-hop result. `elapsedMs` is monotonic only within one phone/runtime; `sincePersistMs=-1` denotes a recovered row without a current-process persistence marker. No artificial batching interval is acceptable; Bluetooth queueing and Room/encryption costs still apply.

## Steps and expectations

1. Start both mesh sessions. Wait until each shows the other as DIRECT. Open both private chats. Send ten numbered texts A→B, then ten B→A, waiting for each receipt. Each arrives once and reaches DELIVERED or READ within 15 seconds under quiet conditions.
2. With both chats open, send five texts from each phone at approximately the same time. Expect ten distinct recipient rows and confirmation both ways; no stuck PENDING, SENT, or unexpected FAILED.
3. Repeat five reconnection cycles: turn B's Bluetooth off, queue two texts on A, restore B's Bluetooth, and wait for DIRECT on both. Send B→A immediately, then A→B. Queued messages deliver once automatically, and both new directions confirm without Refresh. Record time to DIRECT and time to confirmation separately.
4. Repeat with A's Bluetooth off to reverse client/server opportunities. Queue B→A while disconnected, reconnect, and verify automatic delivery. Reconnection may retain the same role; report each recorded READY role rather than assuming reversal.
5. Queue a message with B offline, then close/reopen A without clearing data. Reconnect B. Expect the saved message to resume. A previously SENT but unconfirmed private row also resumes after process restart; existing FAILED rows remain terminal. Restart recovery has a fresh three-attempt budget.
6. If a message arrives but remains SENT, retain both chats and wait up to 75 seconds. A retry must create no duplicate row or notification. A returned receipt stops retries. Three accepted attempts without any receipt may produce FAILED; record this as a test failure for the quiet baseline, not a passing delivery.
7. After the three-phone latency run, reconnect C and send five texts each way. Expect directed relay, one recipient row, returning receipts, and no private broadcast. Also smoke-test Community and SOS using their existing cards.

Expect PENDING only while waiting for a usable route/key or transport admission. SENT means local transport acceptance; it does not prove delivery. Key-change approval remains required: do not approve an unexplained fingerprint change merely to make the test pass.

## Capture and report

Run `scripts/capture_ble_logcat.ps1 -DurationMinutes 10` while the selected ADB phones are connected. Capture both sides and C for relay failures. Focus on READY/role/generation, `READY_IDENTITY`, `SYSTEM_SENT`, `SYSTEM_RECEIVED`, key observation, `PRIVATE_PERSISTED`, `PRIVATE_DISPATCH`, `PRIVATE_RETRY`, `PRIVATE_RETRY_ELIGIBLE`, `PRIVATE_RELAY`, `PRIVATE_STORED`, `PRIVATE_RECEIPT`, `PRIVATE_DELIVERED`, `PRIVATE_FAILED`, E2EE success/failure, and GATT/L2CAP completion/timeouts. Do not share message content, ciphertext previews, or keys.

Report APK hash, models/API, topology, arrival/confirmation median/p95, exact failure timestamp/direction, whether the recipient displayed the message, sender status, duplicate/notification count, reconnection/receipt timing, and capture folder. Local tests and emulator Room checks do not establish physical BLE reliability.
