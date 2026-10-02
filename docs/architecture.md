# Architecture

Last reviewed: 2026-10-02. Source and device evidence prevail.

## System shape

ResQMesh is a Kotlin Android app (SDK 24–36). Compose provides UI, Room stores state, Kotlin serialization encodes `MeshPayload`, Koin supplies dependencies, and coroutines connect events to repositories and ViewModels.

Native BLE, GATT, and optional L2CAP carry traffic. GATT owns setup, readiness, heartbeat, and fallback. Direct dispatch reports acceptance or an exact rejection; only a receipt proves delivery. Nearby Connections and Wi-Fi Direct are not implemented.

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

`BleSessionLifecycle` separates requested sessions from running transport. Adapter broadcasts and reconciliation detect radio/permission changes. OFF suspends transport; ON rebuilds GATT, waits for service registration, and starts advertising/scanning. Go offline cancels recovery; background remains opt-in.

Generations invalidate callbacks before cleanup. GATT callbacks use the main handler; radio registrations and L2CAP listeners capture ownership. Cleanup includes pending clients, locks, queues, listeners, and repository readiness; durable outboxes, identity, keys, and blocks remain. Startup/advertising failures retry with jitter. Scans retain a four-starts-per-30-seconds budget across restarts, plus bounded failure backoff.

Setup uses 20-byte ATT before MTU. A server callback for an outbound ACL does not create a duplicate role. Score/ID election selects an initiator, with isolated fallback. One outbound setup runs; candidates wait. Generation-owned handshake gates pause scans; retirement releases owners before forgetting links. Refresh reconciles orphan owners.

Client connect/handshake deadlines are five seconds. Server timeouts catch orphans; identity waits until READY. Isolated failed scans retry; scans without advertisements for 15 seconds restart. AUTO remains default; absent ATT discovery response, that peer retries with explicit LE.

L2CAP promotion resends complete frames, disarms obsolete GATT flights, and retains deferred work. Sockets have one FIFO ordinary/control writer. Each transport retains 128 transfers, eight reserved control slots, 2 MiB ordinary bytes, and 64 KiB headroom. Control overtakes waiting frames; GATT frames remain non-interruptible. Acknowledged progress protects busy links; stalls retain deadlines.

Limit: three direct neighbors; unknown endpoints occupy separate slots. Last-neighbor loss reconsiders fresh advertisements and resumes discovery. Keep 32 candidates; unobserved advertisements expire after eight seconds. Preserve healthy routes; permit a free third link to bridge unreachable clusters. Full-capacity reclamation requires idle GATT/L2CAP and recent directed alternate paths preserving reachability and a responsive first hop, with a 60-second cooldown. Use owned retirement; otherwise defer. Connect Directly retains block/duplicate/capacity guards. Five/ten-phone validation remains open.

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

CryptoManager uses Keystore RSA keys and per-message AES-GCM; the node ID derives from the hardware public-key hash. Identity/key preferences are excluded from backup. Private sends require a payload-ready local link, directed route, and usable recipient key. Forwarding/receipts use exact stable-ID hops; rejection never becomes broadcast. Messages persist before dispatch, retry on route/key/readiness changes, and expire after 24 hours. The 15-second timeout starts after transport acceptance. SYSTEM keys use stable-ID trust on first use; changed keys stay pending. Dismissing an alert does not replace the pinned key. Public keys are public metadata.

Pending key changes pause private sends and retries. Conditional Room updates protect receipts.

This is not yet a basis for claiming authenticated end-to-end encryption or forward secrecy. TOFU can detect a later substitution but does not authenticate the first observation; out-of-band key verification UI remains an open production concern.

## Persistence and UI

`MeshRepository` joins callbacks, routing, persistence, and UI. Gateway broadcast results report per-neighbor acceptance. Public sends persist pending before dispatch; wholly rejected sends retry, partial acceptance does not rebroadcast, and feedback never implies delivery. A mutex serializes public dispatch/outbox flush. `MeshNetworkGateway` hides Bluetooth types; `MessageStore` hides Room. `PrivateDeliveryPlanner` chooses exact hops. Koin supplies adapters and `AppCoroutineScope` owns background work. UI rules: `docs/ui.md`; evidence: `docs/validation.md`.

Callbacks bind once in repository `init` and survive start/stop and radio suspension. Transport-state events clear readiness/rooted routes; `checkRouteExists` uses actual paths. `canRetireForBridge` checks responsive unblocked READY first hops; `onDirectPeerLost` retains alternate routes.

Local identity setup/rename preserves IDs and keys. Incidents retain reporter-selected helper semantics, signed prerequisites, and transactional projections. Withdrawal or offline helper edits reopen selection without advancing reporter versions. IncidentSyncCoordinator reconciles history/state hashes on READY, changes, and a 30-second backstop; incomplete dependencies prevent completion. Room 9→10 separates validation/application. Physical convergence remains open.

Community, Radio channels, and SOS threads use explicit persisted conversation metadata. Radio stores off-channel messages silently; monitoring plays only newly received selected-channel Radio notes. Public retries retain their original destination. Room 10→11 isolates historical SOS and unidentified public audio without guessing channels.

SOS creation accepts cached capture times only when positive and under 15 minutes old; cached coordinates may remain. Fresh callbacks normalize missing times. Header time fallback does not establish GPS freshness. Cancelling creation returns to the hub; thread Back preserves the active SOS.

SosRepository owns signed, revisioned snapshots and terminal records. Alert IDs include a signing-key namespace; updates/end require that key. Events and projection commit atomically. SOS ignores radio tuning. Creation/end relay urgently with TTL/hop bounds; rejected origin sends remain pending. SosSyncCoordinator exchanges latest signed snapshots, including terminal records, on READY/changes and every 30 seconds with jitter. Pages/retries are bounded and tied to link generations; confirmation proves only the named neighbor's matching snapshot. Signatures establish key continuity, not personal identity. Receiver silence is local. SosAlertController owns independent 30-second siren timers and per-alert notifications; Back never ends the sender's SOS. Matching upgraded APKs are required. GATT frames remain non-interruptible; phone validation remains open.

## Offline maps and notifications

Versioned PMTiles releases use signed manifests, offline P-256 verification with Ed25519 fallback, download completion/hash checks, and atomic activation. Notifications use `MessagingStyle` for private messages and `CATEGORY_ALARM` for SOS. Cold launches still pass through identity and permission setup before entering the mesh.

Background mesh is opt-in. `MeshSessionController` owns start/stop; `MeshForegroundService` anchors sessions as a non-sticky `connectedDevice` service with a silent notification and debounced repository state. It has no boot receiver or wake lock. **Go offline** stops transport and service. Unexpected service destruction stops transport; disabled mode stops it when the activity is destroyed, excluding configuration changes.

## Source map

| Area | Primary paths |
| --- | --- |
| BLE orchestration | `core/network/NativeBleManager.kt`, `core/network/bluetooth/BleRadioController.kt`, `BlePeerAdmissionController.kt`, `GattTransferExecutor.kt`, `L2capTransport.kt`, `BleLifecycleSupervisor.kt` |
| Session recovery | `core/network/bluetooth/BleSessionLifecycle.kt`, `BleScanStartBudget.kt` |
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
