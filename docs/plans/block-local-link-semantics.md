# Block = local no-direct-link semantics (relay-through allowed)

Detailed plan for redefining device blocking on the `fix/ble-reliability`
branch. Addresses open blocker **BLOCK-01**. This is a design plan only; no code
is changed yet. Only a 3-phone physical run proves the hop behavior — a green
build does not.

---

## 1. Purpose

Blocking should become a **local, link-layer control** meaning "do not directly
pair/sync with this peer." A blocked peer must remain fully reachable and
messageable **through a relay hop**. This turns block into a deliberate tool for
forcing and observing multi-hop routing with only 2-3 phones in radio range.

Today, blocking makes two phones mutually message-deaf, so it cannot be used to
test hops. This plan removes the message-suppression and reciprocal-block
behavior while keeping the direct-link refusal that forces the hop.

## 2. Glossary

- **Direct link**: a live GATT (or L2CAP) socket between two phones.
- **Indirect / routed peer**: a node reachable only through one or more relay
  hops, learned from SYSTEM-pulse topology, not from a local socket.
- **Admission**: `BlePeerAdmissionController` deciding whether to initiate a
  direct link to a scanned peer.
- **Payload-ready**: a link that has completed GATT configuration and can carry
  application data (distinct from radio-connected).
- **Election**: RAM/CPU-derived score deciding which peer initiates a link.

## 3. Current behavior (source-observed)

Blocking peer B on device A enforces at three independent layers.

### Layer 1 — direct-link refusal (KEEP)

- `BlePeerAdmissionController.handle()`:
  `if (isBlocked(peerName) || NodeIdentity.matches(peerName, localName())) return`
  — A never initiates a direct link to a blocked B. It also returns before
  recording the advertisement, so B is not published as a directly scannable peer.
- `GattServerManager` (~line 59): on an inbound connection, if the peer is
  blocked, A's GATT server cancels the connection ("Rejected blocked device").
- `NativeBleManager.blockDevice(deviceName)`: sets `store.blockedDevices[key]`,
  finds the connected endpoint by name, and calls `disconnectFromEndpoint(mac)`
  to tear down the existing direct link.

### Layer 2 — message suppression (REMOVE — breaks the hop test)

- `MeshRepository.onMessageReceived` (line ~319):
  `if (!isSystem && (isPrivate || channelId == _currentChannelId.value) && !networkManager.isDeviceBlocked(sender))`
  — the trailing `&& !isDeviceBlocked(sender)` drops every message from B,
  including relayed ones, so a blocked peer's text/SOS never reaches the UI.
- `MeshRepository.onLiveAudioChunk` (line ~307):
  `if (channelId == _currentChannelId.value && !networkManager.isDeviceBlocked(sender))`
  — the same filter drops relayed live audio.

### Layer 3 — reciprocal auto-block (REMOVE — the BLOCK-01 bug)

- `MeshRepository.blockDevice()` / `unblockDevice()` call
  `sendSystemCommand(name, "BLOCK" | "UNBLOCK")`. This builds an encrypted
  private payload, overrides its `type` to `"BLOCK"`/`"UNBLOCK"`, and broadcasts
  it. But the direct link was already torn down in Layer 1, so the command often
  cannot be delivered directly.
- On the remote, `PayloadDispatcher` routes `type == "BLOCK"` to `BlockHandler`
  (`PayloadHandlers.kt`). If `targetName` is the receiver, it calls
  `onDeviceBlocked(senderName)`; `MeshRepository.onDeviceBlocked` then adds the
  sender to the blocked set **and calls `networkManager.blockDevice(sender)`**,
  so the remote blocks back — both phones become message-deaf. `UnblockHandler`
  mirrors this.

## 4. Why a one-sided, local block is sufficient

A direct A-B link can only form if A initiates to B, or A accepts B's inbound
connection. When A blocks B:

- Layer 1 admission stops A initiating to B, and
- Layer 1 GATT-server rejection stops A accepting B's inbound.

Both directions of link formation are refused by A alone. B does not need to
block A for the direct link to stay down. Therefore the reciprocal command
(Layer 3) is unnecessary as well as harmful, and can be removed.

## 5. Target behavior

- A blocks B -> A tears down the direct A-B link and will not re-form it.
- A and B still exchange public/private messages, receipts, SOS, and live audio
  **via a relay** (for example A-C-B).
- B appears on A as an indirect/routed peer, not a direct neighbor.
- Blocking is local; no state is pushed to the remote peer.

### Scenario walkthrough (A, B, C all in radio range)

1. Mesh forms; A-B, A-C, B-C could all be direct.
2. A blocks B. A disconnects the A-B socket and will neither initiate nor accept
   a direct A-B link again.
3. A-C and B-C remain direct. C advertises both A and B in its SYSTEM pulse.
4. A learns B as indirect via C's topology; `findShortestPath(A, B)` = [A, C, B].
5. A sends a private message to B -> directed route through C -> B receives it.
   A public message floods A -> C -> B. B's reply routes back B -> C -> A and,
   with Layer 2 removed, is now accepted and shown on A.
6. A unblocks B -> admission/server no longer refuse -> the direct A-B link is
   free to re-form.

## 6. Routing analysis — how B stays reachable after block

This is the main correctness concern and is verified against `MeshRouter`:

- `updateTopology(sender=C, connectedNodes=[A,B])` sets
  `networkGraph[C] = {A, B}` and `markNodeSeen(B)`. So each SYSTEM pulse from C
  that lists B refreshes B as reachable.
- `recalculateKnownNodes` publishes direct devices first, then flattens
  `networkGraph.values` and adds any remaining node as `isDirect = false`. B is
  therefore surfaced as an indirect KnownNode.
- **Key nuance:** `MeshRouter.removeNode(B)` (invoked from
  `MeshRepository.onDeviceDisconnected` when the A-B link drops) removes only
  `networkGraph[B]` (B's own adjacency as a key) and `lastSeen[B]`. It does
  **not** remove B from C's neighbor set. So B survives in `networkGraph[C]` and
  is re-added as indirect on the next `recalculateKnownNodes`. Blocking does not
  erase B from A's routing view as long as a relay advertises B.
- `findShortestPath(A, B, connectedDevices=[C,...])`: BFS expands A -> its direct
  devices (C) -> `networkGraph[C]` (A, B) -> reaches B, returning [A, C, B].
  Directed private routing works.
- `hasIndirectRoute` / `checkRouteExists` (= `knownNodes` contains the peer):
  once the route exists, B's own admission for A returns early
  (`if (alreadyConnected || hasIndirectRoute(peerName)) return`), quieting B's
  futile direct-reconnect attempts to A.

### Routing-freshness caveat (pre-existing, ties to ROUTE-01)

The topology cleanup prunes any node not seen for 10s, while full SYSTEM pulses
(which carry `connectedNodes`) are sent on topology change or every 60s; micro
PING pulses do not carry neighbors. Between full pulses, B's indirect entry is
refreshed by relayed traffic (`markNodeSeen(sender)` on received messages). With
active A<->B messaging the entry stays alive; when idle it may briefly flicker.
This is existing behavior for all indirect nodes, not introduced by this change,
but it affects how the test is interpreted.

## 7. Change set (no code yet)

1. **`MeshRepository.onMessageReceived` (~line 319)** — remove the
   `&& !networkManager.isDeviceBlocked(sender)` term from the acceptance guard so
   relayed/direct messages from a blocked peer are received, persisted, and
   displayed. Rationale: this is the primary suppression breaking the hop test.
2. **`MeshRepository.onLiveAudioChunk` (~line 307)** — remove the
   `&& !networkManager.isDeviceBlocked(sender)` term (Decision D-1). Rationale:
   block is strictly link-layer; audio relays like text.
3. **`MeshRepository.blockDevice()`** — remove the
   `sendSystemCommand(deviceName, "BLOCK")` call. Rationale: removes reciprocal
   remote block; block stays local.
4. **`MeshRepository.unblockDevice()`** — remove the
   `sendSystemCommand(deviceName, "UNBLOCK")` call. Rationale: symmetry with #3.
5. **Verification (no change expected)** — confirm items in Section 6 hold at
   runtime: after block+disconnect, B remains an indirect KnownNode via C, and
   private/public messages route rather than silently drop. Only if a real gap is
   found (for example disconnect wiping reachability the pulse cannot restore)
   would a minimal follow-up preserve indirect reachability.

No change to admission `isBlocked`, GATT-server rejection, disconnect-on-block,
payload schema, Room, or transport ownership.

## 8. Edge cases and sequences

- **Block mid-transfer:** disconnect drains the A-B queue/flight as today; queued
  payloads for B are not auto-rerouted. Acceptable — the sender re-sends via the
  route on the next attempt. Note as a known limitation, not a fix here.
- **Block with no relay present:** B is genuinely unreachable until a relay
  exists. Expected; the whole point requires a third phone.
- **Unblock:** removes B from `blockedDevices` locally; admission/server stop
  refusing; the direct A-B link is free to re-form on the next election.
- **Dual-MAC / rotated address:** block is keyed by `NodeIdentity.key`, matching
  the existing identity-based checks, so a MAC rotation does not bypass it.
- **Provisional inbound link (placeholder name):** unaffected — block matches by
  identity once a name is known; a still-nameless socket is handled by existing
  admission/rename logic.
- **SOS while blocked:** a blocked peer's SOS now relays and displays (SOS is not
  private-key gated). Consistent with the goal; ties to open blocker SOS-01 only
  for cancellation ownership, which is out of scope here.
- **Private message to a blocked, routed peer:** still fails closed without a
  usable recipient key (D5) — unchanged. If the key is present, it routes via the
  directed path.
- **Blocked peer in Radar:** no longer a directly scannable entry (admission
  early-return), but appears as indirect/routed if reachable; `isBlocked` styling
  still applies via `blockedDeviceNames`.

## 9. Decisions

- **D-1 — live audio relays through the block.** No audio suppression; block is
  strictly link-layer. (User-selected.) Alternative rejected: keep audio muted —
  inconsistent with "still reachable," and complicates the mental model.
- **D-2 — leave the `"BLOCK"`/`"UNBLOCK"` command path inert, do not delete.**
  After #3/#4 no device emits those payloads, so `BlockHandler`, `UnblockHandler`,
  the dispatcher branches, and the `onDeviceBlocked/Unblocked` callbacks become
  unreachable. Keeping them minimizes the diff and preserves the option to build
  a real remote-block feature later. Alternative: delete now — cleaner but a
  wider 5+ file diff while BLOCK-01 is open; deferred as optional follow-up.
- **D-3 — block is silent and one-sided; the blocked peer is not notified.**
  Only the blocker's device shows the block state (via its local
  `blockedDeviceNames` / Radar). Device B receives no "you were blocked" signal
  and does not display or enforce anything toward A. Unblock is likewise local to
  the blocker. Alternative rejected: send B a display-only "Blocked by A" notice
  — it reintroduces the BLOCK-01 delivery-timing fragility (the notice must route
  via relay after the direct link is gone) for a label-only benefit.

## 10. Risks and mitigations

- **Reconnect churn:** the non-blocking side (B) may attempt direct connects to A
  until the indirect route exists; A rejects them, then `hasIndirectRoute` quiets
  B once C's route propagates. Brief and self-resolving; visible in logs.
- **Routing-freshness flicker (ROUTE-01):** an idle blocked peer's indirect entry
  may prune between full pulses (Section 6 caveat). Mitigation for testing: keep
  traffic flowing; do not conflate a freshness flicker with a delivery failure.
- **Silent-drop regression:** the correctness risk in item 5. Mitigation: the
  physical test explicitly checks that messages arrive via C, not just that the
  block "works."
- **UI expectation:** users may expect block to also hide messages. Mitigation:
  documented semantics in `docs/decisions.md`; this is intentional for the mesh
  debug use case.
- **Private key availability:** routed private messages still require the
  recipient key; absence yields a fail-closed refusal, not plaintext.

## 11. Test plan

### JVM / static

- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
- `.\gradlew.bat :app:lintDebug --console=plain`
- `git diff --check`
- Optional unit coverage: a focused test asserting the message-acceptance guard
  no longer consults block state (only if a seam exists without broader change;
  do not add tests speculatively per repo policy).

### Physical test card (3 phones A, B, C, all in radio range)

- Build identity: debug APK from the implementing commit; record path and time.
- Steps:
  1. Bring up all three; confirm A-B, A-C, B-C can be direct and payload-ready.
  2. On A, block B. Confirm the A-B direct link drops and does not re-form.
  3. Send public A->B and B->A; confirm each arrives exactly once and relay
     markers show the path through C.
  4. Send private A->B and B->A; confirm delivery and correct receipts, routed
     via C.
  5. Send live audio A<->B; confirm it relays through C (D-1).
  6. Confirm B shows as indirect/routed on A, not direct.
  7. Unblock B; confirm the direct A-B link re-forms and traffic resumes direct.
- Failure indicators: message dropped/duplicated; A-B re-links while blocked;
  B disappears entirely (not indirect) with C present; private plaintext leakage.
- Log markers: link generation/READY, admission "Rejected blocked device",
  route creation/`routePath`, relay forwarding, delivered/seen receipts, and the
  absence of any `"BLOCK"`/`"UNBLOCK"` payload emission.
- Report: APK identity, device models, Android versions, exact steps, observed
  result, and the narrow failure time window if any.

## 12. Rollback

All four edits are small, local deletions of guard terms / calls. Rollback is a
straight revert of the `MeshRepository` change; no schema, storage, or transport
state is migrated.

## 13. Out of scope

- Rerouting in-flight queued payloads on block.
- SOS cancellation ownership (SOS-01) and route expiry/withdrawal (ROUTE-01).
- Deleting the inert command path (optional follow-up).
- Any transport, schema, or UI redesign.

## 14. Docs to update after implementation

- `docs/status.md`: update the BLOCK-01 row and next actions.
- `docs/decisions.md`: add the local block-semantics decision (D-1, D-2).
- `docs/architecture.md`: only if the block description there changes materially.

## 15. Open questions

- Should the Radar show a distinct "blocked (routed)" state, or is existing
  `isBlocked` styling on the indirect entry enough? (Presentation only; defaulting
  to existing styling unless you want a dedicated state.)

## Status

Approved semantics; live audio relays (D-1); command path inert (D-2); block is
silent and one-sided (D-3). Not yet implemented — awaiting go-ahead to make the
four `MeshRepository` edits.
