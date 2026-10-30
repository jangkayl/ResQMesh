# Developer console card (Stage 1)

Prepared: 2026-10-31 on `feat/developer-console`. Status: local checks only; no device evidence yet. The user installs and operates the phones.

Build identity: record the branch head commit, the debug APK hash, the release APK hash and the three phone models before running. Missing items stay unspecified.

Stage 1 covers the console screens, fast logger, export and release unlock. Hop, TTL, drop reasons, voice traces, link RSSI and queue stats arrive in Stages 2 and 3: expect a dash for hop, TTL and next hop, an empty Voice tab and no RSSI. Those are not failures.

## Setup

1. Add `resqmesh.devPin=<PIN of 8 or more ASCII characters>` to the gitignored `local.properties` (a shorter or non-ASCII value must fail the release build), then build the release APK. Build a second release APK without the line for step R5. Debug builds use `0000` when the line is absent.
2. Three phones A, B, C in a line A-B-C (A and C out of direct range), with the debug build installed. Start `scripts\device\capture_ble_logcat.ps1` on A.

## Debug build

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| D1 | Profile, Developer Tools, switch on, enter `0000` | Badge appears; wrong PIN is rejected with a message | Accepts a wrong PIN; no badge |
| D2 | Tap the badge | Full-screen console with six bottom tabs; Back closes it | Missing tabs; Back does nothing |
| D3 | From A, send public text, private text, an SOS and a voice note over 4 KiB | Packets tab shows OUT and IN cards with scope, type, bytes, transport; public destination "all"; hop and TTL dashes | Missing cards; wrong scope |
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

Run the same A-B-C traffic (public, private, SOS, voice note over 4 KiB) three times: console open, console closed with developer mode on, and developer mode off.

Expected: delivery and timing comparable in all three, no new disconnects or crashes, and with developer mode off the badge is absent and Packets stays empty when re-enabled later.

Failure: slower sends, extra link drops, ANR, or traffic recorded while developer mode is off.

## Markers

In Logcat (debug build): `FRAME_`, `RL_DROP`, `TRANSFER_`, `AndroidRuntime`. Release builds log nothing to Logcat.

## Report

Build identity (commit, APK hashes), phone models and roles, which steps passed or failed, counts of sends and arrivals if observed, any link drops with times, the two export files' inspection result, and failure indicators seen. Successful steps do not close an intermittent failure.
