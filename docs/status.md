# Current status

Last reviewed: 2026-10-16. Baseline: `a9a27ed` on `main` (also `origin/fix/incident-ui-ux`); docs work continues on `chore/context-routing`. This page owns the baseline, open issues and next actions. Build hashes and test counts live in [validation](validation.md).

## Current objective

Prepare for the capstone demo (within two weeks of 2026-10-16) and deployment readiness. Step one is documentation consistency; next is a code best-practice and file-architecture review without behavior changes until approved. UI/UX and mesh concerns are user-reported but not yet described.

## Working by user report

The current candidate is the October 6 APK `1AE82B6C...`; the user reports F1–F14 PASS on it, including the incident UI repair cases. Earlier October 4 reports cover four-phone text/receipts/notes/SOS, recovery, background, blocking, incidents and offline maps. Models, counts, timings and transport coverage were not supplied, so these are qualitative results, not measured rates. Details are in [validation](validation.md).

## Implemented

Behavior and source ownership are in [architecture](architecture.md); accepted policies are in [decisions](decisions.md). The whole-app maintainability decomposition is merged (NativeBleManager keeps a reviewed 743-line facade exception); its evidence is archived in the [October 16 snapshot](../archive/README.md).

## Open issues

| ID | State | Outstanding work |
| --- | --- | --- |
| UX-01 | User-reported concern; details pending | UI/UX issues not yet described. Record screens, steps and screenshots when supplied. |
| MESH-02 | User-reported concern; details pending | Mesh issues beyond DELIVERY-01 not yet described. Record topology, APK, times and focused captures when supplied. |
| DELIVERY-01 | User-reported failure; investigation deferred | On A→B→C→D, public/private text or voice sometimes fails to reach D. Cause unknown; keys, forwarding and blocking are hypotheses only. |
| UI-02 | Local failures; acceptance blocked | Four emulator navigation assertions fail on both the pre-refactor baseline and the current candidate. |
| DEPLOY-01 | Evidence/work pending (replaces RELEASE-01) | Release signing, upgrade path, install guide, process-death/OEM/background battery matrix, accessibility and novice usability. |
| MEASURE-01 | Evidence pending | Repeated arrival/receipt counts, latency, sustained load/fallback and route-withdrawal coverage. |
| SCALE-01 | Evidence pending | Five/ten-phone matrices, range and capacity. |
| TRUST-01 | Work/evidence pending | First-contact verification and defined identity/security guarantees. |

Basic block, incident, SOS and recovery checks have user-reported successes; their adversarial, load and repeated cases remain pending, not known defects. See the [physical checklist](testing/physical-reliability-tests.md).

**Repo hygiene (Phase 0):** the four disabled `*.kt.bak` tests were removed; `scripts/manage_devices.ps1` now lives in `scripts/device/`; refactor-era check scripts and the Figma preview plugin are archived (see the archive index).

**Batch A cleanup** (branch `refactor/batch-a-cleanup`): dead incident UI removed, two thread-safety fixes, swallowed-exception logging, debug-only Logcat and named timer constants. Local checks pass; the user reported all [smoke card](testing/batch-a-cleanup-smoke-card.md) steps working on 2026-10-16 (build hash, models and counts not supplied). Remaining review batches: B crypto/delivery (RSA PKCS#1 padding, unauthenticated private envelopes, decrypt-failure custody retry), C release/CI (R8, signing, androidTest compile in CI, catalog versions, release in-app terminal contents), D architecture (core→data imports, sos→comms coupling, audio in core/utils, 400–500-line screens).

## Next actions

1. Decide push/PR for `chore/context-routing` and `refactor/batch-a-cleanup`.
2. Choose the next review batch (B, C or D); plan before editing code.
3. Collect UX-01 and MESH-02 details, then prioritize them with UI-02 and DELIVERY-01.
4. Work the [capstone plan](plans/capstone-demo-readiness.md) toward the demo, including DEPLOY-01 items needed for installation.
