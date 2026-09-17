# Architecture

Last source review: 2026-09-17, branch `fix/ble-reliability`, with Phase 4 committed. This page describes the current checkout; verify changed paths before relying on it.

## System shape

ResQMesh is a single-module Android application written in Kotlin. Jetpack Compose provides the UI, Room stores nodes and messages, Kotlin serialization encodes the outer Protobuf `MeshPayload`, Koin supplies dependencies, and coroutines connect network events to repositories and ViewModels. The minimum SDK is 24 and the target SDK is 36.

The current transport is native BLE. Phones advertise and scan, form direct GATT client/server roles, and exchange framed payloads. GATT also establishes an optional L2CAP channel where supported; GATT remains required for setup, readiness, heartbeat/fallback, and devices without a usable L2CAP path. Nearby Connections and Wi-Fi Direct are not implemented transports.

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

`PayloadDispatcher` handles system presence, ping/heartbeat, public and private messages, delivery signals, SOS, and audio-related payloads. `MeshRouter` maintains a graph of known neighbors and computes paths. This is graph-based routing, not proof of a complete distance-vector protocol or guaranteed self-healing network.

## Direct-link lifecycle

`NativeBleManager` is the public facade and cross-component policy owner. `BleRadioController` owns Android advertising/scanning; `BlePeerAdmissionController` owns discovery identity/capacity/election policy; `GattTransferExecutor` owns callback-driven GATT transfers; `L2capTransport` owns socket I/O and GATT fallback; and `BleLifecycleSupervisor` owns bounded liveness, scan expiry, and stuck-lock recovery. `GattClientManager` and `GattServerManager` own Android callbacks. `BleLinkRegistry` stores per-attempt records with endpoint, role, generation, lifecycle state, GATT/server reference, queue/operation state, MTU, identity, and timestamps.

The intended lifecycle distinguishes radio connection from payload readiness:

```text
DISCONNECTED -> CONNECTING -> DISCOVERING -> CONFIGURING -> READY
      ^                                                       |
      +------------------- DISCONNECTING <---------------------+
```

Client readiness follows required GATT configuration such as service discovery and CCCD completion. Server readiness follows subscription. Server-to-client GATT fallback uses acknowledged indications so queue advancement is tied to `onNotificationSent` rather than an unacknowledged notification accepted only by the local stack. `GattTransferCoordinator` owns deterministic flight claiming, generation checks, bounded queue admission, chunk completion, removal, and L2CAP queue promotion. `MeshFrameCodec` applies the same length-prefix and payload bounds to GATT and L2CAP. `HeartbeatCoordinator` owns one generation-bound challenge per endpoint; Android scheduling and radio I/O remain in `NativeBleManager`. Same-address late server callback ownership and queue overflow/retry behavior still require review and device evidence.

Client setup treats the default 20-byte ATT payload as the reliable baseline: service discovery and CCCD subscription establish `READY` without waiting for MTU negotiation. A GATT-server connection callback for an endpoint already owned by a live outbound client is treated as another local view of that ACL, not as a second configuring mesh role with its own destructive timeout.

A generation-owned radio handshake gate pauses discovery scanning while any client or server link is configuring. Advertising starts once with the mesh session and is not restarted when the direct-link count changes; local admission remains authoritative even though the advertised count can be stale until the next session. Existing ready links continue carrying traffic, and only the last setup owner may resume balanced scanning. The higher election score is the sole initiator; the yielding peer no longer schedules a delayed role reversal. An inbound setup also blocks a previously scheduled outbound attempt, keeping setup single-flight even when the two roles use different private addresses.

The elected client owns a 15-second setup deadline covering connect, discovery, and CCCD subscription. The server's 20-second configuring deadline is only an orphan backstop if the client and its disconnect callback vanish. A provisional peer's 10-second identity deadline starts only after CCCD reaches `READY`, so identity waiting cannot abort ATT discovery.

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

Topology freshness, empty topology withdrawal, deduplication lifetime, and hop/expiry bounds remain separate routing concerns in `docs/status.md`.

## Private messaging

`CryptoManager` uses an Android Keystore RSA key pair and per-message AES-GCM content encryption. The working tree refuses private sends without a usable recipient public key, drops unencrypted or undecryptable private envelopes, and keeps private locations inside encrypted content. `PeerPublicKeyCache` associates keys with peer/endpoint observations.

This is not yet a basis for claiming authenticated end-to-end encryption or forward secrecy. Public-key authentication, identity binding, key epochs/current-key acknowledgment, and backup/storage policy remain open production concerns.

## Persistence and UI

`MeshRepository` joins network callbacks, `MeshRouter`, persistence, and UI-facing state through two boundaries: `MeshNetworkGateway` hides Android Bluetooth types, and `MessageStore` hides Room/DAO operations. `PrivateDeliveryPlanner` makes the pure direct/next-hop/broadcast selection before the gateway performs transport I/O. Production adapters are supplied by Koin. Room collection and background writes run in the process-owned `AppCoroutineScope`. Compose features cover setup, chat, Radar, SOS, profile, responder tracking, and audio; Active Chat header presentation and Radar row models are separated from their route-level screens. UI rules live in `docs/ui.md`; physical behavior must be checked against `docs/validation.md`.

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

Source code and focused device traces take precedence over this summary.
