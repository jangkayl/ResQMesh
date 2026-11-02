# SOS banner and developer PIN card

Prepared: 2026-11-02 on `fix/dev-pin-sos-banner`. Status: local checks only; no device evidence yet. The user installs and operates the phones.

Build identity: record the branch head commit, the debug APK hash and the phone models before running. Missing items stay unspecified.

## Setup

Two phones A and B in range with the debug build installed. Start `scripts\device\capture_ble_logcat.ps1` on A. The debug PIN is `resqmesh.devPin` from `local.properties`, or `0000` when that line is absent.

## Developer PIN

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| P1 | Profile, Developer Tools, switch on; leave the PIN empty | Unlock is disabled; the app stays open | Unlock is pressable; app closes |
| P2 | Type a wrong PIN, tap Unlock | "Incorrect PIN. Access denied." | App closes; PIN accepted |
| P3 | Delete the text | Unlock is disabled again | Unlock stays enabled |
| P4 | Type the correct PIN, tap Unlock | Developer mode turns on | Rejected; app closes |

## SOS banner

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| B1 | On A, raise an SOS and open its chat | Full banner shows type, status, transmission, time, location, Map, History, End my SOS, and is shorter than before | A detail is missing or cut off; buttons wrap |
| B2 | Open the same chat on B | Same banner with Silence instead of End | End shown to a receiver |
| B3 | Tap an empty part of the banner | It shrinks to one line with type, status, location, time; messages move up with no overlap | Message hidden under the strip; strip wraps |
| B4 | Tap the strip | The full banner returns | Nothing happens |
| B5 | Tap Map, History, Silence and End (then Keep active) | Each does its own action and the banner stays open | A button collapses the banner |
| B6 | Collapse, rotate the phone | Stays collapsed | Reopens |
| B7 | Collapse, leave the chat, open it again | Opens on the full banner | Stays collapsed |
| B8 | Silence on B, then end the SOS on A, checking both collapsed and full | Strip and banner show SILENCED and ENDED correctly | Wrong status |
| B9 | Repeat B1 and B3 in light and dark themes | Text stays readable; card edges and shadow look clean while animating | Unreadable text; clipped edges |
| B10 | Open a chat with very large system font | One-row compact header shows End or Silence directly and does not collapse | Controls unreachable |

## Markers and report

Search `AndroidRuntime` around P1 for any `FATAL EXCEPTION`. Report the build identity, phone models, each step result, and the exception line for any crash, without private message text.
