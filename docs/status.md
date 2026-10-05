# Current status

Last reviewed: 2026-10-06. Branch: `fix/incident-ui-ux`; networking baseline `db2260e`. Earlier presentation baseline: `2e27013`.

## Current objective

Phases 0–7 source work is complete. The user reports F1–F14 passing on the final candidate; four baseline automated navigation checks still block local acceptance. [Phase plan](plans/network-maintainability-refactor.md), [size/ownership ledger](handoffs/maintainability-size-inventory.md) and [phone card](testing/network-maintainability-test-card.md) separate implementation, reports and unresolved evidence. Existing work is preserved; DELIVERY-01 remains deferred.

## Working by user report

On October 6, the user confirmed F1–F14 as PASS on final APK `1AE82B6C...`. Models, counts, timings and transport coverage remain unspecified. [Validation](validation.md) records this separately from the earlier October 4 APK confirmations:

- Successful A–B–C–D public/private text arrival, returning DELIVERED/READ confirmation, complete recorded notes, and SOS delivery.

- App restart, relay removal/return, Bluetooth OFF/ON, and locked/background messaging.

- Mutual direct-link blocking, relay use while blocked, restart persistence, and independent local unblock.

- Incident offers, selection, confirmation, withdrawal/replacement, closure and reconnect synchronization; independent simultaneous SOS cancellation.

- Offline map opening and SOS map display without internet.

These are qualitative reports, not measured rates or proof that every card step passed. Four-phone delivery remains intermittent. Models/API levels, counts, durations, actual GATT/L2CAP use and captures were not supplied for this report.

## Implemented and locally checked

Current source includes generation-owned GATT/L2CAP, three-neighbor admission, directed leased topology, persistent private outbox, recipient receipts, resumable journaled transfers, permanent Keystore identity, opt-in background service, Room 11, signed incident/SOS synchronization, scoped conversations and MapLibre/PMTiles.

Presentation organization preserves Activity/ViewModel bodies and 217 protected files. Navigation and shared/feature presentation now have explicit owners. Its recorded checks include APK builds, Android-test compilation, Lint and 24 focused presentation tests. Earlier Samsung/voice clean snapshots passed 280/348 unit tests respectively; these counts belong to different source snapshots.

**UI repairs:** stable-ID helper Chat, accessible selected filters/search, clear guidance, secondary actions and wrapping/growing controls are implemented. Recorded October 5 builds and Lint passed; eighteen incident UI tests and four focused 200%-text cases passed on API 37. Final phone checks are user-reported PASS. Historical inventory differences are accounted for in the ledger; original evidence stays in [validation](validation.md) and its snapshot.

**Local Git delivery:** `db2260e` retains the earlier UI checkpoint. The user authorized an October 6 progress commit on `fix/incident-ui-ux`, saving 182 source/test, documentation, archive and preservation-check files. Backups, the device script and Figma tools remain untracked. Nothing is pushed; UI-02 remains blocking.

**Reconnect fixture repair:** separately authorized TEST-01 repair updated obsolete hooks, callbacks, recipient keys and timing/wake expectations. JVM-only Base64 support retains all 29 reconnect cases. Its build and 403 tests passed without exclusions; production/configuration was unchanged. The historical guard's stale entries and fixture edit are now reconciled without replacing its baseline.

**Initial networking maintenance:** extracted handler/gateway ownership, presence, live callbacks, repository bindings and GATT host contracts. That stage passed 411 tests, APK compilation and Lint; its guard preserved 447 unrelated files. No device/emulator execution or DELIVERY-01 investigation occurred.

**Maintainability:** 426 JVM tests, builds, Lint (zero errors) and preservation checks pass. NativeBleManager is 743 lines; GATT managers 133/44, admission 158 and MeshRepository 368. 36/40 selected emulator cases pass; four navigation failures reproduce on the saved baseline. Final phone F1–F14 is user-reported PASS. Baselines, failed attempts and exact hashes belong to the [local evidence ledger](handoffs/maintainability-local-evidence.md).

## Open issue and remaining evidence

| ID | State | Outstanding work |
| --- | --- | --- |
| UI-01 | Follow-up implemented; functional phone pass reported | Final F12–F14 reported PASS. Broader usability/measurement evidence remains unspecified; original baselines are preserved. |
| UI-02 | Existing local failures; acceptance blocked | Four navigation assertions fail on both saved baseline and final candidate; unchanged sources/tests. See the local evidence ledger. |
| DELIVERY-01 | User-reported failure; investigation deferred | On A→B→C→D, public/private text or voice sometimes fails to reach D. Cause unknown. Keys, forwarding and blocking are hypotheses only. |
| TEST-01 | Resolved locally | Unit compilation and 403/403 tests pass, including 29 reconnect cases and three Base64 contract checks. |
| MEASURE-01 | Evidence pending | Repeated arrival/receipt counts, latency, sustained-load/fallback, late-callback and directed route-withdrawal coverage. |
| SCALE-01 | Evidence pending | Five/ten-phone controlled matrices, range and capacity measurements. |
| TRUST-01 | Work/evidence pending | First-contact verification and defined identity/security guarantees. |
| RELEASE-01 | Evidence pending | Process-death/OEM/background battery matrix, upgrade/release signing, accessibility and novice usability. |

Previously listed basic block, incident, SOS and recovery checks now have user-reported successes; their adversarial, load and repeated-measurement cases remain pending rather than known defects. Use the [physical checklist](testing/physical-reliability-tests.md) to distinguish them.

## Deferred work and context

Initial extraction results remain historical evidence. Lifecycle, GATT/admission, repository/incident and presentation decomposition is implemented with preserved phase evidence; final acceptance is pending. Stronger repair/replication, resource requests, safety check-ins and location-confidence policies remain proposals. The [dated archive index](../archive/docs-superseded-2026-10-04/README.md) retains their constraints; archived prompts do not authorize implementation.

[Capstone preparation](plans/capstone-demo-readiness.md) remains active; no demo, pilot or deadline completion was confirmed.

## Next actions

1. Review the size/ownership exception and UI-02 evidence; preserve phase artifacts. Investigate existing navigation failures separately before final local acceptance.

2. Preserve the F1–F14 report; add device/setup/count/timing/transport details when available.

3. Keep DELIVERY-01 deferred until focused investigation is separately authorized; record phone/transport/count/timing evidence when supplied.

4. Complete capstone, release/trust, accessibility and process-death/battery evidence before broader claims.
