# Current status

Last reviewed: 2026-09-30. Baseline: `6dac8fa`, `feat/peer-comms-onboarding-ux`.

## Current objective

Validate block recovery and recorded public voice under load, then measure five- and ten-phone operation. Local fixes do not close runtime issues. Existing incident, UI, security, and background work remains in the working tree.

## Implemented and locally checked

Earlier delivery checks passed (debug/release, 146 unit tests, Android-test compilation, Lint). Helper-workflow checks also passed; Room instrumentation and physical convergence remain open.

- Generation-owned BLE, acknowledged GATT, L2CAP promotion, directed READY-rooted topology, empty withdrawals, three-neighbor admission, and private fail-closed routing.
- Persistent private outbox with 24-hour expiry, accepted-only delivery timing, pending-key-change refusal, and conditional failure updates.
- Keystore-derived IDs, backup exclusions, opt-in background service, honest direct/relay/searching UI, restored homepage hierarchy, and incident triage/help UI.
- Terminal incidents in reconnect summaries; Room 6→7 and 7→8 migrations, signed key-continuity events, reporter selection, helper confirmation, and reporter closure. Legacy records remain readable.
- Version 1.0.1/code 2 candidates build locally; signing, phone smoke, large-text/SOS-map review, and first-contact trust validation remain open.

## Reliability tracking checklist

Check off physical phases only after recording APK identity, device matrix, timestamps, results, and capture.

Use the [physical test checklist](plans/physical-reliability-tests.md) for ordered steps and reporting. Record the installed APK identity; the checked build is below.

- [x] R1: Reject unknown blocked endpoints by captured generation; release retired handshake owners; Refresh reconciles orphan owners.
- [x] R2: Only identified, unblocked READY neighbors suppress isolated recovery; unidentified endpoints occupy separate capacity slots.
- [x] R3: Acknowledged write progress protects busy links; stalled chunks and unanswered heartbeats retain deadlines.
- [x] R4: One bounded L2CAP writer per socket; FIFO within traffic classes; control reserve and byte/count bounds on both transports.
- [x] R5: Public acceptance results, visible pending/partial feedback, persistence before dispatch, serialized outbox retry; no whole-broadcast retry after any acceptance.
- [x] R6: 171 unit tests, debug APK, Android-test compilation, Lint, docs, and diff checks passed. Instrumentation execution remains open.
- [ ] R7: A–B–C mutual block, unilateral unblock, restart, relay removal/return, and Refresh phone card below.
- [ ] R8: Recorded-voice burst and mixed text/SOS tests on three phones, including GATT fallback and L2CAP.
- [ ] R9: Five-phone matrix: quiet links, one sender, simultaneous senders, relay loss/recovery.
- [ ] R10: Ten-phone matrix with the same workloads; measure delivery, p95 delay, churn, queue rejection, and recovery.
- [ ] R11: Based on measurements, select media chunk scheduling and recording limits; audit SOS relay priority and bounded control retries. Keep live-PTT removal separate.

Per-transport queue defaults: 128 retained transfers including active; ordinary admission leaves eight control slots. Ordinary bytes: 2 MiB, with 64 KiB control headroom. GATT frames remain non-interruptible; larger notes can delay urgent traffic. These are conservative bounds, not validated device capacity.

## Open blockers

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
| INCIDENT-01 | P1 | Room migration instrumentation, durable event reconciliation, A–B–C helper convergence |
| BG-01 | P1 | Android 12–14+ lock-screen, process-death, and battery measurements |

## Immediate phone card

**Build/devices:** debug 1.0.1/code 2, SHA-256 `DB722F8B5481E9CF1B0997CE499BF7506AE275B2170D560DB29669ADE0B71C7A`. Pre-push delivery checks passed; this APK includes incident/UI changes. Record A/B/C names, models/API, and GATT/L2CAP use. User installs and operates phones. Capture with `scripts/capture_ble_logcat.ps1 -DurationMinutes 10`.

**Setup/steps:** same APK on all three phones; verify actual READY links and public/private text. Block A↔C. Keep B unblocked. Observe 90 seconds; remove/restore B, Refresh A/C, and repeat five times. Unblock only A, then both; restart one app at a time without clearing block preferences. Repeat with five short recorded notes, then simultaneous 5/15/30-second notes from A/C plus public text and test SOS. Test L2CAP and a GATT-only pair/path where available; record an unavailable transport case as untested.

**Expected:** direct A↔C remains denied until both release their records; A/B and B/C recover without clearing blocks; relay/private receipts work; progressing voice traffic avoids false retirement; rejected sends remain pending or show partial feedback. Transport acceptance is not delivery/playback proof.

**Failure/report:** timestamp any disconnect, false direct/relay label, missing/duplicate note, SOS delay, pending stall, or reconnect requiring unblock. Include `BLE_ADMISSION`, identity rejection, handshake acquire/release, `READY`, heartbeat, queue-full, promotion/fallback, and retirement markers. Report five repetitions and 90-second stability; omit private contents.

## Next actions

1. Run R7 before expanding traffic; identify the installed APK.
2. Run R8; investigate the exact failure window if it fails.
3. Advance to R9, then R10 only after the smaller matrix passes.
4. Run existing migration, helper-convergence, UI, and background cards.
5. Choose R11 from measured bottlenecks; keep text/SOS and private fail-closed behavior first.
