# Current status

Last reviewed: 2026-10-17. Baseline: `a9a27ed` on `main` (also `origin/fix/incident-ui-ux`); docs work continues on `chore/context-routing`. Build hashes and test counts live in [validation](validation.md).

## Current objective

Prepare for the capstone demo (within two weeks of 2026-10-16) and deployment readiness. The code/architecture review produced the [revision plan](plans/code-architecture-revision.md): behavior-neutral Phase 0 before the demo, behavior-touching phases after the freeze. UI/UX and mesh concerns are user-reported but not yet described.

## Working by user report

The current candidate is the October 6 APK `1AE82B6C...`; the user reports F1–F14 PASS on it, including the incident UI repair cases. Earlier October 4 reports cover four-phone text/receipts/notes/SOS, recovery, background, blocking, incidents and offline maps. Models, counts, timings and transport coverage were not supplied, so these are qualitative results, not measured rates. Details are in [validation](validation.md).

## Implemented

Behavior and source ownership are in [architecture](architecture.md); accepted policies are in [decisions](decisions.md). The whole-app maintainability decomposition is merged (NativeBleManager keeps a reviewed 743-line facade exception); its evidence is archived in the [October 16 snapshot](../archive/README.md). Home offline grey, including hopped peers, awaits phone validation.

## Open issues

| ID | State | Outstanding work |
| --- | --- | --- |
| UX-01 | User-reported concern; details pending | UI/UX issues not yet described. Record screens, steps and screenshots when supplied. |
| PERF-01 | User-reported lag on app open; fixes local, device evidence pending | Source review: 3 s splash, keystore on Main, per-scan Home recomposition, logger list copies, BLE parsing on the main looper. The first four are fixed in `3b234bf`; BLE threading is deferred because of mesh risk. The user reported the `3b234bf` debug APK as less laggy on 2026-10-17, with some lag remaining. On one phone, adb measurement shows a 3.3 s debug cold start against 0.8 s for a release build of the same source ([validation](validation.md)), so the remaining lag is mostly debug overhead. Demo and beta builds should use the release variant. Release builds keep app Logcat markers debug-only, so capture sessions still need debug builds. The two-phone mesh steps of the [startup lag card](testing/startup-lag-card.md) are pending. |
| MESH-02 | User-reported concern; details pending | Mesh issues beyond DELIVERY-01 not yet described. Record topology, APK, times and focused captures when supplied. |
| DELIVERY-01 | User-reported failure; investigation deferred | On A→B→C→D, public/private text or voice sometimes fails to reach D. Cause unknown; keys, forwarding and blocking are hypotheses only. |
| UI-02 | Local failures; acceptance blocked | Four emulator navigation assertions fail on both the pre-refactor baseline and the current candidate. |
| DEPLOY-01 | Evidence/work pending (replaces RELEASE-01) | Release signing, upgrade path, install guide, process-death/OEM/background battery matrix, accessibility and novice usability. |
| MEASURE-01 | Evidence pending | Repeated arrival/receipt counts, latency, sustained load/fallback and route-withdrawal coverage. |
| SCALE-01 | Evidence pending | Five/ten-phone matrices, range and capacity. |
| TRUST-01 | Work/evidence pending | First-contact verification and defined identity/security guarantees. |
| SEC-01 | Phase A user-reported PASS; Phase B on `feat/security-phase-b` (2026-10-18), gate passes, no device evidence; user-reported spam loss and SOS re-raise drop (2026-10-29) fixed by sender budgets | Run the [Phase B card](testing/security-phase-b-card.md) after the freeze, then merge. Phases C–E wait ([plan](plans/security-hardening.md)). SOS raises budgeted; sync-delivered active SOS alarm. |

Basic block, incident, SOS and recovery checks have user-reported successes; their adversarial, load and repeated cases remain pending, not known defects. See the [physical checklist](testing/physical-reliability-tests.md).

**Layer boundaries / P2 subset** (`refactor/layer-boundaries`, [plan](plans/layer-boundaries.md)): The user reported the [smoke card](testing/layer-boundaries-smoke-card.md) passing on three phones (2026-10-17); merged.

**Batch C hygiene / Phase 0** (`refactor/batch-c-hygiene`): the user reported all [smoke card](testing/batch-c-hygiene-smoke-card.md) steps working on 2026-10-17 (models and counts not supplied).

**Dead screens**: merged; device pending.

**Location controls** (`feat/location-controls`): local checks pass; [card](testing/location-controls-card.md) pending. Incident alerts: [card](testing/incident-notifications-card.md).

**Reconnect sync fix** (`fix/incident-reconnect-sync`): bounded sync, paced creation (D29); [card](testing/incident-reconnect-sync-card.md) pending.

**Code smells refactor:** [card](testing/code-smells-refactor-card.md) pending.

**Batch A cleanup** (`refactor/batch-a-cleanup`): the user reported all [smoke card](testing/batch-a-cleanup-smoke-card.md) steps working on 2026-10-16 (build, models, counts not supplied). Remaining review work: revision-plan phases P1–P6.

**Developer console** (branch `feat/developer-console`, [plan](plans/developer-mode.md)): Stage 1 and live follow are pushed; Stage 2 (traces, timeline) has local checks only and awaits the [phone card](testing/developer-mode-card.md). Stage 3 (signal and queue stats) is pending.

**Unknown duplicate row** (`fix/unknown-duplicate-row`): the peer list keeps one row per peer and follows the surviving socket. JVM tests pass; [phone card](testing/unknown-duplicate-row-card.md) pending. Second-socket cause unmeasured.

**Fast device install**: parallel, ABI-targeted installs; [card](testing/fast-device-install-card.md) pending.

## Next actions

Deployment target (user-confirmed 2026-10-17): capstone demo first, then a signed sideloaded pilot beta. Play Store is out of scope for now.

1. **Developer console:** run the Stage 1 and 2 phone card, then start Stage 3. Then run the [PTT removal card](testing/live-ptt-removal-card.md).
2. **Demo freeze (P0, by about 2026-10-28):** parts 3–4 user-reported done (capture folder and miss times not supplied, so DELIVERY-01 stays unanalysed). The [limitations](demo/known-limitations.md) and [user guide](demo/user-guide.md) are drafted. After the freeze, only separately approved blocker fixes go in.
3. **Release engineering (P0 for beta, after the demo):** revision-plan P5 subset. R8 and backup-off are local (SEC-01); a minified smoke run and CI release build remain.
4. **Reliability and trust (P0/P1 before beta):** fix DELIVERY-01, triage UX-01/MESH-02, resolve UI-02, run SEC-01 phases B–E (these replace envelope v2 and TRUST-01 verification), then an audit.
5. **Beta gate (P1):** run the DEPLOY-01 matrix (upgrade path with migration tests, process death and OEM battery, device support, accessibility and a novice trial), then a small pilot that collects MEASURE-01 counts.
