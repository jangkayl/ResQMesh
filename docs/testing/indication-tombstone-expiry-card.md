# Indication tombstone expiry card

Branch `fix/indication-tombstone-expiry` (on top of `fix/adaptive-connect-timeout`). Record the APK SHA-256, variant and install time on every phone with each result.

## Report that opened this

User report, 2026-11-04, release APK `42d958b2…` on SM-A236E, SM-P615, CPH2127 and V2424: the vivo kept trying to connect to the OPPO and took minutes; the vivo's developer console showed `TRANSFER_STORAGE_FAILED` and `TRANSFER_HASH_FAILED`. CPH2127 Bluetooth log (no app markers, release build):

- 01:29:22: the link to `..EF:C1` (very likely the vivo) dropped (reason 0x08) while CPH2127 was sending it a notification (`Unknown connection_id ... fail sending notification`); no completion callback followed.
- 01:29:28–01:31:07: `..EF:C1` reconnected seven times; each time CPH2127's app called `cancelConnection()` about 20 ms later, the stack failed the cancel (`bta_gatts_cancel_open: failed`), and the vivo dropped the half-open link about 11 s later (reason 0x13).
- 01:31:16: CPH2127 connected outbound to the vivo on a new address, ending the loop.

Inferred cause: the server-indication tombstone (`SERVER_CALLBACK_WAIT`) never cleared, because server renewal waits while another peer (SM-A236E) is healthy. The marker was not visible (release build); other rejection paths do not fit (one other link, same address). Tombstones now expire 10 s after issue (above the 4 s chunk timeout). The transfer hash/storage failures are a separate corruption issue and are not addressed here.

## Setup

- Same APK (this branch) on all four phones; record each hash. Prefer a debug APK on every phone so Logcat keeps `SERVER_CALLBACK_WAIT` and transfer markers.
- Run `.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 10` during the test.
- Same placement as the original report; all four phones linked.

## Steps

1. While images are flowing between the OPPO and the vivo, walk the vivo out of range (or cover it) until its link drops, then bring it back. Note both times.
2. Repeat step 1 three times.
3. Send one Very High public image from each phone; wait two minutes and count arrivals per phone.
4. Export Dev Tools on the vivo and the OPPO.

## Expected

- After each return, the vivo relinks within about 15 s; at most one `SERVER_CALLBACK_WAIT` window (≤10 s) per drop, not minutes of rejections.
- No repeated `cancelConnection()` for the vivo's address in the OPPO log beyond 10 s after a drop.
- Every phone eventually shows all four images, with no duplicates.

## Failure indicators

- Rejections of the same address continue for more than 10 s after a drop.
- A chunk completes on a new link immediately after a drop with no new notification sent (late callback misattributed).
- `TRANSFER_HASH_FAILED` counts per phone (record them; separate issue).

## Report

APK hash per phone, placement, drop/return times, relink time per cycle, `SERVER_CALLBACK_WAIT` lines (debug) or OPPO Bluetooth log excerpt, hash/storage failure counts, per-phone image counts, exports and capture folder. Mark each step PASS, FAIL or UNTESTED.
