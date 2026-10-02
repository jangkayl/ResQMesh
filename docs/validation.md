# Validation

Builds/tests do not prove BLE. Users operate phones; Codex prepares/analyzes cards. Map setup returns to its alert.

## Local checks

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1
git diff --check
```

SOS conversation validation: [phone card](testing/sos-conversations-test-card.md). Run migration instrumentation only on an explicitly selected emulator; physical-phone operation stays with the user.

## Pull-request CI gate

`.github/workflows/lean-qa.yml` runs build, unit tests, Lint, docs, and diff hygiene on pull requests. Failed checks block readiness. CI neither deploys nor validates physical BLE behavior.

PR/merge requires explicit instruction after local checks.

## Physical-device boundary

Codex may build an APK. The user installs and operates physical phones unless explicitly requesting otherwise.

Record APK, models/API, steps, result, failure time.

## Focused Logcat workflow

Start the existing capture script while the selected ADB devices are connected:

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10
```

Analyze focused app markers/time windows:

- Endpoint, stable peer, role, generation, and lifecycle transitions.
- CCCD/configuration completion and payload `READY`.
- Characteristic write and acknowledged-indication `onNotificationSent` completion/failure.
- Queue/in-flight changes and bounded timeouts.
- L2CAP open, EOF/write failure, ownership removal, and GATT fallback.
- L2CAP promotion; late GATT callbacks must not retire the link.
- Heartbeat challenge, send completion, acknowledgment, and timeout.
- Public-key receipt, missing-key refusal, and decrypt failure without content.
- Route creation/withdrawal, relay, dedupe, and delivery state.
- Stable-ID route, key trust/change, and route-unavailable decisions; private traffic must not broadcast afterward.

Expand to system logs only if app markers are insufficient. Never summarize whole captures or reproduce sensitive data.

## Test levels

### Level 1: two-phone smoke

Connect two phones with the same APK; confirm `READY`, public/private text once each, and Radar status.

### Radio recorded-note card

Record APK/devices. With READY and Radio Monitor on, send three overlapping notes; check full ordered playback and speaker names, including equal display names. Play a Messages note: check manual priority and Radio resumption without overlap. Disable monitoring mid-note, send while off, re-enable: expect no backlog. Report missing/repeated notes.

### First-launch guide card

Record APK/device/API. Clear data and open: expect splash, three guide pages, permissions, name, Home. Check Back, Skip, orientation, themes, Settings replay. Reopen: guide stays hidden. SOS/chat notifications during setup must reach their destination afterward; next normal launch shows the guide. Report stale screens, lost destinations, or unexpected mesh starts.

### Level 2: reconnect

Use the [Bluetooth recovery card](testing/bluetooth-recovery-test-card.md) for toggles, isolation, cluster bridges, and background recovery. Record timings, receipts, and captures.

### Level 3: three-phone relay

Arrange A-B-C so A and C depend on B; send both directions, remove and restore B, verify route withdrawal/recovery, and check duplicates and direct-versus-indirect status.

For A-B-C-D, make A/D indirect, send five private messages each way, then reconnect one relay. Record next hop, receipt, and GATT retirement; failed routes must not private-broadcast or loop.

### Reporter-selected helper card

Record APK hash/version, devices/API, topology, timestamps. A reports; B/C offer. Rename A before selection, while awaiting, after confirmation: ownership persists with original report name. Selection locks B's editing; withdrawal removes B and permits C. Repeat withdrawal offline and restart without clearing. B edits offline before learning selection; reconnect: clear selection, require fresh approval. Re-offer never restores selection; disconnection preserves it; history stays closed. Report stale controls, rejections, duplicates, convergence. Capture `IDENTITY`, `INCIDENT_IDENTITY`, `INCIDENT_HELP`, `INCIDENT_REPO`, `READY`; omit names/notes/keys. Phone validation remains open.

### Incident closure replay card

Record devices/API/timestamps. Same build on reporter A/helper B; establish READY. Offer → select → disconnect → B withdraws → reconnect until A sees withdrawal → A cancels → restart B/reconnect. Both must show cancelled history without a helper; repeat five times. Separately confirm/resolve: retain confirmed-helper history; delayed confirmation must not reopen. Report stale selections, duplicates, convergence time. Capture `INCIDENT_HELP`, `CLOSURE_WAITING_DEPENDENCIES`, `WITHDRAWAL_CLEARED_SELECTION`, `SUPERSEDED_SELECTION_SKIPPED`, `INCIDENT_REPO`, READY; omit private content. Ordering uses local tests; Room execution remains open.

### Level 4: release/capstone matrix

Across representative devices, measure delivery, recovery, range, battery, SOS, private-send failure, and hardware-specific failures. Do not infer production guarantees from one run.

## Required test card

Record APK, setup, failures, timestamps, and markers; follow the [block/voice tracker](status.md).

### Automatic incident-sync card

Use `app/build/incident-sync-qa/TEST_CARD.md` with the APK hash. Cover READY/reconnect, connected helper updates, restart, A–B–C, and mixed traffic without Refresh; record convergence/latency/retries and `INCIDENT_SYNC` markers. Phone reliability remains open.

### Background mesh card

- Record APK/device/API; cover Android 12-14+ and Android 13+ notifications granted/denied.
- Enable background mesh, press Home, lock 10-15 minutes, exchange A-B and A-B-C text/SOS, reopen, then use notification **Go offline**. Repeat disabled, task-removed, force-stopped, and for one-hour idle/three-phone battery runs.
- Expect one silent notification, continued receipts/relay, one clean stop, no boot/force-stop restart, and no active BLE without the service. Report duplicate scans/servers, churn, false status, loss, heat/drain, timestamps, battery, and focused service/`READY`/route/receipt markers.

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
| 2026-09-16 | Default-MTU discovery and same-ACL guard | CPH2219 API 31, SM-A236E API 33 | Repeated CPH client to Samsung server setup | Same-ACL guard executed, but four accepted service-discovery attempts received no ATT response and timed out; the Samsung service was registered and the radio ACL stayed connected until the client watchdog | `captures/ble-logcat/20260916-235944/` |
| 2026-09-17 | Handshake radio gate | CPH2219 API 31, SM-A236E API 33 | Paused scan and delayed reversal | ATT discovery timed out both ways; a second ACL began while the first was unresolved. Neither reached `READY`. | `captures/ble-logcat/20260917-001703/` |
| 2026-09-17 | Per-peer explicit-LE fallback | CPH2219 API 31, SM-A236E API 33 | AUTO then explicit LE | Both established radio ACLs but got no ATT service response. Reverse role deferred; Samsung saw a legacy ad. No `READY`; process exit was user task removal. | `captures/ble-logcat/20260917-003212/` |
| 2026-09-17 | Stable advertising and deterministic initiator | CPH2219 API 31, SM-A236E API 33 | Samsung client to CPH server | Overlap error and reversal stopped, but Samsung discovery preceded app callback; CPH saw no ATT request. `transport=2` is LE; retirement was cleanup only. | `captures/ble-logcat/20260917-004926/` |

| 2026-09-17 | Current working tree (user report) | Samsung pair and five-device mesh; models/API/build/capture not supplied | Samsung readiness and five-device availability | User reports Samsung now succeeds and all five nodes were available in the mesh. This is meaningful device evidence, but the missing matrix, repetitions, conditions, and capture prevent capacity or production-reliability claims. | Not supplied |
| 2026-09-18 | Mutual-block working tree | V2424 API 34, CPH2219 API 31, CPH2127 API 31, SM-P615 API 33, SM-A236E API 33 | KAY and LAL block over a five-phone mesh | Both endpoints logged acknowledgement-driven direct teardown. Public and encrypted private traffic then crossed the V2424 relay and decrypted at both endpoints. The capture does not establish restart persistence, unilateral/bilateral unblock, 70-second route stability, or production reliability. | `captures/ble-logcat/20260918-011613/` |
| 2026-09-18 | Stable-ID relay working tree | V2424 API 34, CPH2219 API 31, CPH2127 API 31, SM-P615 API 33 | Indirect private relay both directions | Selected routes relayed and decrypted both ways without private broadcast fallback. Some return receipts lacked a next hop; SM-P615 repeatedly retired one GATT callback link. | `captures/ble-logcat/20260918-233006/` |

Samsung recovery and five-device availability need capture/matrix evidence; capacity and BLOCK-01 remain open.
