# Historical initial networking extraction

Archived: 2026-10-05. This preserves the implemented initial slice before the approved whole-application phases. Its line counts, APK evidence and deferred labels are historical.

# Networking maintainability refactor

Last reviewed: 2026-10-05. Status: implemented structural slices; physical comparison pending.
Source baseline: `db2260e`, branch `fix/incident-ui-ux`, plus the preserved pre-existing workspace changes.

## Objective and boundaries

Give networking responsibilities clear file and dependency ownership while preserving BLE and mesh behavior. This is maintenance work; no speed, delivery-rate or reliability improvement is claimed. DELIVERY-01 remains an unrelated, deferred investigation.

Retain the main handler, single transfer executor, generation ownership, registration gates, initialization order, callback lifetime, state stores, cleanup sequence, queue limits/order, timing, admission/election, routes, receipts, blocks, protocol fields, crypto and Room schema. Existing UI, ViewModels, services and untracked helpers/tests are outside this change.

## Implemented slices

1. **Contracts and handler ownership.** `MeshNetworkGateway` is the repository contract; `NativeBleGateway` now has its own file. Presence, delivery, link-control, audio and incident handlers live in focused files under `core/network/dispatch`. All twelve handler bodies and registration order are preserved.
2. **Facade collaborators.** `BlePresencePublisher` owns pulse counters, topology sequence and full/PING publication. Identity and key providers remain live; only hash/time reset on transport start. `NativePayloadCallbacks` forwards current listeners synchronously through `NativePayloadEvents` and explicit transport capabilities. Private relay policy stays in the facade.
3. **Repository bindings.** `MeshNetworkEventBinder` performs the same 23 assignments once from repository initialization. Named private repository handlers retain peer, route, block, receipt and persistence policy. Binding is not moved to start/stop or suspension. This improves navigation without claiming the repository is fully decomposed or shorter.
4. **GATT capabilities.** Client/server host contracts expose their required operations and shared live resources. `NativeGattHost` proxies the same facade; it creates no state copy, handler, worker or resource lifetime. Compatibility constructors retain existing manager construction. Callback bodies remain unchanged apart from receiver/remote-device access through the host.

The facade is 1,384 lines versus 1,494; more complex lifecycle and dispatch coordination remains deliberately visible. Smaller files are a consequence of responsibility ownership, not a completion threshold.

## Checks and evidence

The local snapshot is `app/build/network-maintenance-audit/2026-10-05/baseline.json`; it preserves 457 tracked/existing untracked files and original contents. It is an ignored local artifact, not a replacement for the historical presentation baseline.

```powershell
python scripts/check_network_refactor.py --baseline app/build/network-maintenance-audit/2026-10-05/baseline.json
```

The fixed allowlist rejects unrelated edits. The guard compares handler bodies, gateway relocation, GATT callbacks, orchestration methods, presence decisions/reset and repository bodies/binding order. Focused presence/adapter tests cover refresh boundaries, empty withdrawal, reset continuity, current listeners/identity, dispatch results and argument preservation. Builds/tests/Lint and current APK identity are recorded in [validation](../../docs/validation.md) and the [phone card](../../docs/testing/network-maintainability-test-card.md).

No phones or emulator are operated for this refactor. Local success cannot close physical validation or the earlier intermittent A-to-D failure. Original validation details are preserved in the [October 5 snapshot](README.md).

## Reviewed remaining candidates

`BlePeerAdmissionController` already owns bounded candidates and a testable scheduler. Further separation would cross candidate/drain and retirement timing, so its policy remains intact. `IncidentRepository` already composes help/sync collaborators; legacy projection/reconciliation must retain shared mutex, replay order and compatibility. Outbox extraction must retain one mutex/worker and journal/receipt ownership. These are future slices, not a reason to modify their mechanisms during this implementation.

Follow up with bounded phone comparisons before broader lifecycle, event-surface or delivery-controller extraction. Preserve earlier archived constraints as historical references; they do not authorize a new slice.
