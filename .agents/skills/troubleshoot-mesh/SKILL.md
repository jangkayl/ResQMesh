---
name: troubleshoot-mesh
description: >-
  Provides runbooks, diagnostic commands, and guidelines for troubleshooting and extending the ResQMesh Bluetooth and BLE networking architecture.
---

# Troubleshooting ResQMesh Network Engine

This skill provides step-by-step instructions for diagnosing issues with the ResQMesh Bluetooth engine.

## 1. Network Topography
The engine is split into two halves:
- `ConnectionlessBleMeshManager.kt`: Handles background discovery via tiny BLE advertisements.
- `BluetoothClassicMeshManager.kt`: Handles heavy-lifting data transfer via Secure RFCOMM bonded sockets.

## 2. Common Issues & Runbook

### Issue A: Sockets fail to connect (Connection Refused)
**Diagnosis:** 
Android 10+ heavily restricts Service Discovery Protocol (SDP). If devices are not paired, the kernel drops incoming requests to non-standard UUIDs.
**Fix:**
Ensure the `createBond()` pairing trigger in `BluetoothClassicMeshManager.kt` is firing for new devices. Check the `[RQ]` device name filter to ensure the target device is successfully identifying itself.

### Issue B: Device only sends one message, then breaks (TIME_WAIT bug)
**Diagnosis:**
The sender closed the RFCOMM socket while bytes were still propagating through the airwaves, causing the kernel to corrupt the SDP cache.
**Fix:**
Ensure the ACK mechanism is intact. The sender must wait for `inStream.read()` and the receiver must send `outStream.write(1)` before either calls `socket.close()`.

### Issue C: Logcat is spamming BLE Chunk logs
**Diagnosis:**
The `ConnectionlessBleMeshManager` rapidly pulses ~20-byte BLE advertisements.
**Fix:**
Check `startChunkBroadcast()` and verify that `AppLogger` calls are silenced for legacy broadcast failures (which happen frequently when the hardware advertisement limit is hit).

## 3. Extending the Architecture
If you need to add new network features:
1. **Large Data:** Always use `BluetoothClassicMeshManager`.
2. **Background Pings:** Always use `ConnectionlessBleMeshManager`.
3. Do NOT attempt to use Insecure Sockets (`listenUsingInsecureRfcommWithServiceRecord`) on modern Android unless you have a workaround for the SDP fingerprinting blocks. Stick to Secure Sockets and `createBond()`.
