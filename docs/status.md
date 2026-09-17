# Current status

Last reviewed: 2026-09-17
Baseline: branch `temp`, HEAD `d7cd5e3`, with substantial uncommitted Android, test, script, and documentation changes.

## Current objective

Preserve the physically validated two-phone BLE/L2CAP improvement, then address the separate asymmetric block-policy defect and finish the remaining ownership, queue-bound, and framing work before broader multi-phone expansion.

In parallel, the civilian-first UI refactor is proceeding through explicit review gates. User review replaced the prior direction with a light-only reference-inspired system: pale-white/lavender canvas, bold dark hierarchy, purple actions, cyan identity accents, green status, raised white cards, and a three-item capsule dock with SOS floating above it. The rebuilt Home screen is complete and awaiting review; Messages will not begin without approval.

## Implemented in the working tree

- Codex context is consolidated into `AGENTS.md` and six canonical documents; the complete earlier documentation is preserved outside active routing.
- Per-attempt `BleLinkRegistry` records, lifecycle/readiness states, client reference/generation checks, and focused registry tests.
- Payload-ready routing/UI selection and endpoint-owned cleanup for stale GATT/L2CAP state.
- Inbound-progress liveness, Radar online/checking/offline feedback, and heartbeat challenge ownership tests.
- Callback-driven GATT client writes and server notification completion for the heartbeat/fallback path.
- Server-to-client GATT fallback now uses acknowledged indications after the 2026-09-16 capture showed accepted notifications repeatedly missing completion callbacks after L2CAP loss.
- L2CAP transport promotion now disarms and migrates overlapping GATT work, preventing a missing GATT callback from tearing down a healthy L2CAP link; connect-lock contention also retains its cooldown instead of retrying on every scan result.
- L2CAP socket identity checks, failure retirement, and GATT retry/fallback behavior.
- Persistent Android Keystore RSA identity, fail-closed private sending, endpoint-aware key cache rules, encrypted private locations, and removal of private plaintext logging in the reviewed handlers.
- Focused unit tests for link lifecycle, liveness, heartbeat ownership, and private-message policy.
- Lean pull-request CI for the debug build, unit tests, canonical-document checks, and diff hygiene; PR creation and merging remain explicit user actions.

The latest recorded local build for these combined changes passed `:app:assembleDebug :app:testDebugUnitTest`. This establishes compilation and focused unit-test evidence only.

## Open blockers

| ID | Priority | Current concern | Completion evidence |
| --- | --- | --- | --- |
| BLE-OWN | P0 | Shared server callback can still be ambiguous for late same-address events | Ownership rule plus focused test and same-process reconnect validation |
| BLE-QUEUE | P0 | Queue capacity, operation identity, traffic priority, and complete retry rules remain incomplete | Bounded tests plus sustained phone traffic without duplicate advancement/starvation |
| FRAME-01 | P0 | GATT frame length and accumulated buffers need common strict bounds | Malformed/oversized frame tests and safe rejection |
| BLOCK-01 | P1 | Block currently disconnects before attempting its remote command, and the receiver interprets that command as a reciprocal block | Define local block semantics, enforce inbound rejection, and test block/unblock/restart behavior |
| LIMIT-01 | P1 | Direct-link constants disagree (`3` versus `4`) | One documented admission rule and measured device behavior |
| ROUTE-01 | P1 | Topology expiry/refresh, empty withdrawal, and loop lifetime need focused repair | Stable three-phone route, withdrawal, and duplicate/loop tests |
| SEC-01 | P1 | Public keys lack authenticated identity binding/current-key proof; storage backup policy is unresolved | Defined threat model, fail-closed tests, and documented claim boundary |
| SOS-01 | P1 | SOS cancellation/follow-up ownership needs sender/alert binding review | Concurrent-alert and cancel-before-location tests |

## Next actions

1. Preserve the validated BLE reliability changes in a focused pull request while recording blocking as a separate known issue.
2. Define and repair local block/unblock semantics, including inbound rejection and restart persistence.
3. Complete server callback ownership and queue/framing bounds.
4. Run repeated two-phone lifecycle tests, then a three-phone relay/route-withdrawal test.
5. Reassess production security and later features only after the core delivery gates pass.

## Scope guard

Reliable text/SOS delivery, honest status, reconnect recovery, and private fail-closed behavior remain ahead of Wi-Fi Direct, alternative transports, precise RSSI location claims, large media, or other speculative features.
