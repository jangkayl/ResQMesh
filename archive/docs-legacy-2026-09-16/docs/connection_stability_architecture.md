---
tags: [ble, stability, current]
---
# Connection stability: implemented safeguards and open gaps

Status: source review at temp/d7cd5e3 plus uncommitted changes on 2026-09-15. The current changes add identity-aware duplicate-link handling, distinct-peer counting, a single-flight outbound connect lock, an MTU fallback, and handshake/connect-lock watchdogs. They have not yet been validated as a complete fix on physical devices.

## Safeguards present in source

- Discovery uses a custom service UUID, while NodeIdentity helps match a peer across advertised and connected addresses.
- The scanner checks for an existing link and may avoid a new direct link when an indirect route already exists.
- Outbound connect attempts use an AtomicBoolean lock. The working-tree edits add bounded lock-release paths and fallback service discovery if an MTU callback is missing.
- The server's working-tree edits compare stable identities and link age before accepting a second link to the same peer.
- SYSTEM/PING traffic and an interaction watchdog try to detect silent links.

These mechanisms are heuristics. They do not prove that every triangle causes radio interference, that a five-second-old duplicate is a zombie, or that exactly one healthy link survives every collision.

## Open repair items

1. Server notification queue: processNextPayload advances after 20 ms; GattServerManager lacks onNotificationSent. Use notification completion to advance.
2. Server disconnect: only selected maps are cleared. Queue, isWriting, chunk buffer, MTU, counters, and timers can survive into a reconnect.
3. Stale callbacks: old client GATT callbacks can modify MAC-keyed state for a newer connection. Check GATT instance and generation.
4. Timeout ownership: releasing the connect lock does not itself close the pending GATT attempt. Store and close that exact attempt.
5. Setup sequencing: the CCCD descriptor write, PSM read, and payload sending can overlap due to a 500 ms fallback. Mark a link READY only after required setup completes or an explicit recovery decision.
6. Limits and recovery: MAX_TOTAL_CONNECTIONS = 3 and MAX_CONNECTIONS = 4 need one admission policy; watchdog and optional L2CAP failure must not cause repeated self-inflicted disconnects.

See [BLE repair pipeline](ble_repair_pipeline.md) for implementation phases, tests, and exit criteria. Do not mark these items resolved because a lock watchdog prevents one form of permanent lock hold.
