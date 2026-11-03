# Adaptive connect timeout card

Branch `fix/adaptive-connect-timeout` (on top of `fix/l2cap-single-write-header`). Record the APK SHA-256, variant and install time on every phone with each result.

## Report that opened this

User report, 2026-11-04, release APK `147c0914…` on SM-A236E, SM-P615, CPH2127 and V2424: the vivo tried to connect to the OPPO several times and took about three minutes; afterwards images were split and out of sync between phones. Logcat only (release build, no exports; V2424 prints no Bluetooth logs, CPH2127 had none before 00:54):

- The mesh started at 00:46:56 (both Samsungs began scanning within a second).
- SM-A236E tried the vivo four times. One failed after 0.6 s; three ended with status 133 at exactly 5.0 s, the app's fixed client connect deadline (`CONNECT_TIMEOUT_MS`). The vivo never linked to the A236E.
- The resulting chain was SM-P615 — SM-A236E — CPH2127 — V2424. The A236E–CPH2127 L2CAP channel closed at 00:49:59, 00:50:39, 00:53:12, 00:53:37 and 00:54:31; CPH2127 logged `too big ... Dropped` again at 00:54:30.

The fix keeps 5 s for each peer's first attempt. Each consecutive connect timeout adds 5 s to that peer's next deadline (max 15 s, under the 25 s connect-lock backstop); a completed connect resets it. The deadline is keyed by stable peer identity, not by address. The CPH2127 L2CAP drops are not addressed.

## Setup

- Same APK (this branch) on all four phones; record each hash.
- Prefer a debug APK on every phone so Logcat keeps app markers; otherwise turn developer mode on everywhere and export from Dev Tools afterwards.
- Run `.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 10` during the test.
- Same placement as the original report. Start the mesh on all four phones within a few seconds; note the time.

## Steps

1. Note the time each phone first shows the vivo as connected (and the vivo shows each peer).
2. Repeat from a cold start (close the app on all four, reopen) twice more.
3. After all links settle, send one Very High public image from each phone at about the same time.
4. Wait two minutes without touching Bluetooth or leaving the app; count which images arrived on each phone.

## Expected

- The vivo links to at least one peer within about 30 s of mesh start, without minutes of repeated attempts.
- Logcat (debug) shows `timed out after 5000ms; next deadline 10000ms`, then a longer deadline, rather than repeated 5000 ms timeouts for the same peer.
- Every phone eventually shows all four images, with no duplicates.

## Failure indicators

- The vivo still takes minutes to link, or `timed out after 15000ms` repeats for the same peer.
- A different peer is visibly delayed while the vivo attempt holds the connect lane.
- Images missing on some phone after two minutes: note sender, receiver and time.

## Report

APK hash per phone, placement, mesh start time, time to first vivo link per run, connect-timeout lines (debug) or exports, per-phone image counts, capture folder. Mark each step PASS, FAIL or UNTESTED.
