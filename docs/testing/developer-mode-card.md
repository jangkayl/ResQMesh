# Developer console card (Stages 1 and 2)

Prepared: 2026-10-31 on `feat/developer-console`. Status: local checks only; no device evidence yet. The user installs and operates the phones.

Build identity: record the branch head commit, the debug APK hash, the release APK hash and the three phone models before running. Missing items stay unspecified.

Stage 1 covers the console screens, fast logger, export and release unlock; Stage 2 adds hop, route, drop, failure, transfer progress and voice data. Link RSSI and queue stats arrive in Stage 3: expect no RSSI. That is not a failure. A dash for hop or TTL is correct for private and non-relayed packets.

## Setup

1. Add `resqmesh.devPin=<PIN of 8 or more ASCII characters>` to the gitignored `local.properties` (a shorter or non-ASCII value must fail the release build), then build the release APK. Build a second release APK without the line for step R5. Debug builds use `0000` only when `resqmesh.devPin` is absent; otherwise the configured PIN applies.
2. Three phones A, B, C in a line A-B-C (A and C out of direct range), with the debug build installed. Start `scripts\device\capture_ble_logcat.ps1` on A.

## Debug build

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| D1 | Profile, Developer Tools, switch on, enter the debug PIN (`0000` only when `resqmesh.devPin` is absent) | Badge appears; wrong PIN is rejected with a message | Accepts a wrong PIN; no badge |
| D2 | Tap the badge | Full-screen console with six bottom tabs; Back closes it | Missing tabs; Back does nothing |
| D3 | From A, send public text, private text, an SOS and a voice note over 4 KiB | Packets tab shows OUT and IN cards with scope, type, bytes, transport; public destination "all" | Missing cards; wrong scope |
| D4 | Open Links | All links listed; READY shown as "Payload READY", earlier states as radio connected or connecting | A not-ready link shown as ready |
| D5 | Overview | Link, error and ring counts match what you see | Counts static |
| D6 | Cause an error (switch Bluetooth off on B) | Red dot on the badge for an ERROR entry; warnings alone give no dot | No dot; dot for warnings only |
| D7 | Long-press the badge | Opens Errors; the dot clears | Opens another tab; dot stays |
| D8 | Packets: use each filter chip, search a short ID and a type, then pause | Matching rows only; pause freezes the list while traffic continues; resume catches up | Filters ignored; list keeps moving while paused |
| D8b | During traffic, watch Packets, Errors and Logs, scroll down, then return to the top | LIVE chip keeps the newest row on screen; scrolling down stops the list moving and shows Go to latest with a growing new-entry count; the button or returning to the top resumes live | Must scroll for new rows; list jumps while reading |
| D9 | Tap a message packet | Detail shows all fields and a timeline of this phone's events with the same ID | Empty timeline for a sent message |
| D10 | Heartbeats | Adjacent heartbeats or pieces collapse into one row with a count; failures never collapse | Failures merged |
| D11 | Logs and Errors tabs | Newest first; MACs appear shortened | Full MAC visible |
| D12 | Export Text, then CSV (top bar), choosing a location each time | Both files save; failure shows a message | Crash; silent failure |
| D13 | Open both files | Only short IDs and marker lines; no peer names, no message text, no full MAC, no key material | Any name, text or full MAC |
| D14 | During export and the file picker, watch links on A and B | No new disconnects; traffic continues | Link drops while the picker is open |

## Stage 2: hop, drop, failure and voice data (debug build, A-B-C)

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| S2-1 | Send public text from A, open the packet on B | Card shows `hop N · TTL left M` (only when TTL is above 0); on C the hop is higher | Hop shown with TTL 0; same hop on B and C |
| S2-2 | Send private text A to C through B | A and B show `route step i/n` and a next hop; B does not show hop or TTL left | TTL left shown for private; no next hop |
| S2-3 | Resend the same packet quickly, and send a burst from one peer | Duplicates and rate-limited traffic show DROPPED with a reason (DUPLICATE, ORIGIN_LIMIT, FRAME_RATE); drop rows are throttled, not one per packet | No reason; rings flooded by drops |
| S2-4 | Switch C off, send private text from A to C | FAILED or expired row with a reason (SEND_NOT_READY, RETRIES_EXHAUSTED or EXPIRED_UNSENT) | Silent loss |
| S2-5 | Send a voice note over 4 KiB from A to B | Packets shows piece progress such as 12/18 on collapsed rows; Voice tab shows size, scope, destination, progress and a reassembled entry on B | No progress; no Voice entry |
| S2-6 | If reproducible, cause a mic failure on A or a playback failure on B | Voice tab shows RECORD_FAILED or PLAYBACK_FAILED with no file path or message text | No entry; path or text shown |
| S2-7 | Send private audio A to C and look at B | B shows "content unknown"; A's own transfer rows say voice note and C shows a voice-note received event | B labels it voice |
| S2-8 | Open a message and read the timeline on A, B and C | Queued, sent, relayed, received, dropped with reason, and receipts, for this phone only; route step where private | Another phone events shown; missing receipts |
| S2-9 | Export Text and CSV | New columns (hop, ttl, path_len, route_step, progress, total, drop) present as numbers or dashes; no names, no full MAC, no text | Any name or full MAC |

## Release build with a configured PIN

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| R1 | Open Profile | No Developer Tools section | Section visible |
| R2 | Tap the App version row 6 times | Still hidden | Appears early |
| R3 | Tap a seventh time | Developer Tools appears for this screen visit | Does not appear |
| R4 | Enable with a wrong PIN, then the right alphanumeric PIN | Wrong rejected; right accepted; badge appears | `0000` accepted; input capped at 4 |
| R4b | Enter five wrong PINs, then the right one at once | Locked for about 30 s with a message; right PIN works afterwards | Right PIN accepted while locked |
| R4c | With developer mode on, rebuild with a different PIN and update over the install | Developer mode is off | Still on |
| R5 | Install the release build made without `resqmesh.devPin`; tap the version row 10 times | No section, no hint, no switch; a previously stored flag does not show the badge | Any developer access |
| R6 | Reopen Profile on the PIN build with developer mode on | Section stays visible so it can be turned off | Section hidden while enabled |

## Regression on the release build (PIN configured)

Run the same A-B-C traffic (public, private, SOS, voice note over 4 KiB), including the Stage 2 steps, three times: console open, console closed with developer mode on, and developer mode off.

Expected: delivery and timing comparable in all three, no new disconnects or crashes, and with developer mode off the badge is absent and Packets stays empty when re-enabled later.

Failure: slower sends, extra link drops, ANR, or traffic recorded while developer mode is off.

## Markers

In Logcat (debug build): `FRAME_`, `RL_DROP`, `TRANSFER_`, `AndroidRuntime`. Release builds log nothing to Logcat.

## Report

Build identity (commit, APK hashes), phone models and roles, which steps passed or failed, counts of sends and arrivals if observed, any link drops with times, the two export files' inspection result, and failure indicators seen. Successful steps do not close an intermittent failure.
