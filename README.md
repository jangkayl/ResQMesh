# ResQMesh 🌐

**ResQMesh** is a completely decentralized, self-healing, multi-hop mesh communication network designed for off-grid scenarios, disaster recovery, and high-security environments. It enables Android devices to connect seamlessly via Bluetooth 5.4 and Wi-Fi Direct (via the Google Nearby Connections API) to form a robust peer-to-peer (P2P) network without relying on cellular towers or the internet.

## 🚀 Key Features

* **Zero-Infrastructure Communication**: Works entirely offline using device-to-device radio connections.
* **Auto-Upgrading Transport Layer**: Discovery and pairing happen via low-energy Bluetooth (BLE 5.4). When high bandwidth is needed (e.g., sharing photos or voice memos), the protocol natively auto-upgrades the physical link to Wi-Fi Direct.
* **Smart Multi-Hop Relaying**: Messages don't stop at physical connection limits. The Distance-Vector Routing Protocol automatically forwards encrypted payloads through intermediary devices (blind relaying) to reach destinations up to 5-hops away.
* **Gossip Presence Mapping**: Nodes constantly exchange presence pulses. You can view an actively updating "Radar" of all nodes in the city, badged by whether they are a `🟢 DIRECT LINK` or a `🌐 MESH HOP`.
* **End-to-End Encryption**: Private messages are wrapped in secure envelopes. If Device B relays a message from Device A to Device C, Device B cannot read the contents of the payload.
* **Rich Media & Geolocation**: Broadcast or privately send text, base64-encoded audio/images, and precise GPS/Fused Location coordinates.

---

## 🏗️ Architecture & Clean Code

The application strictly adheres to Clean Architecture and modern Android development standards. It uses an **MVI (Model-View-Intent) / MVVM** pattern to ensure UI components are completely decoupled from network logic.

### 📂 File Structure

```text
app/src/main/java/com/example/testresqmesh/
├── core/
│   ├── model/               # Data Transfer Objects (ChatMessage, ConnectedDevice, KnownNode)
│   ├── network/             # Infrastructure Layer (MeshNetworkManager, ConnectionsClient interactions)
│   ├── ui/
│   │   ├── components/      # Reusable UI widgets (HighFidelityChatBubble, ResQTopBar)
│   │   └── theme/           # Design System (Color, Typography, Spacing, Material3)
│   └── utils/               # AppLogger, MediaHelper, NotificationHelper
├── data/
│   └── repository/          # MeshRepository (Single Source of Truth, StateFlow aggregation, Routing Logic)
├── feature/
│   ├── comms/               # Communication Domain (ActiveChatScreen, ChatContainerScreen, CommunicationViewModel)
│   ├── profile/             # User Profile & Identity Management
│   ├── radar/               # Map and Radar plotting logic for discovered nodes
│   └── setup/               # Onboarding, Permissions Handling, Network Bootstrapping
└── ui/
    └── state/               # Immutable UI State classes (ChatUiState, ConnectionUiState, RadarUiState)
```

### 🧩 Core Components

1. **`MeshNetworkManager.kt`**: The lowest-level infrastructure layer. It directly interacts with `Nearby.getConnectionsClient()`. It handles physical topology (`P2P_CLUSTER`), payload parsing, and the blind forwarding logic for multi-hop private messages.
2. **`MeshRepository.kt`**: The central brain. It maintains `StateFlow` streams for UI consumption (`knownNodes`, `privateMessages`, `publicMessages`). It evaluates Distance-Vector rules, applying the Gossip Protocol to map out network presence without overwhelming the radio channels.
3. **`CommunicationViewModel.kt`**: Bridges the UI and the Repository. Handles user intents like sending messages, refreshing the mesh, and requesting GPS locks.
4. **Jetpack Compose UI**: Entirely built in Compose with an emphasis on "Glassmorphism" and high-fidelity military/tactical aesthetics.

---

## 📡 The Routing Protocol: How Multi-Hop Works

ResQMesh implements a customized **Distance-Vector Routing** strategy using Gossip Presence:

1. **Pulse Emission**: Every connected node emits a periodic heartbeat containing its name and status.
2. **Table Building**: When a node receives a heartbeat, it logs the sender into its `knownNodes` registry.
3. **Blind Forwarding**:
   - If User A wants to privately message User C, but is only connected to User B.
   - User A sends the payload to B marked as `isPrivate=true` and `targetName="C"`.
   - User B's `MeshNetworkManager` parses the JSON. It sees `targetName != B's Name`.
   - User B's UI ignores the message, but the network layer instantly executes a `broadcastPayload` excluding the port the message arrived on.
   - User C receives it, sees `targetName == C's Name`, decrypts it, and triggers a local notification.
4. **Visual Indicator**: User C's UI parses the origin and flags the message bubble with a `🌐 MESH HOPPED` badge.

---

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Toolkit**: Jetpack Compose (Material Design 3)
* **Concurrency**: Kotlin Coroutines & StateFlow (Reactive streams)
* **Networking**: Google Nearby Connections API (BLE + Wi-Fi Direct)
* **Location**: Google Play Services Fused Location Provider
* **Dependency Management**: Gradle Kotlin DSL

---

## 🧪 Testing the Mesh (Testing Tools)

To verify the multi-hop capabilities without needing to walk hundreds of meters away, the app includes a **Physical Link Disconnect Tool**.

1. Connect 3 phones in a triangle (A, B, and C all connected).
2. On Phone A, open the private chat with Phone C.
3. Tap the **Red LinkOff** icon in the TopAppBar to sever the physical Bluetooth link between A and C.
4. Send a message from A to C. You will see Phone B's `AppLogger` terminal confirm that it securely relayed the payload, and Phone C will receive the message with a `MESH HOPPED` badge.

---

## 🔒 Permissions Required

Due to the nature of P2P offline radio connections, Android strictly requires the following permissions to ensure user safety and transparency:
- `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `NEARBY_WIFI_DEVICES`
- `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` (Required by Android to scan for nearby BT signals)
- `POST_NOTIFICATIONS`

*Note: The app enforces a strict permission gating flow before allowing the user into the main dashboard.*
