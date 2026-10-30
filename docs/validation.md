# Validation

Last reviewed: 2026-10-16. Baseline: see [status](status.md). This page owns build identities, counts and hashes.

Local checks, executed instrumentation, user reports and analyzed captures are different evidence.

## Current candidate (#2, October 17)

- APK SHA-256: `B84ACB1B3D6576ED9E081236DD23EC792DD3B445F4A5BA93A869FB52D6B2EA96` (release, R8, version 1.0.1/code 2, Room 11), built from `42f1660` on `main`. adb confirmed this hash on Samsung SM-P615 (API 33), OPPO CPH2127 (API 31) and vivo V2424 (API 34).
- The first R8 release crashed at startup (`AppDatabase_Impl()` removed); `42f1660` adds a keep rule.
- On 2026-10-17 the user reported all nine [security Phase A card](testing/security-phase-a-card.md) steps PASS on those phones. The user then reported [demo freeze](testing/demo-freeze-card.md) part 1 (startup, not timed) and part 2 (D1–D8) as working; the five-repeat counts, times and captures were not supplied. The local gate passes.
- Previous candidate: the October 6 debug APK `1AE82B6C…41BFE0` (`a9a27ed`). The user reported **F1–F14 PASS** in the [network card](../archive/docs-superseded-2026-10-16/testing/network-maintainability-test-card.md), which also covered the [incident UI repair card](../archive/docs-superseded-2026-10-16/testing/incident-ui-ux-repair-test-card.md). 36 of 40 emulator cases passed (UI-02). Successes do not close [DELIVERY-01](status.md).

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
.\gradlew.bat :app:lintDebug :app:assembleDebugAndroidTest detekt --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\ci\check_boundaries.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\ci\check_docs.ps1
git diff --check
```

Signed release APK: copy `keystore.properties.example` to the gitignored `keystore.properties` (keystore kept outside the repository), then run `.\gradlew.bat :app:assembleRelease`; without it the APK is unsigned. Debug and release signatures differ, so switching clears data. `scripts\device\manage_devices.ps1` options 5/6 offer Debug or Release and replace a differently signed variant only after typed `YES`; options 9/10 clear data/uninstall everywhere. It launches with `am start` because `monkey` re-enables auto-rotate. Options 5/6 package target-phone ABIs; share plain Gradle APKs ([card](testing/fast-device-install-card.md)).

## Historical local milestones

Full October 4–5 audit narratives and APK hashes are in the [October 5 validation snapshot](../archive/docs-superseded-2026-10-05/README.md).

| Historical snapshot | Recorded evidence |
| --- | --- |
| Incident sync capture, October 29 | User-run debug capture on V2424, SM-A236E and CPH2127 (build hash not supplied): limiter drops of `EVENT_SYNC_REQ` stalled repair; missed help changes stayed `DEPENDENCY_PENDING`. [Card](testing/incident-reconnect-sync-card.md). |
| Release signing and device manager, October 17 | Signed release APK verified with apksigner (v2, RSA 2048); script parse and docs checks pass. User reported the updated device manager working on OPPO CPH2127 and Samsung SM-P615. ColorOS refused clear data until Developer options allowed it; uninstall worked. Per-step results, API levels and auto-rotate observations not supplied. |
| Incident UI follow-up `db2260e`, October 5 | Debug/Android-test builds and Lint (zero errors, 148 warnings); 18 incident UI tests and four 200%-text cases passed on API 37 emulator. |
| App ID rename (`edu.cit.resqmesh`), October 17 | Clean debug/Android-test builds, 461/461 JVM tests (golden wire tests unchanged), Lint, detekt, boundary and docs checks pass. The APK package was verified with aapt2, and the reviewer found only the intended replacement. User reported all [rename card](testing/app-id-rename-card.md) steps passing on three phones after a fresh install; APK `828FED8D…403E36` from `c269739`. Counts and captures were not supplied. |
| Layer boundaries (P2 subset), October 17 | Debug/Android-test builds, 461/461 JVM tests (new mapper, `IncidentRules` parity, adapter and use-case tests), Lint, detekt, boundary check (0 allowlisted, with qualified-name scan) and docs pass; reviewer found no behavior regressions. `core/network`, entities and DAOs unchanged. User reported all [smoke card](testing/layer-boundaries-smoke-card.md) steps passing on 2026-10-17 on vivo V2424 (API 34), Samsung SM-P615 (API 33) and OPPO CPH2127 (API 31); APK `EB2A86F9…DFE4222` from source `62c62fc`. Per-step notes, counts and captures not supplied; the optional relay step does not close DELIVERY-01. |
| Startup lag (PERF-01), October 17 | Debug/Android-test builds, all JVM tests (including new `FlowThrottleTest`), Lint, detekt, boundary check and docs pass; committed as `3b234bf`, debug APK `5846FAD2…6170`. Agent-run adb measurement on one OPPO CPH2127 (API 31, 90 Hz, no peer nearby), 3 cold starts after a warm-up: TotalTime 3.24–3.37 s, about 280 skipped frames in 4 Choreographer bursts, Home idle jank 0.6–0.9%. Logcat showed a 0.9 s pre-frame main-thread block and slow first compositions. The same source as an unminified release build signed with the local debug key (`A92670E4…D774`, not committed config) gave TotalTime 0.79–0.90 s, about 61 skipped frames in 2 bursts and Home jank 0–0.07%. Most of the felt lag is debug-build overhead. Single phone, so the mesh steps are untested on both variants. See the [startup lag card](testing/startup-lag-card.md). |
| Batch C hygiene, October 17 | Debug/Android-test builds, all JVM tests (including new wire-format and table-name golden tests and Koin `Module.verify`), Lint, detekt (3,176 baselined issues), boundary check (37 allowlisted) and docs pass. Dependency trees identical before/after the catalog move. User reported all smoke-card steps working on 2026-10-17 for prepared build `f3661cc` (APK `1A734DAF…7BA9D3`); models/API, counts and captures not supplied, and the optional relay step does not close DELIVERY-01. |
| Batch A cleanup, October 16 | 426/426 JVM tests, Lint (0 errors), Android-test compilation; reviewer PASS, auditor APPROVE. Run with `--max-workers=1` and a 1.5 GB heap after out-of-memory daemon crashes on a 7.7 GB machine. User reported all smoke-card steps working on 2026-10-16; APK hash, phone models/API, counts and captures not supplied, and the optional relay step does not close DELIVERY-01. |
| Reconnect fixture repair (TEST-01), October 5 | 403 tests without exclusions, including 29 reconnect cases and three JVM Base64 checks; production unchanged. |
| Samsung repair, October 3 | Clean snapshot: 280 unit tests and debug build; Android tests compiled. See [Samsung card](testing/samsung-send-test-card.md). |
| Voice repair, October 3 | Clean snapshot: 348 unit tests without exclusions, builds, Android-test compilation and Lint. See [voice card](testing/ble-voice-transfer-test-card.md). |
| SOS UI/storage, October 2 | 248 unit tests plus eleven executed Medium_Phone/API 37 migration/navigation instrumentation tests. |
| Bluetooth recovery, October 2 | 266 unit tests, build, Android-test compilation and Lint; see [recovery card](testing/bluetooth-recovery-test-card.md). |
| Presentation organization, October 4 | Builds, Lint and 24 presentation tests; full suite blocked. See [organization card](../archive/docs-superseded-2026-10-16/testing/structure-organization-test-card.md). |

Original September capture results, paths, Samsung reports, and historical procedures remain in [validation before synchronization](../archive/docs-superseded-2026-10-04/evidence/validation-before-sync.md). Older cards and hashes are indexed in the [dated archive](../archive/docs-superseded-2026-10-04/README.md). They identify historical snapshots, not the current candidate.

## Active physical procedures

| Procedure | Coverage |
| --- | --- |
| [Physical checklist](testing/physical-reliability-tests.md) | Block/route recovery, identity/receipt guards, ordinary versus segmented traffic, restart custody, load, scale and other feature regressions |
| [Voice/relay](testing/ble-voice-transfer-test-card.md) | Direct/relay notes, bursts, joining, fallback, recovery and arrival versus confirmation |
| [Recovery](testing/bluetooth-recovery-test-card.md) | OFF/ON, isolation, cluster merge, permissions, background and Go offline |
| [Samsung](testing/samsung-send-test-card.md) | Samsung roles/transports, private receipts and reconnect |
| [Batch A smoke](testing/batch-a-cleanup-smoke-card.md) | No-behavior-change regression check after the Batch A cleanup |
| [Batch C smoke](testing/batch-c-hygiene-smoke-card.md) | Startup DI, audio, SOS alert, notification and upgrade-install regression check after Batch C |
| [SOS/conversations](testing/sos-conversations-test-card.md) | Channel isolation, independent alerts, reconnect terminal state, gestures and UI |
| [Capstone](plans/capstone-demo-readiness.md) | Reporter/helper roles, ownership, withdrawal/offline revision and demo/release evidence |

Existing diagnostic APKs are historical candidates. The normal build enables L2CAP; `-PbleL2cap=false` produces a GATT comparison build when separately authorized. Use one matching variant on every participant; log actual transport. An open socket or label does not prove payload transport.

## Focused capture and reporting

```powershell
.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 10
```

Report test ID, APK hash, A/B/C/D roles/models/API, topology, blocks, permissions/background state, sent/arrived/confirmed/duplicate counts, exact failure time and capture folder. Mark each exact scenario PASS, FAIL or UNTESTED; a qualitative success does not check all repeated steps.

Search application markers and the reported window first: READY/generation, identity admission, FRAME/TRANSFER progress, route withdrawal, PRIVATE_STORED/RECEIPT/DELIVERED, INCIDENT_SYNC and SOS_SYNC. Expand to system logs only when needed. Never reproduce message/audio content, ciphertext previews, credentials, keys or sensitive location.

Measure recipient arrival, receipt confirmation and playback separately. Acceptance and hop custody are not recipient delivery. Cross-phone timing needs a common video/clock; unrelated monotonic clocks cannot be subtracted.

For [DELIVERY-01](status.md), preserve a focused failure report for later analysis; root-cause investigation needs separate authorization.
