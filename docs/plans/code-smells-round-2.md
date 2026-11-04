# Code smells round 2

Last reviewed: 2026-11-04. Status: active plan; steps 1–12 committed on `final-code-smells` with local checks green; device validation pending. It follows the merged first round (`8632799`, [card](../testing/code-smells-refactor-card.md)). Targets come from `config/detekt/baseline.xml`: 687 MagicNumber, 207 FunctionNaming, 104 LongMethod, 95 LongParameterList, 80 CyclomaticComplexMethod, 47 ComplexCondition, 38 TooManyFunctions, 27 VariableNaming and 5 NestedBlockDepth entries. No class is currently flagged LargeClass; class size shows up as TooManyFunctions.

## Rules for every commit

1. **Structure only.** Keep the same inputs, outputs, side effects, log markers, ordering, thread and dispatcher, and lock scope. Don't change wire strings, ProtoBuf field numbers, Room columns, Koin wiring or public signatures used by tests.
2. **One smell, one area, one commit.** Aim for under about 300 changed lines. Never mix a rename with a logic move.
3. **Tests stay unchanged.** If an existing test has to change, the commit changed behavior: revert it. New characterization tests may land in a separate commit *before* the refactor.
4. **Checks before each commit:**
   ```
   .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
   .\gradlew.bat :app:lintDebug :app:assembleDebugAndroidTest detekt --console=plain
   powershell -ExecutionPolicy Bypass -File .\scripts\ci\check_boundaries.ps1
   ```
   `WireFormatGoldenTest` and the Room table-name test must pass untouched.
5. **Shrink the baseline.** After a step, run `.\gradlew.bat detektBaseline` and check that the diff only removes entries.
6. **Commit format:** `refactor(<area>): <smell> in <unit>`, for example `refactor(incident): extract status predicates in IncidentPresentation`.

## Smell recipes

| Smell | Safe move |
| --- | --- |
| Poor naming | IDE rename only. Change `p`, `acc` and `desc` to `payload`, `accuracyMeters` and `description`. Rename UPPER_CASE non-`const` vals to camelCase, or make them `const` in a `companion object` when the value is a compile-time constant. |
| Magic number | Use a `private const val` with a unit suffix (`_MS`, `_BYTES`) next to its only user. Shared values go in a small `object` in the same package. Keep the value byte-identical. |
| Complex condition | Extract it to a named `private fun` or `val` (`isStaleCandidate()`). Keep `&&`/`||` order and short-circuit behavior, since an operand may have side effects. |
| Deep nesting | Use guard clauses (`if (!x) return`) only when the early exit matches the original path exactly. Otherwise extract the inner block. |
| Long method | Extract steps into private functions in the order they ran. Don't reorder calls across `synchronized`, `launch` or `handler.post`. |
| Type conditional | Replace string literals with existing names (`IncidentState.OPEN.name`) or new `const val` names that keep the same string. Use a `when` over constants. Use a handler map only when the branches are already independent. |
| Long parameter list | Group values that always travel together into a `data class` (a parameter object). For Compose, group callbacks into an `…Actions` class. Keep default values the same. |
| Large class | Move a cohesive group of private functions into an `internal` collaborator constructed in the same place. Keep the public facade. |

## Steps, by core function

Work from lowest to highest risk. Steps 1–7 touch no radio code.

### Step 1: Incident status checks (type conditional, naming)
- **Files:** `feature/incident/model/IncidentPresentation.kt`, `IncidentSelectors.kt`, `feature/incident/ui/components/HelpWorkflowPanel.kt`.
- **Changes:** replace `status == "RESPONDING"` and similar with `IncidentState.RESPONDING.name`. Add a private extension such as `Incident.isTerminal()` for `setOf("RESOLVED", "CANCELLED")`. Name the three `ComplexCondition` expressions in `HelpWorkflowPanel` (`canOffer`, `canWithdraw`, `awaitingMyConfirmation`).
- **Proof:** incident JVM tests and `IncidentUi` androidTest compile.

### Step 2: Incident list filtering (long method, complex condition)
- **File:** `IncidentSelectors.kt` (`selectIncidents`).
- **Changes:** extract `matchesQuery(incident, query)` from the six `matches…` flags, and pull out sort and filter as separate functions. The output list order must stay the same.

### Step 3: Help workflow rules (complex condition, long method)
- **Files:** `data/repository/IncidentHelpEventRules.kt` (`LeadSelectedRule`, `OfferUpdatedRule`, `HelperDecisionRule`, `OfferWithdrawnRule`) and `IncidentHelpWorkflow.kt` (`applyEventLocked`, `retainLateEvent`).
- **Changes:** name each rejection predicate (`isStaleSelection`, `isForeignOffer`), keeping the original order. Leave the transaction and `Mutex` boundaries where they are.
- **Proof:** `IncidentHelpWorkflowTest`, `IncidentStateMachineAndDeduplicationTest` and `IncidentAutomaticSyncTest` unchanged and green.

### Step 4: Incident commands and ingest (long parameter list, large class)
- **Files:** `IncidentCommands.kt`, `IncidentEventIngestor.kt`, `IncidentHelpWorkflow.kt` constructors.
- **Changes:** group the DAO parameters into an `IncidentStores` parameter object built by `IncidentRepository` (these classes are not Koin-wired). `IncidentHelpWorkflow` keeps its constructor because tests build it directly.

### Step 5: SOS location validation (complex condition)
- **File:** `data/repository/SosRepository.kt`.
- **Changes:** fix validation was already extracted on `main`; name the envelope and relay checks, reuse `owns()` and rename single-letter variables. Payload type literals move in step 6.
- **Proof:** `SosLifecycleAndSyncTest`.

### Step 6: Payload type names (type conditional)
- **Files:** `PayloadDispatcher.kt`, `dispatch/DeliveryHandlers.kt`, `dispatch/PresenceHandlers.kt`, `ChatSyncCoordinator.kt`, `SosSyncCoordinator.kt`, `ReliableMeshTransfers.kt`, `ratelimit/InboundOriginGate.kt`.
- **Changes:** add `core/network/PayloadTypes` (`const val MESSAGE = "MESSAGE"` and so on), copying the strings exactly, then replace the literals. Keep `MeshPayload.type` a `String`. Changing it to an enum would change serialization.
- **Proof:** `WireFormatGoldenTest`.

### Step 7: Inbound validation and delivery handling (complex condition, nesting, long method)
- **Files:** `MeshInboundMessages.kt` (the 10-clause conversation check), `RecipientReceiptPolicy.kt` and `dispatch/DeliveryHandlers.kt` (`StandardMessageHandler.handle`: long method, deep nesting, complexity).
- **Changes:** extract `isWellFormedConversation(payload)` and split `handle` into `handleReceipt` and `handleMessage` branches.
- **Proof:** `PrivateReconnectDeliveryTest` and `ReliableMeshTransfersTest`.

### Step 8: Offline map storage (nesting, long method)
- **Files:** `core/map/download/MapPackageDownloader.kt` (`downloadToFile`) and `MapStorageGuard.kt` (`unpackZip`).
- **Changes:** extract `copyWithProgress` and `extractEntry`. Keep the exact file-close order and the zip-slip check.
- **Proof:** `MapPackageTest` and `OfflineMapViewModelTest`.

### Step 9: Compose screens (long parameter list, long method)
- **Files:** `IncidentDetailSheet.kt` (26 parameters), `IncidentCoordinationHub.kt`, `HelpWorkflowPanel.kt`, `CreateIncidentSheet.kt` (`onSubmit` with 9 arguments), `ChatBubble.kt`, `ActiveChatScreen.kt`.
- **Changes:** split long composables into private children, keeping text and test tags. `IncidentDetailSheet` and `CreateIncidentSheet` keep their signatures (`IncidentUiTest` calls them). Done: detail sheet body, confirmation sheets, `CreateIncidentSheet`, `ChatBubble` and `ActiveChatScreen`. Hub and panel have no callers and were left alone.
- **Proof:** `IncidentUiTest` compile. UI-02's four existing failures are expected, so compare results before and after.
- **Magic numbers:** `Color.kt` and `Theme.kt` hex colors are design tokens, so leave them as they are. In screens, name only the repeated `dp` and duration values.

### Step 10: BLE naming and constants (naming, magic numbers; strongest model tier)
- **Files:** `NativeBleManager.kt` and the `GattHost`, `GattClientHost` and `GattServerHost` interfaces (the VariableNaming entries).
- **Changes:** move the UUIDs and timeouts into a `BleConfig` object, keeping values byte-identical. This is the P4 `BleConfig` item in the [revision plan](code-architecture-revision.md). Leave the `NativeBleManager` facade line exception unchanged.

### Step 11: BLE admission and session (complex condition, long method, long parameter list; strongest model tier)
- **Files:** `BleBootstrapQueue` (`drainCandidates`, `rescueOrphan`), `BleAdvertisementAdmission` (`handle`, `admitOrElect`), `BleAdmissionDiagnostics.recordDecision` (10 parameters, which become an `AdmissionDecision` record), `ReadyPayloadSetup` and `BleLifecycleSupervisor.checkNow`.
- **Changes:** extraction only, with no change to the `Handler` thread or timing.

### Step 12: GATT callbacks (long method; strongest model tier, last)
- **Files:** `GattClientCallback`, `GattClientConnectionEvents`, `GattServerLinkEvents`, `GattServerAttributeRequests`, `GattTransferExecutor.sendChunk` and `GattClientManager.connectToPersistentGatt`.
- **Changes:** split `handleCallback` per status branch. Done in `d7ed43a`, `803787e` and `d3fbc77` (`GattServerAttributeRequests`), plus lint fix `6ef0a65` for the helpers that `803787e` split. The planned phone run between commits did not happen, so the card below covers steps 10–12 together, and a failure is bisected over those commits.

## Device validation

A build is not BLE proof. After steps 1–9, rerun the existing [code smells card](../testing/code-smells-refactor-card.md) with the new build identity. Steps 10–12 each need that card plus the relay step on three phones before the next step starts. A failure means reverting the last commit, not patching forward.

## Exit

- Every step is committed separately with checks green.
- The baseline has shrunk without new suppressions.
- The card has been run, with the results recorded in [validation](../validation.md).
