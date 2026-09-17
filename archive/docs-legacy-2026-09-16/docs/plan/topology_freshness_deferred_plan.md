---
tags: [plan, deferred, topology, mesh, ble]
---
# Deferred Plan: Topology Freshness and Relay Flicker

*Up:* [[project_summary]] | *Related:* [[bluetooth_architecture]] | [[connection_stability_architecture]]

Status: DEFERRED. Diagnosed and planned, not implemented. Parked while the connection
deadlock and duplicate-link fixes land first, because both touch the same files
(`NativeBleManager`, `MeshRepository`, `MeshRouter`, `RadarScreen`).

## Symptom

Three devices, one hub with two links, two leaves with one link each. On a leaf, the
other leaf oscillates: `Connected (Via Relay)` -> `Discovered / Scanning...` -> back
again, on a repeating cycle. Real GATT connection attempts accompany the label change,
so it is not purely cosmetic.

## Root cause

A ratio bug between three intervals that live in different files.

| Mechanism | Location | Interval |
| --- | --- | --- |
| Topology staleness prune | `MeshRouter.startTopologyCleanup` | removes after 10s |
| Forced full `SYSTEM` pulse | `NativeBleManager.sendSystemPulse` | every 60s when stable |
| `PING` micro-pulse | `NativeBleManager.sendSystemPulse` | every 5s, carries no topology |

Once the mesh is stable the delta optimisation sends only `PING`, and `PingHandler`
deliberately does not relay it. A leaf therefore only refreshes its knowledge of the
far leaf when a full `SYSTEM` pulse is relayed by the hub, i.e. every 60s, while the
cleanup loop prunes it after 10s. That leaves roughly a 50s dead window per minute.

Only indirect nodes are affected: `recalculateKnownNodes` rebuilds direct peers from
`connectedDevices` on every pass, so the direct link never flickers.

The flap also drives radio churn. `scanCallback` gates dialling on `checkRouteExists`,
which reads `knownNodes`, so every prune authorises a real connection attempt to a peer
already reachable through the mesh.

## WP1 - Stop the flicker

1. **One constant governs both cadences.** Define `FULL_PULSE_INTERVAL_MS = 20_000` and
   derive prune thresholds from it: stale at 2x, remove at 3x (40s / 60s). Replaces the
   literal `60000` in `sendSystemPulse` and `10000` in `startTopologyCleanup`. A node
   then needs three consecutive missed pulses to disappear. Cost is negligible: the full
   pulse is ~141 bytes against 5s PING traffic already on the wire.
2. **Stale tier instead of hard removal.** Keep entries in `networkGraph` until the
   remove threshold and add `isStale` to `KnownNode` (which already carries `lastSeen`).
   Radar renders stale relays dimmed rather than reclassifying them.
3. **`PING` counts as liveness.** Add `onPeerHeartbeat(senderName)` to
   `PayloadDispatcherCallback`, call it from `PingHandler`, forward to
   `meshRouter.markNodeSeen()`. Keep `PingHandler` non-relaying; that is a deliberate
   battery optimisation. This covers direct neighbours only, which is correct because
   item 1 covers indirect ones.
4. **Debounce route loss.** Treat stale-but-present nodes as still routed in
   `checkRouteExists`, and track `routeLostAt` per peer so a route must be continuously
   absent for ~15s before dialling is allowed.
5. **Fix the RELAY/HOPPED split.** Current classification uses presence in
   `scannedDevices`, but scan rows time out after 8s and churn on MAC rotation, so a
   stable relay oscillates RELAY <-> HOPPED. Add `hopCount` to `KnownNode`, computed in
   `recalculateKnownNodes` via the BFS already in `findShortestPath`, and display
   "Via Relay - 2 hops" versus "Mesh Hop - 4 hops" from that number.
6. **Minimum dwell time.** In `RadarViewModel`, hold a per-node-key timestamp and
   suppress category changes within ~3s. Keep `classifyRadarNodes` pure.

## WP2 - Fix the topology data itself

1. **Stop advertising scanned devices as connected.** `sendSystemPulse` builds
   `connectedNodes` from `store.connectedEndpointNames`, but `scanCallback` writes into
   that same map for devices only seen, not connected:

   ```kotlin
   store.connectedEndpointIds.add(macAddress)
   store.connectedEndpointNames[macAddress] = peerName
   ```

   So a node tells the mesh it is connected to peers it merely scanned. With several
   phones in one room this injects phantom edges into every graph, corrupts pathfinding,
   and makes the change-detection hash churn on scan noise. Build the list from
   `activeConnections.keys + activeServerConnections.keys` mapped through
   `connectedEndpointNames` and filtered with `NodeIdentity.isPlaceholder`, and compute
   `lastSystemPulseHash` from that same set.
2. **Namespace `PING` IDs.** WIRE CHANGE. IDs are `P1`, `P2`, ... from a per-device
   counter, but dedup is a single global set and `broadcastPayload` caches outgoing IDs,
   so a node's own `P7` permanently blocks a neighbour's `P7`. Use `"${myNodeId}-P$n"`
   or a UUID.
3. **Do not discard a neighbour's edge list on a transient disconnect.**
   `onDeviceDisconnected` calls `meshRouter.removeNode(name)`, which drops
   `topology[thatNode]` immediately, so one blip on the hub link erases everything
   reachable through it. Distinguish confirmed departure (explicit `GOODBYE`, remove now)
   from a dropped link (mark stale, age out).

## Verification

Three-node setup, idle 3+ minutes, watch `AppLogger` on a leaf. Before the fix the far
leaf vanishes ~10s after each pulse and returns ~50s later. After WP1 it should hold
steady with zero connection attempts toward the other leaf. After WP2 the topology graph
should get smaller and more accurate as phantom edges disappear.

## Related scale work (separate, larger)

Not part of WP1/WP2 but blocking anything past roughly five devices:

- TTL / hop limit on every payload. No hop limit exists anywhere in the code today; loop
  prevention rests entirely on the dedup cache. WIRE CHANGE.
- Replace the 500-entry insertion-ordered `seenMessageIds` set with a time-windowed cache
  keyed on `(senderName, id)`. Evicting an ID while copies are in flight makes a packet
  look new again.
- Flood broadcast traffic along the existing Kruskal spanning tree. Keep `SYSTEM` on full
  flood to avoid a bootstrap deadlock, protected by TTL and rate limiting.
- Reserve one connection slot for joiners; refuse to evict articulation points.
- Use RSSI for admission, route weight, and eviction preference.
- Extract `IBleManager` and add a `VirtualBleManager` harness for N-node JVM tests.
