# Security hardening plan

Approved by the user on 2026-10-17. Status: Phase A user-reported PASS. Phase B was implemented locally on 2026-10-18 on branch `feat/security-phase-b`, with no device evidence yet. Phases C–E wait for the demo freeze to end. Tracked as SEC-01 in [status](../status.md).

## Threat model

The attacker is a malicious nearby phone: a modified app or BLE tool that floods, spoofs, replays or sends malformed payloads. A buggy peer is covered by the same bounds. Lost or seized phones and hostile local apps are secondary; the at-rest and intent work below addresses them partly. No forward secrecy is claimed.

## User decisions

- Phased: local-only fixes before the freeze (about 2026-10-28); wire, queue, routing and crypto changes after it.
- One wire break (protocol v2, every test phone reinstalled together), then compatible changes only once the pilot beta starts.
- Over-limit traffic is dropped, and an origin that keeps exceeding gets a cooldown. Limits charge the **origin** node, never the relay that forwarded the traffic. Per-link limits only shape traffic and never put a relay in a timeout.
- Defaults:
  - Per origin: 20 chat messages/min (burst 10), 4 new SOS burst, refill 1/min (SOS updates are a separate class), 6 sync requests/min, 30 presence pulses/min.
  - Cooldown of 60 s after 3 violations.
  - Per link: 64 frames/s and 256 KiB/s.
- Typed messages max 1000 characters; received media capped at 2 MB; developer mode in debug builds only.
- SQLCipher with a Keystore-wrapped raw key; media deleted after 30 days, text kept.
- Notifications keep showing private text; no `FLAG_SECURE`.
- Crypto v2: X25519 + Ed25519 through Google Tink.
- Key verification is optional: QR code plus safety number. Trust-on-first-use and relaying keep working without it.

## Phase A: local, before the freeze (implemented)

1. `core/security/input/TextPolicy` is the single source of truth for field limits. It sanitizes control and bidi characters, normalizes to NFC, and backs both the UI caps and counters and the domain guards (`PrivateSendResult.TooLong`, `IncidentCommands`, `LocalIdentityManager`, the `MeshOutboundDelivery` final guard with an `INPUT_REJECT` marker). A send debounce stops double sends.
2. `BinaryCompressor.decompressBounded` inflates received media incrementally and aborts past 2 MB (`MEDIA_REJECT`). The message is kept and shows `[Attachment unavailable]`.
3. Notification taps go through a non-exported `NotificationRouterActivity` and an in-process `NavigationRequestBus`. `MainActivity` ignores intent extras, so other apps can no longer open the SOS screen or a chat.
4. `DeveloperModePolicy`: developer mode and its PIN exist only in debug builds.
5. Release builds use R8 minify and resource shrinking with serialization keep rules. `allowBackup=false`, and the backup/extraction rules exclude everything. Peer names in logs go through `AppLogger.redactName`.

## Phase B: inbound hardening and rate limiting (after the freeze, wire-compatible)

- **Framing:** `MeshFrameCodec` gets an O(n) growable buffer and a 2 MiB + 64 KiB maximum frame.
- **GATT server:** checks the characteristic, honours offset and prepared writes, stops echoing values, clamps the MTU.
- **L2CAP:** incremental reads with a read timeout and a bounded handler queue.
- **`InboundPayloadValidator` after decode:** field and count caps, TTL clamped to 1..10 for every type, priority clamped. GOODBYE only takes effect from the link's admitted identity and is never relayed.
- **Dedup:** an `ExpiringIdCache` (4096 entries, 24 h, O(1)) replaces the 500-entry set and also records outgoing IDs.
- **Rate limiting:** new `core/network/ratelimit/` package with `TokenBucket` (injected clock), `OriginRateLimiter`, `PenaltyBox`, `LinkTrafficShaper` and a `RateLimitPolicy` data class. Markers: `RL_DROP`, `RL_COOLDOWN`, `LINK_SHAPE`.
- **Receive order:** link shaper → codec → decode → validator → dedup → signature (v2) → origin limit → handler.
- **Expensive handlers:** SOS sync, incident `REBUILD` and `DOMAIN_EVENT` are single-flight. DELIVERED receipts for duplicate messages are rate-limited. SOS `createdAt` gets a future-skew check.
- **Transfers:** a per-peer journal quota (25% of the pool) and clamped priority.
- **Unbounded maps:** converted to `BoundedLruMap` or pruned on disconnect.
- **Key and topology checks:** `MeshRouter` rejects sequence jumps beyond a window, and `PeerPublicKeyDirectory` pins only keys that pass `DirectIdentityPolicy.validate`.
- **Advertisements:** parsed strictly, and a node ID claimed in an advertisement can't evict a link before that identity is admitted.

As built, these changed from the outline:

- SOS limits are charged after the SOS signature verifies, keyed by the signing-key prefix, not the claimed sender.
- A direct payload whose origin differs from the link's admitted identity is rejected.
- Relayed traffic is limited per origin and ingress link, so a spoof on one link can't throttle the victim on another.
- A cooldown never blocks SOS, and is per (origin, traffic class): chat abuse cools down only chat, so presence and sync keep flowing.
- A full L2CAP handler queue applies back-pressure (the reader waits up to 15 s before reading the next body, then closes the link with `L2CAP_QUEUE_STALL`) instead of dropping frames.
- PING, PONG, PIECE and ACK are shaped per link only.
- Topology sequences more than 10 min ahead of the local clock are rejected.
- Validator text and ciphertext caps equal the frame cap, so long voice notes still fit.
- Outgoing chat follows a shared sender budget of 20 (refill 20/min) that composers show; the receiver burst is 25, a 5-message margin for BLE queue bunching.
- Raising a new SOS follows a sender budget of 3 (refill 1/min, `OutgoingSosBudget`) that the SOS hub button shows ("Wait N s", and a message when pressed). The receiver `SOS_NEW` limit is burst 4, refill 1/min, charged only for revision 1 of an unknown `sosId`; a refine or end that arrives first is charged as an update. Ending, refining and reopening the active SOS are never gated.
- The first revision of an SOS this phone sees (live or by sync, whatever its number) raises the notification and alarm once if it is active, created within 10 min and not our own (node id or signing key). Ended, older and known alerts stay silent. `SOS_UPDATE` is burst 10 (6/min), and the end of a known SOS is never limited. The new-vs-reopen decision and the token charge happen under the repository lock.

Open limits:

- Node IDs are 16-bit prefixes, so key pinning only filters malformed keys until Phase D/E.
- A peer whose clock runs more than 10 min fast has its topology ignored.

## Phase C: storage (after the freeze)

- SQLCipher through `SupportOpenHelperFactory`, keyed with 32 random bytes wrapped by a Keystore AES-GCM key and passed as a raw key (no slow key derivation). A one-time `sqlcipher_export` migration converts the existing plaintext database.
- Preferences move to a Tink-AEAD-encrypted DataStore.
- A periodic job prunes media older than 30 days and expired relay data.
- A phone step measures per-hop latency before and after the change.

## Phase D: protocol v2, the single wire break (after the freeze)

- **Versioning:** a `protocolVersion` field, a "peer needs update" banner and a `PROTO_MISMATCH` marker.
- **Identity:** Tink Ed25519 and X25519 keysets under a Keystore master key. The node ID becomes 64 bits of SHA-256 over the Ed25519 public key, carried in a binary advertisement.
- **Proof of possession:** a signed challenge-response during link admission, so `DirectIdentityPolicy.allowsFrame` is enforced.
- **Private messages:** HPKE (X25519, HKDF-SHA256, AES-256-GCM) with an AAD over version, sender, recipient, ID and timestamp. The inner payload carries an Ed25519 signature. Replay is blocked by ID, a timestamp window and a per-sender seen cache.
- **Origin signatures:** public messages, SOS, presence, topology, receipts and GOODBYE are signed, and relays verify the signature before forwarding.
- **Tests:** the golden wire tests are regenerated on purpose.

## Phase E: key verification

- A safety number and QR code, scanned with CameraX and ML Kit, mark a contact Verified.
- The key-change dialog shows the old and new numbers.
- When a verified contact's key changes, sending is blocked until the user accepts the new key.

## Execution and checks

- Each phase step is one Conventional Commit.
- After each step: targeted unit tests (JVM, fake clock; Tink with cleartext test keysets). After each phase: the full D10 gate.
- An auditor reviews dependency, manifest and crypto steps.
- Each phase has a phone card. Phase A: [security Phase A card](../testing/security-phase-a-card.md). Phase B: [security Phase B card](../testing/security-phase-b-card.md).
