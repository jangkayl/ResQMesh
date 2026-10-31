# Image quality: phone card

Status: active procedure. Confirms the WebP image pipeline, camera capture, quality levels and image limit on real phones. Local checks do not prove device behavior, and the 8, 16 and 32 KiB targets and their send-time cost are unmeasured until this card runs. The user reported Standard image quality as quite good, direct High sends as fast and hops as good; build, devices, timings, levels and counts were not supplied. Very High is unreported.

## Build identity

Branch `feat/image-quality`. Record the commit, version and APK hash used. For step 6, also record the older build used.

## Devices and setup

Use phones A, B and C on the same build, payload READY, with B between A and C for the relay steps. Have three photos ready: a bright detailed scene, a dark scene, and a portrait photo taken with the phone camera. Wait a minute between limit steps so the budget refills. The user installs, launches and operates the phones.

## Steps and expected results

1. **Picking:** on A, pick a full-size camera photo. Expect the preview to appear with no freeze or crash.
2. **Rotation:** send the portrait photo. Expect it upright on A and B.
3. **Quality:** send the bright and dark photos in community chat. Expect them visibly sharper than the previous build, both in the bubble and full screen.
4. **Private chat:** repeat steps 2 and 3 in a private chat from A to B. Expect complete images.
5. **Relay:** send one photo from A to C through B, public and private. Expect the image to arrive complete.
6. **Older build:** send a photo from the new build to a phone on a build without this change. Expect it to display.
7. **Measurement:** note the time from send to delivered for steps 3 to 5, against the previous build, with devices and hop count.
8. **Camera:** take a portrait and a landscape photo from the composer. Expect an upright, optimized image, and no file left in the app's `cache/captures` after sending.
9. **Camera cancel:** open the camera and back out. Expect nothing pending, and any earlier pending image kept.
10. **Level switch:** with an image pending, switch to High and back. Expect the hint to change and send to stay disabled while it re-encodes.
11. **High:** send a High image public and private, direct and through B. Expect it sharper than Standard, and record the times.
12. **Priority:** while a High image relays through B, send a text and raise a test SOS. Expect both to go through without a long delay.
13. **Limit, High:** send 4 High images in a row. Expect the 4th to show an image limit wait of about 20 seconds, and text to still send and arrive.
14. **Limit, Standard:** send 7 Standard images in a row. Expect the 7th to wait about 10 seconds.
15. **Multi-hop:** if four phones are available, send High on A→B→C→D. Keep any miss under DELIVERY-01.
16. **Very High switch:** select Very High from Standard and from High. Expect send to stay disabled while it encodes, then a complete image.
17. **Level comparison:** send the same photo at all three levels. Expect visible steps in sharpness, in the bubble and full screen.
18. **Private Very High:** send directly and through B, three times each, and record the times. Expect each to finish. A first hop over 60 s expires, so report any send that never completes.
19. **Very High priority:** while a Very High image relays through B, send a text and raise a test SOS. Expect both to go through without a long delay.
20. **Very High limit:** send 2 Very High images in a row. Expect the second to wait about 20 seconds, and text to still send.

## Failure indicators

Report FAIL for a crash, a freeze when picking, a sideways or mirrored image, a blank or missing image, a send time that feels much worse than the previous build, text or SOS blocked by an image, a limit that lets a 4th High through, or a private High image that never completes.

## Markers

Search `Image compression failed` in `MediaHelper` log lines, transfer sizes from `TRANSFER_` markers, and `AndroidRuntime` in the reported window.

## Report

Build identity, devices, per-step pass or fail, the step 7, 11 and 18 timings, and any bounded failure excerpt with no private content.
