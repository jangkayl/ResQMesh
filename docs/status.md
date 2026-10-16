# Current status

Last reviewed: 2026-10-06. Branch: `fix/incident-ui-ux`. Source HEAD: `a9a27ed` (whole-app decomposition committed after the last doc review; its effect on these docs is unverified). Docs reflect `db2260e` plus networking maintenance; earlier presentation baseline `2e27013`. This page owns the baseline and open issues.

## Current objective

Phases 0–7 source work is complete. The user reports F1–F14 passing on the final candidate; four baseline automated navigation checks still block local acceptance. [Phase plan](../archive/docs-superseded-2026-10-16/plans/network-maintainability-refactor.md), [size/ownership ledger](../archive/docs-superseded-2026-10-16/handoffs/maintainability-size-inventory.md) and [phone card](../archive/docs-superseded-2026-10-16/testing/network-maintainability-test-card.md) separate implementation, reports and unresolved evidence. Existing work is preserved; DELIVERY-01 remains deferred.

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

**Local checks:** the latest builds, unit/Lint counts, emulator results and APK hashes are owned by [validation](validation.md); status does not repeat them. Four emulator navigation assertions (UI-02) fail on both the saved baseline and the final candidate. TEST-01 (reconnect fixtures) is resolved locally.

**UI repairs (UI-01):** stable-ID helper Chat, accessible filters/search, clear guidance and wrapping controls are implemented; final phone checks are user-reported PASS.

**Local Git delivery:** progress commits live on `fix/incident-ui-ux`; backups, the device script and Figma tools remain untracked. Nothing is pushed.

**Maintainability:** whole-app decomposition is implemented (NativeBleManager keeps a reviewed 743-line facade exception). Phase plan, size ledger and evidence ledger are archived in the [October 16 snapshot](../archive/docs-superseded-2026-10-16/handoffs/maintainability-local-evidence.md); no device execution or DELIVERY-01 investigation occurred in that work.

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
