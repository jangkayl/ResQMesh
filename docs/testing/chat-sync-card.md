# Chat sync: phone card

Status: active procedure for [stage 2](../plans/chat-sync.md), text sync. Local checks do not prove device behavior.

## Build identity

Branch `feat/chat-sync`. Record the commit, version and APK hash used. All phones need this build.

## Devices and setup

Use debug builds on phones A, B and C. C is the late joiner, with its app fully closed. Set clocks automatically. The user installs, launches and operates the phones.

## Steps and expected results

1. **Latest 50:** send 60 short Community texts between A and B. Start C. Expect the newest 50 on C within about a minute, in sent order, with original times. Note the time from READY to the last message.
2. **Second neighbor:** close B, restart C near A only. Expect nothing new and no duplicates.
3. **Live during sync:** repeat step 1 with fresh messages, and send a text and an SOS from C during sync. Expect both to arrive without waiting for sync to finish. Report the times.
4. **Baseline:** with no missed history, send a text from C right after READY. Compare with step 3.
5. **SOS replies:** raise an SOS on A, reply from B, then start C. Expect the alert, then the replies in that SOS thread.
6. **Ended SOS:** end the SOS, then start a phone that missed it. Expect the replies in a read-only thread.
7. **Silence:** expect no notification, sound or SOS re-alarm from synced text.
8. **Private and Radio:** send private messages and Radio notes while C is closed. Expect no private content on C, and Radio only through the 10-minute catch-up.
9. **Media:** send a voice note and an image while C is closed. Expect no sync copy at this stage; recent ones can still arrive by the 10-minute hold.
10. **Reconnect:** turn Bluetooth off on C, send texts from A, turn it on. Expect the missed texts within about 40 seconds, with no duplicates.
11. **Blocked sender:** block a sender on C. Expect its messages not to sync.

## Failure indicators

Report FAIL for private content on another phone, duplicate rows, a crash, an alert or notification from synced text, live messages held until sync ends, or a sync that never finishes.

## Markers

Search `CHAT_SYNC` (`pass peer=`, `pending peer=`), `SYNC_COALESCED` and `AndroidRuntime` in the reported window.

## Report

Build identity, devices, per-step pass or fail, the timings from steps 1, 3 and 4, and any bounded excerpt with no private content.
