# Validation

Last reviewed: 2026-10-05. Branch: `fix/incident-ui-ux`; UI candidate based on `2c2b9fc`. Earlier presentation source baseline: `2e27013`.

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

The documented pre-existing untracked reconnect fixture blocks standard mixed-workspace unit-test compilation. Focused or clean-snapshot results do not waive this blocker. No Android suite or physical tests were run for the earlier documentation-only synchronization.

### Antigravity's recorded UI repair, October 4

Antigravity recorded debug build, Android-test compilation, Lint (zero errors, 147 warnings, three hints), docs and diff hygiene success. Its six source repairs removed unsupported readiness/hardware claims, restored search/advanced filters, clarified Critical only, normalized guidance, restored secondary actions and wrapped the card footer. These recorded results belong to that snapshot; they do not validate subsequent edits. Unit compilation remained blocked by 18 diagnostics in the preserved untracked `PrivateReconnectDeliveryTest.kt` (TEST-01).

Repaired candidate debug APK SHA-256: `11F6D282A3945CDFE4A5FEFDB436184A8DA20D8B2F56C4BC905D4CFF9E20AED3`, generated at `app/build/outputs/apk/debug/app-debug.apk`. Physical phone validation following the [repair card](testing/incident-ui-ux-repair-test-card.md) remains pending user execution.

### Provisional follow-up audit, October 4

The user confirmed ongoing editing. Three follow-up findings were historical-name Chat targets, obsolete UI-test selectors and missing quick-filter selected semantics. The combined check built an APK and reproduced TEST-01, then was interrupted; Lint completion/latest revision were unverified. No tests executed or physical phones operated. The reviewer made no app edits or commit. All 217 protected files and nine Activity/ViewModel bodies matched; the old structure guard failed only on eight stale archived-document/tool entries. Docs/diff hygiene passed.

Audit-generated APK SHA-256: `A918F5DF95890EE565B98DBBDFB9CB779D86DC524CE980DA91BB3D839B6ED3F4`. It predates subsequent edits and is not the final candidate. The earlier pre-repair audit APK was `E7FAE6292F3019D55F9DC6C2FF14B3F858C2E495C392F98B3A1049A0301ECCED`. Neither hash inherits the installed-build phone results above.

### Follow-up implementation, October 5

Helper Chat callbacks retain stable offer node IDs; self/missing-ID and reporter-name shortcuts are unavailable. Quick filters expose selection with 48dp targets; search has an accessible label. Updated instrumentation uses current labels and real filtering/search/reset, plus stable-ID chat cases. Large-text screenshots exposed a clipped helper-replacement button and truncated route text: the button now grows from 48dp and status wraps. Transport, repositories, schema, policies and ViewModels are unchanged.

`assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` completed in both the initial combined gate and final layout build. Lint: zero errors, 148 warnings, three hints. The combined `testDebugUnitTest --continue` gate failed at unit compilation with the same 18 TEST-01 diagnostics; no unit tests executed or fixtures excluded. The final standalone build used `--no-daemon --max-workers=1` with a 1GB JVM and in-process Kotlin compilation.

Final app APK SHA-256: `2EF834DED01EE73173DBB27D3E0FBB433D5CFEE2542B1E8C6DDD19A61127A097`; version 1.0.1/code 2, debug, existing L2CAP enabled. Android-test APK: `2B46D4A576CD2EC0047EA4BC0D3848DB2B2DE562179EC0ED26A88D649CD8EC8E`.

Final candidate: **18 incident UI tests passed**, none failed/ignored; **four focused cases passed again at 200% text** (Night/Daylight details, footer and real list filtering/search/reset). Coverage includes current labels, selected semantics, stable-ID callbacks, missing-ID guards, withdrawal/terminal fixtures, legacy acknowledgement and create/offer interactions. The layout check verifies visible glyph bounds/lost lines/ellipsis and the 48dp replacement control; a cached paragraph's unused width initially caused an overly strict overflow assertion to fail. Screenshots confirm the repaired label/status. Font scale and package states were restored, then the emulator was stopped. Logs/screenshots are in ignored `app/build/incident-ui-repair/`.

The earlier October 5 candidate `32B01BA7C62561B08DF440A52959A1CE681B01CA29F34BF523EC404EB289ED61` (test APK `A02EB9BD25C615EBAD9C3AE9CD5C9BB4FE7BEE1386BAF6EC3A9E0E7C0F65BC15`) passed 18 `IncidentUiTest` cases and four focused cases at 200% text on Medium_Phone/API 37. Its large-text screenshots prompted the final layout correction above. Initial startup attempts died before discovery with Android exit reason `LOW_MEMORY`; execution used 3GB emulator RAM with Google Play services, Android System Intelligence and Google Search temporarily disabled. This fixture evidence does not establish BLE, TalkBack or physical-phone behavior.

All 217 protected files and nine Activity/ViewModel bodies still match. The preserved structure guard fails only on the same eight stale inventory entries. A separate 455-file before/after manifest found no changes outside the three repair code/test files and affected docs. Implementation ended without staging, branch, commit or physical-phone operation. The user subsequently requested a local commit of 17 reviewed UI/navigation, test, README and documentation files on `fix/incident-ui-ux`; unrelated work remains uncommitted. This does not waive failed gates. Device behavior remains subject to the [repair card](testing/incident-ui-ux-repair-test-card.md).

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
