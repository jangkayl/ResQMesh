# Late relay pacing: phone card

Status: active procedure. Checks that catch-up voice notes stay behind live sends on a newly joined phone. The user reported the delay; its cause is unproven. Local checks do not prove device behavior.

## Build identity

Branch `fix/late-relay-pacing`. Record the commit, version and APK hash used. Compare against the previous build if step 5 is slow.

## Devices and setup

Use debug builds on phones A, B and C in a line, A→B→C, so B relays. Keep phone D on the same build with its app stopped. Set all clocks automatically before going offline. The user installs, launches and operates the phones.

## Steps and expected results

1. **Build a backlog:** send five 30-second public voice notes from A within two minutes. Expect them on B and C.
2. **Join:** start D within the 10-minute hold, near B. Note the time D becomes READY with B.
3. **Settle:** watch B's log. Expect no `event=late` send to D for about 10 seconds after READY, and `D:settling` meanwhile.
4. **One at a time:** expect the newest note on D first, then older ones, with only one transfer to D at a time.
5. **Send during catch-up:** as soon as D is READY, send text and then a short voice note from D. Time each until it appears on B. Repeat after the catch-up ends. Report both times.
6. **No hold:** repeat steps 2 and 5 with a new join more than 10 minutes after the last note. If this is as slow as step 5, the backlog is not the main cause.
7. **Radio autoplay:** with D monitoring the same channel, repeat with Radio notes older than two minutes. Expect them in history at their original send time with no autoplay. A new Radio note should autoplay.
8. **Reconnect:** turn Bluetooth off on D, send a note from A, then turn it back on within 10 minutes. Expect the note on D after the settle.

## Failure indicators

Report FAIL for a late note never reaching D within its window, duplicates in the chat, sends blocked until catch-up ends, old Radio notes autoplaying, new Radio notes not autoplaying, a crash, or a stuck transfer.

## Markers

Search `RELAY_ROSTER` (`event=late`, `settling`, `busy`), `TRANSFER_RETAINED relay=true`, `TRANSFER_CUSTODY_ACK` and `TRANSFER_OVERFLOW` in the reported window. Use `AndroidRuntime` for crashes.

## Report

Build identity, devices, hop layout, per-step pass or fail, the step 5 and step 6 timings, and any bounded excerpt with no private content.
