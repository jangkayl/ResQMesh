# Current status

Last reviewed: 2026-10-17. Baseline: `a9a27ed` on `main` (also `origin/fix/incident-ui-ux`); docs work continues on `chore/context-routing`. This page owns the baseline, open issues and next actions. Build hashes and test counts live in [validation](validation.md).

## Current objective

Prepare for the capstone demo (within two weeks of 2026-10-16) and deployment readiness. Documentation consistency is done; the code/architecture review produced the [revision plan](plans/code-architecture-revision.md): behavior-neutral Phase 0 before the demo, behavior-touching phases after the freeze. UI/UX and mesh concerns are user-reported but not yet described.

## Working by user report

The current candidate is the October 6 APK `1AE82B6C...`; the user reports F1–F14 PASS on it, including the incident UI repair cases. Earlier October 4 reports cover four-phone text/receipts/notes/SOS, recovery, background, blocking, incidents and offline maps. Models, counts, timings and transport coverage were not supplied, so these are qualitative results, not measured rates. Details are in [validation](validation.md).

## Implemented

Behavior and source ownership are in [architecture](architecture.md); accepted policies are in [decisions](decisions.md). The whole-app maintainability decomposition is merged (NativeBleManager keeps a reviewed 743-line facade exception); its evidence is archived in the [October 16 snapshot](../archive/README.md).

## Open issues

| ID | State | Outstanding work |
| --- | --- | --- |
| UX-01 | User-reported concern; details pending | UI/UX issues not yet described. Record screens, steps and screenshots when supplied. |
| PERF-01 | User-reported lag on app open; fixes local, device evidence pending | Source review: 3 s splash, keystore on Main, per-scan Home recomposition, logger list copies, BLE parsing on the main looper. The first four are fixed in `3b234bf`; BLE threading is deferred because of mesh risk. On 2026-10-17 the user reported the `3b234bf` debug APK as noticeably less laggy than a 2026-10-16 debug build (hash not supplied), with some lag remaining. On one phone, adb measurement shows a 3.3 s debug cold start against 0.8 s for a release build of the same source ([validation](validation.md)), so the remaining lag is mostly debug overhead. Demo and beta builds should use the release variant. Release builds keep app Logcat markers debug-only, so capture sessions still need debug builds. The two-phone mesh steps of the [startup lag card](testing/startup-lag-card.md) are pending. |
| MESH-02 | User-reported concern; details pending | Mesh issues beyond DELIVERY-01 not yet described. Record topology, APK, times and focused captures when supplied. |
| DELIVERY-01 | User-reported failure; investigation deferred | On A→B→C→D, public/private text or voice sometimes fails to reach D. Cause unknown; keys, forwarding and blocking are hypotheses only. |
| UI-02 | Local failures; acceptance blocked | Four emulator navigation assertions fail on both the pre-refactor baseline and the current candidate. |
| DEPLOY-01 | Evidence/work pending (replaces RELEASE-01) | Release signing, upgrade path, install guide, process-death/OEM/background battery matrix, accessibility and novice usability. |
| MEASURE-01 | Evidence pending | Repeated arrival/receipt counts, latency, sustained load/fallback and route-withdrawal coverage. |
| SCALE-01 | Evidence pending | Five/ten-phone matrices, range and capacity. |
| TRUST-01 | Work/evidence pending | First-contact verification and defined identity/security guarantees. |

Basic block, incident, SOS and recovery checks have user-reported successes; their adversarial, load and repeated cases remain pending, not known defects. See the [physical checklist](testing/physical-reliability-tests.md).

**Layer boundaries / P2 subset** (branch `refactor/layer-boundaries`, user-approved 2026-10-17): behavior-neutral mappers, interfaces, use cases and a zero allowlist ([plan](plans/layer-boundaries.md)). Local checks and review pass; the user reported the [smoke card](testing/layer-boundaries-smoke-card.md) passing on three phones on 2026-10-17. Merge waits for explicit instruction.

**Batch C hygiene / Phase 0** (branch `refactor/batch-c-hygiene`, stacked on Batch A): golden wire/table tests, leftover cleanup and archiving, catalog-only versions, Koin root in `app/di` split by layer, `core/utils` regrouped, and CI gates (androidTest compile, detekt baseline, boundary check). Local checks pass; the user reported all [smoke card](testing/batch-c-hygiene-smoke-card.md) steps working on 2026-10-17 (models and counts not supplied).

**Batch A cleanup** (branch `refactor/batch-a-cleanup`): dead incident UI removed, two thread-safety fixes, swallowed-exception logging, debug-only Logcat and named timer constants. Local checks pass; the user reported all [smoke card](testing/batch-a-cleanup-smoke-card.md) steps working on 2026-10-16 (build hash, models and counts not supplied). Remaining review work is phases P1–P6 of the revision plan (crypto/delivery, data boundary, presentation, BLE internals, release/deps, modules).

## Next actions

Deployment target (user-confirmed 2026-10-17): capstone demo first, then a signed sideloaded pilot beta. Play Store is out of scope for now.

1. **Integrate (P0, now):** run the [startup lag card](testing/startup-lag-card.md). Then, on explicit instruction, merge the stack (`chore/context-routing` → `refactor/batch-a-cleanup` → `refactor/batch-c-hygiene` → `perf/startup-lag`) into `main` and build candidate #2 to replace the October 6 APK. That replacement needs a user-reported regression pass.
2. **Demo freeze (P0, by about 2026-10-28):** repeat the [capstone plan](plans/capstone-demo-readiness.md) critical scenarios five times with counts, and capture A→B→C→D runs for DELIVERY-01 without fixing yet. Prepare the limitations, user guide and backup recording. After the freeze, only separately approved blocker fixes go in.
3. **Release engineering (P0 for beta, after the demo):** revision-plan P5 subset. That means release signing, the applicationId rename before any beta install, R8 with keep rules and a minified-APK smoke run, a backup policy (`allowBackup` is currently true), and a release build in CI.
4. **Reliability and trust (P0/P1 before beta):** fix DELIVERY-01, triage UX-01/MESH-02, resolve UI-02, run revision phase P1 crypto (envelope v2) and TRUST-01 first-contact verification, then an audit.
5. **Beta gate (P1):** run the DEPLOY-01 matrix (upgrade path with migration tests, process death and OEM battery, device support, accessibility and a novice trial), then a small pilot that collects MEASURE-01 counts.
