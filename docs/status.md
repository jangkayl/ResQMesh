# Current status

Last reviewed: 2026-09-19
Baseline: uncommitted `fix/startup-bootstrap-admission`; the 2026-09-18 five-phone capture confirms block acknowledgement, direct teardown, and public/private relay.

## Current objective

Make blocking reliably mutual across relays: deny direct links after identity, show the state on both phones, and require both phones to unblock locally.

## Implemented in the working tree

- Context uses canonical docs.
- Per-attempt `BleLinkRegistry` lifecycle/readiness, generation checks, and focused tests.
- Payload-ready routing/UI selection and endpoint-owned cleanup for stale GATT/L2CAP state.
- Inbound-progress liveness, Radar online/checking/offline feedback, and heartbeat challenge ownership tests.
- Callback-driven GATT client writes and server notification completion for the heartbeat/fallback path.
- GATT fallback uses acknowledged indications; L2CAP promotion disarms stale GATT work before it can retire a healthy link.
- Samsung `READY` and five-device availability are user-reported but lack a build/device/capture matrix; they are not capacity evidence.
- Three direct neighbors are allowed; routed peers stay indirect unless the last ready direct link disappears. Explicit direct connect keeps normal guards.
- Busy startup candidates retain stable identity and bounded retry after a five-second setup watchdog; device timing remains unrecorded.
- L2CAP identity checks, retirement, and GATT retry/fallback.
- Runtime `BLUETOOTH_CONNECT` revocation now fails the affected operation safely instead of crashing.
- Persistent Android Keystore RSA identity, fail-closed private sending, endpoint-aware key cache rules, encrypted private locations, and removal of private plaintext logging in the reviewed handlers.
- Focused unit tests for link lifecycle, liveness, heartbeat ownership, and private-message policy.
- Coordinators own GATT-flight/heartbeat state; framing and queues are bounded; repository gateway/store boundaries hide Bluetooth/Room.
- The diagnostic terminal uses structured events, a live direct-link summary, paused-follow scrolling, and Latest/Last Sync controls. Device validation must confirm that its lifecycle matches the phones.
- Active public and private chats anchor the latest messages above the keyboard, automatically return to them, and show recorded community reader circles; phone interaction validation is pending.
- Lean pull-request CI for the debug build, unit tests, error-free Android Lint, canonical-document checks, and diff hygiene; PR creation and merging remain explicit user actions.
- Private images are recipient-requested, directed JPEG attachments: 160 px/8 KiB preview, 1280 px/320 KiB original, 1 KiB encrypted chunks; never Community/SOS. Live voice now expires queued/relayed frames instead of replaying after recovery. Device validation is pending.

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
| MEDIA-01 | P3 | Private attachment protocol is implemented but has no focused phone evidence for queued control priority, relay resume, or source expiry | A-B-C-D request/download/reconnect card with a 320 KiB image and concurrent text/SOS |
| VOICE-01 | P2 | Live voice now has session, sequence, TTL, and bounded relay handling, but fresh-only reconnect behavior has no phone evidence | A-B-C voice disconnect/reconnect card with no outage-audio replay |

## Next actions

1. Build and install the startup-admission working tree; measure two- and three-phone discovery-to-`READY` timing and the one-stalled-peer recovery path.
2. Record unilateral/bilateral unblock, restart persistence, and 70-second relay stability for `BLOCK-01`.
3. Record APK/build identity, device matrix, and repetitions for the Samsung and five-device runs.
4. Run the stable-ID relay card after installing this working tree; capture selected next hop, private relay route-unavailable, key-change, and GATT-retirement markers.
5. Run the private-image card only after the text/SOS gates are stable; do not claim bandwidth, speed, or capacity from local checks.

## Scope guard

Reliable text/SOS, honest status, recovery, and private fail-closed behavior remain ahead of speculative features.
