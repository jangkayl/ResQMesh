# October 4 documentation archive

Archived: 2026-10-04. Source baseline: `2e27013`, branch `refactor/mvvm-presentation-organization`.

These documents preserve earlier designs, diagnoses, test candidates and evidence. They are inactive references, not current instructions or authorization to change BLE/mesh. Original bodies are preserved except relocated links, whitespace cleanup and the user-requested removal noted below; historical "current", "pending" or "not implemented" wording describes the original snapshot.

Use [AGENTS](../../AGENTS.md) and the six canonical documents, starting with [status](../../docs/status.md) and [validation](../../docs/validation.md). The [legacy September archive](../docs-legacy-2026-09-16/README.md) remains intact.

## Plan dispositions

| Preserved plan | Disposition and current owner |
| --- | --- |
| [Local blocking](plans/block-local-link-semantics.md) | Superseded by mutual direct-link denial; do not restore silent one-sided blocking. Accepted policy: decisions D11. |
| [Mutual block protocol](plans/mutual-block-relay-protocol.md) | Implemented baseline, superseded as an active plan. Basic behavior reported working; repeated loss/retry checks remain bounded in physical testing. Old topology/fallback proposals are historical. |
| [Block-hop diagnosis](plans/block-hop-diagnosis.md) | Superseded diagnostic assumptions. Old 10-second expiry/60-second refresh and private broadcast proposals do not describe current directed leased routing. DELIVERY-01 remains a separate undiagnosed issue. |
| [Auto-connect diagnosis](plans/ble-autoconnect-admission-diagnosis.md) | Historical admission diagnosis. Current conservative admission/bridge behavior lives in architecture; no permission to loosen limits or add logging. |
| [Incident UX](plans/emergency-incidents-ux-redesign.md) | Implemented design; functional lifecycle/sync reported working. Accessibility/novice study remains unconfirmed in research and capstone procedures. |
| [Location/map optimization](plans/location-map-optimization.md) | Partially superseded: MapLibre/PMTiles and offline display implemented/reported. Typed location-confidence results, proposed precision/freshness thresholds and SOS refinement policy remain deferred rather than adopted. |
| [Transactional implementation](plans/RESQMESH_TRANSACTIONAL_IMPLEMENTATION_PLAN.md) | Partially superseded: identity, signed incidents/event storage/sync and independent signed SOS exist. Preserve current reporter-selected workflow and separate SOS/incident ownership. Resource requests/check-ins remain proposals; enum entries alone are not feature completion. |
| [Private reliability](plans/private-message-reliability-plan.md) | Partially superseded/deferred: persistent origin sends, serialization and directed receipts exist. Atomic capacity policy, explicit waiting states, enhanced alternate-route repair, measured scale and optional two-copy replication are not declared complete. |
| [NativeBleManager extraction](plans/nativeblemanager-refactor.md) | Deferred. Preserve constraints; no extraction or network mechanism change is scheduled. |
| [Phase 5 repository extraction](plans/phase5-repository-antigravity-guide.md) | Deferred. Its Antigravity prompt is inactive; current repository ownership remains as built. |

The [physical checklist](../../docs/testing/physical-reliability-tests.md) moved from plans into testing. [Capstone preparation](../../docs/plans/capstone-demo-readiness.md) remains the active plan; no demo/pilot completion or deadline was inferred.

## Older test candidates

| Card | Disposition and unique cases retained |
| --- | --- |
| [Identity admission](testing/ble-admission-test-card.md) | Historical candidate/hashes/test count. Identity-before-ordinary-traffic, repeated generations, rename/SEEN and optional legacy checks moved into the active physical checklist. Malformed receipts/tokens require a harness, not normal UI. |
| [Private reconnect](testing/private-reconnect-test-card.md) | Historical timing/persistence candidate. Arrival versus receipts, backoff versus fresh sends, saved unconfirmed work, duplicate receipt replay and logical IDs retained in active procedures. |
| [Latency](testing/mesh-hop-latency-test-card.md) | Historical candidate/targets. Common-clock measurements, verified actual transport, quiet text/note comparison and mixed load retained. Targets are not measured promises. |
| [Stability/direct send](testing/mesh-stability-test-card.md) | Historical candidate. Idle/join-during-voice, small ordinary envelopes, durable relay/receiver restart and public roster checks retained in the physical/voice procedures. |

The current Samsung, recovery, voice, SOS/conversation and presentation cards live in [validation's procedure index](../../docs/validation.md). The current candidate hash belongs to validation; archived APK hashes identify only their original builds. Older local test counts are different snapshots, never a current combined total.

## Preserved evidence

[Validation before synchronization](evidence/validation-before-sync.md) preserves September capture results, dates, paths, earlier user reports and detailed historical procedures. The new October 4 qualitative report is recorded separately in current validation. No original result was promoted into a new measured matrix.

Models/API levels, repetitions, durations, timings and transports missing from the new report remain unspecified. No logs were analyzed during documentation synchronization.

The original [physical checklist](evidence/physical-checklist-before-sync.md) and [capstone preparation version](evidence/capstone-before-sync.md) are also preserved, including the earlier candidate identity and relative preparation timeline. An obsolete administrative concern was removed from the capstone snapshot at the user's request; other historical content remains preserved. Their updated active replacements remain in testing and plans respectively; this does not mark the demo or pilot complete.

## Future use

Open one historical document only when its origin or exact procedure is needed. Confirm it against current source and canonical decisions before proposing work. Deferred labels do not mean completed. No archived repair, protocol expansion, queue change, key-policy change or refactor is authorized by this maintenance.
