# Chat sync plan

Approved by the user on 2026-11-01. Status: all four stages local on `feat/chat-sync`; phone results pending. The user asked to build stages 3 and 4 before a full stage 2 card.

## Goal

Late or new phones should receive recent Community and SOS chat without slowing live traffic. Text arrives first. Voice notes and images follow in the background, within a smaller cap.

## Decisions

- Sync the newest 50 messages per conversation, for Community and up to eight SOS threads.
- Private chat and Radio are not synced. Radio keeps the 10-minute hold.
- Sync runs between direct neighbors and is never relayed onward.
- Synced rows keep sender time and are silent: no autoplay, notification or delivered receipt.
- Replies to a known alert are accepted even after it ends. Replies to an unknown alert wait for a later cycle.

## Stages

1. **Sender time (done on `fix/late-relay-pacing`):** received conversation rows keep sender time, capped at local time.
2. **Text sync (local):** `ChatSyncCoordinator` reads a neighbor's index on READY, when known alerts change and every 30 seconds, then pulls missing text rows by id. Rows are accepted only when requested on that link and matching their index entry. Pages stay under about 3.5 KiB, except one longer message.
3. **Media sync (local):** `ChatMediaPuller` follows after a 10-second settle, one message at a time, newest first, using `ReliableMeshTransfers` late copies. Limits are 10 per conversation and 1 MiB per neighbor. Copies carry `chatSync`, skip the origin limit and are stored only if requested from that neighbor. Room 12 adds sync state for placeholders, the `Synced · time` label, the `Synced history above` divider and the catch-up line. Synced rows skip unread counts and seen receipts. A live or late copy also fills a waiting placeholder.
4. **SOS voice and images (local):** media is allowed while an SOS is active, through `ChatInput` with the same image quality choice and credits as Community, and no location button, because the alert carries location. All phones need the same build.

## Limits

- Public chat and SOS replies are unsigned, so a neighbor can forge synced history as it can forge live chat.
- Sync speed is unmeasured, and a phone gets only what direct neighbors hold.
- A placeholder stays on downloading until a neighbor holding the attachment connects.
- Community media can transfer twice when the 10-minute hold and sync overlap; the row still shows once.
