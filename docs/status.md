# Current status

Last reviewed: 2026-09-22
Baseline: `fix/mesh-reliability-and-latency`; the 2026-09-18 five-phone capture confirms block acknowledgement, direct teardown, and public/private relay. Direct-upgrade behavior awaits a fresh APK run.

## Current objective

Validate convergent mesh-hop topology and directed private delivery under relay loss, queue rejection, and recovery while preserving mutual-block behavior.

## Implemented in the working tree

- Context uses canonical docs.
- Per-attempt `BleLinkRegistry` lifecycle/readiness, generation checks, and focused tests.
- Payload-ready routing/UI selection and endpoint-owned cleanup for stale GATT/L2CAP state.
- Inbound-progress liveness, Radar online/checking/offline feedback, and heartbeat challenge ownership tests.
- Callback-driven GATT client writes and server notification completion for the heartbeat/fallback path.
- GATT uses acknowledged server indications and protects healthy L2CAP from stale callbacks; permission revocation fails safely.
- Three direct neighbors are allowed; queued identity admission retries after a five-second watchdog. Samsung/five-device evidence remains unrecorded.
- Private sends fail closed; link/frame/queue policy and focused unit tests cover lifecycle and heartbeat ownership.
- The terminal, UI shell, and lean CI are implemented; phone validation remains required.
- Tactical UI redesign and Cebu offline-map pilot are in the working tree; device validation remains required.
- Notifications use `MessagingStyle`, seven-message deduplication, branding, and cold-launch routing.
- Permanent node identity derives from the Keystore public key, excludes key/ID preferences from backup, and supports explicit dismissal of key-change alerts (D17).
- Auto-connect zero-peer deadlock recovery: isolated node election yield schedules fallback initiator watchdog (2.5s) if elected master fails to connect; stale zombie/ghost sockets evicted on rebooted advertisement (`directConnections == 0` with >8s silence/age check) and debounced advertising updates protect active links from false teardown.
- Stable topology snapshots: name/ID pairs stay associated, empty neighbor lists withdraw stale adjacency, per-origin sequence rejects delayed snapshots, full refresh is 30 seconds, and UI/stable routing share a 90-second lease.
- Directed private delivery: exact stable-ID next hops only; no private message/receipt broadcast fallback. Direct dispatch returns acceptance/rejection, persistence precedes dispatch, receipt timing starts only after acceptance, and pending rows remain retryable until a 24-hour explicit expiry.
- Stable private-conversation identity: Room history and recipient candidates collapse truncated advertisement labels and complete handshake labels by node ID, prefer the complete display label, and delete every stored alias together.
- Direct Message Latency & Churn Recovery: Relaxed zombie eviction silence/age threshold to 15s to tolerate Android BLE advertisement caching/jitter without false-positive teardowns. Fixed mesh router to use prefix-aware node ID matching, enabling instant direct-message dispatch and receipt termination instead of delayed outbox queuing and fallback flooding.
- Incidents: self-response rejected; optional GPS maps. TTL default 10; explicit Dense 4; no BLE/routing changes.

Local checks passed; physical BLE validation remains required.

## Open blockers

| ID | Priority | Current concern | Completion evidence |
| --- | --- | --- | --- |
| BLE-OWN | P0 | Shared server callback can still be ambiguous for late same-address events | Ownership rule plus focused test and same-process reconnect validation |
| BLE-QUEUE | P0 | Queue capacity and active-flight ownership are bounded, but overflow UX, complete retry rules, and starvation behavior still need device evidence | Sustained phone traffic without duplicate advancement, silent loss, or starvation |
| BLOCK-01 | P0 | The 2026-09-18 five-phone capture shows acknowledgement-driven direct teardown and public/private relay; one-sided-unblock state presentation, restart persistence, and a 70-second stable-route run remain unrecorded | Focused A-B-C run covering restart, unilateral/bilateral unblock, direct non-reconnect, and 70-second relay stability |
| ADMIT-01 | P1 | Automatic direct admission previously deferred every peer already reachable through a hop; queued startup recovery and the working-tree exception need device evidence without redundant-link churn | A routed peer bootstraps only after the last ready direct link disappears; busy startup candidates are retained; blocked/capacity-full peers remain denied |
| LIMIT-01 | P2 | User reports five-device availability, but direct-limit/admission and routed-capacity conditions lack a recorded matrix | Record devices/build/conditions; repeat controlled five-device admission and route tests |
| ROUTE-01 | P0 | FIXED LOCALLY: versioned empty-withdrawal topology and accepted-only directed dispatch remove stale-hop and false-send paths; private broadcast fallback is removed. Physical relay/reconnect validation remains open. | Stable A-B-C route/withdraw/recover run with receipts, queue rejection, no private broadcast, and no repeated callback retirement |
| SEC-01 | P1 | Deterministic node ID bound to Keystore public key, backup exclusions, and key rejection flow implemented (D17); fingerprint display and interactive trust verification UI remain | Defined threat model, approval flow, fail-closed tests, and documented claim boundary |
| SOS-01 | P1 | SOS cancellation/follow-up ownership needs sender/alert binding review | Concurrent-alert and cancel-before-location tests |
| INCIDENT-01 | P1 | Incident signatures/key binding and durable outbox remain open | Threat model, Room migration, and reconnect/new-join card |

## Next actions

1. Build and install the current working tree; run A-B-C for 90 seconds, send five private messages each way, remove/restore B, and verify explicit withdrawal plus directed retry without private broadcast or duplicate truncated-name conversations.
2. Repeat with queue pressure and one relay restart; record dispatch acceptance/rejection, topology sequence, outbox state, receipts, and GATT retirement markers.
3. Record APK/build identity, device matrix, and repetitions for the Samsung and five-device runs.
4. Run the stable-ID relay card after installing this working tree; capture selected next hop, private relay route-unavailable, key-change, and GATT-retirement markers.
5. Diagnose below-capacity auto-connect reports using [`ble-autoconnect-admission-diagnosis.md`](plans/ble-autoconnect-admission-diagnosis.md) before changing admission policy.

## Scope guard

Reliable text/SOS, honest status, recovery, and private fail-closed behavior remain ahead of speculative features.
