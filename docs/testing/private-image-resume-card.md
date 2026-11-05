# Private image resume across hops card

Branch `fix/private-image-resume`. Record the APK SHA-256, variant and install time with each result.

## Report that opened this

User report, 2026-11-05, build not supplied: private images over a relay were laggy when a hop dropped and reconnected mid-transfer. All texts later arrived, but earlier images never did. The bubble bar reached 100%, fell to 0% and loaded again. A newer image was sent while an older one was still sending. Phones, topology and times were not supplied.

Source cause: after hop custody the sender gave the rest of the route only about 15 s. It then re-encrypted the image as a new transmission and uploaded it from piece 0, while the relay still held a durable copy. After three such retries the row was marked FAILED, and there was no retry.

## Setup

- Three phones A → B → C in a line, so A and C are not direct neighbors. Use a fourth phone D for step 3: A → B → C, with an alternative A → D → C. Same debug APK on all, trusted keys exchanged, developer mode on.
- Start with every link READY.

## Steps

1. A sends a private High image to C. Halfway through the bar, toggle B's Bluetooth off for 20 s, then on.
2. A sends a private High image to C. Once A shows "Reached next phone", toggle C's Bluetooth off for 60 s, then on.
3. With A → D → C also available, A sends an image to C, then power off B mid-transfer.
4. A sends image 1, then image 2 at once, then a text, all to C.
5. A sends an image to C and taps Cancel while it is sending; confirm.
6. If any image shows Failed during steps 1–5, bring the route back and tap Retry. If none failed, report Retry as not exercised. With no route at all the row stays Pending, not Failed.

## Expected

1. A's bar holds its percent while B is away and resumes from the same percent. C gets one image.
2. A stays on "Reached next phone" with no 0% restart. C gets one image after reconnecting, and A shows Delivered.
3. A shows "Resending via another route…" about 30 s after B disappears, and C gets the image through D.
4. Image 2 shows "Queued behind earlier image" until image 1 reaches the next phone. The text arrives without waiting for either image.
5. A's upload stops, the bubble is removed and later sends proceed. If Cancel came after "Reached next phone", the dialog warns that it may still be delivered.
6. Retry sends the image, and it is delivered.

## Failure indicators

- The bar falls back to 0% without a "Resending" label.
- Two copies of one image on C, or repeated `PRIVATE_RETRY` for the same message while the route is unchanged.
- An image stuck at Waiting after its first hop has gone and another route exists.
- A newer image completing before an older one to the same next phone, unless the older one is stalled.

## Markers

`TRANSFER_CUSTODY_ACK`, `TRANSFER_REPROBE`, `TRANSFER_SUPERSEDED`, `TRANSFER_STRANDED`, `TRANSFER_CANCELLED`, `PRIVATE_REROUTE`, `PRIVATE_RETRY`, `PRIVATE_DELIVERED`.

## Report

APK hash, phones and positions, step, send/arrival times, bar behavior, number of image copies on C, and marker counts per message.
