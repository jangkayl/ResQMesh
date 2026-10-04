> Historical/inactive reference archived 2026-10-04; see [disposition index](../README.md). Original claims, hashes and instructions are not current authorization.

# Direct sending and transfer recovery

Local: 362 unit tests, debug build, Android-test compilation and Lint passed (zero errors; 147 warnings, three hints). Physical results **UNTESTED**. Retained CPH2127 logs at 2026-10-03 13:38 showed a 1,734-byte transfer repeatedly querying status without starting pieces before expiry; another frame queued 24.8 seconds. This bounds concrete stalls, not every radio-delay cause. The earlier four-phone capture `captures/ble-logcat/20261003-113611/` remains the pre-fix link-churn baseline; voice-related causality was not established.

This build sends serialized MESSAGE/CONVERSATION payloads ≤4 KiB as ordinary frames. Larger payloads/durable relay work retain pieces. Owned/open L2CAP wins endpoint selection; READY GATT remains fallback. Fresh transfers start data, reconnect queries retain request IDs, unchanged selected ownership ignores alternate identity pulses, and active transfers rotate turns.

## Build identity

Version 1.0.1/code 2, branch `fix/bluetooth-recovery-cluster-bridging-and-sos-ux`, base `b096ecaf88a7d63c6e65d5a58acffbc47ad51f39`, current uncommitted source. The older latency APK is not this build.

- Preferred: `app/build/direct-send-qa/resqmesh-direct-fast.apk`, SHA-256 `8C68EA53583563AA01D8C11EA81F2B2A40A679C3C21AAB442485B0D565BE84A5`. L2CAP preferred; GATT fallback.
- Diagnostic: `app/build/direct-send-qa/resqmesh-direct-fast-gatt-only.apk`, SHA-256 `8B5097832CD451A042415EA4B8A3C810468C491731BD92199BA963276D0E60F4`. GATT only; separately built with `-PmeshForceGatt=true`.

Install matching builds on every participant. Users install, launch, stop and operate physical phones. Preserve app data, identities, trusted keys and conversations.

## Devices and setup

A/B/C/D: V2424/API 34, CPH2127/API 31, CPH2219/API 31, SM-P615/API 33. Add E when available; record its model/API. Do not claim five-phone evidence from four phones.

Initially keep phones nearby, foreground, Bluetooth enabled and battery settings unchanged. Record stable IDs and the installed SHA-256. Avoid Refresh during timed runs. Distinguish radio connected, payload READY and routed peers. A green connection alone is insufficient.

Capture each run: `powershell -ExecutionPolicy Bypass -File .\scripts\capture_ble_logcat.ps1 -DurationMinutes 10`. Record wall-clock failure times; local FRAME timings are monotonic and must not be subtracted across unrelated phone clocks.

## Exact runs

1. **Two phones first:** A/B join and settle. Wait ten minutes without sending or Refresh; record READY peers and unexpected churn. Complete runs 2/3 on this pair before adding C/D. Repeat idle and runs 2/3 on four phones, then E when available. Five phones need connected routes, not four direct neighbors each.
2. **One sender:** send 30 numbered private texts and ten five-second notes, one at a time, in each direction. Repeat Radio and Community notes. Count arrivals and private confirmations separately; play every note and check missing, truncated or duplicate audio. Small direct envelopes must have ordinary FRAME events without TRANSFER_RETAINED/status exchanges; note duration alone does not establish serialized size.
3. **Mixed traffic:** send a ten-second note, immediately five texts and an SOS; repeat five times. Then have two phones send notes simultaneously and send a second note while the first is active. Text/second-note progress must occur before the older large note finishes. Repeated identity pulses from alternate endpoints must not repeatedly log PEER_PATH_SELECTED with resume=true for unchanged ownership. Require no permanent blockage, false DELIVERED or pressure-driven retirement.
4. **Joining during voice:** while A/B send voice, join C; repeat with D on A/B/C. Compare with settled links. Optional outbound attempts may defer at most five seconds; isolation recovery must continue. Do not assume inbound attempts are prevented. With two READY endpoints for one peer, confirm payload FRAME transport uses owned/open L2CAP when available.
5. **Forced mesh hops:** establish A↔B↔C, then A↔B↔C↔D, using the existing mutual direct-block procedure or physical separation to prevent endpoint shortcuts. Verify actual first hops and every leg's transport. Send 30 texts and ten notes each direction; receipts must return, private payloads must never broadcast.
6. **Relay loss:** during a note take B offline, wait 45 seconds, and restore it. Repeat five times. Verify route withdrawal/recovery, stored next-hop work and missing-piece status queries. Origin attempts retain bounded deadlines and may fail explicitly; durable relay custody is distinct from the sender's retry policy.
7. **Restart recovery:** after a relay has logged TRANSFER_REASSEMBLED/TRANSFER_RETAINED and before onward completion, stop/reopen it normally without clearing data; restore its session. Repeat with the receiving phone after some pieces arrive. Confirm retained records reload, missing pieces resume, one logical message presents, and receipts replay. Go offline must stop radio work; fully stopped apps must not auto-join.
8. **Fallback:** install the matching GATT-only diagnostic build on all phones; repeat idle, one-sender, mixed, joining and relay-loss runs. Restore preferred builds afterward. Protocol-1 segmentation also uses GATT; measure its ACK/storage overhead separately.
9. **Background:** with background mesh explicitly enabled, repeat a note/text/SOS transfer with a relay locked for ten minutes. Then Go offline; expect no transport work. Record heat, battery change, permission/process events and recovery. Background disabled and process death are separate runs.

## Expected results and failures

Require intact audio, one stored logical message, honest arrival/confirmation, usable routes and no repeated retirement of the same unfinished generation. No permanent query/queue stalls after recovery. A query queued over three seconds must still accept its valid response; a genuine replacement must ignore the previous reconciliation's reply. Investigate unexpected teardown; no Refresh workaround qualifies as a pass.

Use the latency baseline's investigation targets only on settled preferred links: private text arrival p95 ≤2 seconds, short-note arrival p95 ≤5 seconds after Send. These are goals, not measured promises. Record actual values and note sizes; GATT fallback and relay count can change timing. Stop load escalation after a reproducible failure; advance beyond five phones only after smaller runs pass.

Custody ACK proves a peer journaled the complete envelope, not recipient decryption/storage or delivery. Only private application receipts prove private DELIVERED. Protocol-1 relays retain accepted work up to 24 hours subject to quotas/expiry; legacy peers retain the older short-lived path. New joining phones are not retroactively added to a frozen public forwarding roster. Sender restart may create a fresh wire attempt; it still deduplicates the logical chat message.

Server callbacks lack an attempt token. The 30-second same-endpoint cancellation quarantine fences normal delayed callbacks; very late callbacks, changed endpoint addresses, OEM stack behavior and actual radio loss still require physical evidence.

## Important markers and report

Filter READY/CONFIGURING, PEER_PATH_SELECTED, FRAME_QUEUED/STARTED/COMPLETED/FAILED, LINK_RETIRED/DISCONNECTED, SERVER_RETIREMENT_ACK/BACKSTOP, BOOTSTRAP_SETUP_FAILED, CONNECT_DEFERRED_TRANSFER, TRANSFER_RETAINED/FRAME_*/REASSEMBLED/CUSTODY_ACK/EXPIRED/HASH_FAILED/OVERFLOW/STORAGE_FAILED, PRIVATE_RECEIPT/DELIVERED and CONVERSATION_STORED. TRANSFER_FRAME_* identifies status versus data pieces and actual GATT/L2CAP leg; SEGMENTED FRAME_* identifies custody progress. No message/audio content, ciphertext previews or key material.

Report APK hash; phone roles/models/API; topology and actual transport; PASS/FAIL/UNTESTED; sent/arrived/confirmed/missing/duplicate counts; note duration/bytes; p50/p95/max arrival and confirmation; churn and recovery; failure times and capture folder. Local unit/build/Lint checks are not BLE proof.
