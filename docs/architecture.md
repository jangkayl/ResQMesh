# Architecture

Last source review: 2026-09-17, branch `fix/ble-reliability`, with Phase 4 committed. This page describes the current checkout; verify changed paths before relying on it.

## System shape

ResQMesh is a single-module Android application written in Kotlin. Jetpack Compose provides the UI, Room stores nodes and messages, Kotlin serialization encodes the outer Protobuf `MeshPayload`, Koin supplies dependencies, and coroutines connect network events to repositories and ViewModels. The minimum SDK is 24 and the target SDK is 36.

The active transport is native BLE advertising/scanning plus GATT client/server roles and optional L2CAP payloads. GATT remains required for setup, readiness, heartbeat, and fallback. Direct dispatch reports acceptance or an exact rejection; only a receipt proves delivery. Nearby Connections and Wi-Fi Direct are not implemented.

## Data path

```text
Compose screen
    -> ViewModel / use case
    -> MeshRepository
    -> PayloadFactory
    -> MeshPayload
    -> NativeBleManager
    -> ready GATT role or owned L2CAP socket
    -> peer PayloadDispatcher
    -> typed handler
    -> repository / Room / UI state
```

`PayloadDispatcher` handles presence, heartbeat, messages, receipts, SOS, bounded incident events/sync, and audio. Incident events are validated before relay. `MeshRouter` maintains a graph of known neighbors; this is not proof of distance-vector routing or self-healing.

## Direct-link lifecycle

`NativeBleManager` is the public facade and policy owner. Its collaborators own radio, admission/election, GATT execution, L2CAP I/O, liveness, and callbacks. `BleLinkRegistry` stores endpoint/role/generation-owned link state and queues.

The intended lifecycle distinguishes radio connection from payload readiness:

```text
DISCONNECTED -> CONNECTING -> DISCOVERING -> CONFIGURING -> READY
      ^                                                       |
      +------------------- DISCONNECTING <---------------------+
```

Client readiness requires discovery/CCCD; server readiness requires subscription. Fallback uses acknowledged indications, and coordinators own generation checks, bounded queues, frame bounds, L2CAP promotion, and one heartbeat per endpoint. Same-address callback ownership and queue behavior still need device evidence.

If Android revokes `BLUETOOTH_CONNECT` during orphan preemption or an in-flight GATT write/indication, the operation is caught, logged without payload content, and retired through the existing flight/link cleanup path rather than crashing the process.

Client setup uses the reliable 20-byte ATT baseline; `READY` does not wait for MTU negotiation. A server callback for a live outbound endpoint is another view of that ACL, not a second destructive role.

A generation-owned gate pauses scanning during setup while ready links continue traffic. Advertising is session-owned; higher election score is the sole initiator. Busy candidates stay in a stable-ID bootstrap queue, with one outbound `connectGatt` at a time.

The elected client has a five-second connect/discovery/CCCD deadline; bootstrap retries are bounded. Server configuration is an orphan backstop, and provisional identity waiting starts only after `READY`.

Outbound GATT uses Android's `AUTO` transport for known-good peers because the project previously observed immediate disconnects with globally forced LE on some OEM pairs. If `AUTO` reaches `CONNECTED` but receives no ATT service-discovery response, the stable peer identity is marked for explicit `TRANSPORT_LE` on the next attempt in that app session. This is a per-peer compatibility fallback, not a Samsung model allowlist.

When L2CAP becomes available, it takes ownership of any active or queued GATT transfer. The payload is resent in full over L2CAP and the obsolete GATT flight is disarmed, so a late or missing GATT completion callback cannot tear down a healthy L2CAP path. If a GATT callback fails after that handoff race, the manager preserves L2CAP and promotes the payload instead of retiring the link.

Each phone admits at most three distinct direct GATT neighbors. A mesh may contain more than four devices because additional nodes are expected to be reached through routing; the three-link rule is not a total mesh-size claim. The rule still needs multi-phone measurement before any stable-capacity claim.

Discovery does not automatically turn every nearby routed peer into another direct ACL: when at least one payload-ready direct neighbor exists, the existing route is retained. If the last payload-ready direct neighbor disappears, a nearby, unblocked routed peer may pass ordinary election and capacity admission to bootstrap recovery. Radar's explicit **Connect Directly** request follows the same blocked-identity, duplicate-link, and three-neighbor capacity checks; it is not a block bypass.

## Identity, routing, and presence

Stable `NodeIdentity` IDs identify peers across changing BLE endpoint addresses. A MAC/endpoint identifies a physical transport attempt and must not replace node identity. UI and routing should select payload-ready links, not merely scanned or radio-connected endpoints.

A block relationship is persisted by stable identity, not MAC. A `BLOCK_REQUEST` is encrypted to the target and may traverse direct or relay links; the receiver persists complementary direct-link denial and replies with `BLOCK_ACK` before the initiator tears down direct endpoints. Each device releases only its own record—there is no remote `UNBLOCK` command—so both must unblock locally before direct admission resumes. Relayed text, private messages, SOS, receipts, and live audio are intentionally not filtered. Inbound central MACs may be unknown at ACL setup; a direct SYSTEM identity pulse is therefore gated before the peer is published or ordinary direct traffic is dispatched. This working-tree protocol still requires its physical validation card.

Keep these states distinct:

- Direct and payload-ready.
- Direct but configuring or unresponsive.
- Reachable through a route.
- Advertising/recently seen but not connected.
- Offline after a previously known link disappears.

Stable-ID topology uses per-origin sequenced snapshots: empty lists withdraw adjacency, old versions are ignored, full refresh is 30 seconds, and UI/routes share a 90-second lease. Relayed SYSTEM pulses never rename their physical forwarder and are limited to four hops. Legacy name-only topology is display-only; private next hops require exact stable IDs.

## Private messaging

CryptoManager uses an Android Keystore RSA key pair and per-message AES-GCM content encryption. The local node ID is deterministically derived from the SHA-256 hash of the hardware public key (CryptoManager.getMyNodeId()). Preferences holding node identity and peer public keys are excluded from Android Auto/Cloud Backup. The working tree refuses private sends without a payload-ready local link, stable directed route, and usable recipient key. Private forwarding and return receipts use only the planned exact stable-ID hop; an unavailable or rejected hop is never converted into broadcast. Locally originated messages are persisted before dispatch, remain pending when transport does not accept them, and retry when route/key/readiness state changes; pending rows explicitly expire after 24 hours. A 15-second delivery timeout starts only after immediate transport acceptance. Public keys learned in SYSTEM pulses are persisted by stable node ID using trust on first use: a changed key is held pending rather than silently replacing the pinned key, and pending change alerts can be explicitly dismissed without clobbering keys. Public keys are public metadata, not secret material.

This is not yet a basis for claiming authenticated end-to-end encryption or forward secrecy. TOFU can detect a later substitution but does not authenticate the first observation; out-of-band key verification UI remains an open production concern.

## Persistence and UI

`MeshRepository` joins callbacks, `MeshRouter`, persistence, and UI state. `MeshNetworkGateway` hides Android Bluetooth types; `MessageStore` hides Room. `PrivateDeliveryPlanner` chooses direct or directed next-hop delivery before I/O. Koin supplies production adapters and `AppCoroutineScope` owns background work. UI rules live in `docs/ui.md`; device evidence lives in `docs/validation.md`.

Incidents are additive: ready peers exchange bounded version summaries/missing events; no links, routes, admission, or Room tables change. Reporter self-response is rejected; cancellation stays creator-only. Creation can carry optional GPS coordinates, capture time, and accuracy in nullable Room/event fields. Mesh profile supplies broadcast TTL (default 10; explicit Dense 4); direct sync is unchanged. Signed remote authority and a durable incident outbox remain open.

## Offline maps and notifications

Versioned PMTiles releases use signed manifests, offline P-256 verification with Ed25519 fallback, download completion/hash checks, and atomic activation. Notifications use `MessagingStyle` for private messages and `CATEGORY_ALARM` for SOS. Cold launches still pass through identity and permission setup before entering the mesh.

## Source map

| Area | Primary paths |
| --- | --- |
| BLE orchestration | `core/network/NativeBleManager.kt`, `core/network/bluetooth/BleRadioController.kt`, `BlePeerAdmissionController.kt`, `GattTransferExecutor.kt`, `L2capTransport.kt`, `BleLifecycleSupervisor.kt` |
| GATT callbacks | `core/network/bluetooth/gatt/` |
| Link ownership and liveness | `core/network/bluetooth/state/` |
| Payload schema and dispatch | `core/network/MeshPayload.kt`, `PayloadDispatcher.kt`, `dispatch/` |
| Cryptography | `core/network/CryptoManager.kt`, `data/repository/PeerPublicKeyCache.kt` |
| Repository and routing | `core/network/MeshNetworkGateway.kt`, `data/repository/MeshRepository.kt`, `MessageStore.kt`, `MeshRouter.kt`, `PayloadFactory.kt` |
| Room | `data/local/` |
| Compose features | `feature/` and `core/ui/` |
| UI state | `ui/state/UiStates.kt` |
| MapLibre / Offline Maps | `core/map/`, `feature/sos/ui/SosMapScreen.kt` |

Source code and focused device traces take precedence over this summary.
