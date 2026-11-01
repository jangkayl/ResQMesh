# Text input hardening card

Prepared: 2026-11-01 on `fix/text-input-hardening`. Status: local checks only; no device evidence yet. Build the debug APK from the branch head and record its hash here before running.

The change:
- A name containing "unknown" is a normal name. Only a blank name or an exact placeholder with no node ID is not.
- Commas in names no longer split receipts or routes.
- Emoji are no longer cut in half in avatars, advertised names or limits.
- Replying to a multi-line message keeps the quote on one line.
- Invisible characters, direction overrides and long runs of stacked marks are removed. `#` is removed from new display names.
- Text from other phones is cleaned and limited locally, and relayed data stays unchanged.
- The map zip path check rejects a sibling folder sharing the same prefix.

Not changed: the wire format and the Room schema. Rows already saved with a split comma name are not repaired. Quotes can still be forged, and lookalike names are not detected. Live peer names in Radar and peer lists are not cleaned yet.

## Setup

Phones A, B and C on the debug APK, with C out of direct range of A if possible, so that B relays. Airplane mode on, then Bluetooth on. Keep one earlier-build phone available for step 8. Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 30`.

## Steps

1. **Unknown name:** set A's name to `Unknown Hero`. Expected: B lists A, and they exchange private messages both ways.
2. **Comma name:** set B's name to `Cruz, Juan`. A sends B a private message, and B opens it. Expected: one reader in the receipt, and the same after restarting A.
3. **Emoji name:** set C's name to start with an emoji, for example `😀Ana`. Expected: avatars show a whole emoji in chat, peers and incidents, with no box or `?`.
4. **Emoji messages:** send public and private messages with `😀`, `👨‍👩‍👧`, `🇵🇭`, `1️⃣` and `❤️`. Expected: they arrive unchanged, including over the relay.
5. **Emoji at the limit:** paste emoji past the 1,000-character limit. Expected: no broken character at the end.
6. **Multi-line reply:** send a two-line message and reply to it. Expected: the quote is one line, and only your reply is in the body.
7. **Invisible text:** paste only a zero-width space into a message and into a name. Expected: the message and name are refused as blank.
8. **Mixed builds:** with the earlier-build phone, create an incident and offer help from each build, then raise and end an SOS. Expected: all phones agree, and sync stops repeating.
9. **Older map package:** reinstall the offline map. Expected: it extracts and displays normally.

## Failure indicators

- A peer is missing, or stays "connecting", because of its name.
- Reader counts double, or names split at a comma.
- A box, `?` or half an emoji appears.
- A reply body contains the quoted message's second line.
- `INCIDENT_SYNC` repeats without completing, or signed events are rejected.
- Text differs between sender and receiver for normal messages.

## Report

APK hash, phone models, topology, which steps passed, and for failures the time plus `INCIDENT_SYNC` and `SOS_SYNC` markers. Do not share private message text.
