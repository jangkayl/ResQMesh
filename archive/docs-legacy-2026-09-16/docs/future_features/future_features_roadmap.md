# Future Features Roadmap
Historical future ideas only. This document is superseded for planning by [the current feature priority checklist](../roadmap/feature_priority_checklist.md) and [the BLE repair pipeline](../ble_repair_pipeline.md). Wi-Fi Direct, key-exchange migration, virtual private mesh, RSSI heatmaps, and other features below are not implemented merely because they appear here. The private-message description below is outdated: this checkout already uses RSA/AES-GCM hybrid encryption when a recipient key is present, but key authentication and plaintext fallback remain unresolved. Performance, location, and battery-life numbers below are unverified design targets.

This document outlines the detailed roadmap for the advanced features that will transform ResQMesh from a foundational Bluetooth mesh into a production-grade, decentralized disaster communication network.

## 1. Passive Signal Strength (RSSI) Heatmaps
**Concept:** Utilize the Bluetooth RSSI (Received Signal Strength Indicator) values gathered during standard `onScanResult` callbacks to approximate physical distance and spatial relationships between nodes.
**Implementation Plan:**
- **Data Collection:** `NativeBleManager.kt` will continuously average the RSSI of advertised packets (using an Exponential Moving Average filter to smooth out radio noise).
- **Topology Augmentation:** Append the smoothed RSSI integer to the `SYSTEM` topology pulse broadcast.
- **UI Heatmap:** The `RadarScreen` will dynamically adjust the radius and opacity of the glowing pulses around connected nodes based on the signal strength, allowing users to physically walk towards stronger signals (like a hot/cold radar).

## 2. End-to-End (E2E) Encryption
**Concept:** Ensure that relayed messages cannot be read by intermediary hops. Currently, messages are routed in plaintext to allow any node to assist, but private messages need absolute security.
**Implementation Plan:**
- **Key Exchange:** Implement a simplified Diffie-Hellman Key Exchange over the BLE L2CAP sockets. When a user taps a specific device, their public keys are exchanged.
- **Payload Encryption:** Use `AES-GCM-256` to encrypt the `text` or `audioBytes` fields of the `MeshPayload`. The `routePath` and `targetName` remain unencrypted so relays know where to send it, but the payload contents are locked.
- **UI Indicators:** Add a "Lock" icon to private chats, and enforce a visual warning if a user attempts to send sensitive info over an unencrypted global broadcast.

## 3. Battery-Aware Routing Algorithms
**Concept:** Relaying messages consumes battery. A node at 5% battery should not be the primary router for a dense cluster if another node is at 95%.
**Implementation Plan:**
- **Metric Broadcasting:** Include the device's battery percentage in the `SYSTEM` topology pulse (e.g., `0889F|1|Phone B|85%`).
- **Pathfinding Weighting:** Update the Dijkstra/BFS algorithm in `MeshRouter.kt` to weigh edges by battery life. A route with 3 hops using phones at 100% battery will be preferred over a route with 2 hops using a phone at 5% battery.
- **Self-Preservation Mode:** If a node drops below 15% battery, it will gracefully broadcast a `DETACH` signal, transitioning from a Mesh Relay (Server + Client) into a purely passive Leaf Node (Client only) to conserve its remaining power for emergency SOS broadcasting.

## 4. Delay-Tolerant Networking (DTN) & Data Mules
**Concept:** In disasters, two isolated mesh clusters might not have radio contact. If a user walks from Cluster A to Cluster B, their phone should carry queued messages and deliver them upon arrival.
**Implementation Plan:**
- **Persistent Outbox:** Modify `MeshRepository.kt` to store undelivered payloads in an SQLite Room Database instead of dropping them.
- **Viral Infection Delivery:** When a device physically moves and discovers a new mesh cluster, it immediately dumps its queued "Data Mule" outbox to the new nodes, checking if the intended recipient is in the new topology.
- **TTL (Time-To-Live):** Data mule packets will expire after 72 hours to prevent permanent database bloat.

## 5. Virtual Private Mesh (VPM)
**Concept:** Allow first responders (e.g., Firefighters, Medics) to create a segregated, invisible mesh network that operates on the same physical hardware but ignores civilian traffic.
**Implementation Plan:**
- **Team Keys:** Expand the `currentTeamKey` logic in `PreferenceManager.kt`.
- **Cryptographic Separation:** The `teamKey` will be hashed and used as a prefix for the `0xFFFF` Manufacturer Data in BLE advertising. Devices with a different Team Key will instantly discard the scan result in `scanCallback`, acting as if the other device doesn't exist.
- **Bridge Nodes:** Special "Commander" nodes can hold two Team Keys, bridging the Civilian mesh and the Responder mesh when necessary.

## 6. Wi-Fi Direct (Wi-Fi P2P) High-Bandwidth Upgrades
**Concept:** BLE L2CAP is excellent for low-latency text and compressed audio, but too slow for high-resolution images or offline map tiles.
**Implementation Plan:**
- **BLE Bootstrap:** Use the existing BLE Mesh to negotiate a Wi-Fi Direct connection. Node A sends a BLE message: `[WIFI_INVITE, SSID:ResQ_123, PASS:XYZ]`.
- **Dynamic Transition:** Node B receives the BLE invite, connects its Wi-Fi radio to Node A's SoftAP, and transfers the 5MB image file over TCP/IP in 2 seconds.
- **Graceful Teardown:** Once the image is transferred, the Wi-Fi connection is destroyed to save battery, and both nodes fall back to the low-power BLE mesh.
