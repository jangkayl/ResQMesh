---
description: Comprehensive knowledge base of the ResQMesh networking architecture running on the main branch (Google Nearby Connections).
trigger: "When the user asks about the network implementation, Google Nearby Connections, Discovery, or why devices fail to connect."
tags: [resqmesh, network, nearby-connections, architecture]
---

# ResQMesh Network Architecture (Google Nearby Connections)

This document outlines the networking engine currently running on the `main` branch. It relies on the `com.google.android.gms.nearby` API using the `P2P_CLUSTER` strategy to automatically negotiate Wi-Fi Direct and Bluetooth connections.

## 1. Core Technology: Google Nearby Connections API
Instead of managing raw Bluetooth Sockets or BLE Advertising directly, the `main` branch delegates all network routing to Google Play Services (`ConnectionsClient`).
- **Strategy:** `Strategy.P2P_CLUSTER` (Supports an N-to-N M-to-M star topology where everyone connects to everyone).
- **Physical Mediums:** The API automatically starts on Bluetooth (for discovery) and attempts to upgrade to Wi-Fi Direct (for high bandwidth).

## 2. The Smart Tie-Breaker Algorithm (Collision Prevention)
When two devices discover each other simultaneously via Google Nearby, they will both attempt to call `requestConnection()`. If they do this at the exact same millisecond, the Android OS drops both connections (Simultaneous Open Collision).

To solve this, the engine calculates a **Power Score** for each device (based on RAM, CPU cores, and OS version). 
- The Power Score is broadcasted in the discovery name (e.g., `45|John's Phone`).
- **Initiator:** The device with the HIGHER Power Score claims the "Initiator" role and calls `requestConnection()`.
- **Receiver:** The device with the LOWER Power Score claims the "Receiver" role and waits 12 seconds (`Handler.postDelayed`). If the Initiator fails to connect in 12 seconds, the Receiver triggers a failsafe and attempts to connect.

## 3. Sequential Connection Queue (Mutex Lock)
Spamming `requestConnection()` for 10 devices simultaneously crashes the Nearby Connections API.
- The engine uses a `ConcurrentLinkedQueue` and an `AtomicBoolean` (`isConnectingLock`).
- Devices are connected sequentially. The lock is only released inside `onConnectionResult()`, ensuring the Bluetooth Kernel is never overloaded.

## 4. The Deep Cache Wipe
Google Nearby Connections notoriously leaves "Ghost" connections alive in the background even when stopped.
When `startMeshNode()` is called, the engine executes a Deep Cache Wipe:
```kotlin
connectionsClient.stopAllEndpoints()
connectionsClient.stopAdvertising()
connectionsClient.stopDiscovery()
```
This forces the Android Kernel to drop all lingering Bluetooth sockets before restarting the mesh.
