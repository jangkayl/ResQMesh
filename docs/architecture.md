# Architecture

Last reviewed: 2026-10-04. Source baseline: `2e27013`. Source and bounded device evidence prevail.

## System shape and data path

Single-module Kotlin/Compose MVVM uses Room, serialization, Koin, coroutines and existing use cases. Native BLE advertising/scanning and dual-role GATT provide setup/control/fallback; owned L2CAP can carry payloads. Nearby Connections and Wi-Fi Direct are absent.

```text
Compose -> ViewModel/use case -> MeshRepository -> PayloadFactory
        -> MeshPayload -> NativeBleManager -> READY GATT or owned L2CAP
        -> PayloadDispatcher -> typed handler -> repository/Room/UI
```

Dispatch handles presence, messages, receipts, incident sync, SOS and audio; incident validation precedes relay. Acceptance transfers local ownership, hop custody proves journal storage, and recipient application receipts confirm private delivery.

## Direct-link lifecycle

NativeBleManager owns orchestration/policy; collaborators own radio, admission, GATT, L2CAP, liveness and callbacks. BleLinkRegistry stores generation-owned state/queues. Client READY requires discovery/CCCD; server READY requires subscription. Radio CONNECTED alone is insufficient.

BleSessionLifecycle separates requested sessions from running radio. OFF/permission loss suspends transport; restoration rebuilds GATT, waits for service registration and resumes advertising/scanning. Go offline cancels recovery. Background remains opt-in.

Generations fence callbacks, buffers and pending sockets. Retirement removes local ownership before Android cancellation. Server indications capture issued flight/operation at ingress; unresolved indications can prevent same-address reuse. Idle registration may renew without retiring client peers; healthy server peers defer renewal. GATT work uses the main handler.

Scans retain four starts per 30 seconds plus failure backoff. Setup begins at 20-byte ATT; ReadyPayloadSetup serializes PSM reads, optional MTU 247 and writes. Accepted unresolved operations keep their gate; healthy L2CAP survives ATT stalls. Same-ACL guards prevent duplicate roles. Score/ID election and handshake gates serialize attempts; Refresh reconciles orphans. Client connect/handshake deadlines are five seconds. Identity exchange follows READY. Isolated discovery retries; 15-second advertisement silence restarts scanning. AUTO is default, with explicit-LE retry after absent ATT discovery response.

Promotion waits for active GATT frames/unresolved indications. Owned sockets survive sibling-role changes while an original role remains live. RetainedFallbackQueue retries accepted socket frames against GATT pressure. Each transport retains 128 frames, eight control slots, 2 MiB ordinary bytes and 64 KiB headroom. Controls lead waiting work; four small frames precede bulk. Active frames remain non-interruptible.

SYSTEM advertises transfer protocol 1. ReliableMeshTransfers journals MESSAGE/CONVERSATION envelopes over 4 KiB as 1 KiB pieces, four outstanding per neighbor. Hashes, durable hop acknowledgements, status bitmaps, restart/reconnect recovery and 24-hour expiry protect partial transfers. Incoming/outgoing journals each bound ordinary data to 2 MiB; storage failures are explicit. Public custody freezes known-peer rosters; visited IDs bound avoidable cycles. Legacy peers retain whole frames with weaker completion evidence.

Maximum direct neighbors: three; unidentified endpoints occupy capacity separately. Admission retains 32 candidates, expires unobserved advertisements after eight seconds and reconsiders fresh candidates after isolation. A free third link can bridge unreachable clusters. Full-capacity reclamation requires idle transport, recent directed alternate paths preserving reachability, a responsive first hop, owned retirement and 60-second cooldown; otherwise defer. Connect Directly keeps block/duplicate/capacity guards.

## Identity, routing and private delivery

Keystore-derived stable IDs survive endpoint/name changes. Room names resolve by ID; advertisements cannot overwrite them. Headers resolve current names; history preserves recorded labels. Names retain #nodeId; setup omits technical tags, while compatible incoming custom tags remain unverified.

Blocks persist by stable identity. Encrypted BLOCK_REQUEST travels directly or by relay; complementary denial and BLOCK_ACK precede direct teardown. Each phone releases only its local record. Relayed text/private/SOS/receipts/audio remain allowed. Unknown inbound addresses require a gated direct SYSTEM identity before ordinary traffic/publication; blocked identity pulses retire the captured generation.

Keep direct READY, direct configuring/unresponsive, routed, recently advertising and offline states separate. Directed per-origin topology snapshots accept empty withdrawals, reject old versions, refresh fully every 30 seconds and lease cached fragments for 90 seconds. Only identified, unblocked READY neighbors are advertised. UI/delivery paths root at current READY first hops; stale reverse snapshots cannot restore withdrawals. Relayed SYSTEM pulses never rename their physical forwarder and have a four-hop limit. Legacy name-only topology is display-only.

Private envelopes use Keystore RSA and per-message AES-GCM. Sends require usable routes/trusted keys; pending key changes pause sends/retries. One serialized outbox wakes immediately and persists before dispatch. Conditional Room updates protect receipts; insertion precedes recipient receipt/notification and duplicates replay receipts. Rejected receipt sends retry every five seconds. Logical message IDs survive retries; expiry is 24 hours. Native receipt timing follows wire/hop completion; journal-owned notes cannot trigger competing retries. Only the intended recipient resolves private delivery. Direct selection prefers responsiveness, then owned L2CAP.

TOFU first-contact authentication, out-of-band verification, authenticated E2EE and forward secrecy remain unproven.

## Repository, persistence and feature ownership

MeshRepository joins callbacks, routing, persistence and UI. MeshNetworkGateway hides Bluetooth; MessageStore hides Room; PrivateDeliveryPlanner selects exact hops. Koin supplies adapters; AppCoroutineScope owns background work. Callbacks bind once in repository init and survive stop/start or radio suspension. Transport-state changes clear readiness/rooted routes; route checks use actual paths and direct loss preserves alternatives.

Public sends persist pending before dispatch. Per-neighbor acceptance, wholly rejected retries, partial feedback and a serialized flush avoid whole-broadcast retries after acceptance. Acceptance is never recipient delivery.

Room 11 stores explicit Community/Radio/SOS destinations. History/drafts/unread stay scoped; off-channel Radio stores silently and monitoring plays only new selected-channel notes. Retries retain destinations. Migration 10→11 hides legacy unscoped SOS/audio without deleting records.

Incidents use stable reporter/helper authority, signed prerequisites and transactional projections. Withdrawal/offline offer changes reopen selection without advancing reporter versions or choosing replacements. IncidentSyncCoordinator compares history/state hashes on READY, changes and a 30-second backstop; missing dependencies block completion. Room 9→10 separates validation/application.

SosRepository stores signed revisioned snapshots and terminal records atomically. Alert IDs include signing-key namespaces; updates/end require that key. SOS ignores tuning. Creation/end relay urgently with TTL/hop bounds; rejected origin sends remain pending. SosSyncCoordinator reconciles bounded snapshots on READY/changes and every 30 seconds with jitter; confirmation belongs to the named neighbor/snapshot. Receiver silence is local; independent sirens stop within 30 seconds. Sender Back preserves SOS; creation cancellation returns to the hub. Cached timestamps must be positive and under 15 minutes; header fallback time never proves GPS freshness.

## Maps, notifications and sessions

MapLibre renders versioned local PMTiles. Signed manifests use P-256 verification with Ed25519 fallback, hash/completion checks and atomic activation. Private notifications use MessagingStyle; SOS uses CATEGORY_ALARM. Cold launches retain setup gates.

MeshSessionController owns start/stop. Opt-in MeshForegroundService is a non-sticky connectedDevice anchor with a silent, debounced notification; no boot receiver or wake lock. Go offline stops radio/service. Unexpected service destruction stops transport; disabled background mode stops on activity destruction except configuration changes.

## Evidence and source navigation

[Validation](validation.md) records user-reported direct/relay, recovery, feature and offline-map successes. Intermittent A-to-D loss remains [DELIVERY-01](status.md); scale, latency, callback/load, security and OEM/battery claims remain bounded.

Paths below are under `app/src/main/java/com/example/testresqmesh/`:

| Area | Source |
| --- | --- |
| BLE and transfer ownership | `core/network/NativeBleManager.kt`, `core/network/bluetooth/`, `core/network/ReliableMeshTransfers.kt`, `core/network/TransferJournal.kt`, `core/network/dispatch/` |
| Identity/crypto | `core/network/CryptoManager.kt`, `data/repository/PeerPublicKeyCache.kt` |
| Routing/persistence | `data/repository/MeshRepository.kt`, `data/repository/MeshRouter.kt`, `data/repository/MessageStore.kt`, `data/local/` |
| Incident/SOS state | `data/repository/IncidentSyncCoordinator.kt`, `data/repository/SosRepository.kt`, `data/repository/SosSyncCoordinator.kt` |
| Presentation | `app/navigation/`, `feature/*/{ui,viewmodel,model}/`, `core/ui/` |
| Maps/session | `core/map/`, `core/service/` |
