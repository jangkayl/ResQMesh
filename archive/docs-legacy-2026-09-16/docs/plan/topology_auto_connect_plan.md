# Auto-Connection & Mesh Topology Master Plan (V2)
Historical topology proposal. Its 100% reliability, zero-error, and fixed Android GATT-limit claims are not verified. Finish the [BLE repair pipeline](../ble_repair_pipeline.md) and collect device results before adopting this plan.

This blueprint outlines a secure, crash-free architecture to solve the "No One Left Behind" topology issue for ResQMesh. The goal is to allow a dense room of devices (e.g., 10+ phones) to self-organize into a single, unified mesh without deadlocks, without exceeding Android's strict BLE limits, and without any orphaned nodes.

---

## Core Concept: The Dynamic Hybrid Spanning Tree
Android's native BLE stack is inherently unstable when a single device attempts to hold more than 3-4 concurrent GATT connections. To guarantee 100% reliability, **no single device will ever hold more than 3 connections.**

To prevent devices from being "left behind" (orphaned) because all nearby nodes are full, we will implement a **Preemptive Node Eviction** system combined with a **Watchdog Scanner**.

### Pros & Cons of this Approach
**Pros:**
* **Zero Orphan Guarantee:** A stranded device will actively force a connected device to drop an idle connection to make room.
* **Android-Safe:** Keeping the hard limit to `MAX_CONNECTIONS = 3` guarantees zero native Android GATT crashes (Code 133).
* **Battery Efficient:** Devices on the edge of the mesh act as "Leaf Nodes" and turn off their active advertising, saving battery.

**Cons:**
* **Increased Latency:** Messages might take 2-4 hops to cross a room of 10 people instead of being broadcast directly.
* **Complex State Management:** Requires careful Coroutine Watchdog loops to prevent devices from constantly disconnecting and reconnecting to each other (thrashing).

---

## Execution Pipeline (4 Phases)

### Phase 1: The Watchdog Scanner & Thread-Safety Base
Before adding complex topology logic, we must ensure the `BleStateStore` and background loops do not trigger `ConcurrentModificationException` when modifying maps on different threads.
1. **Thread-Safe Memory:** Upgrade all critical State maps (like `activeConnections`, `connectedEndpointNames`) to `ConcurrentHashMap`. Use thread-safe loops for any iterators.
2. **The Watchdog Loop:** Implement a Coroutine loop that runs every 10 seconds. It will scan the `endpointLastSeen` list. If a known device hasn't sent a SYSTEM pulse in 15 seconds, it forcefully cleans up the socket to prevent Zombie connections.

### Phase 2: Dynamic Connection Limits & Leaf Nodes
We will limit the active connections dynamically based on how many devices are in the room to prevent radio contention.
1. **Max Limit:** Hard cap at exactly **3 connections**.
2. **Leaf Node Mode:** If a device is connected to at least 1 other device, and it detects >4 active nodes in the room, it voluntarily drops its `MAX_CONNECTIONS` limit to **1** and stops advertising. It becomes a battery-saving "Leaf Node".
3. **Core Routers:** Only 2-3 devices in the room will naturally become "Core Routers" holding 3 connections, acting as the backbone of the mesh.

### Phase 3: The "No One Left Behind" Eviction Protocol (Rescue)
How does the 10th person connect if everyone else is full?
1. If Device J (the orphaned node) turns on, it will scan and see Devices A, B, and C.
2. Device J will attempt to connect to Device A.
3. Device A is completely full (3 connections). Device A will accept the connection momentarily, check its routing table, and realize Device J is completely orphaned (0 connections).
4. Device A will look at its 3 existing connections and find the one that has been "idle" the longest (Least Recently Used).
5. Device A will **intentionally disconnect** that idle device and give the slot to Device J.
6. The disconnected idle device will easily auto-reconnect to Device B or C because it already has their routing data. No one is left behind!

### Phase 4: Instant Routing Table Sync (One-Way Bug Fix)
To prevent "One-Way Routing" ghost bugs where devices connect but can't message each other:
1. Immediately upon a successful GATT connection, both devices will instantly fire a `SYSTEM` pulse containing their `connectedNodes`.
2. This allows the newly rescued node to instantly broadcast its presence to the entire mesh, updating the global Spanning Tree map instantly.

---

## Next Steps
Once approved, we will begin executing **Phase 1** strictly adhering to the Clean Architecture boundaries, building a highly thread-safe base before injecting the rescue logic.
