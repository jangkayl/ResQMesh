> Historical/inactive reference archived 2026-10-04; see [disposition index](../README.md). Original claims, hashes and instructions are not current authorization.

# Phase 5 repository extraction guide

Reviewed against source on 2026-10-02, branch `fix/bluetooth-recovery-cluster-bridging-and-sos-ux`. Use in Google Antigravity after reading [current status](../../../docs/status.md), [architecture](../../../docs/architecture.md), [UI rules](../../../docs/ui.md), and [validation](../../../docs/validation.md).

Phase 5 remains deferred. This guide records the implemented baseline and constraints for a future extraction; it does not authorize implementation or establish physical reliability.

## Current baseline to preserve

`MeshRepository` composes gateway callbacks, peer state, routing, outgoing persistence, receipts, block relationships, incident sync, scoped conversations, and SOS collaborators. Reuse `MessageStore`, `PrivateDeliveryPlanner`, `PeerNameStore`, `BlockRelationshipStore`, `IncidentSyncCoordinator`, `SosRepository`, and `SosSyncCoordinator`; do not recreate their policy.

- `setupCallbacks()` runs once from repository `init`. `startNode()` starts transport; `stopNode()` stops transport and clears peer state. Neither clears/rebinds callbacks. Bluetooth recovery requires those bindings to survive transport suspension.
- `onTransportStateChanged` publishes transport state and clears connected/scanned/ready peers and rooted reachability for OFFLINE, BLUETOOTH_OFF, PERMISSION_REQUIRED, and ERROR. Session intent is separate from transport readiness.
- `reconcileTransport()` reaches the gateway; activity resume reconciles an online session. Go offline cancels recovery. Background anchoring remains opt-in.
- `checkRouteExists` uses an actual READY-rooted shortest path, not membership in cached known nodes. `canRetireForBridge` requires responsive, unblocked READY first hops and preserves directed reachability. Direct loss uses `onDirectPeerLost` to retain alternate paths.
- Public/private sends persist before dispatch. Acceptance is not receipt. Keep public partial-acceptance behavior, serialized outbox flush, private exact-hop/fail-closed routing, pinned-key checks, expiry, and conditional receipt updates.
- Blocking is a mutual direct-link denial protocol: persisted stable-ID records, routed/direct BLOCK_REQUEST, ACK before teardown, and independent local unblock on both devices. Relayed messaging remains allowed. Do not restore the older local-only blocking proposal.
- Community, Radio channels, and SOS threads have persisted scopes, drafts, unread state, and destination-preserving retries. SOS lifecycle is signed and revisioned; local silence never ends the alert.
- SOS creation cancellation/Back returns to the hub. Sender thread Back leaves the SOS active. Cached location timestamps are accepted only when positive and under 15 minutes old; coordinates may still be cached. Fresh callbacks normalize missing timestamps. Header fallback time does not prove GPS freshness.
- SOS hub/header cards show alert category, status, transmission, time, location accuracy, and history. Conversations use a measured floating header, reversed latest-first list, IME padding, compact controls, and read-only ended history. Radio uses the solid theme background.

## Future slices

### 5A: callback translation

Propose a repository-local binder and map every gateway binding, including conversation/SOS packets, transport state, block/domain events, route checks, and bridge decisions. Retain one callback owner for the repository lifetime. Do not move binding to session start/stop or clear callbacks on Bluetooth OFF. Preserve callback ordering and stable-ID/endpoint checks.

### 5B: peer-state reduction

Extract immutable peer-state updates and explicit effects. Preserve duplicate-endpoint survivors, provisional identity suppression, key-cache lifetime, and ready/live/scanned/routed/offline distinctions. Retain transport suspension resets and alternate routes on direct disconnect. Duplicate events must not retire valid links.

### 5C: outgoing messages and receipts

Extract orchestration around existing factories/planners/stores. Preserve persistence before dispatch, single-owner public flush, exact private next hops, fail-closed keys, accepted-only timeout start, idempotent receipts, and conversation scope. Do not introduce broadcast fallback or new retry policy.

### 5D: policy coordination

Reuse existing SOS/incident coordinators and block storage. Preserve alert ownership, signed terminal states, local silence, mutual direct denial, relay allowance, channel filtering, and bounded audio. Keep feature redesign separate.

## Workflow and Antigravity prompt

Do not start extraction until focused recovery, queue-pressure, and L2CAP/GATT-fallback phone evidence establishes a baseline. The [Bluetooth recovery card](../../../docs/testing/bluetooth-recovery-test-card.md) and existing R7/R8 procedures define pending evidence.

> Read AGENTS.md and the canonical docs linked above, then inspect MeshRepository.kt, MeshNetworkGateway.kt, and the relevant collaborators. Produce a read-only Phase 5A callback map, invariant list, proposed files, and checks against current source. Do not edit, commit, push, add tests, modify transport, or start another slice until the user approves implementation.

For approved slices, compile/build and run affected tests, Lint, documentation checks, and diff checks. Update canonical docs for actual source changes. Record phone outcomes only from supplied device evidence. Summarize the slice and focused phone steps before proceeding to another slice; preserve unrelated changes.
