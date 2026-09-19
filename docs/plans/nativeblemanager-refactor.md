# NativeBleManager maintainability refactor

Implementation plan for reducing `NativeBleManager` size and coupling on the
`fix/ble-reliability` branch. This is a plan, not evidence: no phase proves BLE
behavior, and a green build is not device validation.

## Goal and non-goals

Reduce `core/network/NativeBleManager.kt` (currently ~830 lines, one class) by
moving domain logic and its untyped callback surface into small, testable units,
without changing runtime behavior.

Non-goals: no payload schema, Room, routing-choice, or BLE-ownership changes; no
new transports; no timer/retry tuning; no lifecycle-semantics changes
(generation ownership, connect-lock funnel, L2CAP promotion race stay exactly as
written). Behavior-preserving extraction only.

## Current problems (source-observed)

- Facade still owns domain logic: `sendSystemPulse` (delta-ping vs full-sync
  heuristic, 60s/hash gating, counters), `getElectionScore` (RAM/CPU formula),
  and the "VIP Bouncer" LRU eviction inside `sendDirectPayload`.
- ~25 public mutable `var` callback lambdas; `onMessageReceived` is a 13-argument
  lambda. Untyped, unencapsulated, hard to version.
- Circular coupling: `GattServerManager(context, this)` /
  `GattClientManager(context, this)` hold a back-reference to the facade and
  reach into its mutable fields.
- Manual dependency wiring in field initializers though Koin is the app DI.
- Encapsulation leaks: `store`, `gattServer`, `l2capServerSocket`, `myL2capPsm`
  are public/`var`.
- Nits: class-level `@SuppressLint("MissingPermission")`, wildcard
  `import android.bluetooth.*`, inline fully-qualified references, magic payload
  strings ("PING"/"SYSTEM"/"GOODBYE"/"SEEN"), magic numbers, silent
  `catch (e: Exception) {}` blocks, and a raw `Handler(mainLooper)` while the rest
  of the app uses coroutines.

## Constraints

1. Behavior-preserving; one slice per commit; each slice compiles and passes
   `:app:testDebugUnitTest` and `lintDebug`.
2. Facade public API stays stable until Phase 3.
3. Add characterization tests before extracting any currently untested logic.

## Phases

### Phase 0 - safety net (tests only)
Add JVM tests for the pure logic Phase 2 moves, exposing minimal seams (inject
`now`, RAM/cores, connection snapshot): `getElectionScore` formatting, the
delta-ping vs full-sync decision, and the LRU/VIP eviction selection. No refactor.

### Phase 1 - mechanical hygiene (no logic change)
Explicit imports (drop `android.bluetooth.*` wildcard and inline FQNs);
method-level permission suppression behind one `hasBlePermissions()` entry guard;
`MeshPayloadType` constants replacing magic strings; consolidate named timeout
constants; narrow and log the silent catch blocks.

### Phase 2 - extract domain logic
- `PresencePulsePlanner`: delta-ping/full-sync decision plus counters/timing.
- `MasterElection` (or fold into `BlePeerAdmissionController`): the score formula.
- `ConnectionAdmissionPolicy`: capacity/LRU eviction decision;
  `sendDirectPayload` calls it and acts on the returned decision.
Facade retains scheduling and radio I/O only.

### Phase 3 - typed event surface
Replace the ~25 `var` lambdas with a `NativeBleManagerListener` interface or a
sealed `BleEvent` on a `SharedFlow`; collapse `onMessageReceived` into a
`ReceivedMessage` data class. Update `MeshNetworkGateway`/`NativeBleGateway`
adapter in lockstep. Highest-value change; own reviewable slice.

### Phase 4 - encapsulate state and break the back-reference
Make `store` and socket fields private/internal behind intent-revealing methods;
replace the `(context, this)` manager back-reference with a narrow
`GattHostBridge` interface exposing only what the managers need.

### Phase 5 - Koin construction
Register the collaborators and `NativeBleManager` in a Koin module instead of
field-initializer wiring; inject a `CoroutineScope`/dispatcher instead of the raw
`Handler`, aligning with the existing `AppCoroutineScope`.

### Phase 6 - docs
Update `docs/architecture.md` source map/facade description and, if the event
surface becomes a durable rule, add a `docs/decisions.md` entry. Run the doc
check.

## Expected outcome

Facade roughly 830 to about 550-600 lines, with extracted logic in small
unit-tested classes. LOC reduction is real because logic leaves the class rather
than moving within it.

## Sequencing vs open blockers

`status.md` keeps SAMSUNG-01, BLE-QUEUE, and BLOCK-01 open (P0/P1). Phases 3-4
edit the same setup/cleanup paths under active debugging, so gate them behind
those blockers. Phases 0-2 are low-conflict and safe to start now.

## Per-phase validation

`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`, then
`:app:lintDebug`, then `git diff --check`. Phases touching send/receive/lifecycle
(2, 4) additionally require a Level-1 two-phone smoke plus a Level-2 reconnect run
before any reliability claim. A passing build is not BLE proof.

## Status

Not started. Recommended entry: Phase 0, then Phase 1.
  