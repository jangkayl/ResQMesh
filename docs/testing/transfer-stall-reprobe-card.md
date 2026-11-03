# Transfer stall re-probe and progress card

Branches `fix/transfer-stall-reprobe` and `feat/transfer-progress`. Record the APK SHA-256, variant and install time with each result.

## Report that opened this

User report, 2026-11-03, release APK (hash not supplied): a direct private image SM-A236E→CPH2127 waited a long time and arrived only after the link disconnected and reconnected. The CPH2127 Bluetooth log showed L2CAP congestion, then the channel closing about 20 s after each reopen with a 30 s gap. No app markers (release build); the A236E log was not captured. The stall re-probe addresses the transfer waiting for a reconnect; the link congestion cause is unproven.

## Results

- 2026-11-03, user report, release APK `app-release.apk` built 22:35, SHA-256 prefix `355d74f9421c5743` on CPH2127, V2424 and SM-P615 (SM-A236E not on ADB; its build unverified): images send fast. CPH2127 Bluetooth log 22:35–22:49: two L2CAP drops after congestion bursts; the next channel reopened 1.2 s after a drop (30 s before). No crashes. App markers not in Logcat (release); card steps not run.

## Setup

- SM-A236E (sender) and CPH2127 (receiver), same APK on both, trusted keys exchanged.
- Developer mode on both phones so the in-app developer console keeps transfer markers. Release builds do not print app markers to Logcat; use a debug APK if a Logcat capture is needed.
- Start 1–3 m apart with the peer shown READY.

## Steps

1. Send a private Standard image A236E→CPH2127. Note send time and arrival time.
2. Repeat with High, then Very High quality.
3. Move to 8–15 m (or behind a wall) until text still arrives but slowly; send a Very High image. Do not toggle Bluetooth or leave the screen.
4. Repeat step 3 three times.

## Expected

- Each image arrives without a manual disconnect/reconnect.
- If the receiver goes silent mid-transfer, the sender console shows `TRANSFER_REPROBE` within about 10 seconds, followed by `TRANSFER_CUSTODY_ACK`.
- No duplicate image bubble on the receiver.

## Failure indicators

- Image only arrives after Bluetooth toggle, app restart or peer reconnect.
- Repeated `TRANSFER_REPROBE` for the same transfer with no `TRANSFER_CUSTODY_ACK` while text still flows.
- Duplicate bubbles or a broken image.

## Report

APK hash, phones, distance, quality level, send/arrival times per attempt, count of `TRANSFER_REPROBE` lines per transfer, and whether a reconnect was needed.

## Progress display (`feat/transfer-progress`)

Same setup. Developer mode on adds a "resent · re-checked" line under the bar.

1. Private image A236E→CPH2127, direct: the sender bubble shows a bar with "Sending n/N · p%", then "Received by phone", then "Delivered"/"Read" once receipts arrive.
2. Turn the CPH2127 Bluetooth off mid-send: the bubble shows "Waiting for link"; turn it back on and the count resumes without restarting at 0.
3. Range-limited send (step 3 above): if the bar stops, it reads "Stalled, re-checking…" within about 10 seconds, then continues.
4. Private image through a relay phone: the bubble ends at "Reached next phone" until the delivery receipt, never "Delivered" early.
5. Community image with two neighbors: the bar combines both and ends at "Sent to nearby phones".
6. Text-only messages and the receiver's bubbles show no bar.

Failure indicators: the bar moves backwards, "Delivered" before a receipt, a bar stuck on a delivered message, a bar on the receiver side, or chat scrolling lag while a bar updates.

Report: which steps showed which labels, the final n/N and any resent/re-checked counts.
