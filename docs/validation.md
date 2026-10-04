# Validation

Last reviewed: 2026-10-04. Source baseline: `2e27013`, branch `refactor/mvvm-presentation-organization`.

Local checks, executed instrumentation, user reports and analyzed captures are different evidence. Users install and operate physical phones.

## Current candidate and user-reported outcomes

Report received 2026-10-04; actual test timestamps were not supplied. The user identified the current October 4 APK as the installed build for both successful tests and intermittent A-to-D loss.

- Version: 1.0.1/code 2, debug signing; Room 11.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `D4638F9C5835016E136A0A33E1A8F3457A48D9B455CB0F1AD876D296DAE4E686`.
- Local identity was rechecked during this documentation synchronization; the APK was not rebuilt.
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

The documented pre-existing untracked reconnect fixture blocks standard mixed-workspace unit-test compilation. Focused or clean-snapshot results do not waive this blocker. No Android suite or physical tests were run for the documentation-only synchronization.

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

For DELIVERY-01, preserve a focused failure report for later analysis. No root-cause investigation or network repair is authorized by this documentation work.
