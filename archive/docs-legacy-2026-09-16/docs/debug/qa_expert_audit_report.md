---
tags: [audit, qa, bluetooth, l2cap, rfcomm]
---
# QA Engineering Report: V2 L2CAP & Hybrid Plans
Historical review of proposed transports. Its device-limit and crash predictions are hypotheses, not measured results for the current checkout. Current BLE repair priorities are in [the repair pipeline](../ble_repair_pipeline.md).

*Up:* [[master_pipeline]]

I have aggressively audited both the `hybrid_architecture_migration_plan.md` and the `v2_l2cap_migration_plan.md`. Both plans have edge cases that will cause production crashes if not addressed.

## Part 1: Hybrid Architecture (BLE + RFCOMM) Evaluation
**Verdict:** 3/10. The current blueprint will fail in a production environment with modern Android devices.

### Critical Edge Cases (Hybrid Plan):
1. **Android 10+ MAC Obfuscation Fatal Flaw:** The plan suggests using `ACTION_REQUEST_DISCOVERABLE` to broadcast the true Classic MAC and cache it via "Node Name". This is deeply flawed. BLE uses a randomized MAC. To connect via RFCOMM, it needs the public Classic MAC. If the OS rotates the BLE MAC, the mapping is lost.
2. **BLE/Classic Radio Contention:** BLE scanning and Classic RFCOMM operate on the same 2.4GHz radio but use different scheduling. Running a continuous BLE scanner while rapidly opening/closing RFCOMM sockets will cause severe time-slicing contention at the hardware layer, leading to `IOException`.
3. **Android 12+ Permissions:** Attempting to call `serverSocket.accept()` without explicitly checking `BLUETOOTH_CONNECT` at runtime will result in an immediate `SecurityException` crash.

## Part 2: V2 L2CAP Migration Plan Evaluation
**Verdict:** The L2CAP CoC plan is much closer to a viable modern architecture, but it also has hidden traps.

### Critical Edge Cases (L2CAP Plan):
1. **The PSM Bootstrapping Paradox:** To open an L2CAP socket, the client needs the server's dynamically assigned PSM (port number). If you have to initiate a fragile GATT connection just to read the PSM, you have defeated the entire purpose of bypassing GATT connection limits! Alternatively, placing the PSM in the Scan Response is better, but Android's delivery of Scan Responses is notoriously unreliable on Chinese OEMs (Xiaomi, Oppo).
2. **L2CAP Channel Exhaustion:** While L2CAP bypasses the GATT 133 error, L2CAP channels are still physically constrained by the Bluetooth Controller's memory (often limited to ~5-10 concurrent CoC channels). Sockets must be aggressively closed.
3. **GATT Fallback (API 28-) Connection Limit Regression:** If a large number of legacy devices interact, the GATT fallback mechanism will instantly re-introduce the `MAX_TOTAL_CONNECTIONS` crashes. The legacy fallback *must* retain strict VIP eviction and chunking logic.

## Part 3: RFCOMM vs L2CAP CoC (Android 10+)
**Winner: L2CAP CoC**
RFCOMM is fundamentally incompatible with modern Android's privacy-centric BLE MAC rotation unless devices are bonded. L2CAP CoC operates entirely within the BLE stack, meaning you can connect directly to the randomized BLE MAC discovered during scanning without needing the Classic MAC.

## Part 4: STRICT LIST OF CRASH-PREVENTION EDGE CASES
To prevent the app from crashing in production, the final code *must* implement handling for:
1. **`SecurityException` wrapping:** Every single Bluetooth API call must be pre-checked for `BLUETOOTH_CONNECT`/`SCAN`/`ADVERTISE` permissions, especially on Android 12+.
2. **`IOException` on Socket Connect:** Connections must have strict timeouts and exponential retry backoffs.
3. **Scan Response Nullability:** If using Scan Response for L2CAP PSM, the code must gracefully handle missing data and queue a retry instead of throwing a `NullPointerException`.
4. **API Level Guard Rails:** `listenUsingL2capChannel` will throw `NoSuchMethodError` on API 28 and below. Gated strictly behind `Build.VERSION.SDK_INT >= 29`.
5. **Concurrent Modification in Network Graphs:** Processing thousands of BLE advertisements asynchronously will crash the UI if the Mesh Graph data structure isn't thread-safe (e.g., using `ConcurrentHashMap`).

**Recommendation:** Abandon the Hybrid RFCOMM plan entirely. Refine the V2 L2CAP Plan by embedding the PSM in the primary Advertisement payload (if space permits) or using a robust fallback to Scan Response.
