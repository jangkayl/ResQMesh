# Validation

Last reviewed: 2026-10-06. Branch: `fix/incident-ui-ux`; networking baseline `db2260e`. Earlier presentation baseline: `2e27013`.

Local checks, executed instrumentation, user reports and analyzed captures are different evidence. Users install and operate physical phones.

## User-reported installed candidate and outcomes

Report received 2026-10-04; actual test timestamps were not supplied. The user identified the current October 4 APK as the installed build for both successful tests and intermittent A-to-D loss.

- Version: 1.0.1/code 2, debug signing; Room 11.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `D4638F9C5835016E136A0A33E1A8F3457A48D9B455CB0F1AD876D296DAE4E686`.
- The earlier documentation synchronization rechecked this APK's identity without rebuilding. A later UI audit generated a different candidate, recorded below.
- Phone models/API levels, attempt counts, exact dates/durations, distances, per-leg transports, measured delays and captures were not supplied. Do not infer them from older cards or screenshots.

| Scenario | User-reported result | Limit |
| --- | --- | --- |
| A–B–C–D text, notes and SOS | Successful public/private arrival, returning DELIVERED/READ, complete recorded playback and SOS arrival confirmed | Public/private messages or voice sometimes fail to reach D; DELIVERY-01 remains open |
| Recovery | App restart, relay return and Bluetooth OFF/ON work | Repetition counts, recovery times and callback-race coverage unspecified |
| Background | Locked/background messaging works | Duration, settings/OEM matrix, process death and battery not established |
| Blocking | Mutual direct denial, relaying, restart persistence and independent unblock reported working | Protocol loss/retry and exact repeated-card coverage unspecified |
| Incidents and SOS | Full helper lifecycle/recovery and independent simultaneous-alert cancellation confirmed | Quantitative convergence, load and authority-abuse tests unspecified |
| Offline maps | Offline map and SOS map display work without internet | Corruption, interrupted update, rollback and storage matrix unconfirmed |

This is a qualitative milestone, not a full PASS for every procedure or a measured delivery rate. Public-key handling, forwarding and blocking are possible explanations suggested by the user for DELIVERY-01, not diagnosed causes.

## Local checks and historical milestones

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1
git diff --check
```

Earlier mixed-workspace unit compilation was blocked by the untracked reconnect fixture. Its separately authorized repair is recorded below. No Android suite or physical tests ran for the documentation-only synchronization.

### October 4–5 UI and fixture snapshots

Full audit narratives, historical APK hashes and original limitations are preserved in the [October 5 validation snapshot](../archive/docs-superseded-2026-10-05/README.md).

The committed `db2260e` UI follow-up retains stable-ID helper Chat, accessible selected filters/search, wrapped status and growing helper controls. Debug/Android-test APK builds and Lint passed: zero errors, 148 warnings, three hints. On API 37, eighteen incident UI tests and four focused cases at 200% text passed. These are emulator results; physical execution remains pending under the [UI repair card](testing/incident-ui-ux-repair-test-card.md).

The separately authorized reconnect fixture repair passed all 403 tests without exclusions, including 29 reconnect cases and three JVM Base64 checks. Production/configuration was unchanged; TEST-01 is resolved locally. Historical presentation-guard discrepancies are now accounted for in the [size ledger](handoffs/maintainability-size-inventory.md); its original baseline remains unchanged.

### Maintainability refactor, October 5

Immutable source snapshots, reviewed declaration maps, full historical APK identities and phase results are preserved in the [local evidence ledger](handoffs/maintainability-local-evidence.md). Original baseline/presentation modes still describe their historical snapshots; newer phases use their own explicit scopes. Historical full-suite blockers above remain recorded.

Phases 0–7 source work is complete. Final debug/instrumentation builds, all 426 JVM tests, Lint and preservation checks pass. Of forty selected emulator cases, 36 pass and four navigation assertions fail; the same four fail on the saved pre-phase APK with unchanged sources/tests (UI-02). Four repeated 200%-font incident cases pass. Exact build identities/results are in the ledger; final local acceptance remains blocked.

The user deferred intermediate phone gates until the final batch build. October 4 reports do not validate refactor APKs. Builds and emulator checks do not establish BLE delivery or transport coverage.

### October 6 final-candidate phone report

The user confirmed **F1–F14 PASS** in the [network card](testing/network-maintainability-test-card.md) on the final APK whose hash starts `1AE82B6C`. The preserved APK rehashes to `1AE82B6CE482EBBF4415E64C4FD74FD103D888104A3790AA8EDCEA979441BFE0`. This covers the card's direct/relay messaging, receipts, notes, recovery, blocking/channels, persistence, incident/SOS, drafts, maps and accessibility scenarios as user-reported results.

Report received October 6; actual test times, phone models/API levels, counts, durations, transport evidence and captures were not supplied. No automated rerun was reported. The four recorded UI-02 emulator failures remain unresolved. Successful reported runs do not close intermittent DELIVERY-01 or establish quantitative reliability.

| Historical snapshot | Recorded evidence |
| --- | --- |
| Samsung repair, October 3 | Clean snapshot: 280 unit tests and debug build; Android tests compiled. Earlier 350 isolated tests included unrelated fixtures. See [Samsung card](testing/samsung-send-test-card.md). |
| Voice repair, October 3 | Clean snapshot: 348 unit tests without exclusions, builds, Android-test compilation and Lint. See [voice card](testing/ble-voice-transfer-test-card.md). |
| SOS UI/storage, October 2 | 248 unit tests plus eleven executed Medium_Phone/API 37 migration/navigation instrumentation tests; later device behavior requires its own evidence. |
| Bluetooth recovery, October 2 | 266 unit tests, build, Android-test compilation and Lint; see [recovery card](testing/bluetooth-recovery-test-card.md). |
| Presentation organization, October 4 | Builds, Lint and 24 focused presentation tests; full mixed-workspace suite remained blocked. See [organization card](testing/structure-organization-test-card.md). |

Original September capture results, paths, Samsung reports, and historical procedures remain in [validation before synchronization](../archive/docs-superseded-2026-10-04/evidence/validation-before-sync.md). Older cards and hashes are indexed in the [dated archive](../archive/docs-superseded-2026-10-04/README.md). They identify historical snapshots, not the current candidate.

PR CI runs build/unit tests, Lint, docs and diff hygiene. Failed deterministic checks block readiness; CI does not establish physical BLE behavior. PR/merge requires explicit instruction.

## Active physical procedures

| Procedure | Coverage |
| --- | --- |
| [Networking maintenance](testing/network-maintainability-test-card.md) | Direct/relay messaging, live identity, recovery, blocks, background and incident comparisons after structural extraction |
| [Physical checklist](testing/physical-reliability-tests.md) | Block/route recovery, identity/receipt guards, ordinary versus segmented traffic, restart custody, load, scale and other feature regressions |
| [Voice/relay](testing/ble-voice-transfer-test-card.md) | Direct/relay notes, bursts, joining, fallback, recovery and arrival versus confirmation |
| [Recovery](testing/bluetooth-recovery-test-card.md) | OFF/ON, isolation, cluster merge, permissions, background and Go offline |
| [Samsung](testing/samsung-send-test-card.md) | Samsung roles/transports, private receipts and reconnect |
| [SOS/conversations](testing/sos-conversations-test-card.md) | Channel isolation, independent alerts, reconnect terminal state, gestures and UI |
| [Presentation](testing/structure-organization-test-card.md) | Navigation, drafts/history, notifications, session and appearance regressions |
| [Capstone](plans/capstone-demo-readiness.md) | Reporter/helper roles, ownership, withdrawal/offline revision and demo/release evidence |

Existing diagnostic APKs are historical candidates. The normal build enables L2CAP; `-PbleL2cap=false` produces a GATT comparison build when separately authorized. Use one matching variant on every participant; log actual transport. An open socket or label does not prove payload transport.

## Focused capture and reporting

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10
```

Report test ID, APK hash, A/B/C/D roles/models/API, topology, blocks, permissions/background state, sent/arrived/confirmed/duplicate counts, exact failure time and capture folder. Mark each exact scenario PASS, FAIL or UNTESTED; a qualitative success does not check all repeated steps.

Search application markers and the reported window first: READY/generation, identity admission, FRAME/TRANSFER progress, route withdrawal, PRIVATE_STORED/RECEIPT/DELIVERED, INCIDENT_SYNC and SOS_SYNC. Expand to system logs only when needed. Never reproduce message/audio content, ciphertext previews, credentials, keys or sensitive location.

Measure recipient arrival, receipt confirmation and playback separately. Acceptance and hop custody are not recipient delivery. Cross-phone timing needs a common video/clock; unrelated monotonic clocks cannot be subtracted.

For DELIVERY-01, preserve a focused failure report for later analysis. Root-cause investigation and network repair remain outside this maintenance refactor.
