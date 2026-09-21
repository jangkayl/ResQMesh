# Current status

Last reviewed: 2026-09-20
Baseline: uncommitted `fix/startup-bootstrap-admission` working tree; the 2026-09-18 focused five-phone capture confirms block acknowledgement, direct teardown, and public/private relay, while direct-upgrade behavior is awaiting a fresh APK run.

## Current objective

Make blocking reliably mutual across relays: deny direct links after identity, show the state on both phones, and require both phones to unblock locally.

## Implemented in the working tree

- Context uses canonical docs.
- Per-attempt `BleLinkRegistry` lifecycle/readiness, generation checks, and focused tests.
- Payload-ready routing/UI selection and endpoint-owned cleanup for stale GATT/L2CAP state.
- Inbound-progress liveness, Radar online/checking/offline feedback, and heartbeat challenge ownership tests.
- Callback-driven GATT client writes and server notification completion for the heartbeat/fallback path.
- GATT uses acknowledged server indications and protects healthy L2CAP from stale callbacks; permission revocation fails safely.
- Three direct neighbors are allowed; queued stable-identity admission retries after a five-second setup watchdog. Samsung/five-device reports remain unrecorded device evidence.
- Private sends fail closed; link/frame/queue policy and focused unit tests cover lifecycle and heartbeat ownership.
- The terminal, UI shell, and lean CI are implemented; phone validation remains required.
- Modern Tactical & Utilitarian UI redesign (Phases 1-11): Safety Dark and Field Light themes, tactical dashboard, Walkie-Talkie radar PTT, SOS 2x2 grid with "Slide to Broadcast", context-aware chat composer, 2D mesh path visualizer, radar sweep splash, and GNSS telemetry.
- Cebu offline map pilot: MapPackageManifest model, dual ECDSA/Ed25519 ManifestVerifier, atomic MapStorageGuard, 302-redirect downloader with truncation checks, MapLibre SosMapScreen markers, 36dp compass, telemetry bottom sheet, mapless fallback, and settings UI.
- Android Notification & Deep Link System: NotificationCompat.MessagingStyle with 7-message deduplication, ResQMesh branding, and cold-launch deep-link routing.
- Permanent cryptographic node identity: deterministic Node ID from SHA-256 hash of Keystore public key, Google Cloud Backup exclusions for key/ID prefs, explicit dismissal of pending key change alerts, and People & Paths multi-hop messaging for blocked devices (D17).
- Auto-connect zero-peer deadlock recovery: isolated node election yield schedules fallback initiator watchdog (2.5s) if elected master fails to connect; stale zombie/ghost sockets evicted on rebooted advertisement (`directConnections == 0` with >8s silence/age check) and debounced advertising updates protect active links from false teardown.
- Developer Debugging Mode: PIN `0000` gated Settings toggle persisting in prefs; displays floating top-right HUD badge across main app shell to open live Diagnostic Terminal and legacy Radar overlay.
- Stable Core Topology & Partition Healing: Abolished connect-to-send and VIP bouncer churn during message transmission; private sends fallback to hybrid mesh broadcast if next hop is unrouted; partition bridging operates with randomized jitter and thundering herd abort.

Local checks passed; physical BLE validation remains required.

## Open blockers

| ID | Priority | Current concern | Completion evidence |
| --- | --- | --- | --- |
| BLE-OWN | P0 | Shared server callback can still be ambiguous for late same-address events | Ownership rule plus focused test and same-process reconnect validation |
| BLE-QUEUE | P0 | Queue capacity and active-flight ownership are bounded, but overflow UX, complete retry rules, and starvation behavior still need device evidence | Sustained phone traffic without duplicate advancement, silent loss, or starvation |
| BLOCK-01 | P0 | The 2026-09-18 five-phone capture shows acknowledgement-driven direct teardown and public/private relay; one-sided-unblock state presentation, restart persistence, and a 70-second stable-route run remain unrecorded | Focused A-B-C run covering restart, unilateral/bilateral unblock, direct non-reconnect, and 70-second relay stability |
| ADMIT-01 | P1 | Automatic direct admission previously deferred every peer already reachable through a hop; queued startup recovery and the working-tree exception need device evidence without redundant-link churn | A routed peer bootstraps only after the last ready direct link disappears; busy startup candidates are retained; blocked/capacity-full peers remain denied |
| LIMIT-01 | P2 | User reports five-device availability, but direct-limit/admission and routed-capacity conditions lack a recorded matrix | Record devices/build/conditions; repeat controlled five-device admission and route tests |
| ROUTE-01 | P0 | FIXED (Working Tree): Return receipts lost a directed hop, causing stuck messages. Addressed via hybrid controlled-broadcast fallback (max 3 hops) and 15-second sender timeout. Needs device validation for GATT callback retirement. | Stable route/reconnect run with delivery receipts and no repeated callback retirement |
| SEC-01 | P1 | Deterministic node ID bound to Keystore public key, backup exclusions, and key rejection flow implemented (D17); fingerprint display and interactive trust verification UI remain | Defined threat model, approval flow, fail-closed tests, and documented claim boundary |
| SOS-01 | P1 | SOS cancellation/follow-up ownership needs sender/alert binding review | Concurrent-alert and cancel-before-location tests |

## Next actions

1. Build and install the startup-admission working tree; measure two- and three-phone discovery-to-`READY` timing and the one-stalled-peer recovery path.
2. Record unilateral/bilateral unblock, restart persistence, and 70-second relay stability for `BLOCK-01`.
3. Record APK/build identity, device matrix, and repetitions for the Samsung and five-device runs.
4. Run the stable-ID relay card after installing this working tree; capture selected next hop, private relay route-unavailable, key-change, and GATT-retirement markers.
5. Diagnose below-capacity auto-connect reports using [`ble-autoconnect-admission-diagnosis.md`](plans/ble-autoconnect-admission-diagnosis.md) before changing admission policy.

## Scope guard

Reliable text/SOS, honest status, recovery, and private fail-closed behavior remain ahead of speculative features.

