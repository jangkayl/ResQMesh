# Architecture

Last reviewed: 2026-10-01. Source and device evidence prevail.

## System shape

ResQMesh is a Kotlin Android app (SDK 24–36). Compose provides UI, Room stores state, Kotlin serialization encodes `MeshPayload`, Koin supplies dependencies, and coroutines connect events to repositories and ViewModels.

Native BLE advertising/scanning, GATT, and optional L2CAP carry traffic. GATT owns setup, readiness, heartbeat, and fallback. Direct dispatch reports acceptance or an exact rejection; only a receipt proves delivery. Nearby Connections and Wi-Fi Direct are not implemented.

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

`NativeBleManager` is the facade and policy owner. Collaborators own radio, admission, GATT, L2CAP, liveness, and callbacks. `BleLinkRegistry` stores generation-owned link state and queues.

The intended lifecycle distinguishes radio connection from payload readiness:

```text
DISCONNECTED -> CONNECTING -> DISCOVERING -> CONFIGURING -> READY
      ^                                                       |
      +------------------- DISCONNECTING <---------------------+
```

Client readiness requires discovery/CCCD; server readiness requires subscription. Fallback uses acknowledged indications with generation checks, bounded queues, frame bounds, L2CAP promotion, and one heartbeat per endpoint.

Revoked `BLUETOOTH_CONNECT` triggers flight/link cleanup without payload logging.

Client setup uses the reliable 20-byte ATT baseline; `READY` does not wait for MTU negotiation. A server callback for a live outbound endpoint is another view of that ACL, not a second destructive role.

A generation-owned gate pauses scanning during setup. Retirement releases its owner before forgetting the link; Refresh removes orphan owners and drains candidates without disturbing live handshakes. Higher election score initiates; busy candidates remain queued by stable ID, with one outbound `connectGatt` at a time.

Without identified, unblocked READY neighbors, failed scans retry with jitter; scans without valid advertisements for 15 seconds restart. Recovery pauses during live handshakes and stops with the session.

The elected client has a five-second connect/discovery/CCCD deadline; bootstrap retries are bounded. Server configuration is an orphan backstop, and provisional identity waiting starts only after `READY`.

GATT uses `AUTO` for known-good peers; absent ATT discovery response, retry that peer with `TRANSPORT_LE`.

L2CAP promotion resends complete frames and disarms obsolete GATT flights. Deferred promotion stays queued until capacity returns. Each socket has one bounded writer with FIFO ordinary/control lanes; control may overtake waiting frames. Each transport queue retains at most 128 transfers including active, with eight control slots reserved. Ordinary byte admission is 2 MiB plus 64 KiB control headroom. GATT frames remain non-interruptible. Owned acknowledged GATT chunks and successful L2CAP writes protect progressing transfers from silence retirement; stalled operations retain deadlines.

Each phone admits at most three direct GATT neighbors; unidentified endpoints occupy separate slots, while routed nodes do not count. Stable capacity needs multi-phone measurement.

Discovery preserves healthy routes instead of creating redundant ACLs. After the last usable neighbor disappears, nearby unblocked peers may bootstrap through normal election/capacity admission. **Connect Directly** retains block, duplicate, and three-neighbor guards.

## Identity, routing, and presence

Stable `NodeIdentity` IDs identify peers across changing BLE endpoints. Private conversations group by ID. A separate ID-keyed Room name record updates from ready direct handshakes and received messages; shortened advertisements cannot overwrite it. Inbox and chat headers show that saved current name while historical messages retain their recorded labels. UI and routing select payload-ready links, not scanned or radio-connected endpoints.

Peer names retain `#nodeId`. Setup sends no tag; older saved `[NODE]` defaults are omitted and incoming custom tags remain compatible, unverified labels.

A block persists by stable identity, not MAC. Encrypted `BLOCK_REQUEST` travels directly or by relay; the receiver persists complementary denial and replies with `BLOCK_ACK` before direct teardown. Each device releases only its own record; both must unblock locally. Relayed text/private/SOS/receipts/live audio remain allowed. Unknown inbound MACs require a gated direct SYSTEM identity pulse before publishing the peer or dispatching ordinary traffic. Physical validation remains open.

Unknown blocked identity pulses retire the captured receiving generation even before name binding. Keep these states distinct:

- Direct and payload-ready.
- Direct but configuring or unresponsive.
- Reachable through a route.
- Advertising/recently seen but not connected.
- Offline after a previously known link disappears.

Stable-ID topology uses authoritative directed per-origin snapshots: empty lists withdraw adjacency, old versions are ignored, full refresh is 30 seconds, and cached fragments have a 90-second lease. Only payload-ready, identified, unblocked neighbors are advertised. UI and delivery use the same directed paths rooted at current payload-ready first hops, so a stale reverse snapshot cannot restore a withdrawn route. Relayed SYSTEM pulses never rename their physical forwarder and are limited to four hops. Legacy name-only topology is display-only.

## Private messaging

CryptoManager uses an Android Keystore RSA key pair and per-message AES-GCM content encryption. The local node ID is deterministically derived from the SHA-256 hash of the hardware public key (CryptoManager.getMyNodeId()). Preferences holding node identity and peer public keys are excluded from Android Auto/Cloud Backup. The working tree refuses private sends without a payload-ready local link, stable directed route, and usable recipient key. Private forwarding and return receipts use only the planned exact stable-ID hop; an unavailable or rejected hop is never converted into broadcast. Locally originated messages are persisted before dispatch, remain pending when transport does not accept them, and retry when route/key/readiness state changes; pending rows explicitly expire after 24 hours. A 15-second delivery timeout starts only after immediate transport acceptance. Public keys learned in SYSTEM pulses are persisted by stable node ID using trust on first use: a changed key is held pending rather than silently replacing the pinned key, and pending change alerts can be explicitly dismissed without clobbering keys. Public keys are public metadata, not secret material.

Pending key changes pause private sends and retries. Conditional Room updates protect receipts.

This is not yet a basis for claiming authenticated end-to-end encryption or forward secrecy. TOFU can detect a later substitution but does not authenticate the first observation; out-of-band key verification UI remains an open production concern.

## Persistence and UI

`MeshRepository` joins callbacks, routing, persistence, and UI. Gateway broadcast results report per-neighbor acceptance. Public sends persist pending before dispatch; wholly rejected sends retry, partial acceptance does not rebroadcast, and feedback never implies delivery. A mutex serializes public dispatch/outbox flush. `MeshNetworkGateway` hides Bluetooth types; `MessageStore` hides Room. `PrivateDeliveryPlanner` chooses exact hops. Koin supplies adapters and `AppCoroutineScope` owns background work. UI rules: `docs/ui.md`; evidence: `docs/validation.md`.

Local identity lives in Room; serialized setup/rename preserves user/device IDs and keys and mirrors preferences. Incident ownership uses creator ID and signing key, never names. Report-time names and signed activity stay unchanged. Workflow-v2 offers have independent helper revisions. Selection locks local editing; helpers confirm commitment. Withdrawal or valid offline newer revisions atomically clear selection and reopen requests without advancing reporter versions. Loading/replay repairs stale projections; superseded selections consume reporter versions, and signed historical evidence permits revocation no-ops without clearing newer selections. Only reporters select/resolve/cancel; replacement is never automatic. Workflow-v1 and terminal history remain unchanged. Accepted events/projections share a Room transaction. P-256 signatures prove key continuity, not real-world identity. IncidentSyncCoordinator compares SHA-256 history/state digests after READY/changes and every 30 seconds, repairs missing events in bounded pages, and verifies peer/snapshot completion. Room 9 to 10 separates validation from application. Legacy fallback is limited; physical convergence remains open. Location is a snapshot.

Closure signs prerequisite IDs and reconstructs helper history from signed events; confirmation time is event time. Missing dependencies defer closure across pages. Legacy empty closures prioritize available offer changes but cannot identify missing prerequisites. Use matching builds; older apps ignore dependencies.

## Offline maps and notifications

Versioned PMTiles releases use signed manifests, offline P-256 verification with Ed25519 fallback, download completion/hash checks, and atomic activation. Notifications use `MessagingStyle` for private messages and `CATEGORY_ALARM` for SOS. Cold launches still pass through identity and permission setup before entering the mesh.

Background mesh is opt-in. `MeshSessionController` owns start/stop; `MeshForegroundService` anchors sessions as a non-sticky `connectedDevice` service with a silent notification and debounced repository state. It has no boot receiver or wake lock. **Go offline** stops transport and service. Unexpected service destruction stops transport; disabled mode stops it when the activity is destroyed, excluding configuration changes.

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
| Background session | `core/service/MeshSessionController.kt`, `MeshForegroundService.kt` |

Source and device traces prevail.
