---
tags: [architecture, pipeline, hybrid, ble, rfcomm]
---
# Hybrid Architecture Migration Plan (BLE + RFCOMM)
Historical proposal, not an approved migration or current code path. ResQMesh currently uses native GATT with optional L2CAP and has no RFCOMM mesh transport.

*Up:* [[master_pipeline]]

This document outlines the strict, step-by-step engineering pipeline to convert ResQMesh from a fragile "Always-On GATT Scatternet" to a highly reliable **Hybrid Mesh (BLE Discovery + Classic RFCOMM Data)**.

By following this exact pipeline, we ensure zero regressions, eliminate the GATT 133 connection limit crashes, and significantly boost data transfer speeds.

---

## The Architecture Blueprint

### 1. The Discovery Layer (BLE 4.0)
- **Component:** `BluetoothLeAdvertiser` & `BluetoothLeScanner`
- **Function:** Devices will broadcast a standard 31-byte BLE Advertisement containing a custom Service UUID and their Mesh Node ID.
- **State:** Always running in the background. Uses ~1% battery per hour. *Zero connections are made during this phase.*

### 2. The Listener Layer (Bluetooth Classic Server)
- **Component:** `BluetoothServerSocket` (RFCOMM)
- **Function:** A background thread running `listenUsingInsecureRfcommWithServiceRecord(UUID)`. It waits silently for an incoming socket connection. When it receives one, it reads the byte stream (the message), passes it to `PayloadDispatcher`, and immediately closes the socket.

### 3. The Transfer Layer (Bluetooth Classic Client)
- **Component:** `BluetoothSocket`
- **Function:** When the user hits "Send", the app grabs the target's MAC address from the BLE Discovery graph. It calls `createInsecureRfcommSocketToServiceRecord()` to form a high-speed data pipe, blasts the `MeshPayload` bytes, and immediately tears down the socket.

---

## Implementation Pipeline & Checklist

### Phase 1: Codebase Cleansing & Teardown
Before building the new architecture, we must cleanly remove the old fragile GATT logic to prevent conflicts.
- [ ] Remove `BluetoothGattServerCallback` from `NativeBleManager.kt`.
- [ ] Remove `BluetoothGattCallback` (the GATT Client logic).
- [ ] Strip out the MTU 512-byte negotiation and Chunking logic (Bluetooth Classic RFCOMM handles stream buffering natively at the OS level, meaning we no longer have to manually chop payloads into 512-byte arrays!).
- [ ] Remove the `MAX_TOTAL_CONNECTIONS` VIP eviction logic.

### Phase 2: BLE Connectionless Discovery
- [ ] Implement `startAdvertising()` using `AdvertiseData.Builder()`. Embed the Node Name into the Service Data.
- [ ] Implement `startScanning()`. When a `ScanResult` is found matching our UUID, update the `MeshRouter` network graph with the device's MAC Address and Name.
- [ ] Set a TTL (Time-To-Live) on discovered nodes so they disappear from the Radar if their BLE beacon isn't heard for 60 seconds.

### Phase 3: Classic RFCOMM Listener (The Server)
- [ ] Create an `RfcommServerThread` inner class.
- [ ] Initialize `adapter.listenUsingInsecureRfcommWithServiceRecord(CUSTOM_UUID)`.
- [ ] Run a `while(true)` loop calling `serverSocket.accept()`.
- [ ] When a socket is accepted, read the `InputStream` fully, convert it to a `ByteArray`, and pass it to `processBinaryPayload()`.
- [ ] **Close the socket immediately** after reading to free up the radio.

### Phase 4: Classic RFCOMM Sender (The Client)
- [ ] Rewrite `sendDirectPayload(targetMac, payloadBytes)`.
- [ ] Look up the `BluetoothDevice` using the target MAC.
- [ ] Call `device.createInsecureRfcommSocketToServiceRecord(CUSTOM_UUID)`.
- [ ] Call `socket.connect()`.
- [ ] Write the `payloadBytes` to the `OutputStream`.
- [ ] **Close the socket immediately**.

### Phase 5: The Android 10+ MAC Address Challenge
- **The Problem:** Modern Android (10+) randomizes BLE MAC addresses to prevent tracking. You cannot connect a Bluetooth Classic socket to a randomized BLE MAC.
- **The Solution:** We will implement Android's `ACTION_REQUEST_DISCOVERABLE` intent during the Setup phase. This temporarily exposes the real Bluetooth Classic MAC address to nearby devices, allowing them to cache the true MAC linked to the user's Node Name.

---

## Pros & Cons of this Specific Implementation

**Pros:**
*   **Zero Chunking Logic:** RFCOMM uses `InputStream/OutputStream`. You can push a 50KB image directly into the stream, and the OS handles the packetization. We delete hundreds of lines of complex chunking code.
*   **No Connection Limits:** Because connections only exist for the 0.5 seconds it takes to send a message (and then close), hundreds of devices can operate in the same room without hitting a concurrent connection limit.
*   **High Speed:** RFCOMM operates on the BR/EDR radio, offering speeds up to 2 Mbps natively.

**Cons:**
*   **The MAC Address Workaround:** We will have to prompt the user to make their device discoverable during onboarding to sync their true MAC address with their peers.
*   **No Long Range (PHY_LE_CODED):** As discussed, this limits the physical distance between any two nodes to ~30-50 meters, relying on high density (lots of users) to mesh the data across long distances.
