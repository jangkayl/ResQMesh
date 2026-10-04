# Current status

Last reviewed: 2026-10-05. Branch: `fix/incident-ui-ux`; UI change set based on `2c2b9fc`. Earlier presentation source baseline: `2e27013`.

## Current objective

Deliver the reviewed incident UI/UX repairs on the user-requested local branch, including README, tests and affected docs. Validate the [handover](handoffs/antigravity-incident-ui-ux-repair.md) and [test card](testing/incident-ui-ux-repair-test-card.md). Preserve the visual design, BLE/mesh mechanism and unrelated work. Intermittent A-to-D delivery loss remains deferred; failed checks still block readiness.

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

**UI repairs:** Antigravity's six earlier changes removed readiness/hardware claims, restored search/advanced filters, corrected critical-only wording, normalized guidance, restored secondary actions and wrapped the footer. The October 5 follow-up qualifies helper Chat targets with stable offer node IDs and hides self/missing-ID/reporter-name shortcuts. Quick filters expose selected semantics with 48dp targets; search has an accessible label. Mesh status wraps and helper replacement controls grow from 48dp after screenshot review found large-text clipping. Instrumentation exercises current labels, real filtering/search/reset, stable-ID callbacks and layout bounds.

**Scope/checks:** production edits remain in UI/navigation; all 217 protected files and nine Activity/ViewModel bodies match the preserved baseline. The old structure guard still fails only on eight stale archived-document/tool entries. October 5 app/Android-test APK builds and Lint passed (zero errors, 148 warnings, three hints); all 18 incident UI tests plus four focused cases at 200% text passed on API 37. Physical validation remains pending. See [validation](validation.md) for bounded outcomes and APK identities.

**Local Git delivery:** the user subsequently requested a conventional commit on `fix/incident-ui-ux`. Scope is 17 UI/navigation, instrumentation, README and repair-documentation files. Unrelated network helpers/tests, backups, device scripts and Figma tools remain uncommitted. Nothing is pushed; this local checkpoint does not close the remaining validation gates.

**Reproduced local blocker:** the pre-existing untracked `PrivateReconnectDeliveryTest.kt` prevents standard mixed-workspace unit-test compilation with 18 diagnostics. The fixture was not repaired. Historical results and current audit identity/limitations are in [validation](validation.md).

## Open issue and remaining evidence

| ID | State | Outstanding work |
| --- | --- | --- |
| UI-01 | Follow-up implemented; phone validation pending | Execute the repaired-candidate phone card. Full validation/readiness remains blocked by TEST-01 and the stale structure inventory. |
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

1. Execute the [phone card](testing/incident-ui-ux-repair-test-card.md), including stable-ID chat, both appearances, Back/rotation and accessibility.
2. Resolve TEST-01 and the stale structure inventory separately; repeat appropriate gates. Failed checks remain blocking.
3. Review phone-card outcomes against the local UI commit; resolve remaining gates before any separately authorized PR, push or merge.
4. Keep DELIVERY-01 deferred until a focused investigation is separately authorized; add missing phone/transport/timing evidence when supplied.
5. Complete capstone, release/trust and process-death/battery evidence before broader claims.
