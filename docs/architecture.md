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

Topology is now recorded by stable node ID for private routes. SYSTEM pulses may relay a node's public key and stable-neighbor IDs, but a relayed pulse never changes the identity of its physical forwarding endpoint. SYSTEM forwarding is bounded to four hops. Name-only legacy topology remains visible but is not eligible for indirect private routing; topology freshness, empty topology withdrawal, and deduplication lifetime remain separate concerns in `docs/status.md`.

## Private messaging

CryptoManager uses an Android Keystore RSA key pair and per-message AES-GCM content encryption. The local node ID is deterministically derived from the SHA-256 hash of the hardware public key (CryptoManager.getMyNodeId()). Preferences holding node identity and peer public keys are excluded from Android Auto/Cloud Backup. The working tree refuses private sends without a payload-ready local link, stable directed route, and usable recipient key. For private forwarding and return receipts, relays prioritize the planned directed hop; if that link is disconnected or unavailable, relays evaluate a controlled flood fallback bounded to 3 hops before dropping. A 15-second delivery timeout marks unacknowledged outbound private messages as failed in the UI. Public keys learned in SYSTEM pulses are persisted by stable node ID using trust on first use: a changed key is held pending rather than silently replacing the pinned key, and pending change alerts can be explicitly dismissed without clobbering keys. Public keys are public metadata, not secret material.

This is not yet a basis for claiming authenticated end-to-end encryption or forward secrecy. TOFU can detect a later substitution but does not authenticate the first observation; out-of-band key verification UI remains an open production concern.

## Persistence and UI

`MeshRepository` joins network callbacks, `MeshRouter`, persistence, and UI-facing state through two boundaries: `MeshNetworkGateway` hides Android Bluetooth types, and `MessageStore` hides Room/DAO operations. `PrivateDeliveryPlanner` makes the pure direct/next-hop/broadcast selection before the gateway performs transport I/O. Production adapters are supplied by Koin. Room collection and background writes run in the process-owned `AppCoroutineScope`. Compose features cover setup, chat, Radar, SOS, profile, responder tracking, and audio; Active Chat header presentation and Radar row models are separated from their route-level screens. UI rules live in `docs/ui.md`; physical behavior must be checked against `docs/validation.md`.

## Offline maps and notifications

Offline vector map packages (PMTiles) are distributed via versioned GitHub Releases with metadata manifests and cryptographic digital signatures. Manifest integrity is validated offline by `ManifestVerifier` using Universal ECDSA (NIST P-256 / SHA256withECDSA) with an Ed25519 composite fallback, guaranteeing native verification down to Android 7.0 (API 24) without external library bloat. `MapPackageDownloader` enforces Wi-Fi policies, follows 302 cross-domain release redirects, validates stream completion before SHA-256 hash checks, and relies on `MapStorageGuard` for atomic versioned activation. `NotificationHelper` formats incoming private messages via `NotificationCompat.MessagingStyle` (7-message history ring buffer) and SOS distress alarms via `CATEGORY_ALARM`. On cold launch from notifications, `MainActivity` routes through `IdentitySetupScreen` / `PermissionsScreen` to initialize node identity and radio hardware before entering the active mesh.

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
