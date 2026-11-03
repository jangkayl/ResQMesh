# BLE range probe and Range Boost card

Prepared: 2026-11-03 on `fix/ble-range-probe-scan`. Status: local checks only; no device evidence yet. The user installs and operates the phones.

Build identity: record the branch head commit, the debug APK hash and each phone's model and Android version. Missing items stay unspecified.

## Purpose

1. Find out which test phones support BLE long range (LE Coded PHY), to decide whether adaptive Coded PHY is worth building.
2. Check that Range Boost (aggressive scanning while alone or during an own SOS) improves edge discovery without breaking the mesh.

Range Boost changes only the scan mode. Advertising, GATT, routing and delivery are unchanged, and connection range itself is not extended.

## Setup

Two to three phones with the debug build. Start `scripts\device\capture_ble_logcat.ps1` on phone A. Range Boost is on by default (Profile, Bluetooth Range Boost).

## Capability probe

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| C1 | On each phone open Profile, About | One line shows "Bluetooth long range: Supported / Not supported / Unavailable" and "2M PHY" | Line missing; app closes |
| C2 | Start the mesh on A | One `BLE_PHY` line with `codedPhy`, `phy2M`, `extAdv`, `maxAdvLen`, `api`, `model` | No line; repeated lines in one process |

## Scan mode

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| S1 | A alone, mesh on | `BLE_MESH` `Scan mode=LOW_LATENCY reason=no_peers` | BALANCED while alone; scan failures |
| S2 | Bring B in range until READY | At the next scan restart, `Scan mode=BALANCED reason=peers_present` | Stays LOW_LATENCY with a ready peer |
| S3 | Raise an SOS on A | Next restart shows `reason=sos` | No change; SOS delivery regresses |
| S4 | End the SOS; switch Range Boost off | Next restart shows `reason=boost_off`, BALANCED | LOW_LATENCY with boost off |
| S5 | Across S1–S4, send texts A↔B | Delivery and receipts as before | Missed texts; connect failures |

## Edge cases

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| E1 | With B READY, raise SOS on A | Next restart shows `reason=sos` despite the peer | `peers_present` while SOS active |
| E2 | Walk B out of range until the link drops; wait 30 s | A returns to `reason=no_peers` | Stays BALANCED while alone |
| E3 | Toggle Range Boost while the mesh runs | Change applies at the next scan restart, not instantly; no extra restarts | `Scanner failed` (code 6 throttling) |
| E4 | Toggle Range Boost on/off 10 times quickly | No crash, no scan failures | Crash; repeated `Scanner failed` |
| E5 | Set Range Boost off, force-stop and reopen the app | Switch is still off; `reason=boost_off` | Resets to on |
| E6 | Bluetooth off then on while alone | Mesh recovers; `reason=no_peers` | No scanning after restore; crash |
| E7 | Revoke Nearby devices permission, open About, restore it | About line still shows; mesh resumes after restore | Crash; About line missing |
| E8 | Go offline, then online | Scan mode logged again on restart | No scanning after online |
| E9 | Screen off with background mesh on, 5 minutes | Mesh stays up; texts still arrive | Link lost only with boost on |
| E10 | Three phones A–B–C, A with two READY links | `reason=peers_present` | LOW_LATENCY with links |
| E11 | Any Android 7 phone (if available) | About shows "Unavailable"; `BLE_PHY` shows `unavailable(api=…)` | Crash on About |
| E12 | Regression: A→B→C public text, private text, voice note, SOS | Same delivery and receipts as before | Any new miss |

## Walk-apart (optional, repeat 3 times each)

With line of sight, walk B away from A in fixed steps (for example 5 m). Record the last distance where B is still discovered and reaches READY, with Range Boost on and then off. Note the environment.

## Battery (optional)

A alone for 30 minutes with Range Boost on, then off. Record the battery percentage drop for each.

## Markers and report

Search `BLE_PHY`, `BLE_MESH` `Scan mode`, and `BLE_RECOVERY` `Scanner failed`. Report the build identity, each phone's About line, step results, distances and battery deltas. Do not include private message text.
