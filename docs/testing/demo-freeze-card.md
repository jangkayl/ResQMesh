# Demo freeze card

Prepared: 2026-10-17. Status: on 2026-10-17 the user reported part 1 (startup not timed) and part 2 (D1–D8 ×5) working on candidate #2; tally counts not supplied. Parts 3–4 pending. Covers status next actions 1 (candidate #2 startup and mesh check) and 2 (five repeats with counts, DELIVERY-01 captures, recorded backup) before the demo freeze (about 2026-10-28). This card collects evidence only: no fixes go in unless the user separately approves a demo-blocking one.

## Build identity

- **Candidate #2 (demo build):** release APK `B84ACB1B3D6576ED9E081236DD23EC792DD3B445F4A5BA93A869FB52D6B2EA96` from `42f1660`, already installed on all three phones ([validation](../validation.md)).
- During parts 1–3, do not reinstall, clear data or update. If the APK changes, write down the new hash and restart the affected tallies.
- **Capture build (part 4 only):** a debug APK from the same commit (`manage_devices.ps1`, option 5). Release builds keep app markers out of Logcat, so DELIVERY-01 captures need it. Switching to debug wipes data and keys, so do part 4 last and reinstall candidate #2 afterwards.

## Devices and roles

| Role | Phone | API |
| --- | --- | --- |
| A (reporter, sender) | vivo V2424 | 34 |
| B (relay, helper) | OPPO CPH2127 | 31 |
| C (recipient, alternate helper) | Samsung SM-P615 | 33 |

- **Relay topology A–B–C:** on A, open Mesh, then C's peer details, and choose **Block Device** (mutual direct-link denial) so traffic from A to C must go through B. Unblock it for the direct cases.
- Write down which topology each run used. A fourth phone (D) is needed to reproduce the original A→B→C→D failure. With three phones, record A→B→C.
- Use harmless test text, such as "T3-R2 public" (test 3, repetition 2). Never use real emergencies or personal data.
- **Timing limit** (agreed before timed runs): text and SOS must arrive within 30 s, a voice note within 60 s, and incident changes must reach all phones within 60 s. Anything later counts as a miss, even if it shows up eventually; write down when it arrived.

## Part 1: candidate #2 startup and mesh check (next action 1)

Uses steps 1, 2, 4 and 5 of the [startup lag card](startup-lag-card.md) on the release build only (it has no debug terminal, so step 6 doesn't apply).

1. **Cold start ×3 per phone:** run `adb -s <serial> shell am force-stop edu.cit.resqmesh`, then `adb -s <serial> shell am start -W -n edu.cit.resqmesh/.MainActivity`, and record `TotalTime`. The agent can run this over adb if you ask. Expected: about 1 s or less (an earlier unminified release build measured 0.79–0.90 s on the OPPO).
2. **Home jank:** `dumpsys gfxinfo` before and after 30 s idle on Home with the other phones nearby. Record the Janky frames %.
3. **Identity and mesh:** Incident controls load normally. A and B reach READY, private A→B shows DELIVERED, and an SOS from A alarms on B.

## Part 2: critical scenarios ×5 (next action 2)

Run each case five times. Fill in one row per case and one note per failure (repetition number, time to the minute, what was seen).

| ID | Case | Topology | Expected | Sent | Arrived | Confirmed | Duplicates | Misses (times) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D1 | Public text A→all | direct | Arrives on B and C | | | n/a | | |
| D2 | Private text A→B | direct | DELIVERED, then READ | | | | | |
| D3 | Private text A→C | A–B–C | DELIVERED via B | | | | | |
| D4 | Voice note A→C | A–B–C | Plays completely on C | | | | | |
| D5 | SOS raise on A, then end | direct | Alarm on B and C; ends everywhere | | | n/a | | |
| D6 | Incident lifecycle | direct | A reports, B and C offer, A selects B, B confirms then withdraws, A selects C, A resolves; history on all three | | | n/a | | |
| D7 | Bluetooth OFF/ON on B, then D3 again | A–B–C | Relay resumes; message DELIVERED | | | | | |
| D8 | Locked and background on C, then D2 to C | direct | Notification; delivered while locked | | | | | |

- **Sent:** attempts made.
- **Arrived:** the recipient actually showed it, within the timing limit.
- **Confirmed:** the sender saw DELIVERED.
- A "sent" status alone is not delivery.

## Part 3: rehearsal and recorded backup

1. Rehearse the full demo story twice from a clean start (all phones open, Bluetooth on, no internet): public chat, private message with receipt, relay through B, voice note, SOS raise and end, then the incident lifecycle. Time each rehearsal.
2. Record one complete clean run as the backup video: screen recording on A plus a second camera showing all three phones. Label it with the date and the candidate #2 hash.
3. **Day-of kit:**
   - phones charged, plus cables and a power bank
   - the frozen APK file and its hash on a USB drive
   - the [known limitations](../demo/known-limitations.md) and the [user guide](../demo/user-guide.md)
   - the slides and the backup video, available offline

## Part 4: DELIVERY-01 captures (last, debug build)

1. Install the debug APK from `42f1660` on all three phones (option 5; typing `YES` wipes data). Re-trust keys by sending one private message each way.
2. On the PC, run `.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 15`.
3. With the A–B–C topology, send 10 public and 10 private texts plus 3 voice notes from A to C, about 20 s apart. Write down the exact time of every miss.
4. Repeat with a fourth phone as D (A→B→C→D) if one is available.
5. Report the capture folder and the miss times. The agent searches markers in a bounded window only. Root-cause work on DELIVERY-01 needs separate approval.
6. Reinstall candidate #2 (option 6, Release) when done.

## Failure indicators and report

Report a FAIL for any of these:
- a crash
- a message that never arrives, or arrives after the timing limit
- DELIVERED shown without the message arriving
- duplicates
- an SOS that does not alarm or does not end everywhere
- an incident state that differs between phones after 60 s
- Bluetooth recovery that does not resume the relay
- a part 1 cold start over 2 s

Report: the APK hash, roles and topology, part 1 numbers, the part 2 table, failure notes with times, rehearsal durations, the backup video file name and the part 4 capture folder. Never share message content, keys or real locations.
