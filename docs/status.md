# Current status

Last reviewed: 2026-10-17. Baseline: `a9a27ed` on `main`. Build hashes and test counts live in [validation](validation.md).

## Current objective

Prepare for the capstone demo (within two weeks of 2026-10-16) and deployment readiness. The code/architecture review produced the [revision plan](plans/code-architecture-revision.md): behavior-neutral Phase 0 before the demo, behavior-touching phases after the freeze.

## Working by user report

The current candidate is the October 6 APK `1AE82B6C...`; the user reports F1–F14 PASS on it, including the incident UI repair cases. Earlier October 4 reports cover four-phone text/receipts/notes/SOS, recovery, background, blocking, incidents and offline maps. Models, counts, timings and transport coverage were not supplied.

## Implemented

Behavior and source ownership are in [architecture](architecture.md); accepted policies are in [decisions](decisions.md). The whole-app maintainability decomposition is merged (NativeBleManager keeps a reviewed 743-line facade exception); its evidence is archived in the [October 16 snapshot](../archive/README.md). Home offline grey awaits phone validation.

## Open issues

| ID | State | Outstanding work |
| --- | --- | --- |
| UX-01 | User-reported concern; details pending | UI/UX issues not yet described. Record screens, steps and screenshots when supplied. |
| PERF-01 | User-reported lag on app open; fixes local, device evidence pending | Source review: 3 s splash, keystore on Main, per-scan Home recomposition, logger list copies, BLE parsing on the main looper. First four fixed in `3b234bf`; BLE threading deferred (mesh risk). The user reported the `3b234bf` debug APK as less laggy on 2026-10-17. Adb on one phone shows 3.3 s debug against 0.8 s release cold start ([validation](validation.md)), so the remaining lag is mostly debug overhead. Demo and beta builds should use the release variant. Release builds keep app Logcat markers debug-only, so capture sessions still need debug builds. Two-phone [startup lag card](testing/startup-lag-card.md) steps pending. |
| MESH-02 | User-reported concern; details pending | Mesh issues beyond DELIVERY-01 not yet described. Record topology, APK, times and focused captures when supplied. |
| DELIVERY-01 | User-reported failure; relay fix local, device evidence pending | On A→B→C→D, public/private text or voice sometimes fails to reach D; public images sometimes miss across a hop. Cause unproven. |
| UI-02 | Local failures; acceptance blocked | Four emulator navigation assertions fail on both the pre-refactor baseline and the current candidate. |
| DEPLOY-01 | Evidence/work pending | Release signing, upgrade path, install guide, process-death/OEM/background battery matrix, accessibility and novice usability. |
| MEASURE-01 | Evidence pending | Repeated arrival/receipt counts, latency, sustained load/fallback and route-withdrawal coverage. |
| SCALE-01 | Evidence pending | Five/ten-phone matrices, range and capacity. |
| TRUST-01 | Work/evidence pending | First-contact verification and defined identity/security guarantees. |
| SEC-01 | Phase A user-reported PASS; Phase B on `feat/security-phase-b` (2026-10-18), gate passes, no device evidence; user-reported spam loss and SOS re-raise drop (2026-10-29) fixed by sender budgets | Run the [Phase B card](testing/security-phase-b-card.md) after the freeze, then merge. Phases C–E wait ([plan](plans/security-hardening.md)). SOS raises budgeted; sync-delivered active SOS alarm. |

Block, incident, SOS and recovery: basic user-reported successes; adversarial, load and repeated cases pending ([checklist](testing/physical-reliability-tests.md)).

**Layer boundaries / P2 subset** (`refactor/layer-boundaries`, [plan](plans/layer-boundaries.md)): The user reported the [smoke card](testing/layer-boundaries-smoke-card.md) passing on three phones (2026-10-17); merged.

**Batch C hygiene / Phase 0** (`refactor/batch-c-hygiene`): user-reported [smoke card](testing/batch-c-hygiene-smoke-card.md) pass, 2026-10-17.

**Dead screens**: merged; device pending.

**SOS message notifications** (`feat/sos-message-notifications`): [card](testing/sos-message-notifications-card.md) pending.

**SOS banner, developer PIN crash** (`fix/dev-pin-sos-banner`): [card](testing/sos-banner-dev-pin-card.md) pending.

**GATT write cap:** SM-A276B crash fix; user reported good.

**Location controls** (`feat/location-controls`): local checks pass; [card](testing/location-controls-card.md) pending. Incident alerts: [card](testing/incident-notifications-card.md).

**Reconnect sync fix** (`fix/incident-reconnect-sync`): bounded sync, paced creation (D29); [card](testing/incident-reconnect-sync-card.md) pending.

**Code smells refactor:** [card](testing/code-smells-refactor-card.md) pending.

**Developer console** (branch `feat/developer-console`, [plan](plans/developer-mode.md)): Stage 1 and live follow are pushed; Stage 2 (traces, timeline) has local checks only and awaits the [phone card](testing/developer-mode-card.md). Stage 3 (signal and queue stats) is pending.

**Unknown duplicate row** (`fix/unknown-duplicate-row`): the peer list keeps one row per peer and follows the surviving socket. JVM tests pass; [phone card](testing/unknown-duplicate-row-card.md) pending. Second-socket cause unmeasured.

**Fast device install**: [card](testing/fast-device-install-card.md) pending.

**Relay image delivery** (`feat/image-quality`): late public media forwarding and sender-free completion; [card](testing/relay-image-delivery-card.md) pending.

**Image quality** (`feat/image-quality`): user-rated quality good; [card](testing/image-quality-card.md) pending.

**Transfer-stall/progress** (`feat/transfer-progress`): [card](testing/transfer-stall-reprobe-card.md) pending.

**BLE fixes**: [header](testing/l2cap-single-write-header-card.md), [timeout](testing/adaptive-connect-timeout-card.md), [indication](testing/indication-tombstone-expiry-card.md) cards pending.

**BLE range** (`fix/ble-range-probe-scan`, D1): [card](testing/ble-range-probe-test-card.md) pending.

**Map download** (`fix/map-download-reliability`): user-reported slowness; speed unmeasured; [card](testing/map-download-reliability-card.md) pending.

**Map page UX** (`fix/map-page-ux`): [card](testing/map-page-ux-card.md) pending.

**Adaptive location** (`feat/adaptive-location`): [card](testing/adaptive-location-card.md) pending.

**Text input hardening** (`fix/text-input-hardening`): [card](testing/text-input-hardening-card.md) pending.

**Block hardening** (`fix/block-hardening`): [card](testing/block-hardening-card.md) pending.

**Late relay pacing** (`fix/late-relay-pacing`): [card](testing/late-relay-pacing-card.md) pending.

**Chat sync**: [plan](plans/chat-sync.md), [card](testing/chat-sync-card.md) pending.

## Next actions

Deployment target (user-confirmed 2026-10-17): capstone demo first, then a signed sideloaded pilot beta. Play Store is out of scope.

1. **Developer console:** run the Stage 1–2 phone card, then start Stage 3. Then run [PTT removal](testing/live-ptt-removal-card.md) and [voice limit](testing/voice-note-limit-card.md) cards.
2. **Demo freeze (P0, by about 2026-10-28):** parts 3–4 user-reported done (capture folder and miss times not supplied, so DELIVERY-01 stays unanalysed). The [limitations](demo/known-limitations.md) and [user guide](demo/user-guide.md) are drafted. After the freeze, only separately approved blocker fixes go in.
3. **Release engineering (P0 for beta, after the demo):** revision-plan P5 subset. R8 and backup-off are local (SEC-01); a minified smoke run and CI release build remain.
4. **Reliability and trust (P0/P1 before beta):** fix DELIVERY-01, triage UX-01/MESH-02, resolve UI-02, run SEC-01 phases B–E (these replace envelope v2 and TRUST-01 verification), then an audit.
5. **Beta gate (P1):** run the DEPLOY-01 matrix (upgrade path with migration tests, process death and OEM battery, device support, accessibility and a novice trial), then a small pilot that collects MEASURE-01 counts.
