# Blueprint: Dense Cluster Topology (Degree-Constrained Mesh)
Historical topology design. A degree-three limit and ten-phone connectivity are design targets, not proven device guarantees. The active direct-link constants are inconsistent; see [project status](../project_status_tracker.md).

## Problem Statement
When dealing with dense clusters of devices (10+ devices in a single room) over Android Bluetooth Low Energy (BLE) GATT, devices are practically constrained to a maximum of ~3 concurrent connections due to OS and hardware limitations. The challenge is to form a fully connected mesh network (scatternet) where all devices can communicate via multi-hop routing, ensuring no device is left disconnected (orphaned).

## 1. Algorithms for Degree-Constrained Scatternets

To maintain a connected mesh where each node has a degree $d \le 3$, we must form a degree-constrained spanning tree or graph.

### A. BlueTrees
- **Concept:** A distributed protocol designed to build a connected scatternet. It designates a "root" node that connects to neighbors, which in turn connect to their neighbors, forming a tree. If a node hits the degree limit (3), it stops accepting new children and instructs remaining unconnected neighbors to find other parents.
- **Pros:** Guarantees a connected tree if the underlying physical topology allows it. Simple routing (tree routing).
- **Cons:** High latency between leaf nodes. Root node becomes a bottleneck and a single point of failure.

### B. BlueStars
- **Concept:** Forms a clustered mesh. Nodes elect "cluster heads" based on metrics (e.g., node ID, battery life). Cluster heads connect to surrounding nodes (slaves). Since a cluster head can only have 3 slaves, the network requires overlapping clusters (bridge nodes) to connect the scatternet.
- **Pros:** Decentralized. Better fault tolerance than a simple tree.
- **Cons:** Complex role-switching (master/slave) required for bridge nodes. Highly constrained by the 3-connection limit, meaning clusters are very small (1 master, 2 slaves, 1 bridge), increasing the number of hops.

### C. Randomized k-Regular Graphs (or k-Degree Constrained Graphs)
- **Concept:** Nodes randomly connect to any available node until they reach exactly $k$ connections (here, $k=3$). If a node needs a connection, it queries neighbors. Over time, the network converges into a 3-regular-like graph.
- **Pros:** Highly resilient to node failure. No bottlenecks (no root node). Excellent load balancing.
- **Cons:** Slower convergence. Routing is more complex (requires AODV, DSR, or flood-based routing) compared to a tree.

---

## 2. Orphan Discovery and Integration

If 10 devices are maxed out at 3 connections each, the network is "full" from a local perspective. An 11th device entering the room will see 10 devices, all of which refuse new connections. How does the 11th device integrate?

### A. Connection Rotation (Time-Division Multiplexing)
- **Mechanism:** Nodes do not maintain static connections. Instead, they rapidly cycle through connections. A node might connect to A, B, and C for 100ms, then drop C to connect to D for 100ms.
- **Pros:** Allows an infinite number of devices to share the medium. "Orphans" naturally get picked up during the next rotation cycle.
- **Cons:** Very high overhead on Android BLE. Connection setup takes time (often >1 second). Dropping and recreating connections constantly leads to massive packet loss and throughput degradation.

### B. Preemption / VIP Eviction (Topology Rebalancing)
- **Mechanism:** The network maintains a shared state of the topology. When an orphan broadcasts its presence (via BLE advertising), a nearby node (Node X) with 3 connections detects it. Node X evaluates its 3 existing connections. If dropping one connection (e.g., to Node Y) does not partition the network (because Y has other paths to the mesh), Node X drops Y and connects to the orphan. Node Y is now degree 2 and can find another connection if needed.
- **Pros:** Maintains static connections (stable throughput, low latency). Guarantees that orphans can force their way into the mesh as long as the global graph can support another node.
- **Cons:** Requires a distributed topology map (e.g., Link State routing protocol) so nodes know if dropping a connection will cause a partition.

### C. The "Staircase" or "Leaf Node" Handshake
- **Mechanism:** A dedicated "entry node" protocol. When an orphan (O) sees a maxed-out node (M), M temporarily accepts the connection (exceeding its soft limit or dropping a non-critical connection temporarily) just long enough to pass O the routing table. M tells O, "I am full, but node Z only has 2 connections. Connect to Z." M then drops O.
- **Pros:** Very fast orphan resolution. Low impact on the main mesh.
- **Cons:** Requires at least one node in the mesh to have $d < 3$. If the entire network is perfectly 3-regular, this fails unless the graph is expanded.

---

## 3. Pros and Cons Summary

| Approach | Pros | Cons |
|---|---|---|
| **BlueTrees** | Simple, easy routing | Bottlenecks, high latency, brittle |
| **BlueStars** | Clustered, decentralized | Role-switching overhead, small clusters |
| **Random k-Constrained** | Highly resilient, low bottleneck | Complex routing, slow convergence |
| **Connection Rotation** | Zero orphans theoretically | Practically unusable on Android BLE |
| **Preemption / Rebalancing**| Stable mesh, active orphan inclusion | Requires link-state topology knowledge |

---

## 4. Final Recommendation

For a robust Android BLE scatternet limited to 3 connections per node, the recommended approach is a **Randomized Degree-Constrained Graph combined with Preemption/Rebalancing for Orphan Discovery**.

### Why?
1. **Topology:** A tree (BlueTrees) is too fragile for a dynamic mobile environment. A randomized mesh provides the redundant paths necessary when people walk around and block BLE signals.
2. **Orphan Handling:** Android BLE connection setup is too slow for Connection Rotation. Static, long-lived connections are strictly required for reliable data transfer. Therefore, **Preemption (Topology Rebalancing)** is the only viable way to let a new node into a saturated local cluster.

### Implementation Strategy:
1. **Routing:** Implement a lightweight Link-State routing protocol (like OLSR or a custom flooding protocol) so every node knows the full graph topology.
2. **Connection Phase:** Nodes aggressively seek 3 connections.
3. **Orphan Phase:** Orphans broadcast a special `ORPHAN_BEACON`.
4. **Resolution:** Any maxed-out node that hears the `ORPHAN_BEACON` checks its topology map. It identifies its most "redundant" edge (an edge that is part of a cycle). It drops that edge and connects to the orphan. If no redundant edge exists locally, it ignores the beacon, hoping another node will hear it.
