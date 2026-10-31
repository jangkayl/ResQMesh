# Relay image delivery: phone card

Status: active procedure. Measures whether public images survive a brief loss of C or of the A link on A–B–C. Local checks do not prove device behavior, and passes here do not close DELIVERY-01.

## Build identity

Two debug builds, because markers are debug-only:

- **Pre-fix:** `17536b9`.
- **Fix:** branch `feat/image-quality`, which also carries the camera and image-quality work.

Record the commit, version and APK hash of each. Install one build on all three phones for a run. Debug and release signatures differ, so stay on debug.

## Devices and setup

Phones A, B and C, all payload READY in a line. Keep A and C out of direct range, or use Block between A and C if range cannot separate them. Confirm the topology on the peer list before each case. Use the same Community channel on all phones and the same image, about 100 KB or larger so it travels as pieces. The user installs, launches and operates the phones.

Start a capture on B and C for each run:

```powershell
.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 10
```

## Steps and expected results

Run cases 1–3 ten times each on both builds and fill in the table. Run cases 4–7 on the fix build only.

| Case | Sent | Arrived at C, pre-fix | Arrived at C, fix |
| --- | --- | --- | --- |
| 1 Steady link | | | |
| 2 C late | | | |
| 3 A leaves | | | |

1. **Steady link:** send from A with all links READY. Expect the image on B and C on both builds. A lower fix count is a regression.
2. **C late:** on C press Go offline. Send from A and wait until B shows the image. Bring C back within two minutes and wait for READY. Fix: expect the image on C with `event=late chosen=<C>` on B. Pre-fix: the image is expected to stay missing.
3. **A leaves near completion:** send from A, then press Go offline on A late in the send, varying the moment between repeats. Fix: expect the image on B and C without A returning. Timing is manual, so also record cases where A left too early and the transfer stayed partial; those are not failures.
4. **No repeat:** after case 2, take C offline and back within the window. Expect one image row on C, and `C:forwarded` in a later `event=late` line, with no new transfer to C.
5. **Restart:** repeat case 2, but stop and relaunch the app on B while C is offline. Bring C back. Expect the image on C.
6. **Window end:** repeat case 2, but leave C offline for 11 minutes. Expect `event=expired` on B and no image on C.
7. **Regressions:** send short public text and one private image from A to C. Expect normal arrival and no `RELAY_ROSTER event=late` line for the text.

## Failure indicators

Report FAIL for a crash, a duplicate row on C, a case 2 or 5 miss on the fix build, an image arriving after case 6, repeated transfers to one peer, or fewer steady-link arrivals than pre-fix.

## Markers

Search these in the reported window, not the whole capture:

- `RELAY_ROSTER` with `event=first`, `late`, `expired`, `evicted` or `unreadable`. Skip reasons are `sender`, `visited`, `forwarded`, `not_ready` and `queue_full`. Pre-fix builds have no such marker.
- `TRANSFER_REASSEMBLED`, `TRANSFER_RETAINED`, `TRANSFER_CUSTODY_ACK`, `TRANSFER_EXPIRED`, `TRANSFER_OVERFLOW`, `TRANSFER_STORAGE_FAILED`.
- `RL_DROP`, `RL_COOLDOWN`, `AndroidRuntime`.

A miss with C in `not_ready` throughout the window, while the link looked healthy, is the evidence to report for the unproven heartbeat and liveness hypothesis. Do not change those behaviors from this card.

## Report

Both build identities, phone models and API levels, how A and C were separated, the filled table, per-case pass or fail, failure times and capture folders. Do not include image content.
