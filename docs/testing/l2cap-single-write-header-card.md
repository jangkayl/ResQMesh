# L2CAP single-write header card

Branch `fix/l2cap-single-write-header`. Record the APK SHA-256, variant and install time on every phone with each result.

## Report that opened this

User report, 2026-11-04, release APK (hash not supplied): Very High images sent on SM-A236E, SM-P615, CPH2127 and V2424 at the same time left messages out of sync; some did not hop. Logcat only (release build, developer mode off, no exports):

- SM-P615 had one L2CAP link, to SM-A236E, up for the whole test.
- The SM-A236E–CPH2127 L2CAP link closed and reopened four times between 00:05 and 00:12.
- CPH2127 logged `l2c_lcc_proc_pdu: buffer length=6686 too big. max=56. Dropped`, closed the channel and then lost the BLE link (reason 0x08).
- 876 of 1,095 SM-A236E socket writes on that link were 1 byte: the frame length went out as four 1-byte writes, each its own SDU.
- V2424 prints no Bluetooth logs; its place in the topology is unknown.

The fix sends the 4-byte length in the same write as the first payload chunk. Wire bytes are unchanged, so mixed old/new builds interoperate. Whether it stops the CPH2127 stack drop or the hop misses is unproven.

## Setup

- Same APK (this branch) on all four phones; record each hash.
- Developer mode on every phone before the test so the in-app console keeps link and transfer markers.
- Same placement as the original report; peers READY.
- Optional: run `.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 10` during the test to keep CPH2127 Bluetooth stack logs.

## Steps

1. Send a short public text from each phone in turn. Note the time.
2. Send a Very High public image from all four phones at about the same time. Note the time.
3. Wait two minutes without touching Bluetooth or leaving the app.
4. On each phone, count which of the four images and texts arrived.
5. Repeat steps 2–4 twice.
6. Export from Dev Tools on all four phones and copy the files to the PC.

## Expected

- Every phone eventually shows all four texts and images, with no duplicates.
- Fewer L2CAP link drops between SM-A236E and CPH2127 than the four in the original report.
- No `too big ... Dropped` line in the CPH2127 Bluetooth log, if captured.

## Failure indicators

- A message missing on some phone after two minutes: note which sender, which receiver and the time.
- Repeated L2CAP close/reopen on one link, or `too big ... Dropped` again.
- App crash or the peer list stuck on connecting.

## Report

APK hash per phone, placement, send times, per-phone arrival counts for each round, number of link drops seen, exported Dev Tools files and capture folder (if any). Mark each step PASS, FAIL or UNTESTED.
