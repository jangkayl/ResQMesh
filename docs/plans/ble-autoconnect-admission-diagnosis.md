# BLE auto-connect admission diagnosis

## Purpose

Explain and validate reports that a nearby device does not auto-connect even though the UI appears below the three-direct-neighbor limit. This is a diagnostic plan, not approval to loosen admission or create redundant links.

## Source findings

`MAX_TOTAL_CONNECTIONS` is three, but it is not the only gate.

1. `BlePeerAdmissionController` deliberately defers a nearby peer when a route to it exists and this node already has any payload-ready direct link. This protects a healthy relay topology from redundant direct ACLs.
2. Even below three, admission stops when this node has two direct links and the scanned peer advertises one or more direct links: `directLinks >= 2 && advertisement.directConnections > 0`. The candidate is removed, rather than queued for capacity recovery.
3. Only the higher deterministic election score initiates. The lower-score node yields and relies on the other node to scan and dial.
4. `distinctLinkCount()` counts live client/server sockets before they are payload `READY`; the UI may therefore show fewer usable peers than admission considers occupied.
5. One GATT setup lane is intentional. A second outbound attempt returns `DEFERRED` while another handshake owns the radio, then should retry through the bootstrap queue.

These are plausible explanations, not proof of the reported device behavior. The advertisement's `directConnections` value and the actual link lifecycle must be captured on phones.

## Decision boundary

Keep the three-neighbor ceiling, one outbound setup lane, stable-ID duplicate protection, block enforcement, and healthy-route deferral unless evidence identifies a narrow false denial. Do not implement "always connect to every visible peer." It would consume ACL capacity and cause mesh churn.

## Phase 1: Instrument the decision, without changing policy

Add one structured, privacy-safe `BLE_ADMISSION` decision record for every scanned candidate and every queue drain. It must include:

- stable-ID hash or existing safe node label, endpoint suffix only, and decision reason;
- direct socket count, payload-ready count, configured max, and peer-advertised direct count;
- indirect-route boolean, local/peer election scores or winner, candidate age/attempt count;
- handshake owner/age, connect-lock owner/age, and terminal result (`STARTED`, `DEFERRED`, or `REJECTED`).

Do not log message payloads, keys, full MAC addresses, or location.

## Phase 2: Focused unit coverage

Extract or test the admission policy as deterministic cases:

| Case | Expected result |
| --- | --- |
| 0–1 direct links, elected initiator, no route | Candidate starts after jitter |
| 2 direct links, peer advertises 0 | Candidate may proceed |
| 2 direct links, peer advertises 1 | Explicit two-link policy denial is recorded |
| Indirect route plus ready direct neighbor | Explicit route-preservation deferral is recorded |
| Indirect route but no ready direct neighbor | Candidate may bootstrap through normal election/capacity gates |
| Busy handshake | Candidate remains queued and retries |
| Configuring/failed socket | Count and cleanup behavior are visible; no permanent false capacity |
| Lower local election score | Local yield is recorded and remote initiation is expected |

## Phase 3: Controlled phone capture

Build identity: record commit/branch and APK timestamp. Use two phones first, then a three-phone A-B-C topology. Start all phones from stopped app state; enable Bluetooth and required permissions; keep phones within one to two metres.

1. Record discovery-to-`READY` for a clean two-phone pair.
2. Form A-B, then present C. Repeat with C advertising zero and one direct neighbor where possible.
3. Form A-B-C with an indirect A-C route; remove B's ready link and verify whether A-C bootstrap begins.
4. Introduce one deliberately stalled setup and confirm its five-second watchdog releases the lane and the queued peer retries.

Capture only `BLE_MESH`, `BLE_ADMISSION`, `AndroidRuntime`, and relevant Bluetooth/GATT markers for the test window. Report decision reason, counts, election winner, `connectGatt` start, `CONNECTED`, discovery, CCCD, `READY`, timeout, and cleanup sequence.

## Phase 4: Select the smallest repair only after evidence

- If the report is an intentional healthy-route or two-link deferral, clarify the UI/status and retain policy.
- If the peer-advertised count is stale, refresh advertisement state on topology changes and add a regression test.
- If configuring/ghost links consume capacity after cleanup, repair ownership/retirement rather than raising the limit.
- If the elected initiator is not retrying after a yielded scan, repair only candidate retention/retry scheduling.

Any approved implementation must preserve the three-neighbor maximum, fail-closed private routing, block checks, serialized GATT setup, and existing relay behavior. Run focused unit tests, debug assembly, docs checks, diff hygiene, then repeat the exact physical card before declaring resolution.
