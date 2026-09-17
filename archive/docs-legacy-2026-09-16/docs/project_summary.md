---
tags: [overview, current]
---
# ResQMesh project summary

ResQMesh is an Android offline messaging and emergency communication prototype. Phones discover peers through BLE advertising and scanning, establish native GATT client/server links, exchange Protobuf payloads, and relay messages over multiple direct links. An optional BLE L2CAP channel can carry payloads after its PSM is learned through GATT; GATT remains an active transport and fallback. There is no Nearby Connections or Wi-Fi Direct transport in this checkout.

## Implemented building blocks

- `NativeBleManager`, `GattClientManager`, `GattServerManager`, and `BleStateStore` manage BLE discovery, direct links, payload queues, and optional L2CAP sockets.
- `PayloadDispatcher` and its handlers process `SYSTEM`, `PING`, message, delivery, SOS, and audio payloads. Protobuf is used for the outer `MeshPayload`.
- `MeshRouter` maintains a graph of known neighbors and calculates paths/topology decisions. This is graph-based routing, not a full distance-vector protocol.
- `MeshRepository` joins network events with Room message/node storage and exposes state to Compose ViewModels and screens.
- `NodeIdentity` provides a stable node ID for matching a peer across names and BLE endpoint addresses.
- `CryptoManager` uses a process-lifetime RSA keypair and AES-GCM hybrid encryption for private-message content. Public keys are propagated in mesh payloads without authenticated identity binding; do not claim authenticated E2EE or per-session forward secrecy yet.

## Current priorities and limits

The connection lifecycle and queues require repair and physical-device validation. See [BLE repair pipeline](ble_repair_pipeline.md) and [project status](project_status_tracker.md). The direct-link limit is inconsistent in source (`MAX_TOTAL_CONNECTIONS = 3`, `MAX_CONNECTIONS = 4`) and must be unified before making a capacity claim. Range, throughput, self-healing, and ten-node reliability are test goals, not established guarantees.

Future Wi-Fi Direct, RFCOMM, coded-PHY range, extended advertising, and alternative routing proposals in `blueprints/` are historical design options; none should be presented as shipped features.
