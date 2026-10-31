# Engineering decisions

Last reviewed: 2026-10-17. Baseline: see [status](status.md). These describe accepted choices; source and bounded device evidence prevail.

## D1: Reliability before transport expansion

Prioritize BLE text/SOS, recovery and honest delivery state. Wi-Fi Direct, SoftAP, RFCOMM replacement, advertisement flooding and protocol migration remain deferred. Reported successes do not close intermittent A-to-D loss.

## D2: GATT remains the control path

Native advertising/scanning and dual-role GATT are the base. Owned, healthy L2CAP can carry payloads; GATT remains setup/readiness/liveness/fallback. A failed socket cannot leave a peer falsely usable.

## D3: Readiness is an application fact

Radio CONNECTED is insufficient. Separate payload READY, configuring/unresponsive, routed, recently seen and offline states. Routing needs a usable first hop.

## D4: Link attempts own state

Queues, operations, timers, callbacks, buffers, MTU, sockets and cleanup belong to endpoint/role/generation. Old callbacks cannot mutate replacements. Stable node identity is separate from BLE address.

## D5: Private messaging fails closed

Missing/unusable trusted keys or encryption failure cannot produce plaintext, invalid envelopes or successful delivery. Undecryptable traffic cannot be acknowledged as successful private delivery. Authentication and key-lifecycle guarantees remain limited.

## D6: Evidence controls claims

Builds, focused tests, executed instrumentation, qualitative user reports and analyzed captures prove different things. Quantitative range, capacity, latency, battery, delivery and security claims require conditions and measurements. Keep an actual failure open beside successful runs.

## D7: Conservative feature scope

Origin outboxes and transfer journals exist; disconnected-cluster delivery is not guaranteed. Stronger transactional outbox capacity/route repair, bounded replication, resource requests, safety check-ins and unfinished location-confidence policies remain proposals. Battery-aware routing needs measurements. RSSI supports cautious trends, not distance/triangulation. Data mules, virtual private mesh, large media expansion and new protocols are inactive research ideas.

## D8: Lightweight context engineering

One shared router, `AGENTS.md`, plus six owner documents hold current context; each fact lives in one owner. Tool entry files (`CLAUDE.md`, `GEMINI.md`) only import `AGENTS.md` and add tool-specific notes. Avoid extra routers/indexes, ICM stage trees and knowledge graphs. Archived prompts are historical and never authorize implementation.

## D9: Planning lifecycle

Small work stays in its task; medium work updates status. Only large multi-session initiatives get one active plan, capped at 1,200 words. Extract lasting decisions/results before archival. Label implemented, superseded, partially superseded and deferred material explicitly; preserve unique tests and historical build identities.

## D10: Deterministic PR gate

CI repeats debug build, unit tests, Lint, Android-test compilation, detekt (baselined), architecture-boundary and docs checks, and diff hygiene. Failed checks block readiness and cannot be waived by AI explanation. PR creation and merge require explicit instruction. Local/CI checks never substitute for physical evidence.

## D11: Mutual direct-link denial, independent local release

Stable-ID block requests/ACKs establish denial before direct teardown. Both phones persist records and unblock locally. Relayed text/private/SOS/receipts/audio remain allowed. See validation. This is a routing-debug policy, not authenticated security.

## D12: Conservative recovery and cluster bridging

Recovery must not destroy healthy routes: Bluetooth OFF suspends and ON rebuilds; Go offline cancels recovery; background is opt-in. A spare link may bridge an unreachable cluster, but reclaiming a full-capacity link requires proof that reachability survives. Mechanics and timers: [architecture](architecture.md).

## D13: MapLibre and local PMTiles

MapLibre Native/local PMTiles replaces osmdroid and bulk public raster downloads.

## D14: Offline manifest signatures

ECDSA P-256 is the default; Ed25519 is the supported fallback where available. This preserves older Android compatibility without an extra cryptography library. Packaging instructions must match the existing generator/verifier.

## D15: Notification setup gates

Cold notification launches complete identity, permissions and setup before opening the saved destination. In-session notifications navigate directly.

## D16: Emergency cartography

Show in-map attribution, medical POIs and subtle other-amenity labels at zoom 15+. Preserve the 36dp compass, distinct GPS/SOS markers and slide sheets. Map presence never proves location freshness.

## D17: Permanent hardware-bound identity

Node IDs derive from the Keystore public-key hash. Identity/peer-key preferences are excluded from cloud backup to avoid restoring metadata without keys. Pending key changes require explicit handling.

## D18: Leased topology and directed private delivery

Topology is versioned and leased so stale state cannot restore withdrawn routes. Private traffic uses stable-ID directed routes and never falls back to broadcast; rejected sends stay retryable. Details: [architecture](architecture.md).

## D19: Reporter-selected lead helper

Stable identity/signing key owns reporter authority and historical names. Reporters select/revoke/close; helpers confirm/decline/withdraw. Selected offers cannot be edited. Withdrawal/offline revision requires fresh selection, never automatic replacement. Route loss never reassigns. Signed closure dependencies order replay; key continuity is not deployment authority.

## D20: Bounded resumable recorded transfers

Large recorded notes must survive disconnects without unbounded memory: protocol-1 envelopes are journaled in resumable pieces with fixed per-neighbor and per-transport bounds, and legacy peers keep whole frames. Custody never proves recipient delivery. Sizes and limits: [architecture](architecture.md).

## D21: Incident history and projection digests

Broadcasts stay the fast path; periodic history/state digest comparison repairs missed incident events. Signed authority and pending dependencies gate completion, and queue acceptance never proves convergence. Intervals and paging: [architecture](architecture.md).

## D22: Independent conversation and SOS ownership

Community, Radio and SOS keep independent history, drafts and unread state. SOS ownership belongs to the origin key: sender Back preserves it, receiver silence is local, and only signed origin termination ends it, reconciled after reconnect. Matching builds are required. Details: [architecture](architecture.md).

Deferred designs: [archive index](../archive/docs-superseded-2026-10-04/README.md).

## D23: Preserve behavior during networking decomposition

Structural refactors preserve facade APIs, callback order, generation fences, persistence, queues, routing, crypto and Compose lifetimes, and imply no performance or delivery change. Target 200–400-line owners; NativeBleManager keeps a reviewed 743-line exception. Results: [validation](validation.md).

## D24: Private-envelope crypto direction

Hybrid encryption stays; the planned RSA-OAEP envelope was superseded on 2026-10-17 by D26 (X25519 plus Ed25519 through Tink). Until then, no authenticated E2EE claim; forward secrecy is never claimed.

## D26: Origin-charged limits and a single wire break

The threat model is a malicious nearby phone. Rate limits charge the originating node after dedup and signature checks, never the relay, and per-link limits only shape traffic. User input limits come from one `TextPolicy`. After the freeze, protocol v2 is one coordinated break (every test phone reinstalled together), and later changes stay compatible. Storage uses SQLCipher with a Keystore-wrapped raw key. Plan: [security hardening](plans/security-hardening.md).

## D25: Packages before Gradle modules

Enforce layer boundaries with packages and `scripts/ci/check_boundaries.ps1`; its allowlist reached zero on 2026-10-17 ([plan](plans/layer-boundaries.md)). It uses adapters rather than renamed repositories, String-typed models and `feature-no-data`, and adds use cases only for multi-step flows. Cross-feature rules wait for P3. The project stays one module; other behavior-touching refactors wait until after the demo freeze.

## D27: User-controlled location

Auto-detect defaults on; each SOS may exclude location; location never blocks SOS. No background location or withheld wire flag.

## D28: Incident notifications

New Critical/Serious incidents notify everyone unless toggled off; reporter/helper alerts are ungated. Own events, replay and incidents over 30 minutes old stay silent.

## D29: Bounded incident sync, paced creation

Sync (protocol 3) carries open and 24 h closed incidents, at most 100, with full history; older stay local. Sync is never rate limited. Per signing key, reports are paced 3 then 1/20 s and offer changes 5/min per incident; receivers pace live broadcasts only; withdrawals and lead decisions are exempt.

## D30: Release developer console

Hidden; requires build-set PIN. [Plan](plans/developer-mode.md).

## D31: No live PTT

Voice notes only; older `LIVE_AUDIO` drops. `liveAudioChunk` carries transfers.
