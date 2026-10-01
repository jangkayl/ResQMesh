# Engineering decisions

This page records durable choices, not brainstorming transcripts. New ideas remain in the Codex task until the user accepts, rejects for a lasting reason, or defers them. Code and physical evidence can require a decision to be revised.

## D1: Reliability before transport expansion

The current release path prioritizes dependable BLE text/SOS delivery, reconnect recovery, honest UI state, and measured device behavior. Wi-Fi Direct, SoftAP, RFCOMM replacement, advertisement flooding, and other transport migrations are deferred. They add discovery, permissions, lifecycle, fallback, and device-matrix complexity before the existing path is stable.

## D2: Native BLE GATT remains the control path

Native advertising/scanning and dual-role GATT are the implemented base. Optional L2CAP may carry payloads when it is owned and healthy, but GATT remains necessary for setup, readiness, heartbeat/fallback, and compatibility. A failed L2CAP path must not leave a peer falsely usable.

## D3: Readiness is an application fact

Radio `CONNECTED` is not sufficient for routing or user-visible online state. Payload use requires a verified `READY` role after required GATT configuration. Direct-ready, configuring/unresponsive, indirectly reachable, recently seen, and offline are distinct states.

## D4: Link attempts own their state

Queues, operations, timers, callbacks, buffers, MTU, sockets, and cleanup belong to a specific endpoint/role/generation. A stale callback or losing duplicate attempt must not mutate or delete a replacement link. Stable node identity remains separate from a BLE address.

## D5: Private messaging fails closed

Do not send private content as plaintext or as an invalid encrypted envelope when a usable recipient key is missing or encryption fails. Do not acknowledge, store, or present unencrypted/undecryptable private payloads as successful private delivery. Claims remain limited until public keys are authenticated and bound to identity with an intentional key lifecycle.

## D6: Evidence controls claims

A build proves compilation; a focused unit test proves a bounded rule; a physical run provides evidence for the tested devices and scenario. None alone establishes broad BLE reliability. Range, capacity, latency, battery, self-healing, security, and rescue claims must cite measured conditions and limitations.

## D7: Conservative feature scope

- Persistent outbox/delay-tolerant delivery is the strongest later feature because it directly supports disconnected clusters, but it follows the current reliability gate.
- Battery-aware behavior should be driven by measurements rather than assumed optimization.
- RSSI may support a cautious stronger/weaker trend, not precise distance, heatmaps, or victim triangulation.
- Large media, virtual private mesh, data mules, and protocol replacement are research ideas rather than current requirements.

## D8: Lightweight context engineering

Codex uses one root `AGENTS.md` router and a small set of canonical documents. There is no duplicate root `CONTEXT.md`, full ICM stage tree, knowledge graph, or project skill until real complexity demonstrates a need. Superseded material stays outside active routing.

## D9: Planning lifecycle

Small work stays in one task. Medium work updates current status. Only large multi-session initiatives receive one temporary `docs/plans/<slug>.md`; after completion, lasting decisions move here and the plan is archived or removed. The user performs physical-phone tests and Codex maintains concise status/validation results.

## D10: Lean deterministic PR gate

Pull requests repeat the debug build, unit tests, canonical-document checks, and diff hygiene in GitHub Actions. A failed deterministic check blocks readiness and cannot be waived by AI interpretation. Codex reports locally validated work and waits for explicit permission before creating a pull request; merging is never automatic. CI is local-code evidence only, so device-facing BLE behavior still requires the user-run physical test card.

## D11: Blocking is mutual direct-link denial, with independent local release

A block request must reach the named peer through a direct or relayed path, make both phones show and enforce the direct-link denial, and then remove their direct socket. Public/private text, SOS, receipts, and live audio remain routable through other peers. The remote phone does not automatically unblock when the initiator does: each phone must explicitly tap Unblock locally before a direct link may return. The protocol requires stable-identity persistence, acknowledgement/retry, and post-identity admission enforcement; it is implemented in the working tree but remains unvalidated. This is a routing-debug policy, not a privacy or authenticated-security claim.

## D12: Direct links may bootstrap recovery, but do not replace healthy routes by default

Discovery retains a healthy routed path instead of forming a redundant direct ACL for every nearby peer. Once no payload-ready direct neighbor remains, an unblocked nearby routed peer may proceed through the normal election, cooldown, and capacity checks to restore direct reachability. An explicit user request uses those same checks and cannot override direct-link block denial or capacity. Device validation must confirm recovery without churn.

## Considering new work

Before selecting a difficult fix or feature, compare the smallest viable change, a structural alternative, and a non-code/operational alternative when relevant. Evaluate capstone value, production value, reliability impact, Android/device support, security, complexity, migration risk, and physical-test cost. Do not treat an explored option as an accepted requirement.
## D13: Offline maps use MapLibre and local PMTiles

MapLibre Native with local PMTiles replaces osmdroid and public raster tile downloads. This supports offline maps without scraping OSM tiles.

## D14: Offline map manifest verification uses Universal ECDSA (NIST P-256)

Map packages default to ECDSA P-256 signatures with Ed25519 fallback because Android 24–29 lack native Ed25519 support. No extra cryptography library is required.

## D15: Notification deep link routing preserves node setup flow

Cold notification launches complete identity, permissions, and mesh setup before opening the saved chat or SOS destination. In-session notifications navigate directly.

## D16: Tactical map overlays and emergency cartography filtering

Maps show in-sheet OpenStreetMap attribution. Emergency POIs include medical infrastructure (`hospital`, `clinic`, `doctors`); other amenities appear as subtle labels at zoom 15+. Overlays use a 36dp compass, distinct GPS/SOS markers, and slide sheets.

## D17: Hardware-bound permanent node identity and cloud backup exclusions

Node IDs derive from the Keystore public-key hash (`CryptoManager.getMyNodeId()`). Identity and peer-key preferences are excluded from cloud backup to avoid restoring keys without their Keystore pair. Pending key changes require explicit rejection.

## D18: Leased topology and accepted-only directed private delivery

Topology uses leased snapshots; empty snapshots withdraw adjacency and old versions are ignored. Private traffic uses exact directed IDs, never broadcast fallback. Rejected sends remain retryable; receipt timing starts after acceptance.

## D19: Reporter-selected civilian lead helper

Ownership uses stable user ID and signing key; names remain historical snapshots. One offer per helper; selection locks local editing until withdrawal/removal. Reporters select, revoke, resolve, or cancel; helpers confirm, decline, or withdraw. Withdrawal or signed offline edits reopen requests; replacement needs fresh selection. Helper revisions never advance reporter versions; superseded selections consume valid reporter versions. Route loss changes connection labels, never selection. No voting, open comments, automatic failover, verified-responder claim, or duplicate-response guarantee is implied. Signing proves device-key continuity only; first-contact trust and deployment authority remain open.

## D20: Bound voice pressure without changing the payload protocol

Keep three direct neighbors and whole-frame compatibility. Each GATT/L2CAP queue counts active bytes and reserves control capacity: 128 transfers, 2 MiB ordinary bytes, eight slots/64 KiB headroom. Acknowledged progress delays silence retirement; attempts alone do not. Public broadcasts expose neighbor acceptance, persist before dispatch, and retry wholly rejected sends only. Larger-scale media scheduling depends on the phone matrix in status.
