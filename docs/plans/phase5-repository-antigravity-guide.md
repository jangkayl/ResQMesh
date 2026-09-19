# Phase 5 repository extraction guide

Use this guide in Google Antigravity for the existing `fix/ble-reliability` branch. It is an implementation plan, not evidence that any Phase 5 behavior is verified.

## Scope and guardrails

`MeshRepository.kt` is currently the composition point for gateway callbacks, peer state, outgoing message creation, delivery receipts, SOS, blocking, and live audio. Extract behavior without changing payload schemas, Room schema, public ViewModel APIs, routing choices, or BLE ownership.

Before editing, read `AGENTS.md`, `docs/status.md`, `docs/architecture.md`, `docs/validation.md`, `MeshRepository.kt`, `MeshNetworkGateway.kt`, `MessageStore.kt`, `PrivateDeliveryPlanner.kt`, and the relevant existing payload handlers. Inspect `git status` and preserve unrelated changes. Do not add Wi-Fi Direct/Nearby, new retries, timer-based BLE fixes, or new unit tests unless the user explicitly asks.

Do not start Phase 5 until focused queue-overflow/retry and L2CAP-fallback phone evidence exists. Local compile and static checks are necessary but do not prove device behavior. Do not commit, push, or create a PR without explicit user approval.

## Execution order

### 5A - transport callback/event translation

Create a repository-local callback binder (for example `MeshNetworkEventBinder`) that assigns `MeshNetworkGateway` callbacks and forwards typed events to injected functions or focused collaborators. Move no policy initially: keep the existing order and conditions for status, connect/disconnect, liveness, scan, keys, routing, messages, seen/delivered receipts, SOS cancellation, and live audio.

Keep `MeshRepository` as the owner that starts/stops the binder. Ensure one active callback owner and clear callbacks on stop if the current lifecycle requires it. Preserve endpoint versus stable-identity checks exactly.

Check: compile; manually inspect every `MeshNetworkGateway` callback has exactly one equivalent binding; run `git diff --check`.

### 5B - peer-state reduction

Extract pure, repository-owned peer state (connected, scanned, blocked, responsive, and router-facing updates) into a collaborator such as `MeshPeerStateReducer`. Inputs are events; outputs are immutable state updates and explicitly named effects. Keep `StateFlow` ownership/API stable in `MeshRepository` until callers can migrate without behavior change.

Preserve the duplicate-endpoint survivor rules, provisional-name suppression, key-cache direct-link lifetime, and the distinction between ready, live, scanned, routed, and offline. Never disconnect a link merely because a state reducer receives a duplicate event.

Check: compile; compare every old callback branch to its new reducer path; device test direct connect, dual-address rename, disconnect, rescan, and Radar status.

### 5C - outbound messaging and receipts

Extract public/private outgoing payload construction, local persistence, direct-vs-next-hop selection, and receipt operations into `MeshMessageSender` and/or `MeshReceiptCoordinator`. Reuse `PayloadFactory`, `PrivateDeliveryPlanner`, `MessageStore`, and `MeshNetworkGateway`; do not duplicate their logic.

Maintain these invariants: private send fails closed without a usable recipient key; local save occurs before transmission as today; only payload-ready links carry sends; return routes for private delivery receipts remain unchanged; and receipt handling stays idempotent in `MessageStore`.

Check: compile; inspect public/private sends and seen/delivered paths; physical two-phone public/private sends both ways, receipt visibility, and L2CAP-to-GATT fallback without duplicate messages.

### 5D - SOS, blocking, and live-audio policy

Extract each policy into a small named collaborator or existing feature policy handler. SOS must retain sender/alert binding and cancellation semantics. Blocking must first enforce local rejection/disconnect behavior and must not create reciprocal remote blocking after disconnect. Live audio must retain channel filtering, blocked-sender rejection, bounded buffering, and its existing `SharedFlow` delivery.

Do not bundle feature redesigns into this refactor. The current blockers for blocking semantics and SOS ownership remain open until dedicated phone scenarios pass.

Check: compile; phone-test SOS alert/cancel ordering, block/unblock/restart, ignored blocked traffic, active-channel audio, wrong-channel audio, and reconnect after unblock.

## Per-slice workflow

1. Ask Antigravity for a read-only mapping of old lines to a proposed collaborator and list behavior invariants.
2. Review and approve that mapping before edits.
3. Implement one slice only; do not mix 5A-5D.
4. Run `./gradlew.bat --no-daemon :app:compileDebugKotlin --console=plain --offline`, `powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1`, and `git diff --check`.
5. Update `docs/architecture.md` and `docs/status.md` only when the source structure or active next action changes. Record physical-device outcomes in `docs/validation.md` only after the user supplies the scenario and result.
6. Stop, summarize changed files, checks, known gaps, and a focused device test card. Wait for approval before the next slice or a commit.

## Antigravity prompt

> Read `AGENTS.md`, `docs/status.md`, `docs/architecture.md`, `docs/validation.md`, and `docs/plans/phase5-repository-antigravity-guide.md`. Work only on Phase 5A. First provide a read-only callback-to-event mapping and list invariants that must not change. Do not edit, commit, push, add tests, modify BLE transport, or start another phase until I approve the mapping.
