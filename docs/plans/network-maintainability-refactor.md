# ResQMesh maintainability phases

Last reviewed: 2026-10-06. Status: Phases 0–7 source implementation complete; final F1–F14 phone checks user-reported PASS; four baseline automated navigation checks remain blocking. The user deferred phone comparison until the final batch build.

Source baseline: `db2260e` plus the preserved uncommitted initial refactor and existing workspace work.

## Objective and boundaries

Decompose all 21 production Kotlin files above 500 lines into cohesive owners, networking first. Aim for 200–400 lines; smaller cohesive files are valid. Files above 500 require a recorded responsibility and disposition. Do not compress formatting, remove useful comments or relocate a whole oversized block merely to lower a count. The [size/ownership ledger](../handoffs/maintainability-size-inventory.md) records current coverage.

Retain Compose/ViewModel/use cases/repositories/gateway, Koin and the single module. Preserve public APIs, flows, schemas, crypto, protocol fields, stable identity, mesh hopping, admission/election, queue limits/order, retries, receipts, blocking, authority and UI appearance/behavior. No new libraries/modules/frameworks or build configuration changes. DELIVERY-01 is outside this refactor.

New helpers are internal and use explicit capabilities/live providers. Keep one owner for shared state/resources; no duplicate handler, scope, worker, store or queue. Package and folders match. Feature-specific UI stays in its feature; shared UI cannot depend on feature implementations.

## Phase record and ordered implementation

The [archived initial extraction](../../archive/docs-superseded-2026-10-05/network-maintainability-before-phases.md) preserves prior handler/gateway separation, presence publication, live callbacks, GATT hosts and 23 initialization-time repository bindings. That candidate passed 411 unit tests; it is not physical proof for later builds.

| Phase | Ownership and invariant | State |
| --- | --- | --- |
| 0 | Fresh 478-file baseline, historical discrepancy reconciliation, explicit scope/move map, architecture/size mode | Implemented; baseline build passed, 411 existing test results retained |
| 1 | NativeInboundPipeline: original receive gates/identity/dispatch; NativeOutboundDispatcher: selection, relay/fallback/GATT enqueue/promotion; OutboundFrameReporter: original progress/legacy custody publication | Implemented; 423 unit tests, builds, Lint and preservation checks pass; phones pending |
| 2 | BleTransportSessionController, BlePeerDirectory, BleLinkRetirementController, BleHeartbeatDriver | Locally checked: 426 tests, builds, Lint and preservation guard pass |
| 3 | GattClientAttempt/Callback/setup helpers; GattServerRegistration/Callback/link/attribute handlers and GattL2capAcceptor | Client and server locally checked: 426 tests, builds, Lint and preservation guards pass |
| 4 | BleAdvertisementAdmission, BleBootstrapQueue, BleAdmissionDiagnostics with one admission session | Locally checked: 426 tests, builds, Lint and preservation guard pass |
| 5 | MeshPeerCoordinator, MeshOutboundDelivery, MeshInboundMessages, MeshBlockCoordinator, MeshConversationMedia | Locally checked: 426 tests, builds, Lint, preservation and four emulator cases pass |
| 6 | IncidentCommands, IncidentEventIngestor, LegacyIncidentProjection, LegacyIncidentSync, IncidentEventCodec | Locally checked: 426 tests, builds, Lint, preservation and five Room cases pass |
| 7 | Incident, SOS/map, comms, profile/settings and navigation visual sections; IncidentViewModel models/selectors | Build/full 426-unit suite/Lint/preservation pass; 36/40 selected emulator cases pass; four baseline navigation failures remain |

Phase 1 lifts fifteen declarations with reviewed receiver substitutions and removes two unused private facade delegates. Its original 48 remaining block-method bodies are unchanged except calls into the relocated fallback/relay owner. ReliableMeshTransfers, journals, directed receipts, shared store, main handler and GATT coordinator keep their existing instances. Identity/listeners remain live and the journal stays lazy. Historical Phase 1 NativeBleManager decreased from 1,384 to 1,213 lines; new owners are 125/225/47 lines. The final facade is 743 lines, the reviewed host/construction exception; all other original oversized files are below 500.

Phase 2 retains generation fences, registration, receiver/runnable lifetime, wall/monotonic clocks, jitter, recovery and exact shutdown sequence. Existing session, lifecycle-supervisor, radio and transfer engines remain. State moves only with its complete owner.

Phase 3 retains each attempt's captured link/generation, handler, discovery flags, gates and cleanup. Server registration captures transport/server epochs; L2CAP keeps its existing thread/socket ownership. No extra posting, suspension or timeout.

Phase 4 retains one candidate/advertisement cache, generation, drain flag and bridge cooldown. Preserve ordering, clock calls, capacity, scores, jitter, attempts and retirement policy; three direct neighbors remain.

Phase 5 retains one router and a single public/private outbox mutex, conflated wake channel, worker, progress/timeout ownership. Bind the same 23 callbacks once in init. Keep live identity/channel reads, trusted-key refusal, `sender != myNodeName`, persistence-before-receipt order, duplicate handling, recipient-only confirmation and all flow defaults/buffers/sharing policies.

Phase 6 retains one projection mutex/rebuilding flag and current help/sync owners. Preserve projection/help replay/Room lock hierarchy; locked helpers cannot reacquire the mutex. Keep signed authority, validation, cancellation, pending replay order and legacy compatibility.

Phase 7 extracts visual sections with explicit values/callbacks. Keep state, launchers, saveable keys, effects, subscriptions, focus, semantics and gestures at their current lifetimes. Map extraction retains one MapView/observer/location subscription/style/cleanup. ViewModel operators/scopes/types remain unchanged. Other 401–500-line files stay untouched unless explicitly covered.

## Checks and gates

The fresh ignored snapshot is `app/build/maintainability-phases/2026-10-05-200635/baseline.json`; earlier snapshots are not replaced.

```powershell

python scripts/check_network_refactor.py --baseline app/build/maintainability-phases/2026-10-05-200635/baseline.json --phase-manifest scripts/refactor_scopes/phase1.json

pwsh -File scripts/check_structure.ps1 -ArchitectureOnly -BaselinePath app/build/maintainability-phases/2026-10-05-200635/baseline.json

```

Historical default modes stay intact. The phase mapping, reviewed wiring hashes and allowlist protect public method signatures, HEAD/index and phase-specific unrelated files. Inspection checks packages/shared UI, rejects new import cycles and new/growing oversized files, and records pending ones. Its import graph is a navigation check, not compiler dependency analysis.

Compile and run relevant behavioral checks after each extraction. At phase completion run debug build, the full unit suite without exclusions, instrumentation compilation, affected available UI/Room instrumentation, Lint, docs/links and diff hygiene. Record exact APK hashes in [validation](../validation.md) and the [phone card](../testing/network-maintainability-test-card.md). Phase 1 adds characterization for ingress ownership, live callback order, relayed identity, journal gates, queue admission, L2CAP pressure/fallback and frame publication.

The user replaced intermediate phone gates with final physical validation. Preserve each phase source snapshot/APK and run local gates before advancing. Supply one final edge-case card. A local regression still blocks progression. UI-02 records four unchanged navigation checks failing on both baseline and candidate; their repair is separate, and final acceptance remains blocked. On October 6 the user confirmed final-candidate F1–F14 PASS; setup/count/timing/transport details remain unspecified. Ambiguous A-to-D loss stays inconclusive and outside diagnosis.

## Handoff

Update this plan, status, architecture/decisions and validation as each phase actually completes; maintain one build-aware card and the size ledger. The user explicitly authorized a local progress commit on October 6. No automatic phone operation, push, PR, feature redesign or performance/reliability claim. Full acceptance requires every oversized file's disposition, passing local gates and recorded required physical comparisons.
