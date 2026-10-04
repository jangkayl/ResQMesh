> Historical/inactive reference archived 2026-10-04; see [disposition index](../README.md). Original claims, hashes and instructions are not current authorization.

# NativeBleManager maintainability refactor

Reviewed against source on 2026-10-02, branch `fix/bluetooth-recovery-cluster-bridging-and-sos-ux`.

This is a deferred refactor guide. Bluetooth recovery and cluster admission are implemented in the current source; the extractions below remain proposed. Read [current status](../../../docs/status.md), [architecture](../../../docs/architecture.md), and [validation](../../../docs/validation.md) before editing. Source overrides older plans.

## Current source baseline

- `NativeBleManager` orchestrates session lifecycle, radio, admission, GATT, L2CAP, liveness, payload dispatch, and mutable callbacks.
- `BleSessionLifecycle` separates requested sessions from running transport. OFF/permission loss suspends transport; availability restoration rebuilds it. Go offline cancels recovery.
- `BleScanStartBudget` retains four starts per 30 seconds across restarts. Startup/advertising failures retry with jitter; scanning has bounded failure backoff.
- GATT service registration gates advertising/scanning. Transport generations fence callbacks and pending sockets; GATT events run on the main handler.
- `BlePeerAdmissionController` retains bounded fresh candidates, reconsiders them after isolation, and elects a bridge initiator by score/stable ID.
- A spare third link may bridge unreachable clusters. Capacity reclamation uses owned retirement only for idle GATT/L2CAP links with recent directed alternate routes preserving reachability and a 60-second cooldown.
- `MeshRepository` supplies actual route existence and `canRetireForBridge`; `MeshRouter.onDirectPeerLost` preserves surviving relay paths.
- `sendSystemPulse` and `getElectionScore` still live in the facade. GATT managers still hold facade back-references. Mutable callbacks remain; `onMessageReceived` has 13 arguments.

The earlier line-count target and unconditional LRU/VIP extraction proposal are obsolete. Do not reintroduce blind eviction or change recovery policy during structural work.

## Deferred slices

1. Characterize presence/election logic before extraction, when implementation is authorized. Existing lifecycle, admission, writer, and stable-route tests cover recovery invariants.
2. Apply mechanical hygiene: explicit imports, named protocol/time constants, and focused permission/error handling. Preserve scheduling and callback order.
3. Extract presence planning and election logic without changing decisions. Admission policy already belongs to `BlePeerAdmissionController`; preserve its bridge guards.
4. Introduce typed events/listeners, updating `MeshNetworkGateway` and `NativeBleGateway` together. Include conversation/SOS events, transport state, route checks, and bridge-retirement decisions.
5. Encapsulate mutable fields behind a narrow GATT host interface. Preserve transport/link generations, captured ownership, startup registration, handshake locks, and pending-socket cleanup.
6. Consider Koin construction separately. A dispatcher/coroutine migration must preserve the serial lane; it is not a mechanical replacement for the current handler.

## Gates and handoff

Recovery phone evidence remains pending. Defer lifecycle/event-surface extraction until the Bluetooth recovery card and focused queue/fallback scenarios establish a baseline. Do not present proposed slices as completed.

Antigravity should first provide a read-only source-to-collaborator map, affected paths, invariant list, and test plan. Implement one approved slice at a time. Keep payload/Room schemas, public ViewModel APIs, timing, routing choices, block policy, and transports stable.

Run debug assembly, unit tests, Android-test compilation, Lint, documentation checks, and diff checks for approved edits. Device-facing work also needs the [recovery phone card](../../../docs/testing/bluetooth-recovery-test-card.md). A build does not prove BLE behavior. Preserve unrelated files; commit/push only when the user authorizes them.
