# BLE long range (Coded PHY) and edge tolerance card

Prepared: 2026-11-13 on `feat/ble-coded-phy`. Status: local checks pass (unit tests, lint, detekt). On 2026-11-13 the user reported long range working; devices, distances and individual step results were not supplied. The user installs and operates the phones.

Build identity: record the branch head commit, the debug APK hash and each phone's model and Android version. Missing items stay unspecified.

## What changed

- Heartbeat: a link is retired after 3 consecutive missed ACKs (about 24 s), not 1. Inbound traffic or an ACK resets the count.
- Scan mode (Range Boost on): LOW_LATENCY while alone, during an own SOS, or while a direct-link slot is free (`reason=free_slots`). BALANCED only once all 3 slots are READY (`reason=links_full`).
- Connect timeout starts at 8 s (was 5 s), still growing to 15 s per slow peer.
- Coded PHY, only on phones whose controller supports it:
  - a second connectable long-range advertisement next to the normal one;
  - scanning on all PHYs;
  - connects to long-range-only addresses allow Coded;
  - links request Coded S=8 when link RSSI is −75 dBm or weaker, or on the first missed heartbeat.
- Phones without Coded support behave as before, apart from the heartbeat, scan and timeout changes above.

Coded links run at 125 kbit/s, so images and voice notes are slower on them. Links do not switch back to 1M until they reconnect.

## Setup

Two phones with this debug build: A and B. Run the [range probe card](ble-range-probe-test-card.md) steps C1 and C2 first, and record each phone's `codedPhy` and `extAdv`. Start `scripts\device\capture_ble_logcat.ps1` on A.

## Steps

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| P1 | Start the mesh on A | `BLE_PHY adv=coded` on a Coded-capable phone, otherwise `adv=legacy-only reason=…` | No `BLE_PHY adv` line; `Advertising failed` |
| P2 | B near A until READY | `BLE_PHY link=CLIENT … rssi=…`; no Coded request while RSSI is above −75 | `Scanner failed`; READY never reached |
| P3 | With A and B READY, check scan mode | `Scan mode=LOW_LATENCY reason=free_slots` | BALANCED with a free slot |
| P4 | Walk B away slowly and keep sending texts | On a Coded pair: `request=CODED_S8`, then `BLE_PHY link=… tx=CODED rx=CODED` | Link drops with no Coded request on a Coded pair |
| P5 | Briefly cover B with your body or step behind a wall, then return | `heartbeat ACK missed (1/3); retrying`, then the link recovers | Retired after the first miss |
| P6 | Keep B out of range | `heartbeat ACK missed (3/3); retiring` about 24 s after silence is noticed | Link never retires; stuck "connected" row |
| P7 | Restart B's mesh while out of 1M range but still in Coded range | B is discovered; `Connecting to … with 1M|CODED initiator phys`; READY | Never discovered; repeated connect timeouts |
| P8 | Mixed pair: one phone without Coded support | Connects on 1M as before; no new failures | Connect or delivery regression |
| P9 | Regression: public text, private text, voice note, SOS A↔B | Same delivery and receipts as before (voice slower on a Coded link) | Any new miss |

## Walk-apart (repeat 3 times per environment)

Run it with line of sight, then through one wall, then through a crowd or trees. Walk B away from A in 5 m steps. At each step, send one text and record whether it arrived.

For each environment, record:
- the last distance where B is discovered;
- the last distance where B reaches READY;
- the last distance where a text is delivered;
- the PHY in use.

Compare against the previous build, or against a pair without Coded support.

## Battery (optional)

A with one READY peer for 30 minutes, Range Boost on. Record the battery drop and compare with the [range probe card](ble-range-probe-test-card.md) battery result.

## Markers and report

Search for these markers:
- `BLE_PHY`
- `BLE_MESH` `Scan mode`
- `heartbeat ACK missed`
- `BLE_RECOVERY` `Scanner failed` / `Advertising failed`

Report:
- the build identity;
- each phone's `codedPhy`/`extAdv`;
- step results;
- distances per environment;
- battery deltas.

Do not include private message text.
