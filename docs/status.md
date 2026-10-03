# Current status

Last reviewed: 2026-10-03. Branch: `fix/bluetooth-recovery-cluster-bridging-and-sos-ux`.

## Current objective

Validate automatic Bluetooth recovery and cluster reconnection, alongside Community/Radio/SOS, incident convergence, and R7/R8. Physical reliability, security, and background evidence remain open.

[Phone validation pending](plans/capstone-demo-readiness.md): [incident UX redesign](plans/emergency-incidents-ux-redesign.md), withdrawal cleanup, reporter identity, and selected-offer editing guards.

## Implemented and locally checked

Identity build fixed; APK/19 focused tests pass. Full suite blocked by incomplete lifecycle/reconnect tests. Delivery/helper/title/incident checks passed; title fallback remains. Incident instrumentation/physical convergence remain open.

- Generation-owned BLE, acknowledged GATT, L2CAP promotion, directed READY-rooted topology, empty withdrawals, three-neighbor admission, and private fail-closed routing.
- Persistent private outbox with 24-hour expiry, accepted-only delivery timing, pending-key-change refusal, and conditional failure updates.
- Keystore-derived IDs, backup exclusions, opt-in background service, honest direct/relay/searching UI, restored homepage hierarchy, and incident triage/help UI.
- Emergency incidents UX: single-page detail, prominent title and 80-char support, Room 6→7, 7→8, 8→9 migrations, signed events, reporter selection, helper confirmation, and closure. Legacy records remain readable.
- Version 1.0.1/code 2 candidates build locally; signing, phone smoke, large-text/SOS-map review, and first-contact trust validation remain open.

## Reliability tracking checklist

Record APK/device matrix, timestamps, results, and capture before checking physical phases.

Use the [physical checklist](plans/physical-reliability-tests.md); record the installed APK.

- [x] R1: Reject unknown blocked endpoints by captured generation; release retired handshake owners; Refresh reconciles orphan owners.
- [x] R2: Only identified, unblocked READY neighbors suppress isolated recovery; unidentified endpoints occupy separate capacity slots.
- [x] R3: Acknowledged write progress protects busy links; stalled chunks and unanswered heartbeats retain deadlines.
- [x] R4: One bounded L2CAP writer per socket; FIFO within traffic classes; control reserve and byte/count bounds on both transports.
- [x] R5: Public acceptance results, visible pending/partial feedback, persistence before dispatch, serialized outbox retry; no whole-broadcast retry after any acceptance.
- [x] R6: 248 unit tests, debug APK, Android-test build, Lint, docs, and diff checks passed. Migration/SOS UI instrumentation is recorded below.
- [ ] R7: A–B–C mutual block, unilateral unblock, restart, relay removal/return, and Refresh phone card below.
- [ ] R8: Recorded-voice burst and mixed text/SOS tests on three phones, including GATT fallback and L2CAP.
- [ ] R9: Five-phone matrix: quiet links, one sender, simultaneous senders, relay loss/recovery.
- [ ] R10: Ten-phone matrix with the same workloads; measure delivery, p95 delay, churn, queue rejection, and recovery.
- [ ] R11: Based on measurements, select media chunk scheduling and recording limits; audit SOS relay priority and bounded control retries. Keep live-PTT removal separate.

Per-transport queue defaults: 128 retained transfers including active; ordinary admission leaves eight control slots. Ordinary bytes: 2 MiB, with 64 KiB control headroom. GATT frames remain non-interruptible; larger notes can delay urgent traffic. These are conservative bounds, not validated device capacity.

## Open blockers

Protocol-v2 incident sync adds history/state hashes, bounded repair/retries, READY snapshots, and a 30-second backstop. Room 9→10 separates validation/application. Migrations passed on the emulator; incident phone validation remains pending.

| ID | Priority | Remaining completion evidence |
| --- | --- | --- |
| BLE-OWN | P0 | Late same-address server callback ownership and replacement-link phone validation |
| BLE-QUEUE | P0 | R8: no false retirement, starvation, or silent rejection; fallback/retry behavior under pressure |
| BLOCK-01 | P0 | R7: B remains usable with A↔C blocked, restart persistence, honest unilateral-unblock state |
| ADMIT-01 | P1 | Recovery to B without unblocking C, retained startup candidates, no redundant-link churn |
| ROUTE-01 | P0 | Directed withdrawal/recovery and receipts; no false relay/private broadcast |
| LIMIT-01 | P2 | R9/R10 measured matrix; prior five-device report is not stable-capacity proof |
| SEC-01 | P1 | Fingerprint verification UI and first-contact trust boundary |
| SOS-01 | P1 | Concurrent alert/cancellation ownership and load-time priority |
| INCIDENT-01 | P1 | Incident UI runtime plus reconnect/connected-update and A–B–C convergence on phones |
| BG-01 | P1 | Android 12–14+ lock-screen, process-death, and battery measurements |

## Current SOS validation

Conversation metadata, Room 10→11, per-channel Radio history, per-alert SOS replies, signed lifecycle, terminal persistence, bounded sync, and local silence are implemented. Creation cancellation returns to the hub; header/location timestamps are guarded. Floating conversation headers and compact controls retain Antigravity's cards/icons. Debug build, 248 unit tests, Lint (zero errors; 147 warnings), docs, and diff checks passed. Eleven Medium_Phone/API 37 instrumentation tests passed: three migrations and eight SOS/Radio UX checks. Latest UI phone validation remains open.

Run the [SOS conversation phone card](testing/sos-conversations-test-card.md) using its final APK identity. Use the existing physical checklist for R7/R8; preserve unrelated block/incident evidence.

Older unscoped SOS/voice history entries are intentionally hidden. Current Radio channels and identified SOS threads retain history, drafts, and unread counts; migration classification remains intact.

Bluetooth recovery now separates session intent/radio lifetime, rebuilds after ON, fences old callbacks, retries isolated discovery, and permits safe cluster bridges. Debug build, 266 unit tests, Android-test compilation, and Lint passed. APK identity and physical steps are in the [recovery phone card](testing/bluetooth-recovery-test-card.md); phone results remain pending.

Antigravity: [repository handoff](plans/phase5-repository-antigravity-guide.md) and [transport refactor](plans/nativeblemanager-refactor.md) reflect current source; extraction remains deferred.

## Next actions

1. Run the [Bluetooth recovery card](testing/bluetooth-recovery-test-card.md) on two/three/five phones; capture toggle latency, cluster convergence, and churn.
2. Run the SOS phone card with its recorded APK hash: cross-channel A–B–C, simultaneous alerts, receiver silence, sender Back, partition/end/reconnect, and voice pressure.
3. Run incident convergence and R7/R8 cards; investigate focused failures.
4. Complete trust, UI/accessibility, and background/lock-screen validation.
5. Advance to ten phones only after smaller matrices pass.
