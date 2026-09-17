---
tags: [architecture, codebase, current]
---
# ResQMesh architecture and source map

Checked against temp/d7cd5e3 and uncommitted changes on 2026-09-15. This is a source map, not a statement that reliability, delivery, or encryption has been validated.

## Data path

1. NativeBleManager advertises/scans through Android BLE, manages a GATT client/server direct link, and optionally registers an L2CAP socket for a peer.
2. GattClientManager and GattServerManager perform asynchronous connection setup and receive writes/notifications. BleStateStore holds active links, queues, buffers, identities, and timers.
3. A complete byte payload is decoded as a Protobuf MeshPayload by PayloadDispatcher. Handlers process presence, messages, and forwarding. Duplicate IDs are suppressed.
4. MeshRepository joins network events, graph paths, and Room storage. MeshRouter maintains a neighbor graph, shortest paths, and a spanning-tree calculation.
5. ViewModels expose state to Compose screens for chat, Radar, SOS, onboarding, and profile.

The current queue and setup timing can produce a link that appears connected before it is ready to exchange app payloads. See [BLE repair pipeline](ble_repair_pipeline.md).

## Useful source files

| Area | Current files | Role |
| --- | --- | --- |
| BLE transport | core/network/NativeBleManager.kt; core/network/bluetooth/gatt/GattClientManager.kt; GattServerManager.kt; core/network/bluetooth/state/BleStateStore.kt | Discovery, direct links, queues, optional L2CAP |
| Payloads | core/network/MeshPayload.kt; PayloadDispatcher.kt; dispatch/PayloadHandlers.kt | Protobuf envelope, duplicate suppression, local handling/relay |
| Identity and crypto | core/model/NodeIdentity.kt; core/network/CryptoManager.kt | Stable node ID; RSA/AES-GCM private envelope |
| State and routing | data/repository/MeshRepository.kt; MeshRouter.kt; PayloadFactory.kt | Room integration, neighbor graph, outgoing payloads |
| UI | feature/comms/; feature/radar/; feature/sos/; feature/setup/; feature/profile/ | Compose screens and ViewModels |

## Claims to keep precise

- MeshRouter uses a graph and shortest-path computation; do not describe it as a complete distance-vector routing protocol.
- Room stores local app data; no infrastructure server is required for the direct BLE path.
- L2CAP is optional and GATT remains active. There is no Wi-Fi Direct or Google Nearby Connections implementation in this checkout.
- CryptoManager has no authenticated key exchange or per-session forward secrecy. AES-GCM/RSA describe the primitives, not a complete security guarantee.
- Connected, ready, reachable through a hop, and recently seen are different states. The UI and thesis should distinguish them.
