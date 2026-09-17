# Current status

Last reviewed: 2026-09-17
Baseline: commit-backed `fix/ble-reliability` branch; physical validation remains pending.

## Current objective

Restore cross-OEM GATT readiness without weakening known-good links or allowing setup collisions.

## Implemented in the working tree

- Context uses `AGENTS.md` plus six canonical docs; superseded documentation remains outside active routing.
- Per-attempt `BleLinkRegistry` records, lifecycle/readiness states, client reference/generation checks, and focused registry tests.
- Payload-ready routing/UI selection and endpoint-owned cleanup for stale GATT/L2CAP state.
- Inbound-progress liveness, Radar online/checking/offline feedback, and heartbeat challenge ownership tests.
- Callback-driven GATT client writes and server notification completion for the heartbeat/fallback path.
- Server-to-client GATT fallback now uses acknowledged indications after the 2026-09-16 capture showed accepted notifications repeatedly missing completion callbacks after L2CAP loss.
- L2CAP promotion disarms overlapping GATT work, preventing late callbacks from tearing down a healthy link; connect-lock contention retains its cooldown.
- Client setup now starts service discovery with the default MTU instead of overlapping discovery with MTU negotiation; the server callback also suppresses a duplicate timeout-owning role when it is only the server view of the same outbound ACL. The 2026-09-16 SM-A236E validation disproved setup ordering as the complete fix: CPH2219 acted as client, while the Samsung server accepted the ACL but did not answer primary-service discovery requests.
- A generation-owned handshake gate pauses scanning during GATT setup and resumes it after the final owner exits. Local tests pass.
- The follow-up Samsung captures proved scan pausing and explicit LE were both insufficient: ATT discovery timed out with `AUTO` and `TRANSPORT_LE`. Samsung repeatedly logged an overlapping legacy-advertiser restart immediately before the inbound GATT callback. The current leading application-side defect is advertising churn during connection establishment, not role selection or transport choice.
- The minimal Samsung repair keeps advertising stable and removes role reversal, but the 2026-09-17 retest still failed with Samsung as client. Samsung's stack began discovery before the app's connected callback; CPH received no ATT request. `transport=2` is normal LE and unowned-endpoint retirement is cleanup, not the cause.
- Direct-link admission now consistently uses three distinct neighbors; meshes larger than four devices depend on routed hops rather than a full direct-link graph.
- L2CAP socket identity checks, failure retirement, and GATT retry/fallback behavior.
- Persistent Android Keystore RSA identity, fail-closed private sending, endpoint-aware key cache rules, encrypted private locations, and removal of private plaintext logging in the reviewed handlers.
- Focused unit tests for link lifecycle, liveness, heartbeat ownership, and private-message policy.
- The in-app diagnostic terminal now uses structured Connection/Sync/Transport/Routing/Security/System events, a live direct-link summary, newest-first paused-follow scrolling, and Latest/Last Sync controls. Local compile/test evidence is still required; device validation must confirm the displayed lifecycle matches the phones.
- Lean pull-request CI for the debug build, unit tests, canonical-document checks, and diff hygiene; PR creation and merging remain explicit user actions.

Local build and unit tests passed; physical BLE validation remains required.

## Open blockers

| ID | Priority | Current concern | Completion evidence |
| --- | --- | --- | --- |
| BLE-OWN | P0 | Shared server callback can still be ambiguous for late same-address events | Ownership rule plus focused test and same-process reconnect validation |
| BLE-QUEUE | P0 | Queue capacity, operation identity, traffic priority, and complete retry rules remain incomplete | Bounded tests plus sustained phone traffic without duplicate advancement/starvation |
| FRAME-01 | P0 | GATT frame length and accumulated buffers need common strict bounds | Malformed/oversized frame tests and safe rejection |
| SAMSUNG-01 | P0 | SM-A236E radio links connect, but ATT discovery receives no response under both AUTO and explicit LE; overlapping legacy-advertiser refresh occurs immediately before connection | Keep advertising stable for the whole mesh session, simplify to one setup deadline/initiator, then require five repeated `READY` connections |
| BLOCK-01 | P1 | Block currently disconnects before attempting its remote command, and the receiver interprets that command as a reciprocal block | Define local block semantics, enforce inbound rejection, and test block/unblock/restart behavior |
| LIMIT-01 | P1 | The three-direct-neighbor rule is unified in source but unmeasured across larger device sets | Stable three-phone relay and four-plus-node admission/route tests |
| ROUTE-01 | P1 | Topology expiry/refresh, empty withdrawal, and loop lifetime need focused repair | Stable three-phone route, withdrawal, and duplicate/loop tests |
| SEC-01 | P1 | Public keys lack authenticated identity binding/current-key proof; storage backup policy is unresolved | Defined threat model, fail-closed tests, and documented claim boundary |
| SOS-01 | P1 | SOS cancellation/follow-up ownership needs sender/alert binding review | Concurrent-alert and cancel-before-location tests |
| QUALITY-01 | P1 | Lint baseline: 27 errors, 58 warnings, 2 hints; errors are mainly unchecked GATT/location permissions | Repair permission errors before enabling lint in CI |

## Next actions

1. Retest the clean opposite role: CPH client and Samsung server, without role reversal or advertiser restart.
2. If that succeeds, add a stable-identity per-peer role preference after repeated discovery failure; if it fails, isolate the phones with a standard GATT test app before another transport change.
3. Validate a three-phone A-B-C routed hop, then add a fourth/fifth node while enforcing three direct neighbors per phone.
4. Triage Android Lint permission errors before enabling lint as a blocking CI gate.
5. Complete server callback ownership and queue/framing bounds.

## Scope guard

Reliable text/SOS, honest status, recovery, and private fail-closed behavior remain ahead of speculative features.
