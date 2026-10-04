# Current status

Last reviewed: 2026-10-04. Branch: `refactor/mvvm-presentation-organization`; source baseline: `2e27013`.

## Current objective

Documentation is synchronized with implemented behavior and user-reported achievements. The public README explains features, architecture, project structure and setup for repository readers. Preserve the working BLE/mesh mechanism; intermittent A-to-D delivery loss remains recorded for later investigation.

## Working by user report

The user confirmed the October 4 APK identified in [validation](validation.md), including:

- Successful A–B–C–D public/private text arrival, returning DELIVERED/READ confirmation, complete recorded notes, and SOS delivery.
- App restart, relay removal/return, Bluetooth OFF/ON, and locked/background messaging.
- Mutual direct-link blocking, relay use while blocked, restart persistence, and independent local unblock.
- Incident offers, selection, confirmation, withdrawal/replacement, closure and reconnect synchronization; independent simultaneous SOS cancellation.
- Offline map opening and SOS map display without internet.

These are qualitative reports, not measured rates or proof that every card step passed. Four-phone delivery remains intermittent. Models/API levels, counts, durations, actual GATT/L2CAP use and captures were not supplied for this report.

## Implemented and locally checked

Current source includes generation-owned GATT/L2CAP, three-neighbor admission, directed leased topology, persistent private outbox, recipient receipts, resumable journaled transfers, permanent Keystore identity, opt-in background service, Room 11, signed incident/SOS synchronization, scoped conversations and MapLibre/PMTiles.

Presentation organization preserves Activity/ViewModel bodies and 217 protected files. Navigation and shared/feature presentation now have explicit owners. Its recorded checks include APK builds, Android-test compilation, Lint and 24 focused presentation tests. Earlier Samsung/voice clean snapshots passed 280/348 unit tests respectively; these counts belong to different source snapshots.

**Recorded local blocker:** the pre-existing untracked `PrivateReconnectDeliveryTest.kt` prevents standard mixed-workspace unit-test compilation. This audit did not rerun that suite or repair the fixture. Historical results and current limitations are in [validation](validation.md).

## Open issue and remaining evidence

| ID | State | Outstanding work |
| --- | --- | --- |
| DELIVERY-01 | User-reported failure; investigation deferred | On A→B→C→D, public/private text or voice sometimes fails to reach D. Cause unknown. Keys, forwarding and blocking are hypotheses only. |
| TEST-01 | Recorded local blocker | Reconcile the existing reconnect fixture separately before claiming a full mixed-workspace suite pass. |
| MEASURE-01 | Evidence pending | Repeated arrival/receipt counts, latency, sustained-load/fallback, late-callback and directed route-withdrawal coverage. |
| SCALE-01 | Evidence pending | Five/ten-phone controlled matrices, range and capacity measurements. |
| TRUST-01 | Work/evidence pending | First-contact verification and defined identity/security guarantees. |
| RELEASE-01 | Evidence pending | Process-death/OEM/background battery matrix, upgrade/release signing, accessibility and novice usability. |

Previously listed basic block, incident, SOS and recovery checks now have user-reported successes; their adversarial, load and repeated-measurement cases remain pending rather than known defects. Use the [physical checklist](testing/physical-reliability-tests.md) to distinguish them.

## Deferred work and context

Transport/repository extraction, stronger route-repair/replication proposals, resource requests, safety check-ins and unfinished location-confidence policies remain inactive. The [dated archive index](../archive/docs-superseded-2026-10-04/README.md) preserves their disposition and unique historical checks. Archived prompts do not authorize implementation.

[Capstone preparation](plans/capstone-demo-readiness.md) remains active; no demo, pilot or deadline completion was confirmed.

## Next actions

1. Keep DELIVERY-01 for a separately authorized, focused investigation using the installed build and exact failure window.
2. Add missing phone/build conditions and measured outcomes when supplied; use [voice/relay](testing/ble-voice-transfer-test-card.md) and physical procedures without blanket PASS labels.
3. Resolve TEST-01 separately and repeat appropriate local gates.
4. Complete capstone evidence, release/trust, accessibility and process-death/battery checks before broader claims.
