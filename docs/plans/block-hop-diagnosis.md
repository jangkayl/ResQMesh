# Block-forced mesh-hop diagnosis and repair plan

No-code plan for the reported failed/inconclusive mutual block test. It does not
assert a root cause without the focused A-B-C capture. It complements
`mutual-block-relay-protocol.md`; do not implement either repair track until the
capture identifies the failed layer.

## Question to answer

After A blocks B, are A-C and B-C payload-ready, is B present as an indirect
node on A, and does each traffic class reach B through C? Separate these facts:

1. block control request/ack delivery;
2. direct A-B denial and reconnect attempt;
3. topology/path availability A-C-B;
4. B public-key availability at A after A-B teardown;
5. public, private, receipt, SOS, and live-audio relay behavior.

A direct link down is not proof that a mesh route exists. A visible indirect
Radar row is not proof that private data can encrypt. A successful public flood
is not proof that directed private routing or STP audio works.

## Source-backed suspects

### S1 — topology expires before full refresh (highest probability)

`MeshRouter.startTopologyCleanup` removes entries older than 10 seconds, but
`NativeBleManager.sendSystemPulse` sends a full `connectedNodes` SYSTEM pulse
only when topology changes or after 60 seconds; the intervening pulses are PINGs
without neighbor information. C can therefore stop contributing its `C -> B`
edge well before A blocks B. `findShortestPath(A, B)` becomes empty even though
A-C and B-C are physically READY. This is the existing ROUTE-01 defect.

### S2 — the direct disconnect removes B's usable key

`MeshRepository.onDeviceDisconnected` calls
`PeerPublicKeyCache.forgetDirectLink(B, endpoint)` when A no longer has a direct
B link. The cache has one key observation per identity. A may lose B's key as
soon as block closes A-B; it only regains it when a relayed B SYSTEM pulse reaches
A through C. Private messages and new encrypted controls then fail closed while
public broadcasts may still work. This must not be confused with a broken hop.

### S3 — raw identity equality breaks control delivery

`BlockRequestHandler` and `BlockAckHandler` compare `targetName` and encrypted
envelope identities using raw `==`. Advertised/truncated/full names can differ,
even where `NodeIdentity.matches` correctly recognizes the same peer elsewhere.
A relay can keep forwarding a target's control, or the target can reject it,
without recording B's reciprocal block state or ACKing.

### S4 — audio has a distinct forwarding policy

Text broadcasts can flood; private messages use a directed path with broadcast
fallback; live audio forwards only to STP neighbors. A success/failure in one
path says nothing conclusive about another.

## Capture setup

Use the same freshly built APK on A, B, C. Record model/API/build time. Start a
10-minute focused Logcat capture before actions. Keep all phones initially in
range, allow key exchange, and label the test times T0 onward. Do not include
message contents or key material in the report.

### Pre-block baseline (T0)

Record separately on all phones:

- payload-ready direct links A-B, A-C, B-C;
- A topology contains C and C advertises B; A Radar lists B either direct or
  indirectly known;
- A has just received a relayed B SYSTEM/public-key observation through C;
- public, private, and audio each succeed A<->B before blocking.

If the pre-block private test fails, stop: key availability is already broken.
If A does not know C->B before block, stop: routing baseline is missing.

### Controlled block timeline

1. At T1, A taps Block B. Mark the exact second.
2. At T1+0–3 s, find request/ACK/control-handler events and confirm whether B
   displays blocked state. Also record every A-B lifecycle transition.
3. At T1+3–10 s, confirm A-C and B-C remain READY; do not infer this from scan
   visibility alone.
4. At T1+10 s, inspect A's topology/known-node view. Does C still list B? Does
   `findShortestPath`/route log show A-C-B or no route?
5. Send in this order, waiting for each result: public A->B, public B->A,
   private A->B, private B->A, delivered receipt, live audio A->B, live audio
   B->A. Record arrival count, displayed route/hop state, and failure time.

## Decision matrix

| Observation | Diagnosis | Repair track |
| --- | --- | --- |
| B never shows blocked; no ACK | S3 or request delivery failure | Identity-aware request handler and control markers; verify request stays on live direct link until ACK. |
| B shows blocked; A-B closes; A-C/B-C READY; A has no B indirect route | S1 | Repair topology freshness/request before modifying block protocol. |
| Public reaches B via C; private refuses missing key | S2 | Retain relay-origin key observations or force/request a B SYSTEM key refresh through C before private/control retries. |
| Public/private reach; audio fails | S4 | Diagnose STP neighbor computation and add audio-specific route evidence; do not alter text routing. |
| A has A-C-B route but control/public/private all fail at C | Relay endpoint/forwarder failure | Inspect C `getConnectedEndpointIdByName`, queue, priority send, and dedupe markers. |
| A-B returns READY while block state active | Direct-deny enforcement race | Fix scheduled client recheck and post-identity gate; identity-wide close all matching endpoints. |
| Works briefly then fails around 10 s idle | S1 confirmed | Source-scoped topology expiry/refresh repair. |

## Repair design, after confirmation

### Track R1 — topology freshness and explicit refresh

Replace generic node-level 10-second pruning with source-edge snapshots:
`topology[source] = neighbors + receivedAt`. Expire a source's advertised edges
only after a TTL compatible with full SYSTEM cadence (for example >60 seconds),
not each individual neighbor. Direct READY/liveness refreshes the direct source;
an empty SYSTEM is an explicit withdrawal. On direct-link removal/block, request
full topology from each remaining READY neighbor or make a bounded forced full
pulse response. Recalculate known nodes from live direct edges plus unexpired
source snapshots. Add tests for C->B surviving A-B removal, withdrawal, and TTL.

### Track R2 — endpoint-aware multiple key observations

Replace the single cache entry with per-peer observations keyed by endpoint/type
(direct vs relayed) and time. Removing A-B removes only the A-B direct
observation; a B key learned in a SYSTEM pulse via C remains usable under the
existing fail-closed policy. If no relay observation exists, queue the private
operation/control retry and request a fresh SYSTEM key through C; never use
plaintext or a stale unknown key. Test direct disconnect, delayed C relay pulse,
and replacement endpoint races.

### Track R3 — identity-aware control forwarding

Use `NodeIdentity.matches` for target/envelope/local comparisons and for finding
the current hop position in `directedRoute`; never raw string equality. Validate
outer route hints against decrypted envelope fields, append routes without
collapsing distinct identity representations, and log `BLOCK_REQUEST_RECEIVED`,
`BLOCK_ACK_SENT`, `BLOCK_ACK_RECEIVED`, `CONTROL_FORWARD`, and rejection reason
with stable IDs/endpoint only. Keep legacy controls ignored.

### Track R4 — traffic-specific forwarding

Do not “fix mesh hop” with one broad broadcast change. Public remains deduped
flood; private uses next-hop then bounded fallback; receipts retain return route;
audio uses an explicitly verified STP tree. Add an audio path test after R1 so
STP is calculated from fresh topology.

## Completion evidence

A repair is ready only after a fresh APK run shows: B receives block state/ACK;
A-B stays non-READY; A-C/B-C stay READY; A knows A-C-B; public/private/receipts
and audio each work once in both directions through C; each local unblock alone
keeps A-B down; both releases allow reconnection; and the route survives at
least 70 seconds idle. Record focused timestamps/capture and device matrix.
