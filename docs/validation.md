# Validation

Last reviewed: 2026-10-16. Baseline: see [status](status.md). This page owns build identities, counts and hashes.

Local checks, executed instrumentation, user reports and analyzed captures are different evidence.

## Current candidate (October 6)

- APK SHA-256: `1AE82B6CE482EBBF4415E64C4FD74FD103D888104A3790AA8EDCEA979441BFE0` (debug, version 1.0.1/code 2, Room 11), built from the maintainability refactor merged as `a9a27ed`.
- User-reported **F1–F14 PASS** on October 6 in the [network card](../archive/docs-superseded-2026-10-16/testing/network-maintainability-test-card.md): direct/relay messaging, receipts, notes, recovery, blocking/channels, persistence, incident/SOS, drafts, maps and accessibility. On 2026-10-16 the user confirmed this also covered the [incident UI repair card](../archive/docs-superseded-2026-10-16/testing/incident-ui-ux-repair-test-card.md) phone cases.
- Local: debug/instrumentation builds, 426 JVM tests, Lint (zero errors) and preservation checks pass. Of 40 selected emulator cases, 36 pass; four navigation assertions fail on both this build and the pre-refactor APK (UI-02).
- Not supplied: test times, phone models/API levels, counts, durations, transport evidence or captures. Reported successes do not close [DELIVERY-01](status.md) or establish quantitative reliability.

Phase snapshots, declaration maps and historical APK identities are in the [local evidence ledger](../archive/docs-superseded-2026-10-16/handoffs/maintainability-local-evidence.md).

## Earlier user-reported outcomes (October 4, historical)

Reported on the October 4 APK `D4638F9C5835016E136A0A33E1A8F3457A48D9B455CB0F1AD876D296DAE4E686` (debug, 1.0.1/code 2, Room 11). Test times, models, counts, transports and captures were not supplied.

| Scenario | User-reported result | Limit |
| --- | --- | --- |
| A–B–C–D text, notes and SOS | Successful public/private arrival, returning DELIVERED/READ, complete recorded playback and SOS arrival confirmed | Intermittent loss at D; see [DELIVERY-01](status.md) |
| Recovery | App restart, relay return and Bluetooth OFF/ON work | Repetition counts, recovery times and callback-race coverage unspecified |
| Background | Locked/background messaging works | Duration, settings/OEM matrix, process death and battery not established |
| Blocking | Mutual direct denial, relaying, restart persistence and independent unblock reported working | Protocol loss/retry and exact repeated-card coverage unspecified |
| Incidents and SOS | Full helper lifecycle/recovery and independent simultaneous-alert cancellation confirmed | Quantitative convergence, load and authority-abuse tests unspecified |
| Offline maps | Offline map and SOS map display work without internet | Corruption, interrupted update, rollback and storage matrix unconfirmed |

This is a qualitative milestone, not a full PASS for every procedure or a measured delivery rate.

## Local checks

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1
git diff --check
```

## Historical local milestones

Full October 4–5 audit narratives and APK hashes are in the [October 5 validation snapshot](../archive/docs-superseded-2026-10-05/README.md).

| Historical snapshot | Recorded evidence |
| --- | --- |
| Incident UI follow-up `db2260e`, October 5 | Debug/Android-test builds and Lint (zero errors, 148 warnings); 18 incident UI tests and four 200%-text cases passed on API 37 emulator. |
| Batch A cleanup, October 16 | 426/426 JVM tests, Lint (0 errors), Android-test compilation; reviewer PASS, auditor APPROVE. Run with `--max-workers=1` and a 1.5 GB heap after out-of-memory daemon crashes on a 7.7 GB machine. User reported all smoke-card steps working on 2026-10-16; APK hash, phone models/API, counts and captures not supplied, and the optional relay step does not close DELIVERY-01. |
| Reconnect fixture repair (TEST-01), October 5 | 403 tests without exclusions, including 29 reconnect cases and three JVM Base64 checks; production unchanged. |
| Samsung repair, October 3 | Clean snapshot: 280 unit tests and debug build; Android tests compiled. Earlier 350 isolated tests included unrelated fixtures. See [Samsung card](testing/samsung-send-test-card.md). |
| Voice repair, October 3 | Clean snapshot: 348 unit tests without exclusions, builds, Android-test compilation and Lint. See [voice card](testing/ble-voice-transfer-test-card.md). |
| SOS UI/storage, October 2 | 248 unit tests plus eleven executed Medium_Phone/API 37 migration/navigation instrumentation tests; later device behavior requires its own evidence. |
| Bluetooth recovery, October 2 | 266 unit tests, build, Android-test compilation and Lint; see [recovery card](testing/bluetooth-recovery-test-card.md). |
| Presentation organization, October 4 | Builds, Lint and 24 focused presentation tests; full mixed-workspace suite remained blocked. See [organization card](../archive/docs-superseded-2026-10-16/testing/structure-organization-test-card.md). |

Original September capture results, paths, Samsung reports, and historical procedures remain in [validation before synchronization](../archive/docs-superseded-2026-10-04/evidence/validation-before-sync.md). Older cards and hashes are indexed in the [dated archive](../archive/docs-superseded-2026-10-04/README.md). They identify historical snapshots, not the current candidate.

## Active physical procedures

| Procedure | Coverage |
| --- | --- |
| [Physical checklist](testing/physical-reliability-tests.md) | Block/route recovery, identity/receipt guards, ordinary versus segmented traffic, restart custody, load, scale and other feature regressions |
| [Voice/relay](testing/ble-voice-transfer-test-card.md) | Direct/relay notes, bursts, joining, fallback, recovery and arrival versus confirmation |
| [Recovery](testing/bluetooth-recovery-test-card.md) | OFF/ON, isolation, cluster merge, permissions, background and Go offline |
| [Samsung](testing/samsung-send-test-card.md) | Samsung roles/transports, private receipts and reconnect |
| [Batch A smoke](testing/batch-a-cleanup-smoke-card.md) | No-behavior-change regression check after the Batch A cleanup |
| [SOS/conversations](testing/sos-conversations-test-card.md) | Channel isolation, independent alerts, reconnect terminal state, gestures and UI |
| [Capstone](plans/capstone-demo-readiness.md) | Reporter/helper roles, ownership, withdrawal/offline revision and demo/release evidence |

Existing diagnostic APKs are historical candidates. The normal build enables L2CAP; `-PbleL2cap=false` produces a GATT comparison build when separately authorized. Use one matching variant on every participant; log actual transport. An open socket or label does not prove payload transport.

## Focused capture and reporting

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10
```

Report test ID, APK hash, A/B/C/D roles/models/API, topology, blocks, permissions/background state, sent/arrived/confirmed/duplicate counts, exact failure time and capture folder. Mark each exact scenario PASS, FAIL or UNTESTED; a qualitative success does not check all repeated steps.

Search application markers and the reported window first: READY/generation, identity admission, FRAME/TRANSFER progress, route withdrawal, PRIVATE_STORED/RECEIPT/DELIVERED, INCIDENT_SYNC and SOS_SYNC. Expand to system logs only when needed. Never reproduce message/audio content, ciphertext previews, credentials, keys or sensitive location.

Measure recipient arrival, receipt confirmation and playback separately. Acceptance and hop custody are not recipient delivery. Cross-phone timing needs a common video/clock; unrelated monotonic clocks cannot be subtracted.

For [DELIVERY-01](status.md), preserve a focused failure report for later analysis; root-cause investigation needs separate authorization.
