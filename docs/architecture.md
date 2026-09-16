# Architecture

Last source review: 2026-09-16, branch `temp`, HEAD `d7cd5e3`, with uncommitted changes. This page describes the current checkout; verify changed paths before relying on it.

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

`NativeBleManager` coordinates discovery, admission, role selection, payload routing, liveness, and transport fallback. `GattClientManager` and `GattServerManager` own Android callbacks. `BleLinkRegistry` stores per-attempt records with endpoint, role, generation, lifecycle state, GATT/server reference, queue/operation state, MTU, identity, and timestamps.

The intended lifecycle distinguishes radio connection from payload readiness:

```text
DISCONNECTED -> CONNECTING -> DISCOVERING -> CONFIGURING -> READY
      ^                                                       |
      +--------------- DISCONNECTING / FAILED <---------------+
```

Client readiness follows required GATT configuration such as service discovery and CCCD completion. Server readiness follows subscription. Server-to-client GATT fallback uses acknowledged indications so queue advancement is tied to `onNotificationSent` rather than an unacknowledged notification accepted only by the local stack. Callbacks and timeouts should act only on their owned link reference/generation. The working tree includes callback-driven client writes and server indications, heartbeat challenges, inbound-progress liveness, endpoint cleanup, and L2CAP failure fallback. Same-address late server callback ownership and complete queue bounds still require review and device evidence.

When L2CAP becomes available, it takes ownership of any active or queued GATT transfer. The payload is resent in full over L2CAP and the obsolete GATT flight is disarmed, so a late or missing GATT completion callback cannot tear down a healthy L2CAP path. If a GATT callback fails after that handoff race, the manager preserves L2CAP and promotes the payload instead of retiring the link.

The source still contains conflicting direct-link limits: `MAX_TOTAL_CONNECTIONS = 3` and `MAX_CONNECTIONS = 4`. Do not make a stable-capacity claim until one rule is implemented and measured.

## Identity, routing, and presence

Stable `NodeIdentity` IDs identify peers across changing BLE endpoint addresses. A MAC/endpoint identifies a physical transport attempt and must not replace node identity. UI and routing should select payload-ready links, not merely scanned or radio-connected endpoints.

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

`MeshRepository` joins network callbacks, `MeshRouter`, Room DAOs, and UI-facing state. Compose features cover setup, chat, Radar, SOS, profile, responder tracking, and audio. UI rules live in `docs/ui.md`; physical behavior must be checked against `docs/validation.md`.

## Source map

| Area | Primary paths |
| --- | --- |
| BLE orchestration | `core/network/NativeBleManager.kt` |
| GATT callbacks | `core/network/bluetooth/gatt/` |
| Link ownership and liveness | `core/network/bluetooth/state/` |
| Payload schema and dispatch | `core/network/MeshPayload.kt`, `PayloadDispatcher.kt`, `dispatch/` |
| Cryptography | `core/network/CryptoManager.kt`, `data/repository/PeerPublicKeyCache.kt` |
| Repository and routing | `data/repository/MeshRepository.kt`, `MeshRouter.kt`, `PayloadFactory.kt` |
| Room | `data/local/` |
| Compose features | `feature/` and `core/ui/` |
| UI state | `ui/state/UiStates.kt` |

Source code and focused device traces take precedence over this summary.
