---
tags: [research, collision, google-nearby, routing]
---
# 🔬 Research: Mesh Connection Collisions & Race Conditions
Historical research. Sections about Google Nearby Connections describe an older or alternative transport, not the current native BLE GATT source. Use [Bluetooth architecture](../bluetooth_architecture.md) for the active path.

*Up:* [[master_pipeline]]

## 1. Analysis of Google Nearby Connections (P2P_CLUSTER)
The user's previous implementation utilized the Google Nearby Connections API, which is essentially a massive wrapper around Bluetooth, BLE, Wi-Fi Direct, and WebRTC.
*   **Why it took 8-30 seconds to connect:** Google Nearby uses BLE for discovery, but then attempts to forcefully negotiate a high-bandwidth Wi-Fi Direct connection under the hood. Negotiating Wi-Fi Direct Group Ownership between two Android phones is notoriously slow and highly dependent on environmental interference.
*   **Why it handled 3 devices well:** Wi-Fi Direct easily handles 1 Group Owner and multiple Clients.
*   **Why 4 devices booting simultaneously crashed it:** This is known as the **"Thundering Herd"** or **"Symmetric Collision"** problem.

## 2. The "Thundering Herd" Collision Problem
When 4 devices boot simultaneously, they all begin scanning and advertising at the exact same millisecond.
Node A sees B, C, and D. Node B sees A, C, and D.
Every device immediately fires a "Connect" request to every other device simultaneously. Because mobile radios are half-duplex (they struggle to process an outgoing connection request and an incoming connection request on the same antenna at the exact same time), the requests physically collide. The Android OS panics, drops all connections, and fires a storm of disconnect events.

---

## 3. Industry Solutions for Simultaneous Collisions

### Solution A: Randomized Exponential Backoff (The CSMA/CD Approach)
*Used heavily in standard Wi-Fi and Ethernet routing.*
*   **How it works:** When Node A discovers Node B, it does **not** connect instantly. Instead, it generates a random delay (e.g., between 500ms and 3000ms). It waits. If Node B connects to Node A during that waiting period, Node A simply accepts it and cancels its own outgoing request.
*   **Pros:** Highly resilient. Solves 99% of simultaneous boot crashes because no two devices will attempt to connect at the exact same millisecond.
*   **Cons:** Adds an artificial delay (up to 3 seconds) to the initial mesh formation.

### Solution B: Lexicographical Master Election (The Tie-Breaker)
*Used in standard Bluetooth Piconet formation.*
*   **How it works:** When two nodes discover each other, they compare a unique identifier. The device with the "Highest" ID becomes the designated Initiator (Client). The device with the "Lowest" ID is banned from initiating and must sit silently as a Listener (Server).
*   **Pros:** Mathematically guarantees zero race conditions. Instant connection (no random delays).
*   **Cons:** Requires a flawless, un-truncated ID. (Note: The user attempted this with `myDeviceName > peerName`, but because Android often truncates BLE names to 20 bytes, the names appeared identical to the OS, breaking the tie-breaker and causing a crash).

### Solution C: Asymmetric Duty Cycling
*Used in the official Bluetooth SIG Mesh protocol.*
*   **How it works:** Devices do not advertise and scan 100% of the time. They cycle (e.g., Scan for 800ms, Advertise for 200ms). Critically, a randomized "jitter" is added to the timer so that if 4 devices boot at the exact same time, their cycles immediately drift out of sync, preventing them from shouting over each other.
*   **Pros:** Prevents radio channel saturation in dense rooms (50+ people). Massively saves battery.
*   **Cons:** Increases the time it takes to discover a new peer depending on the cycle length.

---

## 4. Final Verdict for ResQMesh
The disconnect storm happening right now in the GATT implementation is the exact same "Thundering Herd" problem that crashed Google Nearby.

To fix this reliably in a persistent GATT mesh, we must combine **Solution A** and **Solution B**:
1.  We must embed a tiny 4-character random Hex string into the BLE advertisement (e.g., `NodeB_9F2A`).
2.  We use the Hex string for a strict Master Election (`if myHex > peerHex { connect() }`).
3.  If a tie somehow still occurs, we fallback to a Randomized Backoff timer to prevent the collision.
