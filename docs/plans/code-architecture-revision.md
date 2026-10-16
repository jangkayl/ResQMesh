# Code and architecture revision

Last reviewed: 2026-10-17. Status: active plan. Phase 0 is done locally on `refactor/batch-c-hygiene`; P1–P6 wait until after the capstone demo freeze. Each phase needs its own approval and a detailed plan before any code is edited.

## Why

A whole-app review (2026-10-17) found clean `core/network` layering, solid network and data tests, and working CI. It also found structural debt that makes change risky:

- `NativeBleManager` exposes callback slots and its internals publicly.
- Room entities reach Compose directly.
- Navigation is built from manual flags.
- Feature packages and ViewModel shapes are inconsistent.
- Release builds have no R8 and no signing.
- The private-envelope RSA wrap uses PKCS#1 v1.5 padding.

The rule for the demo window is to make behavior-neutral changes now and defer behavior-touching refactors.

## Phase 0: done (behavior-neutral)

| Step | Result |
| --- | --- |
| Wire guard | Golden ProtoBuf/JSON/frame tests (`WireFormatGoldenTest`) and Room table-name test. No polymorphic serialization exists, so package moves cannot change the wire. |
| Leftovers | Four non-compiling `*.kt.bak` tests deleted. Refactor-era scripts and the Figma preview archived. Device scripts in `scripts/device/`. Unused `G711Ulaw` removed. |
| Catalog | All dependency versions in `gradle/libs.versions.toml`; resolved trees identical. |
| DI | Koin root moved to `app/di/` and split into platform, network, database, repository, domain and ViewModel modules. Eager order preserved; `Module.verify` test added. |
| Packages | `core/utils` split into `core/audio`, `core/logging`, `core/notification` and `feature/sos/alert`. Migrations moved to `data/local/migrations`. Incident presentation moved to `feature/incident/model`. |
| CI gates | androidTest compile, detekt 1.23.8 with a baseline, and `scripts/ci/check_boundaries.ps1` with a shrink-only allowlist (4 core, 33 feature). Runs on push to `main`. |

Phase 0 exits when local checks are green and the [Batch C smoke card](../testing/batch-c-hygiene-smoke-card.md) passes on phones.

### Scenarios from the deleted `.bak` tests

These had no obvious current coverage. Port them as real tests in P2:

- WAITING_FOR_KEY / WAITING_FOR_ROUTE outbox states
- route-repair persistence
- late failure from an earlier attempt
- receipt reversal along the traversed route
- fail-closed receipt next-hop loss
- bounded receipt reroute
- GATT round-robin across endpoints

## Post-demo phases

**P1 Crypto and delivery (Batch B, strongest model tier).**
- Versioned private envelope v2: RSA-OAEP-SHA256 under a new Keystore alias, plus AES-GCM AAD binding version, sender, recipient and message ID.
- Keep v1 decryption for a migration window and advertise v2 capability.
- Fix the decrypt-failure custody retry.
- Confirm and document which media (voice, images) are encrypted.
- Exit: golden tests updated intentionally, and a mixed-version phone card.
- X25519/Ed25519 is the long-term direction ([D24](../decisions.md)).

**P2 Data boundary.**
- Add `MeshRepository`, `IncidentRepository` and `SosRepository` interfaces, plus entity→`core/model` mappers. Shrink the boundary allowlist to zero.
- Add a `ConversationStateStore` in place of the ViewModel DAO.
- Replace gateway `on…` slots with `SharedFlow<GatewayEvent>`.
- Use `WhileSubscribed`, read-only flows and a typed connection status.
- Remove `KoinJavaComponent` lookups from composables, and inject an audio scope instead of raw `CoroutineScope(Dispatchers.IO)`.

**P3 Presentation.**
- Type-safe Navigation Compose with `@Serializable` routes and one graph per feature, replacing the `MainContainerScreen` flags. Re-run UI-02.
- `SosViewModel`, with no public `sosRepository`.
- One `UiState` plus one event flow per ViewModel.
- Split screens over 400 lines into stateful and stateless parts with previews, and share the permission rows.
- Move strings to `strings.xml` and add semantic theme colors.
- Move SharedPreferences reads out of composables.

**P4 BLE internals (strongest model tier, full phone matrix).**
- Regroup `core/network/bluetooth` into radio, admission, session, link, heartbeat, transfer, codec, gatt and l2cap.
- Add a `BleConfig` for constants.
- Use per-link records instead of parallel maps in `BleStateStore`.
- Use one confined dispatcher.
- Inject `NativeBleManager` collaborators and make its internals private; send notifications as events.
- Scope permission suppression per call, and catch specific exceptions.

**P5 Release and dependencies.**
- R8 with keep rules (serialization, Room, MapLibre) and release signing from environment or local properties.
- applicationId rename: phones reinstall clean and keys reset.
- Upgrade one library family per commit: lifecycle/activity/core, then Room 2.7+, then Koin 4.
- Java 17.
- Isolated ktlint formatting commit.
- `MigrationTestHelper` migration tests.

**P6 Gradle modules.** Extract `:core:model`, `:core:domain`, `:core:data`, `:core:network` and `:feature:*` after the boundary allowlist reaches zero.

## Rules for every phase

- Use one branch per phase and one logical change per commit.
- Before review, these must pass: build, unit tests, Lint, androidTest compile, detekt, the boundary and docs checks, and `git diff --check`.
- Golden wire tests change only in P1.
- Device-facing phases end with a phone card that the user runs.
- No push or PR without explicit instruction.
