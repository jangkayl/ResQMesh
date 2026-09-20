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

Replaced osmdroid and dynamic public raster tile downloading with MapLibre Native and downloaded local PMTiles packages. This ensures true offline reliability, avoids violating OSM tile scraping policies, and provides high-performance vector rendering.

## D14: Offline map manifest verification uses Universal ECDSA (NIST P-256)

Replaced Ed25519 with Universal ECDSA (NIST P-256 / secp256r1, SHA256withECDSA) as the default map package signature algorithm, with Ed25519 composite fallback. Ed25519 is natively supported only on Android 11+ (API 30+), throwing runtime security provider exceptions on older Android versions (API 24–29). ECDSA provides universal hardware-accelerated verification across all supported Android versions with zero third-party cryptography library overhead.

## D15: Notification deep link routing preserves node setup flow

Cold-launch from private message or SOS alert notifications routes to `IdentitySetupScreen` (or `PermissionsScreen` if permissions are ungranted) rather than jumping directly to the homepage (`MainContainerScreen`). Node identity, hardware readiness, and mesh startup must complete before the user enters the active mesh. Target chat nodes and SOS map views are preserved and automatically opened once setup is completed. Active in-session notifications continue direct navigation without re-prompting setup.

## D16: Tactical map overlays and emergency cartography filtering

MapLibre default engine watermarks and attribution widgets are suppressed in favor of unified in-sheet OpenStreetMap legal attribution to maximize screen real estate during disaster response. Emergency POI cartography strictly filters medical infrastructure (`hospital`, `clinic`, `doctors`) from general commercial amenities to prevent false alarms or clutter, while non-emergency POIs render as subtle labels at zoom 15+. Map overlays use compact 36dp true-north compass dials, distinct high-vis custom markers (cyan GPS puck vs. crimson SOS teardrop), and vertical slide sheets.

## D17: Hardware-bound permanent node identity and cloud backup exclusions

Node IDs are derived deterministically from the SHA-256 hash of the device's hardware-backed public key (`CryptoManager.getMyNodeId()`) rather than ephemeral random UUIDs. This prevents key-versus-ID desynchronization across app restarts and re-installations. `resqmesh_prefs` (`node_id`) and `resqmesh_peer_public_keys` are excluded from Google Cloud Backup (`backup_rules.xml` and `data_extraction_rules.xml`), ensuring restored preferences cannot conflict with newly generated Keystore key pairs. Peer public key resolution supports display-name fallbacks and explicit rejection of pending key change alerts.
