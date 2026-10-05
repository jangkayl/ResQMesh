# Maintainability regression and edge cases

Last reviewed: 2026-10-06. Phase: 7 final batch. Physical results: **PASS (user-reported F1–F14)**. DELIVERY-01 remains open; this card does not diagnose it.

## Build and comparison identity

- Candidate: version 1.0.1/code 2, debug, Room 11, existing L2CAP configuration.
- Preserved candidate: `app/build/maintainability-phases/batched-2026-10-05/phase7-debug.apk`.
- Candidate SHA-256: `1AE82B6CE482EBBF4415E64C4FD74FD103D888104A3790AA8EDCEA979441BFE0`.
- Local/emulator outcomes and earlier phase identities: [evidence ledger](../handoffs/maintainability-local-evidence.md). These are separate from phone evidence. Local acceptance is blocked by four existing navigation failures (UI-02), reproduced on the saved baseline; the candidate is for comparison, not a release sign-off.
- Historical comparison APK: `app/build/maintainability-phases/2026-10-05-200635/baseline-debug.apk`, SHA-256 `1D42E828EDAB73B398AF9B25655EE609A2E54B13EA1F92B8F3B14A01DF6E4224`. It is the preserved pre-phase baseline, not the candidate. October 4 `D4638F9C...` reports do not validate this refactor.

## October 6 user report

The user confirmed all F1–F14 on candidate `1AE82B6C...` as PASS. This is a user report received October 6; actual test dates/times, models/API levels, counts, durations, per-leg transports and captures were not supplied. Do not infer them from the proposed steps. The four automated UI-02 failures retain their failed local results; no automated rerun was reported. DELIVERY-01 remains open despite successful reported relay runs.

## Setup and result rules

The user installs and operates phones A/B/C/D. Keep app data, identity, block records, maps and background preferences. Use the same candidate on all participants. Record actual models/API levels, permissions, roles, transport evidence and distances; none are assumed.

Start with an A–B direct pair, then verify a genuine A→B→C→D chain without unintended shortcuts. Visibility, CONNECTED, READY, acceptance, hop custody, arrival/storage and recipient receipts are different observations. Use synthetic content and recognizable short recorded notes. The repetitions below are proposed test steps, not historical success counts.

Run F1–F3 first, then recovery/load, repositories and UI. For a failure, repeat the same setup on the preserved baseline and record both full hashes. A newly reproducible regression is FAIL. An ambiguous intermittent A-to-D failure is INCONCLUSIVE under DELIVERY-01; do not mark it fixed. Keep unrun substeps UNTESTED, even when another substep passes. Do not assign a guaranteed delivery deadline; report observed timing and waiting duration.

## Networking and ownership checks

| ID | Exact steps | Expected outcomes / failure indicators | Result |
| --- | --- | --- | --- |
| F1 Direct and receipts | Go online A/B. In both directions send three public texts, three private texts and one recorded note. Open the recipient conversation. | Correct READY identity; intended arrival and complete playback. Private DELIVERED/READ comes from the recipient. Record sent/arrived/confirmed/duplicate counts separately. No confirmation for a missing message. | PASS (user report) |
| F2 Identity and readiness | Refresh while connecting, wait idle beyond 30 seconds, send again, and rename through the existing flow. On a fresh peer with unavailable/untrusted key, attempt private send before readiness. | No false READY or private delivery, wrong peer rename, bypass of trusted-key refusal or new connection churn. Pending/refused state follows existing policy. Do not remove established keys to manufacture this case. | PASS (user report) |
| F3 Four-phone relay | Verify A–B–C–D links and absence of A–D/A–C shortcuts. Repeat F1 A→D and D→A; deliver SOS independently. | Record arrival, return receipts, playback and SOS on each intended phone independently. Successful runs remain achievements; intermittent loss remains open. | PASS (user report) |
| F4 Burst and mixed load | A sends five recognizable short notes consecutively while D sends replies; add text/private messages and an SOS during the burst. Repeat foreground and with an opted-in relay locked. | Notes remain complete/playable; no duplicate stored messages/notifications, competing retry storm, deadlocked worker or falsely confirmed delivery. Record each item and timing; transport acceptance alone is insufficient. | PASS (user report) |
| F5 Loss during transfer | Begin a longer recorded note A→D. Turn relay B Bluetooth off during transfer, return it, then send a fresh text after readiness. Repeat with normal relay app restart. | Existing pending/custody/recovery behavior persists; no corrupted playback, duplicate rows or stale generation overwriting the recovered link. Report retry and final recipient confirmation separately. | PASS (user report) |
| F6 Recovery and intent | Repeat Bluetooth OFF/ON and Go offline/online three times on a direct phone and a relay. Go offline while Bluetooth is off, then turn Bluetooth on. Restart normally. | Requested recovery resumes only under existing online/background policy. Go offline stays offline; no duplicate sessions, callbacks or workers. Readiness/names recover without stale rows. | PASS (user report) |
| F7 Blocking and channels | With an alternate relay path, mutually deny a direct link; restart, then unblock each phone independently. Send allowed relayed content. Use two Radio channels and Community/SOS threads. | Direct denial and persisted local records remain; one local unblock does not clear the other record. Permitted relay behavior remains. History/drafts/unread stay scoped; off-channel notes do not autoplay. | PASS (user report) |
| F8 Capacity and rapid reconnect | With at least five available phones, join/leave peers around three direct neighbors; refresh and reconnect the same address during cleanup. Send after replacement readiness. | Three-direct-neighbor policy remains. No duplicate roles, stale callback completing a replacement queue, stuck capacity, unexpected retirement of a healthy link or false delivery. Record topology; a fourth direct neighbor is not required. | PASS (user report) |
| F9 Persistence/background | Queue private text and a note while the recipient is absent; restart the sender, return the recipient and reopen the chat. Test locked/background with opt-in enabled and disabled; exercise Go offline. | Existing outbox/journal persistence and background intent hold. Recipient storage precedes receipt/notification; duplicates preserve seen state. Do not clear data. Report settings/duration and OEM behavior explicitly. | PASS (user report) |

## Incident, SOS, map and presentation checks

| ID | Exact steps | Expected outcomes / failure indicators | Result |
| --- | --- | --- | --- |
| F10 Incident roles and races | Reporter creates; helper offers; reporter selects; helper confirms. Withdraw the selected offer, select a replacement, confirm, then close. Reconnect a previously absent participant. Also withdraw near selection/confirmation and replay after reconnect. | Reporter/helper authority and existing lock/replay ordering remain. Withdrawal reopens selection without choosing a replacement or advancing reporter authority. Final projections converge without hanging or resurrecting closure. Record every action/phone. | PASS (user report) |
| F11 Independent SOS | Two origins create SOS alerts. Sender Back, receiver silence, confirmation/cancel and origin closure; reconnect a previously absent phone. | Back preserves sender SOS; silence is local; cancellation/closure affects only the matching origin alert. Terminal records reconcile. SOS receipt/queue acceptance is not incident convergence. | PASS (user report) |
| F12 Drafts and navigation | Draft incident title/type/urgency, private text/reply/attachment and Radio note. Rotate/recreate through normal Android operations; navigate/back and cancel each form. Restore an open help-offer draft. | Existing saveable drafts/selections remain; intentionally unsaved state retains its prior behavior. No duplicate send, changed destination, premature submit or lost saved offer text. | PASS (user report) |
| F13 Offline map lifecycle | Open an SOS with/without coordinates and with/without an installed verified package, offline. Expand/collapse details, switch coordinate tabs, copy, recenter, rotate, leave/reopen and return from package settings. | Same fallback/formatting/actions; one map/location lifetime, correct observer/style cleanup, no crash, duplicate subscriptions or lost return target. Record package version and permission state. Map behavior is user-reported PASS; resource lifetime was not instrumented on phones. | PASS (user report) |
| F14 Accessibility and controls | In light/dark modes, portrait/landscape and large text, exercise incident dialogs, search/filter rapid taps, private chat, SOS controls, keyboard, attachments, recording cancel and TalkBack traversal. | Same labels/semantics/focus/gestures and layout; controls remain reachable without overlap. Cancelled recording does not transmit; channel selection and rapid filters retain current state. Record screenshots only with synthetic content. | PASS (user report) |

## Focused evidence and report template

Failure indicators: crashes, stuck queues/retries, false READY/receipt, callback duplication, corrupt/duplicate notes, changed block/offline behavior, stale incident/SOS state or lost saveable drafts. Capture only the relevant window. Useful markers include `BLE_MESH`, `BLE_RECOVERY`, READY/generation, `SYSTEM_SENT`, `BLE_TRANSFER`, `PRIVATE_DISPATCH`, `PRIVATE_STORED`, `PRIVATE_RECEIPT`, `PRIVATE_DELIVERED`, `INCIDENT_SYNC` and `SOS_SYNC`. Keep plaintext, keys, ciphertext previews and personal location out of shared captures.

```text
Test/substep: F__ ; PASS / FAIL / UNTESTED / INCONCLUSIVE
Candidate full SHA-256: ; comparison full SHA-256:
Phones/models/API: ; A/B/C/D roles and actual direct links:
Permissions/block/background/channel/map state:
Attempts: ; sent: ; arrived/stored: ; receipt-confirmed: ; duplicates:
Note playback completeness: ; observed timings/waiting duration:
Expected vs actual: ; exact failure time: ; focused capture path:
Baseline comparison outcome: ; unrun substeps:
```

F1–F14 are now supplied user-reported passes for this candidate. Quantitative evidence and the independent automated UI-02 gate remain outstanding. Compilation or results on other builds cannot substitute for this report.
