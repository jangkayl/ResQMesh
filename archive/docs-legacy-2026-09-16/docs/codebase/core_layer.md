---
tags: [codebase, core, current]
---
# Core layer

- core/network/NativeBleManager.kt: BLE advertising/scanning, direct payload selection, GATT queues, optional L2CAP registration, heartbeats, and watchdog.
- core/network/bluetooth/gatt/GattClientManager.kt and GattServerManager.kt: asynchronous GATT client/server callbacks, setup, writes, notifications, and L2CAP PSM bootstrap.
- core/network/bluetooth/state/BleStateStore.kt: current map-based link/queue/identity state. Per-link lifecycle ownership is a repair target.
- core/network/MeshPayload.kt: Protobuf payload model.
- core/network/PayloadDispatcher.kt and dispatch/PayloadHandlers.kt: decoding, duplicate suppression, local handling, and relay.
- core/network/CryptoManager.kt: current RSA/AES-GCM private-message envelope. It is implemented, but key authentication and per-session forward secrecy are absent.
- core/model/NodeIdentity.kt and MeshModels.kt: stable node matching and shared models.
- core/ui/ and core/utils/: Compose shell/components, themes, logging, notifications, codecs, and helpers.

For the actual GATT and optional L2CAP relationship, read [Bluetooth architecture](../bluetooth_architecture.md).
