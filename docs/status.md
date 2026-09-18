# Current status

Last reviewed: 2026-09-18
Baseline: uncommitted `fix/startup-bootstrap-admission` working tree; the 2026-09-18 focused five-phone capture confirms block acknowledgement, direct teardown, and public/private relay, while direct-upgrade behavior is awaiting a fresh APK run.

## Current objective

Make blocking reliably mutual across relays: deny direct links after identity, show the state on both phones, and require both phones to unblock locally.

## Implemented in the working tree

- Context uses canonical docs.
- Per-attempt `BleLinkRegistry` lifecycle/readiness, generation checks, and focused tests.
- Payload-ready routing/UI selection and endpoint-owned cleanup for stale GATT/L2CAP state.
- Inbound-progress liveness, Radar online/checking/offline feedback, and heartbeat challenge ownership tests.
- Callback-driven GATT client writes and server notification completion for the heartbeat/fallback path.
- Server-to-client GATT fallback now uses acknowledged indications after the 2026-09-16 capture showed accepted notifications repeatedly missing completion callbacks after L2CAP loss.
- L2CAP promotion disarms overlapping GATT work, preventing late callbacks from tearing down a healthy link; connect-lock contention retains its cooldown.
- User reports the stabilized Samsung pairing now reaches `READY`, and a five-device mesh run shows all nodes available. Exact build, device/API matrix, repetitions, and capture are not yet recorded, so this is encouraging device evidence rather than a capacity or production-reliability claim.
- Direct-link admission now consistently uses three distinct neighbors; meshes larger than four devices depend on routed hops rather than a full direct-link graph.
- A nearby routed peer is normally kept on its existing mesh path, avoiding redundant direct ACL churn. If no payload-ready direct neighbor remains, admission may bootstrap one direct link; explicit Radar **Connect Directly** uses the same block, duplicate, and capacity guards.
- Startup admission now retains elected peers by stable identity when the radio gate or connect lock is busy. One no-progress client setup is bounded to five seconds, then the queued bootstrap can continue and the failed peer retries with bounded backoff. Device timing evidence is still required.
- L2CAP identity checks, retirement, and GATT retry/fallback.
- Runtime `BLUETOOTH_CONNECT` revocation now fails the affected operation safely instead of crashing.
- Persistent Android Keystore RSA identity, fail-closed private sending, endpoint-aware key cache rules, encrypted private locations, and removal of private plaintext logging in the reviewed handlers.
- Focused unit tests for link lifecycle, liveness, heartbeat ownership, and private-message policy.
- Coordinators own GATT-flight/heartbeat state; shared framing rejects malformed or oversized payloads; pending GATT work is capped at 128 transfers per endpoint. Repository gateway/store boundaries hide Bluetooth/Room, while Active Chat and Radar models are separated without public UI changes.
- The diagnostic terminal uses structured events, a live direct-link summary, paused-follow scrolling, and Latest/Last Sync controls. Device validation must confirm that its lifecycle matches the phones.
- Active public and private chats anchor the latest messages above the keyboard, automatically return to them, and show recorded community reader circles; phone interaction validation is pending.
- Lean pull-request CI for the debug build, unit tests, error-free Android Lint, canonical-document checks, and diff hygiene; PR creation and merging remain explicit user actions.

Local checks passed; physical BLE validation remains required.

## Open blockers

| ID | Priority | Current concern | Completion evidence |
| --- | --- | --- | --- |
| BLE-OWN | P0 | Shared server callback can still be ambiguous for late same-address events | Ownership rule plus focused test and same-process reconnect validation |
| BLE-QUEUE | P0 | Queue capacity and active-flight ownership are bounded, but overflow UX, complete retry rules, and starvation behavior still need device evidence | Sustained phone traffic without duplicate advancement, silent loss, or starvation |
| BLOCK-01 | P0 | The 2026-09-18 five-phone capture shows acknowledgement-driven direct teardown and public/private relay; one-sided-unblock state presentation, restart persistence, and a 70-second stable-route run remain unrecorded | Focused A-B-C run covering restart, unilateral/bilateral unblock, direct non-reconnect, and 70-second relay stability |
| ADMIT-01 | P1 | Automatic direct admission previously deferred every peer already reachable through a hop; queued startup recovery and the working-tree exception need device evidence without redundant-link churn | A routed peer bootstraps only after the last ready direct link disappears; busy startup candidates are retained; blocked/capacity-full peers remain denied |
| LIMIT-01 | P2 | User reports five-device availability, but direct-limit/admission and routed-capacity conditions lack a recorded matrix | Record devices/build/conditions; repeat controlled five-device admission and route tests |
| ROUTE-01 | P0 | Relay capture delivered private traffic both ways, but some return receipts lost a directed hop and one relay repeatedly retired a GATT callback link | Stable route/reconnect run with delivery receipts and no repeated callback retirement |
| SEC-01 | P1 | Public keys persist by stable ID with TOFU change detection, but first-contact authentication, fingerprint display, and backup policy remain unresolved | Defined threat model, approval flow, fail-closed tests, and documented claim boundary |
| SOS-01 | P1 | SOS cancellation/follow-up ownership needs sender/alert binding review | Concurrent-alert and cancel-before-location tests |

## Next actions

1. Build and install the startup-admission working tree; measure two- and three-phone discovery-to-`READY` timing and the one-stalled-peer recovery path.
2. Record unilateral/bilateral unblock, restart persistence, and 70-second relay stability for `BLOCK-01`.
3. Record APK/build identity, device matrix, and repetitions for the Samsung and five-device runs.
4. Run the stable-ID relay card after installing this working tree; capture selected next hop, private relay route-unavailable, key-change, and GATT-retirement markers.

## Scope guard

Reliable text/SOS, honest status, recovery, and private fail-closed behavior remain ahead of speculative features.
