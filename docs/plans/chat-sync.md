# Chat sync plan

Approved by the user on 2026-11-01. Status: stages 1 and 2 local on `feat/chat-sync`; stages 3 and 4 wait for phone results from stage 2.

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
3. **Media sync (pending):** media follows after a 10-second settle, one message at a time, newest first, using `ReliableMeshTransfers` late copies. Limits are 10 per conversation and 1 MiB per neighbor. A Room migration adds sync state for placeholders, the `Synced · time` label, the `Synced history above` divider and the catch-up line, and stops synced rows counting as unread.
4. **SOS voice and images (pending):** allow media while an SOS is active and reuse `ChatInput`. All phones need the same build.

## Limits

- Public chat and SOS replies are unsigned, so a neighbor can forge synced history as it can forge live chat.
- Sync speed is unmeasured, and a phone gets only what direct neighbors hold.
- Until stage 3, messages with media do not sync, synced rows carry no label, and a new phone counts them as unread.
