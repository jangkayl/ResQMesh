# Chat sync: phone card

Status: active procedure for [chat sync and SOS media](../plans/chat-sync.md). Local checks do not prove device behavior.

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
9. **Own images:** with C closed, send one high-resolution image from A and one from B. Start C next to B only. Expect both images, each showing `Image · downloading…` first and then filling in place.
10. **Past 10 minutes:** repeat step 9, starting C more than 10 minutes later. Expect both images.
11. **Media cap:** send 12 images in Community with C closed. Expect the newest 10 to fill and two rows showing `Image not synced`.
12. **Live first:** during step 11, send a text and a short image from C. Expect them ahead of older images. Report both times.
13. **Late labels:** expect grey `Synced · time` on synced rows, one `Synced history above` divider, `Catching up · 3 of 10` until media ends, no unread badge and no chat jump.
14. **SOS media:** in an active SOS, send a voice note, gallery image and camera photo from each side. Expect all on the other phones. Expect no location button or image quality choice, and a read-only thread after the SOS ends.
15. **Upgrade:** install over an existing build. Expect old chat history intact with no `Synced` labels.
16. **Reconnect:** turn Bluetooth off on C, send texts from A, turn it on. Expect the missed texts within about 40 seconds, with no duplicates.
17. **Blocked sender:** block a sender on C. Expect its messages not to sync.

## Failure indicators

Report FAIL for private content on another phone, duplicate rows, an image in the wrong row, a placeholder stuck while its holder is connected, a crash, an alert or notification from synced text, live messages held until sync ends, or a sync that never finishes.

## Markers

Search `CHAT_SYNC` (`pass peer=`, `pending peer=`, `media filled`), `TRANSFER_RETAINED`, `SYNC_COALESCED` and `AndroidRuntime` in the reported window.

## Report

Build identity, devices, per-step pass or fail, the timings from steps 1, 3, 4 and 12, and any bounded excerpt with no private content.
