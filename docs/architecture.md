# Architecture

Last reviewed: 2026-10-17. Baseline: see [status](status.md). Source and bounded device evidence prevail.

## System shape and data path

Single-module Kotlin/Compose MVVM uses Room, serialization, Koin and coroutines. Features depend only on `core`: `core/model` mirrors of Room rows, `core/domain/repository` interfaces, `core/domain/incident` rules and `core/domain/usecase`. `data` implements the interfaces with thin adapters (`data/repository/adapter`) and maps rows in `data/mapper`. `check_boundaries.ps1` forbids `data.*` in features and `data`/`feature` in core, scanning imports and qualified names, with an empty allowlist. Native BLE advertising/scanning and dual-role GATT provide setup/control/fallback; owned L2CAP can carry payloads.

```text
Compose -> ViewModel/use case -> core interface -> data adapter -> MeshRepository -> PayloadFactory
        -> MeshPayload -> NativeBleManager -> READY GATT or owned L2CAP
        -> PayloadDispatcher -> typed handler -> repository/Room/UI
```

Dispatch handles presence, messages, receipts, incident sync, SOS and audio; incident validation precedes relay. Acceptance transfers local ownership, hop custody proves journal storage, and recipient application receipts confirm private delivery.

## Direct-link lifecycle

NativeBleManager owns orchestration/policy; collaborators own radio, admission, GATT, L2CAP and liveness. BlePresencePublisher owns pulse counters/publication; NativePayloadCallbacks forwards current listeners synchronously through NativePayloadEvents. GattClientHost/GattServerHost and NativeGattHost proxy the same facade resources without copying state, adding scheduling or changing lifetime. BleLinkRegistry stores generation-owned state/queues. Client READY requires discovery/CCCD; server READY requires subscription. Radio CONNECTED alone is insufficient.

NativeInboundPipeline owns decode/identity/transfer gates; NativeOutboundDispatcher owns selection, relay tracking, fallback/enqueue and promotion. OutboundFrameReporter publishes progress/custody. BleTransportSessionController owns receiver/retry/shutdown; BlePeerDirectory and retirement/heartbeat owners share the store/handler/coordinator. BleTransportResources retains platform handles/registration epoch. GATT client helpers share one captured attempt; server handlers/acceptor share one registration. Permission-sensitive operations retain checked entry points. Admission shares one candidate/advertisement session.

BleSessionLifecycle separates requested sessions from running radio. OFF/permission loss suspends transport; restoration rebuilds GATT, waits for service registration and resumes advertising/scanning. Go offline cancels recovery.

Generations fence callbacks, buffers and pending sockets. Retirement removes local ownership before Android cancellation. Server indications capture issued flight/operation at ingress; unresolved indications can prevent same-address reuse. Idle registration may renew without retiring client peers; healthy server peers defer renewal. GATT work uses the main handler.

Scans retain four starts per 30 seconds plus failure backoff. Setup begins at 20-byte ATT; ReadyPayloadSetup serializes PSM reads, optional MTU 247 and writes. Accepted unresolved operations keep their gate; healthy L2CAP survives ATT stalls. Same-ACL guards prevent duplicate roles. Score/ID election and handshake gates serialize attempts; Refresh reconciles orphans. Client connect/handshake deadlines are five seconds. Identity exchange follows READY. Isolated discovery retries; 15-second advertisement silence restarts scanning. AUTO is default, with explicit-LE retry after absent ATT discovery response.

Promotion waits for active GATT frames/unresolved indications. Owned sockets survive sibling-role changes while an original role remains live. RetainedFallbackQueue retries accepted socket frames against GATT pressure. Each transport retains 128 frames, eight control slots, 2 MiB ordinary bytes and 64 KiB headroom. Controls lead waiting work; four small frames precede bulk. Active frames remain non-interruptible.

SYSTEM advertises transfer protocol 1. ReliableMeshTransfers journals MESSAGE/CONVERSATION envelopes over 4 KiB as 1 KiB pieces, four outstanding per neighbor. Hashes, durable hop acknowledgements, status bitmaps, restart/reconnect recovery and 24-hour expiry protect partial transfers. Incoming/outgoing journals each bound ordinary data to 2 MiB; storage failures are explicit. Public custody freezes known-peer rosters; visited IDs bound avoidable cycles. Public media relays keep one restart-safe onward copy 10 minutes (128/2 MiB, oldest evicted) for later READY neighbors, each once; `RELAY_ROSTER` logs this. Completed transfers need no sender link (`DetachedSender`) but keep block, frame and origin gates. Legacy peers retain whole frames with weaker completion evidence.

Maximum direct neighbors: three; unidentified endpoints occupy capacity separately. Admission retains 32 candidates, expires unobserved advertisements after eight seconds and reconsiders fresh candidates after isolation. A free third link can bridge unreachable clusters. Full-capacity reclamation requires idle transport, recent directed alternate paths preserving reachability, a responsive first hop, owned retirement and 60-second cooldown; otherwise defer. Connect Directly keeps block/duplicate/capacity guards.

MeshRepository keeps public flows/router and initialization-time callback binding. MeshPeerCoordinator handles peer/name/topology updates, one row per peer (ConnectedPeerRows); MeshOutboundDelivery owns the mutex, conflated wake channel, worker; PrivateReceiptWatch owns receipts. MeshInboundMessages shares that mutex for conversation storage. MeshBlockCoordinator owns retry jobs; MeshConversationMedia keeps channel/SOS-facing and audio coordination. Identity/channel providers and SOS reply capability keep policy.

## Identity, routing and private delivery

Keystore-derived stable IDs survive endpoint/name changes. Room names resolve by ID; advertisements cannot overwrite them. Headers resolve current names; history preserves recorded labels. Names retain #nodeId; setup omits technical tags, while compatible incoming custom tags remain unverified.

Blocks persist by stable identity. Encrypted BLOCK_REQUEST travels directly or by relay; complementary denial and BLOCK_ACK precede direct teardown. Each phone releases only its local record. Relayed text/private/SOS/receipts/audio remain allowed. Unknown inbound addresses require a gated direct SYSTEM identity before ordinary traffic/publication; blocked identity pulses retire the captured generation.

Keep direct READY, direct configuring/unresponsive, routed, recently advertising and offline states separate. Directed per-origin topology snapshots accept empty withdrawals, reject old versions, refresh fully every 30 seconds and lease cached fragments for 90 seconds. Only identified, unblocked READY neighbors are advertised. UI/delivery paths root at current READY first hops; stale reverse snapshots cannot restore withdrawals. Relayed SYSTEM pulses never rename their physical forwarder and have a four-hop limit. Legacy name-only topology is display-only.

Private envelopes use Keystore RSA and per-message AES-GCM. Sends require usable routes/trusted keys; pending key changes pause sends/retries. One serialized outbox wakes immediately and persists before dispatch. Conditional Room updates protect receipts; insertion precedes recipient receipt/notification and duplicates replay receipts. Rejected receipt sends retry every five seconds. Logical message IDs survive retries; expiry is 24 hours. Native receipt timing follows wire/hop completion; journal-owned notes cannot trigger competing retries. Only the intended recipient resolves private delivery. Direct selection prefers responsiveness, then owned L2CAP.

TOFU first-contact authentication, out-of-band verification, authenticated E2EE and forward secrecy remain unproven.

## Repository, persistence and feature ownership

MeshRepository joins callbacks, routing, persistence and UI. MeshNetworkGateway hides Bluetooth; MessageStore hides Room; PrivateDeliveryPlanner selects exact hops. Koin supplies adapters; AppCoroutineScope owns background work. MeshNetworkEventBinder binds the same 23 callbacks/providers once in repository init; named private repository handlers retain policy. Bindings survive stop/start or radio suspension. NativeBleGateway has a separate adapter file. Payload handlers are grouped by presence, delivery, link control, audio and incident events under dispatch/. Transport-state changes clear readiness/rooted routes; route checks use actual paths and direct loss preserves alternatives.
Phase B inbound order: link shaping, codec, decode, validator, dedup, InboundOriginGate, handler. SOS is limited after signature checks; sync is unlimited (direct peer only); live incident broadcasts are paced; GOODBYE is never relayed ([plan](plans/security-hardening.md)).

Public sends persist pending before dispatch. Per-neighbor acceptance, wholly rejected retries, partial feedback and a serialized flush avoid whole-broadcast retries after acceptance.

Room 11 stores explicit Community/Radio/SOS destinations. History/drafts/unread stay scoped; off-channel Radio stores silently and monitoring plays only new selected-channel notes. Retries retain destinations. Migration 10→11 hides legacy unscoped SOS/audio without deleting records.

Incidents use stable reporter/helper authority, signed prerequisites and transactional projections. Withdrawal/offline offer changes reopen selection without advancing reporter versions. IncidentSyncCoordinator (protocol 3) compares cached hashes over the requester's scope (open plus 24 h closures, max 100) on READY, changes and every 30 seconds; missing dependencies block completion. Out-of-order help events defer or become history; rejections are re-validated. IncidentEventIngestor owns projection mutex/rebuild flag; legacy classes stay compatible.

SosRepository stores signed revisioned snapshots and terminal records atomically. Alert IDs include signing-key namespaces; updates/end require that key. SOS ignores tuning. Creation/end relay urgently with TTL/hop bounds; rejected origin sends remain pending. SosSyncCoordinator reconciles bounded snapshots on READY/changes and every 30 seconds with jitter; confirmation belongs to the named neighbor/snapshot. Receiver silence is local; independent sirens stop within 30 seconds. Sender Back preserves SOS; creation cancellation returns to the hub. Cached timestamps must be positive and under 15 minutes; header fallback time never proves GPS freshness.

Presentation screens retain state/effects/launchers and scopes; visual sections receive explicit values/actions. IncidentSelectors is pure; public model packages and ViewModel flow/sharing operators remain. [Ownership/source inventory](../archive/docs-superseded-2026-10-16/handoffs/maintainability-size-inventory.md) records the facade exception and map lifetime.

## Maps, notifications and sessions

MapLibre renders versioned local PMTiles. Signed manifests use P-256 verification with Ed25519 fallback, hash/completion checks and atomic activation. Private notifications use MessagingStyle; SOS uses CATEGORY_ALARM. Incident alerts follow D28. Cold launches retain setup gates.

`LocationPreferences` gates foreground-only tracking (default on); excluded SOS location sends no coordinates or refine. MeshSessionController owns start/stop. Opt-in MeshForegroundService is a non-sticky connectedDevice anchor with a silent, debounced notification; no boot receiver or wake lock. Go offline stops radio/service. Unexpected service destruction stops transport; disabled background mode stops on activity destruction except configuration changes.

## Evidence and source navigation

[Validation](validation.md) records user-reported successes. Intermittent A-to-D loss remains [DELIVERY-01](status.md); other claims remain bounded.

Paths below are under `app/src/main/java/edu/cit/resqmesh/`:

| Area | Source |
| --- | --- |
| BLE and transfer ownership | `core/network/NativeBleManager.kt`, `core/network/bluetooth/`, `core/network/ReliableMeshTransfers.kt`, `core/network/TransferFlight.kt`, `core/network/TransferJournal.kt`, `core/network/dispatch/` |
| Identity/crypto | `core/network/CryptoManager.kt`, `data/repository/PeerPublicKeyCache.kt` |
| Routing/persistence | `data/repository/MeshRepository.kt`, `data/repository/MeshRouter.kt`, `data/repository/MessageStore.kt`, `data/local/` |
| Incident/SOS state | `data/repository/IncidentHelp*.kt`, `data/repository/IncidentSyncCoordinator.kt`, `data/repository/SosRepository.kt`, `data/repository/SosSyncCoordinator.kt` |
| Domain boundary | `core/model/`, `core/domain/{repository,incident,usecase}/`, `data/mapper/`, `data/repository/adapter/` |
| Presentation | `app/navigation/`, `feature/*/{ui,viewmodel,model}/`, `core/ui/` |
| Maps/session | `core/map/`, `core/service/` |
| DI, audio, logging, notifications | `app/di/` (layer modules), `core/audio/`, `core/logging/{,trace/}`, `core/notification/`, `feature/sos/alert/` |
