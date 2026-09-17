# Final Protocol Verdict: Disaster Mesh Network
Historical protocol comparison. Its fixed connection capacities, sub-100-ms setup, pairing, range, and guaranteed L2CAP reliability statements are not verified for ResQMesh devices. The current app uses GATT plus optional L2CAP; [Bluetooth architecture](../bluetooth_architecture.md) describes the source.

## 1. Introduction
This document evaluates the three primary connected Bluetooth protocols—**GATT**, **Bluetooth Classic (RFCOMM)**, and **BLE L2CAP CoC (Connection-Oriented Channels)**—to determine the optimal choice for a disaster mesh network. Advertising (connectionless) data transfer is excluded from this evaluation as it is reserved strictly for discovery.

The evaluation focuses on three critical vectors for a disaster mesh network:
1. Reliability for many connections (10+ devices)
2. Pairing Speed & Automatic Connection capability
3. Distance and physical range limitations

---

## 2. Protocol Evaluation

### Bluetooth Classic (RFCOMM)
*   **Reliability for Many Connections (Poor):** Bluetooth Classic uses a strict piconet topology with a hard limit of **7 active slave devices** per master. Attempting to juggle 10+ devices requires complex scatternet roles and constant role-switching, which frequently crashes the OS Bluetooth stack.
*   **Pairing Speed / Automatic Connection (Poor):** Establishing an RFCOMM socket is notoriously slow and heavily burdened by legacy security. It almost always requires user prompts (PIN codes, Just Works confirmation prompts) on modern mobile operating systems (iOS/Android), breaking the seamless "zero-touch" mesh requirement.
*   **Distance (Moderate):** Limited to standard Bluetooth ranges (typically 10-100 meters depending on the power class). It does not support newer long-range physical layers.

### BLE GATT (Generic Attribute Profile)
*   **Reliability for Many Connections (Moderate/Good):** Modern OS stacks can handle 10-15+ concurrent BLE connections fairly well. However, GATT introduces significant overhead. It is designed for short, stateful data (attributes), so streaming continuous mesh data quickly clogs the MTU buffers, leading to higher latency and dropped packets under load.
*   **Pairing Speed / Automatic Connection (Good):** Connections are extremely fast (often under 100ms). They can be established silently in the background without user prompts if encryption is not mandated by the characteristics.
*   **Distance (Excellent):** Supports Bluetooth 5.0 **Coded PHY**, theoretically allowing ranges of up to 1+ kilometers line-of-sight by using forward error correction (FEC).

### BLE L2CAP CoC (Connection-Oriented Channels)
*   **Reliability for Many Connections (Excellent):** Bypasses the GATT attribute overhead completely, offering a raw data stream (similar to a TCP socket) over BLE. It maintains the ability to connect to 10+ devices concurrently without crashing the OS, and it handles higher throughput and multiplexing significantly better than GATT.
*   **Pairing Speed / Automatic Connection (Excellent):** Opening an L2CAP socket is just as fast as a GATT connection. It inherits BLE's ability to connect automatically in the background without requiring OS-level user pairing prompts.
*   **Distance (Excellent):** Because it runs on top of BLE, L2CAP CoC fully supports **Coded PHY**, enabling the same 1km+ long-range capabilities as GATT.

---

## 3. Final Verdict

**WINNER: BLE L2CAP CoC**

For a disaster mesh network, **BLE L2CAP CoC** is the definitive winner.

*   **RFCOMM** is instantly disqualified due to its strict 7-device limit and intrusive user pairing prompts.
*   **GATT** has the range (Coded PHY) and the background connection speed, but its attribute-based overhead makes it a poor fit for raw, continuous data streams between many nodes.
*   **L2CAP CoC** offers the "best of both worlds." It provides the clean, stream-oriented socket interface of RFCOMM, but runs over the modern BLE stack. This allows it to support **Coded PHY for extreme range**, connect silently and rapidly without user prompts, and reliably juggle 10+ devices by avoiding GATT overhead. It is the perfect backbone for a resilient, high-throughput disaster mesh.
