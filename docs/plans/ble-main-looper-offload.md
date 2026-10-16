# BLE inbound main-looper offload

Last reviewed: 2026-10-17. Status: Phase 0 measured; Phases 1–4 deferred. Source baseline `a9a27ed`. Reason: user-reported lag when the app opens.

**Phase 0 result:** on two phones, frame and Compose work caused over 90% of main-thread stall time. BLE inbound work was under 3%, and each decrypt took 30–85 ms ([capture](../validation.md)). The offload would not remove the reported lag. A release-like build then felt smooth on both phones, so most of the reported lag was debug-build overhead. Revisit this plan only if later evidence shows BLE stalls.

## Main-looper dependency map

Every BLE state decision runs in one serial domain: the main looper. `NativeBleManager.handler`, `BleRadioController.handler` and `GattClientAttempt.timeoutHandler` are separate handlers on that looper.

| Owner | Main-looper use | Serialized state |
| --- | --- | --- |
| `BleRadioController` | Re-posts scan/advertise callbacks; scan watchdog | Plain `scanGeneration`, `advertiseGeneration`, `scannerOwner`, `scanCallback`, `recoveryAttempts`; atomic `scanActive` |
| `GattClientAttempt` | Connect timeout, discovery retry | Attempt phase, connect lock |
| GATT client/server callbacks | Re-post binder-thread callbacks | `BleLink.receiveBuffer`, `lastGattChunkAt`, link transitions, indication tickets |
| `GattTransferExecutor.processNext`, `NativeOutboundDispatcher.promoteGattWorkToL2cap` | Main-looper trampolines | GATT single-flight, coordinator queues, L2CAP promotion |
| `L2capTransport` threads | Post frames, progress and loss | Socket ownership; `incoming` feeds `isTransportIdle` |
| `BleLifecycleSupervisor` | 5 s tick | Connect-lock backstop, liveness, stale retirement, scan expiry |
| Admission scheduler | Sessions/bootstrap | Election, candidates, handshake gate |
| Repository callbacks | Run synchronously | `_connectedDevices` read-modify-write, `recalculateKnownNodes`; not thread-safe |

The SOS relay already enters off-main (`Dispatchers.IO`); the trampolines re-serialize it.

## Suspected main-thread cost (unmeasured)

- Scan parsing is cheap string work; the cost is downstream admission and repository updates.
- Each inbound frame is decoded twice: once in `NativeInboundPipeline`, again in `PayloadDispatcher`.
- `decryptHybrid` does an AndroidKeyStore RSA private operation. Queued private messages and receipts replay in a burst when the app opens.
- `StandardMessageHandler` decompresses and Base64-encodes public media on main; reassembled transfers also post back to main.

Hypothesis only: open-app lag comes from decrypt, media and repository work, not radio callbacks.

## Options

**(a) Dedicated BLE HandlerThread for the whole stack.**
- Android permits it: `connectGatt` passes no handler, so callbacks already arrive on binder threads.
- Every handler and both looper asserts must move in one change. A partial move races plain fields across two domains.
- Repository callbacks would leave Main and lose `_connectedDevices` updates unless each one is marshalled back.
- A slow decrypt would still stall heartbeats and GATT chunks.
- Test cost: full lifecycle recertification. Rejected for now.

**(b) Keep lifecycle on Main; add one serial inbound prepare lane (recommended).**
- A single-thread executor handles decode, decrypt for this node and public media preparation.
- Main keeps generation/ownership checks, identity admission, dedup, routing, receipts and repository callbacks.
- One FIFO lane for all endpoints keeps per-endpoint order, so a SYSTEM identity pulse still precedes PIECE/ACK gating.
- Captured generation and receiving links are rechecked on return.

**(c) Operational mitigation only.** Measure, remove the duplicate decode and accept the residual lag. Adopted as Phase 0, not as the end state.

Scan and admission stay on Main. If measurement shows scan floods, coalescing identical per-endpoint advertisements is a separate measured step.

## Phases

**Phase 0 — measure (implemented, debug builds only).** No behavior change. Markers go straight to Logcat tag `RESQ_PERF`, bypassing the in-app terminal so they don't perturb Main.
- `MAIN_SLOW ms= target=` for any main-looper dispatch of 32 ms or more.
- `INBOUND_PREP type= bytes= decodeMs= handleMs=` when dispatch takes at least 4 ms.
- `INBOUND_DECRYPT ms= ok=` for every hybrid decrypt.
- `INBOUND_MEDIA kind= bytes= ms=` for public media preparation.
- Proceed only if decrypt, decode or media dominate `MAIN_SLOW` in the open-app burst.

**Phase 1 — single decode.** `PayloadDispatcher.dispatch` accepts the pipeline's decoded payload. Pure refactor; `NativeInboundPipelineTest` and `HybridPrivateRoutingTest` stay green.

**Phase 2 — prepare lane.**
- `processBinaryPayload` captures generation and links on Main, submits to the lane, and posts a `PreparedInbound` back.
- Handlers use prepared values when present and fall back to inline work otherwise.
- Decrypt failure still rejects, and plaintext is never logged.
- Dedup stays on Main; decrypting a duplicate is acceptable.
- L2CAP decrements `incoming` only after the Main-side dispatch.
- Stale results are dropped by the generation check; stop does not kill the lane.

**Phase 3 — tests.** JVM tests with a fake executor and handler:
- FIFO order across mixed decrypt and plain frames.
- SYSTEM then PIECE ordering.
- A generation change mid-lane drops the result.
- Decrypt failure rejects.
- A replacement link on the same endpoint is not retired.

**Phase 4 — phones.** Run the card below before and after, recording `MAIN_SLOW` counts.

Route: crypto plus core inbound path, so planner → executor → tester → auditor. No push or PR without an explicit request.

**Acceptance:**
- No decrypt- or media-attributed `MAIN_SLOW` during the open-app burst.
- Card outcomes match the baseline.
- Unit tests and the docs check pass.

## Validation card outline

The card goes in `docs/testing/` when Phase 2 starts.

- **Build identity:** branch, commit, APK SHA-256, build type.
- **Devices:** A and B (model, API level, StrongBox); C only for the relay step.
- **Markers:** `RESQ_PERF`, `BLE_MESH` READY, `MeshNetwork_E2EE`, `BLE_TRANSFER`, `SOS_QUEUE`, `BLE_RECOVERY`.

| Step | Action | Expected | Failure indicators |
| --- | --- | --- | --- |
| READY | Open on A and B | READY and identity within baseline time | No READY; placeholder name persists |
| Private DELIVERED | A sends five private texts to B | In order; receipt-confirmed DELIVERED | Reordering, E2EE FAILED, duplicates |
| Open burst | Force-stop B; A queues five texts; reopen B | Queue drains; UI responsive; no decrypt-attributed `MAIN_SLOW` | Jank, lost messages |
| SOS | A raises SOS | B notification and incident; relay accepted | Missing alert, `inbound-full` |
| Relay | A–B–C line; A sends private text to C | B relays blind; C decrypts; DELIVERED returns | B decrypts, receipt lost |
| Bluetooth toggle | B Bluetooth off 10 s, then on | No crash; READY returns; queued text delivered | Stuck connect lock, stale dispatch |

**Report fields:** pass/fail per step, timings, `MAIN_SLOW` counts before and after, a bounded excerpt around each failure, and steps not run (which stay unspecified).
