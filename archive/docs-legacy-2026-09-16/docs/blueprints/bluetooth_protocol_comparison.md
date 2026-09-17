---
tags: [architecture, bluetooth, rfcomm, ble, comparison]
---
# Bluetooth Protocol Comparison for Mesh Networks
Historical design comparison. Assertions about universal Android behavior, exact connection ceilings, guaranteed absence of GATT errors, and competitor implementations are not current project evidence. This checkout uses native GATT with optional L2CAP.

*Up:* [[master_pipeline]]

When building a decentralized disaster app that must work on **ALL** Android devices (not just modern BLE 5.0 devices), choosing the right radio protocol is the most critical decision.

Here is a detailed architectural breakdown of the three native Android approaches, how they work, and the pros/cons of each.

---

## Option 1: BLE 4.x GATT Scatternet (Your Current Approach)
**How it works:** Devices use standard BLE 4.0 scanning to find each other, then form active `BluetoothGatt` connections to transfer data.
*   **Pros:**
    *   **Universal Compatibility:** Works on virtually every Android device from the last 10 years.
    *   **Decent Speed:** With 512-byte MTU and `CONNECTION_PRIORITY_HIGH`, it can stream text and small data reasonably fast.
*   **Cons:**
    *   **The "GATT 133" Death:** Android's BLE stack is incredibly fragile when holding multiple connections. Most Android phones will hard-crash the Bluetooth stack (returning Error 133) if you attempt to hold more than 3 to 4 concurrent GATT connections.
    *   **Mesh Fragmentation:** Because of the 4-connection limit, if 10 people are in a room, they cannot all connect to each other. The network becomes fragmented, requiring complex logic to constantly disconnect and reconnect (thrashing) to pass messages around.

## Option 2: BLE 4.x Connectionless Flooding (Advertisement Broadcasting)
**How it works:** Devices *never* connect to each other. Instead, they chop the message into tiny pieces and embed the pieces directly into their BLE "Advertising Data" (the same signal that broadcasts the device name). Any phone scanning nearby simply reads the advertisement.
*   **Pros:**
    *   **Infinite Scalability:** Because there are no connections, there are no connection limits. 100 devices can be in a room and easily read each other's broadcasts.
    *   **No Errors:** Completely bypasses the fragile Android GATT connection stack.
*   **Cons:**
    *   **Microscopic Bandwidth:** Standard BLE 4.0 advertising packets can only hold a maximum of **31 bytes** total (usually leaving only ~20 usable bytes for your payload).
    *   **Terrible for Large Data:** Sending a 50KB image would require broadcasting 2,500 separate advertisements and praying the receiver doesn't miss a single one.

## Option 3: Bluetooth Classic (RFCOMM / SPP Sockets)
**How it works:** This is the older, standard Bluetooth protocol (the same one used to stream music to your car). It opens a direct, high-speed `BluetoothSocket` (Serial Port Profile) between two MAC addresses.
*   **Pros:**
    *   **Massive Bandwidth:** Can easily achieve 1 to 3 Mbps. It is perfect for sending images, audio, and massive routing tables instantly.
    *   **High Connection Limits:** Unlike BLE GATT, Android can natively and stably hold up to 7 concurrent Bluetooth Classic sockets without crashing.
    *   **Insecure Sockets:** Android provides `createInsecureRfcommSocketToServiceRecord()`, which allows two apps to connect and transfer data entirely in the background *without requiring the users to accept a Bluetooth Pairing pop-up*.
*   **Cons:**
    *   **Battery Drain:** Bluetooth Classic uses significantly more power than BLE when active.
    *   **iOS Incompatibility:** Apple strictly bans third-party developers from using Classic SPP. (If your app is Android-only, this is irrelevant).
    *   **No Background Scanning:** Classic Bluetooth cannot efficiently scan in the background.

---

## The Ultimate Recommendation: The "Universal Hybrid"
If you want to support **all older phones**, bypass the GATT connection limits, and still send images rapidly, the absolute best industry approach (used by competitors like Briar and Bridgefy) is a **BLE + Classic Hybrid**.

### How to Implement It:
1.  **Phase 1: Discovery (BLE 4.0)**
    *   All phones run a low-power `BluetoothLeScanner`.
    *   They advertise a standard 31-byte BLE packet containing only two things: `[App Identifier UUID] + [Their MAC Address]`.
    *   *Result:* Every phone discovers who is nearby using almost zero battery, without ever connecting.
2.  **Phase 2: The Transfer (Bluetooth Classic)**
    *   When you hit "Send Message", your app looks at the MAC addresses discovered by BLE.
    *   It instantly opens a **Bluetooth Classic Insecure RFCOMM Socket** to the target's MAC address.
    *   It blasts the message (or 50KB image) through the socket at 2 Mbps.
    *   *Crucial Step:* The moment the message is delivered, it **closes the socket** to save battery.

### Summary
By using BLE purely for *discovery* and Bluetooth Classic purely for *data transfer*, you get the best of both worlds: extreme battery efficiency, universal compatibility on all old phones, zero GATT 133 crashes, and lightning-fast image transfers!
