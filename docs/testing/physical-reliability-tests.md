# Physical reliability checklist

Last reviewed: 2026-10-04. Status: active procedure with qualitative user-reported successes and remaining exact-case coverage. Source baseline: `2e27013`.

## Build identity and evidence

Use the [current candidate/report](../validation.md): version 1.0.1/code 2; normal October 4 APK, SHA-256 beginning D4638F9C. Use the complete hash in validation and matching APKs on all phones. Recalculate identity after any rebuild; no APK was rebuilt for documentation maintenance.

The former September 30 checklist candidate SHA-256 `DB722F8B5481E9CF1B0997CE499BF7506AE275B2170D560DB29669ADE0B71C7A` is historical. Its recorded unit/build/Android-test compilation/Lint results do not identify the current installed build.

User reports successful four-phone text/receipt/note/SOS paths, block/restart/relay recovery, toggles/background, incidents/SOS lifecycle and offline maps. These successes do not establish the repetitions or variants below. **DELIVERY-01:** some public/private messages or notes fail to reach D through A→B→C→D; record the failure for later investigation without assuming a cause.

Models/API, exact test times/counts, durations, transport and captures were not supplied. A successful general flow is separate from a completed repeated test. Report PASS, FAIL or UNTESTED per exact case.

## Setup and reporting

Label A/B/C, then D/E onward; record model/API, installed hash, stable IDs, blocks, permissions, battery restrictions and background setting. Start foreground/unlocked/nearby. Preserve data/keys/blocks/history for restart tests; upgrades and data clearing are separate procedures.

Use numbered harmless texts and unique recording labels. Count recipient arrival, private confirmation and complete playback separately. Use test SOS content and end it afterward. Connect only authorized ADB phones and retain phone-to-serial mapping:

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 15
```

USB-powered captures do not measure battery performance. Timings across phones require common video/clock; per-device monotonic deltas describe only local stages.

## Core phone cases

| ID | Exact procedure | Expected result and coverage limit |
| --- | --- | --- |
| T1 Baseline | Three phones, no blocks: five public/five private texts each way per pair, three short notes; five quiet minutes. | Arrive once, private receipt returns, complete playback and truthful route labels. General success reported; counts/quiet interval unconfirmed. |
| T2 Mutual block | A/C deny their direct link, B remains usable. Wait 90 seconds; exchange five public/private texts each direction through B, plus A/B and B/C. Repeat five cycles. | No A/C direct READY, relay remains usable; general block/relay success reported, repeated protocol coverage unconfirmed. |
| T3 Relay loss | Keep blocks; remove B, verify withdrawal, queue traffic, restore B. Observe 90 seconds before Refresh; repeat five times. | Honest route/pending state and automatic recovery. Record automatic versus Refresh recovery separately; relay-return success reported. |
| T4 Independent release | Release on A only, restart A/C without clearing, then release on C. Reverse order. Launch blocked endpoints before B, then add B. | One-sided release cannot restore direct eligibility; both local releases required. Basic persistence/release reported; startup orders unconfirmed. |
| T5 Replacement | Restart, toggle Bluetooth and leave/return range, three repetitions each. Send during/after loss. | Fresh usable links and preserved pending traffic; no old-generation teardown. Restart/toggles reported, exact repetitions/races unconfirmed. |
| T6 One sender | Five 5-second notes, then three 15-second and three 30-second notes; text between notes. | Complete once, progressing healthy links remain usable; duration/load matrix unconfirmed. |
| T7 Concurrent load | A/C send five short then longer notes concurrently; B sends text and creates/ends SOS. Three rounds including relay blocks. | Independent text/control progress and intact audio. Measure active-frame delays; no guaranteed immediate SOS or checked burst count. |
| T8 Whole rejection | Send text/note while isolated, then restore a peer; repeat under queue pressure. | Visible waiting/rejection, eligible recovery once, explicit expiry/failure; acceptance never promises delivery. |
| T9 Partial acceptance | A has two READY neighbors; one busy, one draining. Send labeled traffic and observe actual rejection. | Partial feedback, no whole-broadcast resend to accepted neighbors. No observed partial rejection means UNTESTED. |
| T10 Transport | Matching normal versus separately prepared GATT-only variants; repeat T6–T8 and interrupt/restore busy peer. | Log actual per-leg FRAME transport, fallback and negotiated chunk size. Historical diagnostic hashes are not current variants; no transport coverage inferred. |
| T11 Five phones | Ten quiet minutes; one sender ten texts/three notes, then all send five texts/three notes. Three rounds plus relay return. | Count all intended recipients, churn/recovery/rejections; three direct neighbors is not total capacity. Controlled matrix remains pending. |
| T12 Ten phones | Repeat T11 after smaller failures are understood. | Record rate, duplicates, median/p95 delay, recovery, rejections and heat; remains pending. |
| T13 Features/upgrades | Follow active Radio/SOS, helper, navigation, background and map procedures. Existing-data upgrade/migration separate from fresh install. | Functional incident/SOS/map/background reports do not establish every UI, migration or failure case. |

## Unique cases retained from older cards

- **Identity/receipt:** distinguish READY from admitted identity/key usability. Queued private work may proceed only when usable. Rename the recipient; confirm directed DELIVERED and visible-chat SEEN. Rejoin ten times and compare generations. Blocked identities cannot carry ordinary direct traffic; relay policy remains separate.
- **Compatibility/harness:** an optional upgraded/legacy pair needs explicit old/new hashes and legacy admission evidence. Malformed-key, wrong-target/public-flag receipt and stale-token cases require deterministic tests or a separately authorized harness; normal phone actions cannot prove them.
- **Private recovery:** queue while the recipient is absent; restart sender without clearing; restore recipient and confirm saved pending/unconfirmed work recovers. Terminal FAILED remains terminal. If arrival precedes receipt, observe retry/deduplication and one notification/row; confirmation stops retries. Never approve unexplained key changes to make a test pass.
- **Timing/backoff:** compare quiet direct, A–B–C and A–B–C–D in both directions, thirty numbered private texts each where feasible. Send fresh text while older work waits for retry; fresh dispatch should not inherit that wait. Preserve separate public retry behavior. Baselines require equivalent phones/topology/load; never downgrade/clear data just to create one.
- **Notes/transport stages:** quiet five/ten-second notes in private, Community and Radio where supported; time from Send/recording end, then arrival and playback separately. Log serialized size: MESSAGE/CONVERSATION at ≤4 KiB should use ordinary frames; larger/journal-owned work uses pieces. An open L2CAP socket/label alone is not payload evidence.
- **Idle/join/resume:** ten quiet minutes, then join another phone during notes. After relay custody and before onward completion, restart the relay; separately restart a partially receiving phone. Expect missing-piece recovery, preserved logical ID and receipt replay. Public forwarding uses its frozen known-peer roster; later joiners are not retroactively added.
- **Latency targets:** earlier preferred-path goals (text arrival p95 ≤2 seconds; short-note p95 ≤5 seconds) are investigation targets, not results or release promises. Log arrival/confirmation/playback, note bytes and actual path separately; current DELIVERY-01 prevents a reliability claim.

## Failures, markers and report

Missing/duplicate/unplayable traffic, false delivery, repeated retirement, silent rejection, unauthorized direct links, wrong channel/alert, lost history or permanent pending after restored readiness fail the relevant case. Do not increase load after reproducible failure.

Report test ID/repetition, APK, device roles/API, topology/blocks, exact time/action, expected/actual result, sent/arrived/confirmed/duplicate counts, recovery/Refresh effect, note size and capture folder. Mark unavailable conditions UNTESTED.

Focus READY/IDENTITY_ADMITTED, FRAME, TRANSFER custody/status/hash/storage/expiry, PRIVATE_DISPATCH/STORED/RECEIPT/DELIVERED, LINK_RETIRED, BLE_ADMISSION/RECOVERY, route withdrawal and INCIDENT_SYNC/SOS_SYNC. Omit content, ciphertext and keys. DELIVERY-01 is recorded for later authorized analysis; this checklist does not schedule a network repair.
