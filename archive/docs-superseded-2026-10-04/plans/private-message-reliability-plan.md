> Historical/inactive reference archived 2026-10-04; see [disposition index](../README.md). Original claims, hashes and instructions are not current authorization.

# Private-message reliability plan

## Purpose

Make private text delivery resilient to ordinary BLE disruption without weakening confidentiality or overloading a dense room. This is a staged plan, not evidence that any target delivery rate, latency, or 5-10 phone capacity is achieved.

## Outcome and boundaries

The target is durable encrypted store-and-forward: a private message is persisted before dispatch, uses stable-ID directed routing, reports truthful progress, and can select one alternate route after a next-hop loss. `DELIVERED` remains reserved for a recipient receipt.

Private ciphertext must never fall back to plaintext or uncontrolled broadcast. Public text and SOS retain their separately bounded forwarding policy. Do not add Wi-Fi Direct, Nearby, RSSI-distance routing, a new BLE connection policy, or a broad Phase 5 refactor here. Preserve the three-direct-neighbor cap, payload-`READY` admission rule, key-change fail-closed behavior, and existing GATT/L2CAP ownership.

## Delivery state contract

| State | Meaning | Automatic action |
| --- | --- | --- |
| `WAITING_FOR_KEY` | No trusted usable recipient key or a key change is pending | Reconsider only after a trusted key event |
| `WAITING_FOR_ROUTE` | No usable payload-ready directed route | Reconsider after topology/readiness change and `nextAttemptAt` |
| `QUEUED_TO_NEXT_HOP` | Durable locally; eligible to hand to a selected transport | Attempt once when due |
| `SENDING` | Accepted by the selected neighbor transport; awaiting recipient receipt | Do not flush or duplicate; timeout/recover only |
| `DELIVERED` | Recipient delivery receipt returned | Stop attempts |
| `FAILED` | Expired, explicit rejection, or manual-terminal failure | Never auto-retry |

A GATT/L2CAP queue acceptance is not recipient delivery. An L2CAP asynchronous write must report a failure back to repository state; neither a scheduled write nor a local flush can mark `DELIVERED`.

## Phase R0 — establish the baseline

1. Record build SHA/APK identity and phone models/Android versions.
2. Run A-B-C with no A-C link in both directions; capture only relevant `BLE_MESH`, `MeshNetwork_E2EE`, `PayloadDispatcher`, route, and receipt markers.
3. Measure payload-ready establishment, recipient receipt latency, duplicate count, queue depth, and recovery after B is disabled/re-enabled.
4. Do not change routing from these results until the causal timeline identifies whether loss is readiness, queue ownership, route staleness, or receipt return failure.

Completion: a reproducible baseline with stated topology and repetitions, not one successful demonstration.

## Phase R1 — correct the source outbox

Implement this as one focused change before multipath work.

1. Replace separate capacity enforcement plus message insertion with one Room `@Transaction`. Mark the minimum number of oldest active entries `FAILED` with `OUTBOX_CAPACITY_EXCEEDED`; never silently delete an outgoing message.
2. Remove `runBlocking` from private send/retry paths. Preserve caller-visible UX only after durable persistence; use structured repository/application coroutines and a minimal explicit result/state if a synchronous Boolean cannot remain truthful.
3. Flush only retry-eligible entries whose `nextAttemptAt` is due. Exclude `SENDING`, `DELIVERED`, expired, and terminal `FAILED` rows.
4. Add per-message single-flight ownership so concurrent readiness, topology, key, manual-retry, and timeout events cannot dispatch the same ID twice.
5. Preserve the same logical message ID across route repairs for deduplication and receipts; use a per-attempt transmission ID only where current payload behavior already supports it.

Required automated checks: transaction/capacity behavior, no concurrent duplicate flush, `nextAttemptAt`, key fail-closed behavior, expiry, L2CAP write failure, and receipt-only `DELIVERED`. Run unit tests, debug assembly, docs check, and `git diff --check`.

## Phase R2 — directed alternate-route repair

When the selected first hop becomes unavailable before receipt:

1. Recompute from current payload-ready links and canonical stable node IDs.
2. Prefer a direct recipient, then shortest valid route, then a route whose first hop differs from the failed endpoint/identity.
3. Persist the new attempt metadata before dispatch.
4. Send one alternate attempt only. If no route exists, retain `WAITING_FOR_ROUTE` with bounded retry time; never broadcast private ciphertext.

This phase does not add parallel copies, relay custody, or new admission behavior. Test A-B-C-D with A-E-F-D as a physically separated alternate branch. Remove B before it forwards and remove C after B has accepted the message; verify one repair attempt, no flood, no duplicate UI row, and a truthful final status.

## Phase R3 — scale gate: 5 then 10 phones

Do not enable private replication before R1/R2 pass repeatedly. Test a sparse line, a branching topology, and a dense same-room topology. In a dense room, more copies can increase BLE queue pressure and delay; disable replication while queues are busy or readiness is unstable.

For each topology, record at least 20 private-message attempts per direction across repeat runs: delivery success, p50/p95 recipient-receipt latency, duplicate rate, route-repair time, max queue depth, expiry/failure reason, and link churn. Test 3 phones first, then 5, then 10. A result applies only to the named build, devices, layout, and traffic load.

## Phase R4 — optional bounded encrypted replication

Only after R3 shows that one-route repair is stable, evaluate a controlled experiment: urgent private messages may use at most two encrypted copies when two payload-ready routes have different first hops and both queues are below a measured threshold. Use hop limit, TTL, copy budget, stable-ID duplicate cache, recipient receipt, and cancellation of remaining attempts. Normal private text remains single-route. Never use a network-wide private flood.

Compare one-copy versus two-copy results on the same phone matrix. Accept replication only if it materially improves delivery/recovery without unacceptable p95 delay, duplicates, or queue growth; otherwise retain directed single-copy repair.

## Completion rules

Source checks are necessary but not BLE proof. Update `docs/validation.md` only with user-run physical results; update status/architecture when behavior changes. Do not claim guaranteed delivery, low latency, 10-phone reliability, authenticated E2EE, or production readiness. The user installs and operates phone builds; implementation must finish with a focused test card and wait for approval before the next phase or any commit.
