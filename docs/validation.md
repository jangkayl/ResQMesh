# Validation

Builds and unit tests validate local code paths; they do not prove BLE behavior. The user performs every physical-phone action. Codex prepares focused steps, analyzes targeted evidence, and maintains the concise results below.

## Local checks

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1
git diff --check
```

Use checks proportional to the change. Do not repeat broad tests after an unrelated documentation-only edit.

## Pull-request CI gate

`.github/workflows/lean-qa.yml` repeats the build, unit tests, documentation check, and diff hygiene on every pull request. Superseded runs for the same pull request are cancelled. Any failed command blocks readiness; AI review may explain the failure but cannot waive it. The workflow does not deploy, merge, run an emulator, or claim physical BLE validation.

After local checks pass, Codex reports the result and waits for explicit user approval before creating a pull request. Merging is always a separate explicit user action.

## Physical-device boundary

Codex may build an APK and report its path; producing an APK is not physical testing. The user installs it, launches/stops the app, changes phone settings, operates the UI, and performs the scenario. Codex does not operate physical phones unless explicitly requested for the current task.

Record the branch/commit or working-tree identity, APK build time, device models, Android versions, exact steps, observed UI result, and approximate failure time.

## Focused Logcat workflow

Start the existing capture script while the selected ADB devices are connected:

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10
```

The raw capture may contain broad Android buffers so crashes, process death, permissions, and OEM Bluetooth errors remain available. Analysis must begin with the reported time window and intentional app markers:

- Endpoint, stable peer, role, generation, and lifecycle transitions.
- CCCD/configuration completion and payload `READY`.
- Characteristic write and acknowledged-indication `onNotificationSent` completion/failure.
- Queue/in-flight changes and bounded timeouts.
- L2CAP open, EOF/write failure, ownership removal, and GATT fallback.
- L2CAP promotion of queued GATT work; after promotion, late/missing GATT callbacks must not retire the healthy link.
- Heartbeat challenge, send completion, acknowledgment, and timeout.
- Public-key receipt, missing-key refusal, and decrypt failure without content.
- Route creation/withdrawal, relay, dedupe, and delivery state.

Inspect `AndroidRuntime`, process lifecycle, permission errors, or system Bluetooth/GATT lines only when focused markers cannot explain the observation. Never load or summarize a whole capture, and never reproduce private plaintext, ciphertext previews, or key material.

## Test levels

### Level 1: two-phone smoke

Connect the same APK on two phones; confirm payload-ready status; send public and private text in both directions; verify each appears once and Radar states are honest.

### Level 2: lifecycle and recovery

Restart each app separately, toggle Bluetooth on one phone, move out of range and return, repeat reconnect three to five times, send during and immediately after recovery, and confirm stale endpoints/keys do not break the replacement link.

### Level 3: three-phone relay

Arrange A-B-C so A and C depend on B; send both directions, remove and restore B, verify route withdrawal/recovery, and check duplicates and direct-versus-indirect status.

### Level 4: release/capstone matrix

Across representative devices, measure delivery success, reconnect time, latency, range conditions, battery behavior, SOS correctness, private-message failure policy, and hardware-specific failures. Do not infer production guarantees from a single run.

## Required test card

For every device-facing change, Codex provides: build identity and APK path; devices/setup; exact steps; expected results; failure indicators; relevant markers; and the observations the user should report. If the test fails, continue the same task with its capture and timestamp.

## Latest verified results

Keep only meaningful milestones; raw captures remain under `captures/`.

| Date | Build/state | Devices | Scenario | Concise result | Capture |
| --- | --- | --- | --- | --- | --- |
| 2026-09-15 | Phase 1 working tree | V2424 API 34, CPH2219 API 31 | Initial setup and traffic | Both roles reached `READY`; initial transport traffic observed; no reconnect in the window | `captures/ble-logcat/20260915-231147/` |
| 2026-09-15 | Phase 1 working tree | V2424 API 34, CPH2219 API 31 | Reconnect and private text | One reconnect resumed traffic; asymmetric RSA failures exposed stale/missing-key behavior | `captures/ble-logcat/20260915-231605/` |
| 2026-09-16 | Liveness working tree | V2424 API 34, CPH2219 API 31 | Peer app restarts | Trace reproduced a stale `READY` client suppressing recovery; later fresh link/key exchange succeeded | `captures/ble-logcat/20260916-004633/` |
| 2026-09-16 | Inbound-progress recovery | CPH2219 API 31 plus second phone | Silent link recovery | Silent link retired after about 24 s; fresh client reached `READY` and received a key | `captures/ble-logcat/20260916-054345/` |
| 2026-09-16 | Presence build before heartbeat challenge | CPH2219 API 31, SM-P615 API 33 | L2CAP loss and reconnect | Old recovery took about 22.6 s plus about 6 s to replacement `READY`; user reported delayed response | `captures/ble-logcat/20260916-055141/` |
| 2026-09-16 | Acknowledged-indication working tree | CPH2219 API 31, CPH2127 API 31 | Repeated connect, traffic, and recovery | Links reached `READY` and private traffic decrypted, but overlapping GATT work timed out after L2CAP opened and locally triggered repeated teardown/reconnect | `captures/ble-logcat/20260916-223531/` |
| 2026-09-16 | L2CAP-promotion working tree | CPH2219 API 31, CPH2127 API 31 | Reconnects plus public, private, media, and block-related traffic | Six queued-transfer promotions and repeated bidirectional delivery completed without the earlier normal-traffic reconnect loop; one callback timeout followed the intentional block-related L2CAP disconnect, exposing a separate block-policy/cleanup defect | `captures/ble-logcat/20260916-230519/` |

This run supports the tested two-phone transport-promotion behavior only. It does not validate block semantics, three-phone routing, all disconnect paths, or production reliability.
