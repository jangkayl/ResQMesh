# ResQMesh V2: L2CAP & BLE Advertisements Migration Plan
Historical migration proposal. The source already contains optional L2CAP bootstrapped through GATT, while GATT remains an active payload path. The class tree and milestones below should not be read as the current codebase.

## 1. Executive Summary
This document outlines the architectural blueprint for migrating the ResQMesh application from its current data transfer mechanism to a hybrid approach utilizing BLE Advertisements for peer discovery and BLE L2CAP Connection Oriented Channels (CoC) for high-throughput data transfer (API 29+), while falling back to standard GATT characteristics for legacy devices (API 28 and below).

## 2. Discovery Phase: BLE Advertisements
Discovery will entirely rely on BLE Advertising, decoupled from GATT server instantiation during the initial phase.
*   **Advertiser:** Nodes will broadcast a specific service UUID along with a small payload containing node identifiers and capability flags (e.g., L2CAP support flag).
*   **Scanner:** Nodes will scan continuously (or in duty cycles) for the specific service UUID. When a peer is found, the scanner will read the capability flags to determine the connection strategy.

## 3. Data Transfer Phase: L2CAP CoC (API 29+)
L2CAP CoC provides a socket-like interface over BLE, bypassing the GATT overhead, resulting in higher throughput and lower latency.

### 3.1 Server Implementation
The server node must open an L2CAP channel and listen for incoming connections.
*   Use `BluetoothAdapter.listenUsingInsecureL2capChannel()` or `listenUsingL2capChannel()` to obtain a `BluetoothServerSocket`.
*   The system will assign a PSM (Protocol/Service Multiplexer) dynamically.
*   The server must advertise this PSM value. Since BLE advertisements have limited space, the PSM can be exposed via a small GATT characteristic (read-only) or embedded in the scan response data.
*   Once `serverSocket.accept()` returns a `BluetoothSocket`, data can be read/written using standard `InputStream` and `OutputStream`.

### 3.2 Client Implementation
The client node initiates the connection to the server's L2CAP channel.
*   The client discovers the peer and determines the PSM (via scan response or reading a specific GATT characteristic).
*   Use `BluetoothDevice.createInsecureL2capChannel(psm)` or `createL2capChannel(psm)` to obtain a `BluetoothSocket`.
*   Call `socket.connect()`. Upon success, use `InputStream` and `OutputStream` for data exchange.

## 4. GATT Fallback (API 28 and Below)
Devices running Android 9 (API 28) and below do not support L2CAP CoC. The system must gracefully fall back to the existing GATT-based data transfer.
*   **Negotiation:** The advertising payload's capability flag will indicate if L2CAP is supported. If either the scanner or advertiser lacks L2CAP support (determined by API level or flag), they must negotiate a standard GATT connection.
*   **Mechanism:** Data will be chunked into MTU-sized packets and written to/notified via specific GATT characteristics (`WRITE_TYPE_NO_RESPONSE` for higher speed).

## 5. Code Structure Changes
The codebase will be refactored to abstract the underlying transport mechanism.

```
com/resqmesh/
├── ble/
│   ├── discovery/
│   │   ├── BleAdvertiser.kt
│   │   └── BleScanner.kt
│   ├── transport/
│   │   ├── BleTransport.kt (Interface defining send/receive/connect)
│   │   ├── l2cap/
│   │   │   ├── L2capServer.kt
│   │   │   └── L2capClient.kt
│   │   └── gatt/
│   │       ├── GattServer.kt
│   │       └── GattClient.kt
│   └── connection/
│       └── ConnectionManager.kt (Handles strategy selection and fallback)
```

## 6. Implementation Milestones
1.  **Refactor Discovery:** Implement standalone advertising and scanning.
2.  **L2CAP Prototypes:** Build standalone L2CAP server/client components and verify throughput.
3.  **PSM Exchange:** Implement the mechanism to share the dynamic PSM from server to client.
4.  **Transport Abstraction:** Create the `BleTransport` interface and wrap GATT/L2CAP implementations.
5.  **Integration & Fallback:** Wire up the `ConnectionManager` to handle seamless fallback between L2CAP and GATT.
