# Physical reliability checklist

Prepared 2026-09-30. All boxes remain open until tested. Use this across sessions; report Pass, Fail, or Untested for each test. Passing this matrix supports the tested devices and conditions, not guaranteed capacity.

## Build and setup

Install the same APK on every phone. Checked output: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256: `DB722F8B5481E9CF1B0997CE499BF7506AE275B2170D560DB29669ADE0B71C7A`. Pre-push unit tests, debug build, Android-test compilation, and Lint passed. Physical validation remains open. Recalculate the hash if rebuilt.

Label phones A/B/C, then D/E onward. Record model, Android version, battery, background-mesh setting, APK hash, and start/end times. Keep initial tests foreground, unlocked, and nearby. Preserve block/history data for restart tests.

Use numbered harmless messages (`A-T01`) and say a unique label in each recording (`A-V01`). Check receipt on recipient phones; sender acceptance/status alone is insufficient. Use test SOS content and cancel afterward.

Connect authorized USB-debugging phones and start capture before each batch:

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 15
```

The script captures all authorized ADB devices and prints its folder. Keep the phone-to-serial mapping. USB-powered tests do not establish battery performance.

## First session: three phones

- [ ] **T1 — Baseline.** With no blocks, establish usable direct links. Send five public texts and five private texts each way per tested pair. Send three short recorded notes. Check recipient counts, readable private messages, complete playback, and truthful People & Paths labels. Fail: missing/duplicate content, unexplained churn, or “direct” without usable delivery. Observe quiet links for five minutes.
- [ ] **T2 — Exact mutual-block case (R7).** A blocks C; C blocks A; B remains unblocked. Wait 90 seconds. Send five public and five private texts A→C and C→A through B. Also exchange A↔B and B↔C texts. Repeat five cycles. Pass: A↔C direct links stay denied, B remains usable, and relayed messages arrive once. Unblocking C must not be required for A to use B.
- [ ] **T3 — Relay removal and Refresh (R7).** Keep A↔C blocked. Disable Bluetooth on B; wait for route withdrawal. Send labeled public/private messages while isolated. Restore B; observe recovery for 90 seconds before pressing Refresh on A/C. Record automatic recovery separately from recovery after Refresh. Repeat five times. Pass: routes withdraw honestly, pending traffic recovers appropriately, and B becomes usable without changing blocks. Fail: false delivered status, permanent isolation, or unblock needed.
- [ ] **T4 — Unilateral release and persistence (R7).** From mutual block, unblock C on A only. Direct A↔C must remain denied while C retains its block; B/relay communication must work. Restart A, then C separately without clearing data; repeat messaging. Then release A on C; direct connection becomes eligible, but a healthy relay/capacity policy may defer it. Repeat with C releasing first. Also launch blocked A/C before B, then launch B: both must recover to B.
- [ ] **T5 — Replacement-link recovery.** Restart each app separately, toggle Bluetooth on each phone, and move one out of range and back. Repeat each disruption three times. Send during disruption and immediately after recovery. Pass: new links carry text/notes; stale callbacks do not cause repeated teardown. Record recovery seconds and whether Refresh was needed.

## Second session: voice and queues

- [ ] **T6 — One sender (R8).** A sends five 5-second public recordings consecutively, then three 15-second and three 30-second recordings. Send numbered text between notes. Check B/C playback and counts. Pass: progressing transfers remain connected; incomplete recordings, duplicates, and unexplained disconnects are failures.
- [ ] **T7 — Concurrent senders (R8).** A/C each send five short public notes concurrently, then repeat with longer notes. B sends five texts and one test SOS during transfer. Repeat three rounds, including the blocked relay topology. Measure recipient text/SOS arrival and voice completion separately. Large active GATT frames can still delay SOS; record that delay as an open issue even without disconnects.
- [ ] **T8 — Whole rejection/outbox (R8).** With A isolated, send a public text and short note. Expect waiting/pending feedback. Restore B; check eventual receipt once. Repeat during observable queue pressure. Oversized recordings may fail explicitly rather than retry forever. Fail: silent rejection, premature delivered claim, stuck eligible pending sends, or duplicates. Disconnecting after acceptance is a different case; local acceptance does not promise delivery.
- [ ] **T9 — Partial acceptance (R8).** Keep two usable neighbors for A; make one busy while the other drains normally. Send labeled public traffic. If rejection occurs, expect partial feedback. Capture dispatch results: no automatic whole-broadcast retry to neighbors already accepting it. Recipient deduplication alone cannot prove this. If partial rejection never occurs, mark Untested; do not claim coverage.
- [ ] **T10 — Transport comparison (R8).** Repeat T6–T8 on confirmed L2CAP and a GATT-only path where available. Identify transport from logs; do not invent a UI switch. Interrupt a busy peer and restore it to exercise teardown/replacement. Check recovered delivery, fallback logs, and absence of old-socket churn. Mark unobserved fallback or unavailable transport Untested.

## Later sessions

- [ ] **T11 — Five phones (R9).** Run 10 quiet minutes, one sender with ten texts/three short notes, then all phones with five texts/three notes each. Repeat three rounds; remove/restore a relay. Check each intended recipient, routes, recovery, queue feedback, heat, and crashes. Maximum three direct neighbors remains; every pair need not connect directly.
- [ ] **T12 — Ten phones (R10).** Repeat T11 only after investigating smaller-test failures. Record expected/received counts, duplicates, median/p95 arrival delay, disconnects, recovery time, and rejections. Keep the workload identical for comparison.
- [ ] **T13 — Other changed-feature regressions.** Follow validation.md cards for Radio playback/manual priority, helper selection/revocation/closure and reconnect history, first-launch/navigation, and background mesh enabled/disabled with 10–15-minute lock tests. Test supported location/SOS/private attachments separately. Record permissions, crashes, lost history, and wrong connection labels. Database migrations need existing-data upgrade checks/instrumentation; a clean installation cannot validate them.

## Report each failure

Provide test ID, APK hash, devices, topology/blocks, exact action, expected/actual result, time to the second, recovery time, whether Refresh/unblocking helped, and capture folder. Record counts and playback separately. Relevant markers: BLE_ADMISSION, identity rejection, handshake acquire/release, READY, heartbeat, queue-full, promotion/fallback, retirement, route withdrawal, and receipts. Share no private content or keys. Stop increasing load after a reproducible failure; preserve the capture for diagnosis.
