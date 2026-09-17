# Mutual block control over direct and relayed links

Supersedes `block-local-link-semantics.md` for BLOCK-01. This is a design plan,
not implementation evidence. It implements the accepted D11 policy: block is
shown and enforced on both phones, traffic may still relay, and each phone must
locally tap Unblock before a direct link can return.

## Goal

When A blocks B:

1. A immediately denies new direct A-B links.
2. B receives a reliable block request through the current direct link or mesh
   hops, shows “Blocked by A,” and also denies direct B-A links.
3. After B acknowledges, A closes the existing A-B direct socket.
4. Text, private messages, SOS, receipts, and live audio continue through
   relays such as A-C-B.
5. A unblocking alone is insufficient. B must independently tap Unblock; only
   after neither side has an active local block may election/admission reconnect
   the direct link.

This is link policy for mesh testing, not a privacy mute or authenticated access
control mechanism.

## Diagnosed defects

- `MeshRepository` no longer emits a block control payload, so B never learns
  the block and continues to initiate direct GATT.
- `GattServerManager` checks `connectedEndpointNames[device.address]` at the
  inbound callback. Android often reports a central MAC distinct from the
  advertised endpoint MAC, so this map is null and A accepts B before identity
  is known.
- `BleStateStore.blockedDevices` is memory-only, so app restart forgets direct
  denial.
- Legacy `BLOCK`/`UNBLOCK` handlers auto-apply remote unblock and have no
  app-level acknowledgement, retry, or duplicate recovery.

## Non-negotiable invariants

- Stable `NodeIdentity` keys, never MAC addresses, identify a block record.
- Radio connected, payload-ready, direct, indirect, and offline remain distinct.
- No blocked peer’s relayed message/audio/SOS is filtered at `MeshRepository`.
- A relay must never be blocked merely because it carries a payload from B.
- Private send remains fail-closed without a usable recipient key.
- No direct link resumes until **both** persistent local records are removed.
- A stale callback/control packet cannot release a newer block operation.

## State and persistence

Introduce a repository-owned `BlockRelationshipStore`, keyed by
`NodeIdentity.key(peer)`, backed by existing app preferences (not `NodeEntity`,
whose primary key is a rotating MAC). It exposes a `StateFlow` for Radar and
survives restart.

A record includes stable peer ID/name, initiator (`LOCAL` or `REMOTE`),
operation ID, status (`PENDING_ACK`, `CONFIRMED`, `RELEASED_LOCALLY_WAITING`),
and update time. Any non-released record means `denyDirect(peer) = true`.

`blockedDeviceNames` remains a derived compatibility view. Add a typed UI model
so A shows “You blocked B,” B shows “Blocked by A,” and a locally released peer
shows “Waiting for peer to unblock.” Both surfaces offer only their own Unblock
action; neither sends an unblock command.

## Control protocol

Use new payload types, not legacy `BLOCK`/`UNBLOCK`:

- `BLOCK_REQUEST`: unique transmission `id`; stable operation ID in
  `targetMessageId`; target in `targetName`; a directed route when known.
- `BLOCK_ACK`: unique `id`; same operation ID in `targetMessageId`; target=A;
  return route toward A.

No schema migration is needed: existing `id`, `targetName`, `targetMessageId`,
`directedRoute`, and `routePath` suffice. The control factory must create a
private encrypted envelope using B’s known key; B must successfully decrypt
before applying the request. This is only the project’s current fail-closed
minimum, not authenticated authorization (SEC-01 remains open).

### A taps Block on B

1. Persist A’s `PENDING_ACK` record before transport work and update Radar.
2. Tell the network layer to deny **new** direct B links, but retain an already
   ready A-B socket long enough to exchange the request/acknowledgement.
3. Send `BLOCK_REQUEST` to B: direct endpoint if ready, otherwise the first
   payload-ready next hop from `PrivateDeliveryPlanner`; broadcast only when no
   route exists.
4. Retry with a new transmission ID but the same operation ID after a bounded
   timeout and when topology/ready links change. Persist the operation so restart
   resumes retries. Do not use one message ID for retries: global dispatcher
   dedupe would suppress a repeated ACK.
5. On matching `BLOCK_ACK`, mark A `CONFIRMED`, retire the A-B direct identity,
   and stop retries. On timeout, keep local denial and a pending outbox; tear
   down the old direct socket after the bounded grace period, then retry through
   relays when available.

### B receives BLOCK_REQUEST

1. Verify target identity and decrypt the private envelope. Ignore malformed,
   wrong-target, or stale operation requests.
2. Persist B’s `CONFIRMED` remote-initiated record before changing transport;
   update Radar/notification to show “Blocked by A.”
3. Deny new direct A links, send `BLOCK_ACK` on the reversed directed route (or
   controlled broadcast fallback), then retire the current direct A-B identity.
4. A duplicate/retry with the same operation ID changes no state but sends an
   ACK again. This makes loss/retry idempotent.

### Independent Unblock

Unblock removes only the device’s own persisted record and direct-deny state.
It sends **no** `UNBLOCK` control. A first release leaves B’s record active, so
B still refuses direct links. When B separately releases, ordinary discovery
and election may reconnect. Old `UNBLOCK` payloads must be ignored/logged, not
silently release state.

## Direct-link enforcement

Add narrow network APIs such as `denyDirectIdentity`, `releaseDirectIdentity`,
and `disconnectDirectIdentity`; do not expose mutable transport maps.

Keep advertisement admission refusal for known identities. Because inbound
central MACs are often unknown, GATT server callbacks cannot reliably reject at
ACL creation. Add an identity gate when the first **direct** SYSTEM identity
pulse resolves the endpoint: if its stable identity has active direct denial,
allow only the matching pending block control/ack, then close it; do not publish
it as a usable direct peer or deliver ordinary direct application traffic. Never
apply this rule to a relayed payload, whose sender differs from its physical
endpoint. This post-identity gate is essential even after B receives the block,
for stale installs/restarts/races.

## Files and slices

1. Add `data/repository/BlockRelationshipStore.kt` and pure state/operation
   models; wire persistence and Radar-derived state in `MeshRepository`.
2. Add `BlockControlCoordinator` plus typed gateway/dispatcher callbacks;
   replace legacy block/unblock behavior with request/ack routing and retries.
3. Update `MeshNetworkGateway`, `NativeBleManager`, `GattServerManager`, and
   `NativeBleManager.processBinaryPayload` for identity-bound deny/teardown.
4. Update Radar UI models/screen for local/remote/pending/waiting states.
5. Add focused JVM tests for state transitions, duplicate ACK replay, retry IDs,
   no remote auto-unblock, direct-vs-relayed identity gating, and persistence.
6. Update architecture/status/decisions after each accepted slice. Do not mix
   Samsung transport changes, routing expiry repair, or broad Phase 5 work.

## Validation card

Use A-B-C, then repeat in the reported five-device mesh. Record exact APK,
models/API levels, time, topology, and focused Logcat.

1. Establish direct A-B/A-C/B-C and key exchange.
2. A blocks B; both Radar screens show complementary state. Confirm request and
   ACK IDs, then A-B direct `READY` is gone and cannot reform.
3. Public/private text, receipts, SOS, and live audio A<->B arrive once via C.
4. Restart A and B separately; direct denial persists and controls retry safely.
5. A unblocks only: A shows waiting, B stays blocked, no direct A-B link.
6. B unblocks: both records clear and direct A-B may reform.
7. Repeat with B initially reachable only through a relay and with a dropped ACK.

Failure markers: direct `READY` after confirmed block; block state on only one
phone; remote automatic unblock; relay itself denied; duplicate control loops;
private plaintext fallback; or loss of indirect reachability. Required logs:
operation/transmission IDs, stable identity, endpoint, route, ACK/retry,
identity-gate close, and local release state.

## Risks

The main risks are short pre-identity ACL windows, ACK loss, identity/address
mismatch, restart persistence, and interaction with current 10-second topology
expiry. The protocol bounds the first three with identity gating, durable
operation IDs, idempotent ACK replay, and post-identity teardown; phone tests
remain the only proof. Existing absent authenticated identity binding means do
not claim hostile-peer security from this control protocol.
