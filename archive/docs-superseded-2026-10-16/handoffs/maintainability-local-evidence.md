# Maintainability local evidence

Last reviewed: 2026-10-06. Local/emulator results below retain their run-time outcomes; October 6 final-candidate phone F1–F14 is user-reported PASS. DELIVERY-01 is open and outside this refactor.

### Initial network maintenance, October 5 (historical)

Source baseline: `db2260e` plus the current uncommitted structural change. Handlers, gateway adapter, presence publisher, live payload callbacks, repository bindings and GATT host contracts now have distinct owners. Queue, routing, generation, blocking, crypto, schema and timing policies remain preserved. The scoped guard compares moved bodies/order and protects unrelated tracked/untracked files.

Debug build, all 411 unit tests without exclusions, Android-test APK compilation and Lint passed. Eight new tests cover presence refresh/reset/withdrawal and live callback/argument forwarding. Lint has zero errors, 148 warnings and three hints; those totals match the pre-refactor report. Instrumentation was compiled, not executed.

The scope guard passed: 447 unrelated files unchanged, twelve handler bodies, 91 orchestration bodies, GATT callbacks and 23 repository bindings preserved. A negative check rejected a protected-source hash mismatch using an in-memory baseline copy; no source was modified. Docs, local links and diff hygiene passed. Original validation text is retained in the dated snapshot.

Debug APK SHA-256: `1D42E828EDAB73B398AF9B25655EE609A2E54B13EA1F92B8F3B14A01DF6E4224`; version 1.0.1/code 2, Room 11, L2CAP enabled. See the [network card](../testing/network-maintainability-test-card.md). The October 4 phone report does not apply to this candidate. No phones/emulator were operated; DELIVERY-01 stays deferred.

### Maintainability Phases 0–1, October 5

Fresh baseline: `app/build/maintainability-phases/2026-10-05-200635/baseline.json`, 478 tracked/untracked files and original bodies. The baseline build/unit task passed with 411 existing results retained. Earlier networking/presentation snapshots remain immutable; historical discrepancies were reconciled before extraction.

Phase 1 extracts ingress, outbound and frame reporting. Debug build, all 423 unit tests (zero failures/errors/skips), Android-test APK compilation and Lint passed. Twelve new cases cover ingress ownership/live callback order, relayed identity, transfer gates, queue rejection, L2CAP pressure/fallback and reporting. Lint remains zero errors, 148 warnings, three hints. No affected UI/Room code changed; instrumentation was compiled, not executed.

The phase guard passed: 468 unrelated files byte-identical; fifteen lifted declarations and 48 remaining orchestration bodies preserved with explicit substitutions; public method signatures, reviewed wiring, HEAD/index unchanged. Package/shared-UI/size checks pass, with no new import cycles. Existing notification/navigation imports retain one cycle; inspection is not compiler analysis. Negative guard checks reject protected-byte or moved-return-value changes. Docs, links and diff hygiene pass.

Historical Phase 1 APK SHA-256: `6C9997F7D5A40E043B96FD385A910BA3B88D62FFC25AE7D02DD7812978101875`. Version/schema/L2CAP settings match the baseline. The preserved comparison APK is `app/build/maintainability-phases/2026-10-05-200635/baseline-debug.apk`, hash `1D42E828...` above. N1/N3 remain UNTESTED under the [phase card](../testing/network-maintainability-test-card.md); The user subsequently deferred comparison until the final batch build. DELIVERY-01 remains open; no phone operation or diagnosis occurred.


## Later phase artifacts

Snapshots, APKs, original failed attempts and logs remain under `app/build/maintainability-phases/batched-2026-10-05/`; these ignored artifacts are local, not automatically included in Git handoffs. Each phase manifest in `scripts/refactor_scopes/` freezes reviewed source wiring and mapped bodies, public signatures and unrelated tracked/untracked bytes. HEAD/index stay unchanged. No test case was excluded or weakened.

| Phase | Debug APK SHA-256 | JVM tests | Lint errors/warnings/hints | Instrumentation |
| --- | --- | ---: | --- | --- |
| 2 | `B36A4665AC8BB436F56FA2BCC2E373B83811CEA5DAA655792F06706EF812BAC4` | 426 | 0/148/3 | Compiled; UNTESTED |
| 3a | `16C4C49A84D71C77B464544C4046A08650CCB357840E10202AC20ACFE3F956D7` | 426 | 0/148/3 | Compiled; UNTESTED |
| phase3b | `23B3207DED2AC368F249285D9963ABC21C643BED0C3AEB5391AFE5B717001CE5` | 426 | 0/148/3 | UNTESTED |
| phase4 | `A363B2A1235CFD734516A9CABEBFC5C510FB2F1EA558C70143584A93E380F9CF` | 426 | 0/148/3 | UNTESTED |
| phase5 | `710EA7F7798935EA50CDBE4AC6D5523C4B6AB50329A9D9BB05EC848D0D3D8BC4` | 426 | 0/148/3 | PASS: 4 tests, Medium_Phone API 37 emulator; Room delivery and server-device callback ownership |
| phase6 | `F2FEA2366CC2635A3E409548774BCD1EFD97495083AB45C4336F313240250467` | 426 | 0/148/3 | PASS: five incident Room cases (emulator) |
| phase7 | `1AE82B6CE482EBBF4415E64C4FD74FD103D888104A3790AA8EDCEA979441BFE0` | 426 | 0/149/3 | Compiled; 36/40 selected cases pass, four baseline failures; four repeated 200%-font cases pass |

All listed phases passed debug build, the complete unit suite and Android-test APK compilation. A compiled test APK is distinct from executed instrumentation. Physical outcomes, transport coverage and DELIVERY-01 diagnosis remain pending.

The Phase 5 receipt test now invokes the extracted delivery owner, retaining all timing/storage assertions. Original failure logs remain. The first emulator attempt exited before cases; a nearby low-memory exit was observed. The unchanged APK passed the subsequent Room-delivery/server-wrapper run (four cases); no production repair was made for emulator startup.

Architecture inspection is an import graph/navigation check rather than compiler dependency analysis. It preserves the existing notification/navigation cycle and rejects new cycles. New owners use explicit operations and shared instances; SOS reply capability retains the original nullable policy and suspend call.

Phase 6 retains its initial failed emulator logs. Activity exit information identified low-memory kills before cases. JUnit then exposed an existing `@Before` fixture returning a database ID; an explicit `Unit` return type fixes discovery while preserving setup/assertions. Full local gates reran successfully; the production APK hash stayed unchanged. The five-case run passed on the same API 37.1 AVD with its original 2 GiB RAM setting. No physical device was operated.


## Final batch verification and limits

All source phases are implemented. The final debug build, complete 426-case JVM suite (zero failures/errors/skips), Android-test APK compilation, Lint and preservation checks pass. The executed instrumentation gate is **not passing**: twenty incident UI cases and sixteen other UI/Room/callback cases pass; four navigation cases fail. No case was excluded, ignored or weakened. Four selected incident cases also pass with system font scale 2.0; the prior 1.0 setting was restored.

Two new cases verify report title/type/urgency restoration and an open help-offer editor/draft restoration. All original eighteen incident cases remain intact. Existing lifecycle/generation, registration, outbox single-flight/conflation, private storage/duplicates/notifications and incident replay coverage runs in the complete JVM suite. These are bounded checks, not device reliability proof.

### Existing navigation failures: UI-02

| Test | Observed failure on candidate and baseline |
| --- | --- |
| ResQAppShellTest.shell_exposesVoiceDestination_andDispatchesSelection | Mission node exists but is not displayed |
| SosNavigationTest.shellReminderReservesSpaceAboveNavigation | Content-bottom/reminder-top spacing assertion fails |
| SosNavigationTest.largeTextCompactThreadKeepsComposerAndControlsAccessible | Expected sos_controls node is absent |
| SosNavigationTest.keyboardControlsRemainOpenWhenKeyboardCloses | Expected sos_controls node is absent |

The same four unchanged test methods fail with the same assertions on the preserved pre-phase production APK 1D42E828... and final candidate 1AE82B6C..., using the same runner APK/emulator without clearing data. Exact hashes and protected-source comparisons are in phase7-navigation-comparison.json. ResQAppShell, SosThreadScreen, SosThreadHeader, PublicConversationScreen and both test files remain byte-identical to the fresh baseline. The baseline does not contain the expected SOS controls dialog; the visibility/spacing failures also predate these phases. This comparison identifies existing failures, not their full cause or a repair. They remain blocking final local acceptance; separate UI/test investigation is needed. No UI behavior or assertions were changed to make them pass.

Evidence remains in phase7-final.log, phase7-harness-checked.log, the four final runner logs and phase7-evidence.json in the batch directory. The AVD is Medium_Phone/API 37.1 x86_64, 16 KiB pages. Recorded low-memory process exits at 2 GiB led to a temporary 4 GiB command-line override and cold boot; AVD files and application data were preserved. Earlier failed attempts remain. No physical phone was operated.

The new restoration test initially attempted to scroll a pinned footer. Its interaction now asserts visibility and clicks; its draft/selection/submission assertions and original cases are unchanged. Lint has zero errors, 149 warnings and three hints. The additional warning duplicates the existing MaplessSosFallback parameter-order warning on its internal delegate; original public ordering remains.

The final phase guard checks 85 mapped chunks, public method/default preservation, reviewed wiring, package alignment and dependency boundaries; all 508 unrelated files at its snapshot remain byte-identical. The fresh-baseline union scope preserves 443 unrelated tracked/untracked files and unchanged HEAD/index. Historical modes/baselines remain intact. The existing notification/navigation import-cycle component is retained; no new component appeared. Twenty of the original 21 oversized sources are below 500; NativeBleManager's 743-line public-host/construction exception is reviewed in the size ledger.

Active offline MapView lifetime, real GATT/L2CAP, OEM/background behavior, TalkBack and all physical comparisons remain pending in the [final phone card](../testing/network-maintainability-test-card.md). Compilation is separate from executed instrumentation and phone evidence. DELIVERY-01 remains open; no diagnosis or repair was included. No commit, push or PR was made.

## October 6 supplied physical results

The user confirmed final-candidate F1–F14 PASS, explicitly identifying APK `1AE82B6C...`. Its full hash is `1AE82B6CE482EBBF4415E64C4FD74FD103D888104A3790AA8EDCEA979441BFE0`; the saved APK was rehashed before recording this report. The [phone card](../testing/network-maintainability-test-card.md) now labels every row as user-reported PASS. Phone models/API levels, actual test dates/times, counts, durations, captures and transport coverage remain unspecified. No phone was operated by Codex.

These supplied results supersede the physical UNTESTED state at local handoff for the final candidate only. Earlier phase identities and run-time outcomes stay historical. No automated rerun was reported: four baseline UI-02 assertions still block final local acceptance. DELIVERY-01 remains open; the successful report is not a diagnosis or repair of intermittent loss.

## October 6 progress checkpoint

The user explicitly requested a local commit to save progress. Its scope is 182 source/test, documentation, archive and preservation-check files, including JVM fixture support needed for the full suite. Four test backups, the device script and three Figma files remain untracked and unchanged. APKs, logs and immutable snapshots stay in their ignored local directories; they are not Git artifacts. No push or PR is included.

Preservation, documentation and staged-diff checks passed before the checkpoint. The earlier phase snapshots still retain their original HEAD/index identities; their historical checks are not rewritten to hide the intentional Git checkpoint. Source and unrelated working-file bytes are checked separately during commit verification. The four baseline UI-02 failures and DELIVERY-01 remain open; this saves progress rather than granting release acceptance.
