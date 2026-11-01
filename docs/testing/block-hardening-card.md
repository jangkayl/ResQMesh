# Block hardening card

Status: pending. Build identity: record the debug APK hash and the `fix/block-hardening` commit.

## Setup

Three phones, A, B and C, all on the same build. A and C are in radio range of each other, and B reaches both. Start with no blocks. Capture Logcat on A and C. Compilation does not prove these steps.

## Steps

| # | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| 1 | A blocks C. | Direct link drops within about 3 s. C shows "They blocked you". | A/C stay READY, or C has no block row. |
| 2 | Wait 90 s. | No A/C READY link. Each shows the other in red as Blocked · Relayed on Network, Home and graph. | A direct link returns, or the row is not red. |
| 3 | Exchange public and private text with receipts between A and C through B. | Arrives once, receipts return, chat header shows the blocked tag. | Message or receipt missing. |
| 4 | Remove B and watch 2 minutes. Restore B. | Chat says it is waiting for a relay, rows show Blocked · Nearby, then queued text arrives. | Repeated connect and disconnect on A or C. |
| 5 | Restart A and C. | Still blocked, no direct link. | Direct READY after restart. |
| 6 | Release on A only, then on C. | A shows the one-sided note and no link forms. After C releases, a link may form. | Link forms after one release, or none after both. |
| 7 | With two phones only, block while the peer is off, then bring it in range. | The peer learns the block, with no sustained reconnect loop. | Sustained reconnect loop. |

## Markers

Grep a bounded window for `block acknowledgement received`, `block request acknowledged`, `Identity gate rejected`, `Skipping GATT connect to blocked peer`.

## Report

Step, repetition, APK, phone models, time, expected and actual result, and capture folder. If step 4 or 7 shows sustained reconnecting, record it: that is the trigger for the conditional reconnect backoff. Mark unavailable steps UNTESTED.
