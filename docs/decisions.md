# Engineering decisions

Last reviewed: 2026-10-04. Source baseline: `2e27013`. These describe accepted choices; source and bounded device evidence prevail.

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

Persistent origin outboxes and protocol-1 transfer journals are implemented; universal disconnected-cluster delivery is not guaranteed. Stronger transactional outbox capacity/route repair, bounded replication, resource requests, safety check-ins and unfinished location-confidence policies remain proposals. Battery-aware routing requires measurements. RSSI supports cautious trends, not distance/triangulation. Data mules, virtual private mesh, large media expansion and new protocols are inactive research ideas.

## D8: Lightweight context engineering

One root AGENTS router and six canonical documents own current context. Test cards hold procedures; validation owns outcomes. Avoid duplicate CONTEXT/GEMINI routers, ICM stage trees or knowledge graphs. Archived prompts are historical references and never authorize implementation.

## D9: Planning lifecycle

Small work stays in its task; medium work updates status. Only large multi-session initiatives get one active plan, capped at 1,200 words. Extract lasting decisions/results before archival. Label implemented, superseded, partially superseded and deferred material explicitly; preserve unique tests and historical build identities.

## D10: Deterministic PR gate

CI repeats debug build, unit tests, Lint, docs and diff hygiene. Failed checks block readiness and cannot be waived by AI explanation. PR creation and merge require explicit instruction. Local/CI checks never substitute for physical evidence.

## D11: Mutual direct-link denial, independent local release

Stable-ID block requests/ACKs establish denial before direct teardown. Both phones persist their own records and unblock locally. Relayed text/private/SOS/receipts/audio remain allowed. Basic behavior has user-reported success; loss/retry and repeated coverage remain bounded by validation. This is a routing-debug policy, not authenticated security.

## D12: Conservative recovery and cluster bridging

OFF suspends an active session; ON rebuilds it. Go offline cancels recovery; background is opt-in. Preserve healthy routes. A spare third link may bridge an unreachable cluster. Full-capacity reclamation requires idle transport, recent directed alternate paths preserving reachability, owned retirement and 60-second cooldown; otherwise defer.

## D13: MapLibre and local PMTiles

MapLibre Native/local PMTiles replaces osmdroid and bulk public raster downloads. Local map display and SOS map use have user-reported offline success.

## D14: Offline manifest signatures

ECDSA P-256 is the default; Ed25519 is the supported fallback where available. This preserves older Android compatibility without an extra cryptography library. Packaging instructions must match the existing generator/verifier.

## D15: Notification setup gates

Cold notification launches complete identity, permissions and setup before opening the saved destination. In-session notifications navigate directly.

## D16: Emergency cartography

Show in-map attribution, medical POIs and subtle other-amenity labels at zoom 15+. Preserve the 36dp compass, distinct GPS/SOS markers and slide sheets. Map presence never proves location freshness.

## D17: Permanent hardware-bound identity

Node IDs derive from the Keystore public-key hash. Identity/peer-key preferences are excluded from cloud backup to avoid restoring metadata without keys. Pending key changes require explicit handling.

## D18: Leased topology and directed private delivery

Accept empty withdrawals and ignore old topology versions. Private traffic uses stable-ID directed routes, never broadcast fallback. Rejected sends remain retryable; receipt timing follows transfer completion.

## D19: Reporter-selected lead helper

Stable identity/signing key owns reporter authority and historical names. Reporters select/revoke/close; helpers confirm/decline/withdraw. Selected offers cannot be edited. Withdrawal/offline revision requires fresh selection, never automatic replacement. Route loss never reassigns. Signed closure dependencies order replay; key continuity is not deployment authority.

## D20: Bounded resumable recorded transfers

Keep three neighbors and GATT setup/fallback with optional L2CAP. Protocol-1 envelopes over 4 KiB use durable 1 KiB pieces, four outstanding per neighbor and 24-hour expiry. Legacy peers retain whole frames. Keep 128-frame/2 MiB bounds, eight control slots/64 KiB reserve and safe priority between frames. Public custody preserves known-peer rosters; custody never proves recipient delivery.

## D21: Incident history and projection digests

Immediate broadcasts remain the fast path. On READY, changes and a 30-second ±3-second backstop, compare history/state hashes. Exchange missing original events or rebuild divergent projections. Signed authority, bounded pages, current-peer snapshot ownership and pending dependencies gate completion. Queue acceptance/version equality never proves convergence.

## D22: Independent conversation and SOS ownership

Community, Radio and SOS separate history/drafts/unread state. Off-channel Radio stores silently; unscoped history stays hidden. SOS ignores tuning. Sender Back preserves it; receiver silence is local; signed origin-key termination ends it. Retain terminal records and reconcile on reconnect. Sirens stop within 30 seconds; active GATT frames remain non-interruptible. Matching builds are required.

Deferred designs are preserved in the [archive index](../archive/docs-superseded-2026-10-04/README.md); current evidence and failures live in [validation](validation.md) and [status](status.md).
