# GATT frame integrity card

Branch `fix/gatt-frame-integrity`, built on `fix/private-image-resume`. Record the APK SHA-256, variant and install time with each result.

## Report that opened this

2026-11-06 debug capture, APK `0340CF294E0D823B` on CPH2127 (Android 12), SM-A236E and SM-P615 (Android 13). Tablet → A236E → CPH2127, private image of 45 KB: the tablet → A236E hop took 8 s, but A236E → CPH2127 took 48 s. Between 02:00:17 and 02:01:06 the CPH2127 logged 7 `TRANSFER_HASH_FAILED`, 5 `TRANSFER_STORAGE_FAILED direction=inbound` and repeated `Could not parse incoming payload`; the other phones logged none. The first failure happened before L2CAP was ready.

Source cause: GATT frame reassembly has no integrity check or resync, so a lost or duplicated chunk produces a length-correct wrong frame. A piece accepted on size alone poisoned the transfer until the whole-transfer hash failed. What loses the chunk on the CPH2127 is unproven.

## Setup

- Same debug APK on CPH2127, SM-A236E and SM-P615, trusted keys exchanged, developer mode on.
- Tablet and CPH2127 far enough apart that the route goes through A236E. Start with all links READY.

## Steps

1. Send 5 private High images, one at a time, tablet → CPH2127 through A236E. Note send and arrival times.
2. Send 3 more quickly in a row.
3. Repeat step 1 in the other direction, CPH2127 → tablet.
4. Regression: private and public text, a voice note, a public image and an SOS raise and end, across the same hop.
5. Pull Logcat from all three phones.

## Expected

- Each relay → CPH2127 leg finishes in about the same time as the tablet → relay leg, with no 40–50 s stalls.
- `TRANSFER_PIECE_CRC_FAILED` may appear on CPH2127; each is followed by the image completing without `TRANSFER_HASH_FAILED`.
- `GATT_RESYNC` may appear; text, voice, SOS and public traffic still arrive.
- One copy of each image on the receiver.

## Failure indicators

- `TRANSFER_HASH_FAILED` still repeating for one transfer.
- An image never completes while `TRANSFER_PIECE_CRC_FAILED` repeats for the same index (the sender's checksum is wrong, or the corruption is systematic).
- Text, voice, SOS or receipts missing after a `GATT_RESYNC`.

## Markers

`TRANSFER_PIECE_CRC_FAILED`, `TRANSFER_HASH_FAILED`, `GATT_RESYNC`, `GATT_RX_STATS`, `TRANSFER_REPROBE`, `TRANSFER_CUSTODY_ACK`, `PRIVATE_DELIVERED`.

## Report

APK hash, phones, positions, per-image send/arrival times per leg, marker counts per phone, and any regression in step 4.
