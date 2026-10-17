# Layer boundaries (P2 subset)

Last reviewed: 2026-10-17. Status: active plan on `refactor/layer-boundaries` (from `perf/startup-lag`). The user approved it on 2026-10-17 to start before the demo freeze, on its own branch. The demo candidate stays on the existing stack until this branch passes its phone card.

This is the behavior-neutral subset of P2 in the [revision plan](code-architecture-revision.md). Deferred to later P2 work: the gateway `SharedFlow<GatewayEvent>`, `WhileSubscribed`, and the typed connection status.

## Target

```text
app/ (MainActivity, di, navigation)       composition root
feature/<x>/{ui,viewmodel,model}  ->  core/** only
core/domain/{usecase,repository,incident} -> core/model, core/location
data/**  ->  core/**  (implements core/domain/repository)
core/network  ->  no data/feature
```

- **Domain models** live in `core/model`. They mirror entity fields and keep `String` status fields, so old rows never throw on enum decoding.
- **Feature-only screen state** stays in `feature/<x>/model`.
- **Mappers** are `data/mapper/*Mappers.kt` functions: `toModel()`, plus `toEntity()` only where the data layer needs it.
- **Repository interfaces** are in `core/domain/repository/`. Thin adapters in `data/repository/adapter/` wrap the existing repositories, which the user chose over renaming them. `MeshRepository`, `IncidentRepository`, `SosRepository` and all BLE code are not edited.
- **Incident rules** (`mayPerform`, `nextState`, `isReporter`) move to `core/domain/incident/IncidentRules`. The data-layer `IncidentPolicy` and `IncidentOwnership` delegate to them.
- **Use cases** are added only for logic-heavy flows: `RaiseSosUseCase`, `EndSosUseCase` and `SendPrivateMessageUseCase`. Existing `MeshUseCases` pass-throughs stay.
- **Boundary rule:** `feature-no-data` covers all of `data.*`, which the user chose on 2026-10-17. The checker also scans fully-qualified names. The allowlist ends empty.
- **No cross-feature rule yet.** `sos -> comms` (the shared `CommunicationViewModel`) is P3 work.

## Steps

Each step compiles and passes its tests on its own and is one commit.

1. Models and mappers, not yet used. Round-trip mapper tests.
2. `IncidentRules`, with `IncidentPolicy` and `IncidentOwnership` delegating to it. Parity table test over every state × event × role. This touches data authority code, not network code.
3. Interfaces and adapters, with Koin bindings in `app/di`. The SOS adapter's `StateFlow` mirrors the current value, so the first frame is never empty. `KoinModulesVerifyTest` must pass.
4. Mesh consumers switch to `MeshNodeRepository`: `MeshUseCases`, `MeshSessionController`, `MeshForegroundService` and `WalkieTalkieViewModel`.
5. `core/location/LocationClientFactory` for `TacticalLocationCard`, `MapLibreMapCanvas` and `CreateIncidentSheet`. Each keeps today's shared-versus-fresh client choice.
6. `core/notification/SosAlertNotifier`, implemented by `SosAlertController`. A data adapter implements `SosAlertSink`.
7. SOS UI. `CommunicationViewModel` uses `SosAlertRepository` and `ConversationStateRepository`. `sosRepository` becomes private, replaced by `isOwnSos` and `sosActivity`. This removes the fully-qualified references in the SOS screens.
8. Use cases: SOS raise and end (15-minute cached-location freshness, refinement guarded by `isCurrent`), and private send (`Sent`, `NeedsKeyVerification`, `Failed`). The ViewModels keep their UI state and strings.
9. The incident ViewModel, setup ViewModel and `feature/incident/**` move from entities to models.
10. Checker hardening: `feature-no-data`, a fully-qualified-name scan, and an empty allowlist.
11. Docs (architecture, decisions, status, validation) and the layer boundaries smoke card in `docs/testing/`.

These checks run from step 4 on: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `detekt`, `assembleDebugAndroidTest`, `check_boundaries.ps1`, `check_docs.ps1` and `git diff --check`.

## Guards and risks

- `WireFormatGoldenTest`, `EntityTableNameTest` and the migration tests must not change. No `@Serializable` class or entity is edited.
- No BLE, routing, crypto or dispatch code is edited.
- `SosThreadScreen`'s non-catching event decode moves into the mapper unchanged.
- Koin adapters are lazy, and the eager resolution order is unchanged.
- The phone-visible risk is the SOS and incident screens after steps 7–9, which the smoke card covers.

## Exit

The allowlist is empty, local checks are green, a review finds no blocking issue, and the user reports a smoke card pass. Merging needs explicit instruction.
