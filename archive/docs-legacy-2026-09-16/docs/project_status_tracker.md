---
tags: [status, issues, current]
---
# Project status and issue tracker

Baseline: temp at d7cd5e3 with uncommitted source edits as of 2026-09-15. A source review is evidence of a code path, not proof that it caused a specific phone failure. Record device traces and test results before closing items.

## Repair now

| ID | Priority | Evidence in source | Completion evidence |
| --- | --- | --- | --- |
| BLE-01 | P0 | Server notify queue advances after 20 ms; no onNotificationSent handler | One notification in flight; callback advances the queue once; two-phone traffic test |
| BLE-02 | P0 | Server disconnect does not clear pendingQueues/isWriting and other per-link state | Fresh state after reconnect; repeated disconnect test |
| BLE-03 | P0 | Client cleanup is MAC-keyed without GATT instance/generation checks | Late callback cannot remove a newer connection |
| BLE-04 | P0 | Connect timeout releases a lock without closing the specific pending GATT | Timed-out GATT is closed; late callback ignored |
| BLE-05 | P0 | CCCD/setup and payload traffic can overlap; sends rely on fixed delays | Setup reaches READY via callbacks or bounded recovery; no overlapping GATT operations |
| BLE-06 | P1 | Two direct-link constants differ: 3 and 4 | One documented limit and admission path |
| BLE-07 | P1 | L2CAP selection and interaction watchdog may amplify a stalled queue | Forced socket-failure and stalled-heartbeat tests |

Detailed repair order: [BLE repair pipeline](ble_repair_pipeline.md).

## Additional findings from current source review

These paths were rechecked on 2026-09-15. `:app:assembleDebug` and `:app:testDebugUnitTest` passed, but checked-in tests still cover only template examples. None of the items below has physical-device validation.

| ID | Priority | Evidence in source | Proposed fix and completion evidence |
| --- | --- | --- | --- |
| PARSE-01 | P0 | Both GATT reassembly loops read a signed frame length and append to buffers without a maximum; the L2CAP path alone has a 10 MB length check | Use one bounded frame decoder for both GATT roles; reject nonpositive/oversized lengths and cap buffered bytes; malformed-frame tests |
| SEC-02 | P0 | `PayloadFactory.buildPrivatePayload()` sends plaintext when `targetPubKey` is null, while encryption failure can produce an `isEncrypted` payload with null ciphertext/key | Define private-send policy; queue or fail closed until a trusted recipient key is available; test missing key and encryption failure |
| ROUTE-01 | P1 | `MeshRouter` expires graph entries after 10 s, while unchanged topology sends a full `SYSTEM` pulse only every 60 s; `PING` does not refresh indirect nodes | Align graph expiry with topology refresh/lease semantics; test stable three-phone route for several minutes |
| ROUTE-02 | P1 | `SystemPulseHandler` forwards `connectedNodes` only when the list is nonempty, so an empty topology announcement cannot replace an older neighbor list | Always process a topology snapshot, including empty; test neighbor removal and route withdrawal |
| SEC-03 | P1 | Decrypted private text is written to Logcat and the in-app terminal; Room stores private text/media/location while `allowBackup=true` and backup rules are templates | Remove private-content logs and define a deliberate backup policy; verify log and backup outputs contain no sensitive content |
| ROUTE-03 | P2 | Dedupe retains at most 500 message IDs and handlers relay messages without a hop/expiry limit | Add bounded lifetime/hop count and origin-scoped dedupe; test looped and delayed duplicates |
| TEST-02 | P1 | Checked-in unit/instrumented tests are template arithmetic/package-name checks | Add focused state-machine, queue, frame, routing-expiry, and private-send-policy tests as those components are repaired |
| L2CAP-01 | P1 | `NativeBleManager.sendDirectPayload()` starts a new thread for each L2CAP send, returns immediately, and only logs write failure | Use one bounded per-socket send queue; close/retire a failed socket and retry or fail through a defined GATT fallback; stress-test ordering and failures |
| L2CAP-02 | P1 | L2CAP reader `finally` removes a MAC-keyed socket without checking that it is the socket it opened | Remove by socket identity/generation so an old reader cannot erase a replacement socket; reconnect test |
| SEC-04 | P1 | Encrypted private envelopes include text/media JSON but omit requested `locationLat`/`locationLng`; plaintext fallback includes coordinates | Include location in the authenticated encrypted content and decode it at the recipient; private-location tests for key-present/missing paths |
| SOS-01 | P1 | Any received SOS cancel clears the single current alert, regardless of sender or alert ID; an asynchronous location update may still send after local cancel | Bind cancellation and follow-up updates to the active SOS ID/sender and state; test concurrent alerts and cancel-before-location-callback |

## Partial working-tree changes to verify

- Stable NodeIdentity from d7cd5e3 replaces the earlier fuzzy name matching. Do not reintroduce name.take(15) as the primary identity rule.
- Uncommitted changes add identity-aware duplicate-link checks and distinct-peer counts. Test simultaneous inbound/outbound links across different BLE MACs.
- Uncommitted changes add connect-lock and handshake watchdogs plus MTU fallback. These may prevent a stranded lock; they do not yet close a timed-out GATT attempt or guard old callbacks.
- Radar and repository changes are uncommitted. Confirm their UI state reflects the selected physical link after collision and reconnect.

## Separate capstone/documentation work

- SEC-01: RSA/AES-GCM private-message envelopes exist. Public-key authentication, node identity binding, and per-session forward secrecy are not established.
- DOC-01: Avoid Nearby Connections, Wi-Fi Direct, Bluetooth 5.4, full distance-vector routing, guaranteed self-healing, and fixed range claims for the current checkout.
- CLEAN-01: Confirm whether standalone advertiser/scanner classes are unused before removing them; a file's presence alone does not define the active network path.
- TEST-01: Measure two-, three-, five-, and ten-phone behavior, hop delivery, reconnect rate, latency, battery use, and hardware-specific failures.

## Status rule

An item is complete only when source behavior, a passing build/focused test, and the relevant physical-device test agree. Keep logs, device versions, test steps, and results alongside the closure note.
