---
tags: [ble, architecture, current]
---
# Bluetooth architecture

ResQMesh uses native Android BLE advertising/scanning plus dual-role GATT links. Each node can accept a GATT client through its server and initiate a GATT client connection to another node. The source also starts an optional L2CAP server and may open an L2CAP client socket after reading the peer's PSM through GATT. L2CAP has not replaced GATT.

## Direct-link setup and data

1. NativeBleManager advertises a custom service UUID and scans for the same service.
2. GattClientManager calls connectGatt, attempts MTU negotiation, discovers services, enables notifications through the CCCD, and may read the L2CAP PSM characteristic.
3. GattServerManager accepts incoming GATT links, exposes the service/characteristics, receives client writes, and can notify connected clients.
4. NativeBleManager queues Protobuf payload bytes. Client links write to RX; server links notify through TX. If an L2CAP socket is registered for an endpoint, sendDirectPayload may use its stream instead.
5. PayloadDispatcher and handlers decode, suppress duplicate IDs, deliver locally, or forward through the mesh. MeshRouter uses a neighbor graph to calculate paths.

A connected GATT socket is not necessarily ready for payload exchange. Current setup and queue operations still rely on fixed delays and need callback-driven sequencing; see [BLE repair pipeline](ble_repair_pipeline.md).

## Identity, presence, and direct-link limits

NodeIdentity associates a stable node ID with a peer so advertised and connected endpoint addresses can be matched. SYSTEM pulses carry topology information and public-key data; PINGs refresh a link without a full topology update. The intended direct-link ceiling is not settled: MAX_TOTAL_CONNECTIONS is 3 and MAX_CONNECTIONS is 4 in NativeBleManager.

## Known transport risks

- Server notifications advance after a 20 ms timer without onNotificationSent completion.
- Server disconnect can leave queue, in-flight flag, buffer, MTU, and counters behind.
- Old GATT callbacks and connection timeouts need ownership/generation checks.
- A registered L2CAP socket may be stale. L2CAP failure and GATT fallback need separate device testing.
- The heartbeat/watchdog must be evaluated with a stalled queue so it does not create a self-reinforcing disconnect cycle.

This document describes code structure, not measured range, throughput, or reliability. Bluetooth controller capabilities and Android behavior vary by device.
