# BLE refactor regression card

Prepared: 2026-11-05. Status: not yet run. Branch `refactor/ble-config-constants` (on top of PR #35, code smells round 2). Local checks on that date: 934/934 JVM tests, Lint, detekt, boundary and docs checks pass. No device evidence.

This card exercises the code PR #35 restructured: GATT client/server connect and CCCD setup, collision handling, heartbeat and stale retirement, admission/bootstrap, private relay forwarding and chunked transfers. It complements the [code smells card](code-smells-refactor-card.md).

## Build identity

Build `.\gradlew.bat :app:assembleDebug` from the branch head. Record the commit and APK SHA-256 (`Get-FileHash app\build\outputs\apk\debug\app-debug.apk`). Install the same APK on every phone.

## Devices and setup

Phones A, B and C. **2P** cases need two phones; **3P** cases need three. Bluetooth on, app foreground, screens awake. Capture with `.\scripts\device\capture_ble_logcat.ps1` on each phone if available. Search only the listed markers; never copy message plaintext or key material.

## P0: connection (2P)

| # | Case | Steps | Expected | Markers |
| --- | --- | --- | --- | --- |
| 1 | Cold connect | Open A, then B | Both connected and payload-ready within about 15 s | `READY descriptorStatus`, `READY CCCD indications enabled` |
| 2 | Role swap | Restart both; open B first | Same as 1 with roles reversed | Same as 1 |
| 3 | Simultaneous open | Open both together, three times | One link per peer, no loop | `Dual-Link Collision` (keep on one side, yield on the other) |
| 4 | MTU | Send public text after 1 | Arrives; one MTU request | `MTU_REQUEST requested=247`, then `MTU Expanded to` or `MTU Expansion failed` |
| 5 | Public text | A sends public text | Arrives on B with correct sender | — |
| 6 | Private text | A to B, then B replies | Both arrive with DELIVERED | `E2EE SUCCESS` |
| 7 | Large voice note | A sends a note over 4 KiB | Complete playback on B | `TRANSFER_REASSEMBLED`, `TRANSFER_CUSTODY_ACK` |
| 8 | Image | A sends a public image | Complete, not "[Attachment unavailable]" | no `MEDIA_REJECT` |

## P0: recovery (2P)

| # | Case | Steps | Expected | Markers |
| --- | --- | --- | --- | --- |
| 9 | Bluetooth OFF/ON | B off 30 s, then on | Offline, then automatic reconnect | `GATT Socket disconnected`, then READY |
| 10 | Offline private queue | B off, A sends private text, B on | Pending, then DELIVERED | — |
| 11 | Out of range | Walk B away until the link drops, then return | Stale link retired, reconnect, no ghost row | `Retiring stale GATT roles` or `GATT heartbeat ACK timed out` |
| 12 | Force stop | Force-stop B 20 s, reopen | Drop, then reconnect | — |
| 13 | Reboot | Reboot B, open app | One row for B on A, reconnect on new address | `Purging zombie link` or `GHOST EVICTION` |
| 14 | Rapid toggle | Toggle Bluetooth on B five times quickly | No crash, one link, no stuck Connecting | `Connect lock stuck` at most once, then recovery |

## P1: guards (2P)

| # | Case | Steps | Expected | Markers |
| --- | --- | --- | --- | --- |
| 15 | Block | A blocks B, then unblocks | Direct link refused, then restored | `Rejected blocked device` or `Skipping GATT connect to blocked peer` |
| 16 | Permission revoked | Revoke Nearby devices on A while linked, return, regrant | No crash; clean teardown; recovery after regrant | `BLUETOOTH_CONNECT was revoked`, no `FATAL EXCEPTION` |
| 17 | Locked | Lock A 2 min; B sends text | Arrives with notification | — |
| 18 | Burst | A sends 10 public texts quickly | All arrive in order, no disconnect | — |
| 19 | Send during reconnect | Send from A as B's Bluetooth returns | Queued then delivered once | — |
| 20 | Help workflow | Create, offer, edit, select, confirm, resolve | Same final state on both | — |
| 21 | SOS | Raise, update location, end on A | Alarm, location and end on B | — |
| 22 | Offline map | Open SOS map offline | Map displays | — |

## P0: three phones (3P)

For relay, place A and C apart with B between them; confirm on A's radar that C is reachable via B.

| # | Case | Steps | Expected | Markers |
| --- | --- | --- | --- | --- |
| 23 | Public relay | A sends public text | C receives via B | — |
| 24 | Private relay | A sends private text to C | C receives; A shows DELIVERED; B cannot read it | B: `Private relay next-hop dispatch`, `E2EE ROUTING` |
| 25 | No route | C Bluetooth off; A sends private text to C | B drops without broadcast; A Pending, then delivered after C returns | B: `Private relay route unavailable; dropping without broadcast` |
| 26 | Relay voice note | A sends a note over 4 KiB to C | Complete playback on C | C: `TRANSFER_REASSEMBLED` |
| 27 | Late joiner | C off; run 20 on A and B; turn C on | C converges | `INCIDENT_SYNC` `COMPLETE` |
| 28 | SOS on three | A raises SOS | Alarm on B and C | — |
| 29 | Route preserved | Bring C near A while relay via B is healthy | A keeps the routed path | `Deferring direct upgrade` |
| 30 | Middle drops | During 23, B Bluetooth off, then on | Clean loss, then recovery or direct link if in range | `Bootstrap setup started` |
| 31 | All close | All three together, restart apps | At most two direct links each, no loop | `Battery Master Election`, no repeated disconnects |

Not coverable with three phones: the three-direct-link capacity limit, orphan rescue and bridge retirement (four or more phones), and range or scale claims. DELIVERY-01 remains open.

## Failure indicators and report

FAIL on a crash or `FATAL EXCEPTION`, missing READY on either role, duplicate links to one peer, a connect/disconnect loop, Connecting stuck over 30 s, missing DELIVERED, an incomplete note or image, private text readable on the relay, disagreeing incident state or a missing SOS alarm.

Report the commit, APK hash, phone models and API levels, PASS/FAIL/UNTESTED per case, repeat counts for 3 and 14, and the capture folder and approximate time of any FAIL.
