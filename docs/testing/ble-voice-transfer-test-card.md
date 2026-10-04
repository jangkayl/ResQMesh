# BLE voice transfer repair: phone card

Last reviewed: 2026-10-04. Source baseline: `2e27013`. Status: active procedure. Current APK/version/full hash and the qualitative report are in [validation](../validation.md); historical candidates below are not the current build.

Successful direct/relay note playback, text/receipts, SOS and recovery were reported. Intermittent A-to-D text/note loss remains DELIVERY-01. Exact bursts, join-under-load, diagnostic fallback, restart custody and timed background cases are not all confirmed.

Historical repair snapshot: 2026-10-03, base `aab6749`, branch `fix/ble-voice-transfer-reliability`, then-local changes, version 1.0.1/code 2. Phone scenarios were unexecuted in that local check batch; the October 4 user report is separate.

## Historical repair candidates and local checks

Normal debug enables L2CAP; `-PbleL2cap=false` disables its client/server path for comparison. Both builds retain the same chunk protocol and GATT fallback. Install one variant on all phones in a run; record its hash and device models/API levels. Preserve data and keys.

Historical candidates were saved in `app/build/outputs/ble-repair/`; these hashes are not the current October 4 normal candidate. A current transport comparison needs separately prepared matching variants:

| APK | SHA-256 |
| --- | --- |
| `resqmesh-hybrid-debug.apk` | `8FF098B678ABB1F5D3C250A6A5EBC6C61B76B024995EC1E15086E147D95DE933` |
| `resqmesh-gatt-debug.apk` | `EB724BC786F0E25AE4B4B4B962A904B6E69068CA0193FDE28FE05A7A6DA224B0` |

Local results: **PASS** — all 348 unit tests in the clean commit snapshot (zero failures/errors/skips, no test exclusions), debug build, earlier hybrid/GATT APK builds, `compileDebugAndroidTestKotlin`, Lint (zero errors; 147 warnings), docs, and diff hygiene. Normal `app-debug.apk` was rebuilt with L2CAP enabled. These checks provide no physical BLE evidence.

Local checks cover setup sequencing, indication ownership/promotion, fallback queue pressure, endpoint health, chunk loss/restart/reassembly/custody, relay storage order, expiry, and private receipt timing. They do not simulate Android radio behavior.

The mixed workspace has a pre-existing untracked `PrivateReconnectDeliveryTest.kt` that expects missing store APIs, repository injection parameters, and callback signatures. It still blocks that workspace's full unit-test compilation and remains outside this commit. Earlier checks passed 371 tests with only that fixture excluded. Commit validation instead archived `aab6749` into a temporary clean directory, overlaid the 46 selected repair paths, and ran `:app:testDebugUnitTest :app:assembleDebug` without an init file or exclusions. All 348 tests and the build passed. No physical tests were run for that local check batch; current qualitative phone reports are recorded separately in validation.

## Devices and setup

Use A/B for direct tests, then A/B/C with B as the relay. Include the Samsung device that previously failed, if available. Enable Bluetooth/permissions and join the same team. For locked-screen tests enable the existing opt-in background mesh on every phone. Record initial direct/relay paths and trusted-key status. Use harmless test notes of 10–30 seconds; label them A1, A2, B1, etc.

Use the existing capture script on connected ADB devices: `scripts/capture_ble_logcat.ps1 -DurationMinutes 10`. The user installs, launches, operates, and stops phone apps.

## Exact steps and expected behavior

1. **Direct baseline:** A/B reach payload READY. Send private text both directions, then one recorded note each. Expect one complete playable note and a recipient receipt; queue acceptance or hop custody alone is insufficient.
2. **Burst:** A sends three notes quickly; B sends three at the same time. While they transfer, exchange five short texts and create/end one test SOS. Repeat three times. Expect every note once, responsive text/control traffic, and stable healthy neighbors. Record send-to-arrival and send-to-confirmation times separately. Rejection must remain visible/retryable.
3. **Joining:** While A/B exchange notes, join C. Expect existing READY paths to keep working while C configures. C becomes a usable private recipient only after readiness, identity, and usable trusted-key setup. Connecting to C must not retire A/B solely for queue pressure.
4. **Relay:** Arrange A–B–C with no direct A–C link; verify the actual route. Repeat the burst between A/C and exchange text/SOS through B. Expect directed private traffic, complete notes, recipient confirmation, and no duplicate playback. Do not infer hopping from physical placement alone.
5. **Loss/resume:** Start three notes, then disable Bluetooth on the receiving side or relay before completion. Restore Bluetooth without clearing data and allow automatic recovery. Repeat with one app restart during transfer. Expect retained upgraded transfers to resume missing pieces after a new READY identity exchange. Missing routes must not trigger private broadcast. Record any duplicate presentation.
6. **Background:** Repeat direct and relay bursts with one screen locked, then all screens locked for 10 minutes. Reopen and verify stored notes/receipts. Use Go offline afterward: expect transport to stop and remain stopped until a normal Join.
7. **Transport comparison:** Repeat baseline/burst/loss with the GATT diagnostic APK on all phones, then restore matching normal builds. Compare delays, rejections, disconnects, and completion. GATT may be slower. Android/OEM link loss can still require reconnection.

## Failure indicators and evidence

Fail a scenario for truncated/unplayable or missing notes, duplicate presentation/playback, false delivered state, unexplained healthy-link retirement, permanent pending state despite restored readiness, or private broadcast fallback. Record queue rejection separately from silent loss. Known storage bounds can reject admission; a rejected note must not be shown as delivered.

Focus captures on `PAYLOAD_SETUP`, `READY`, `FRAME_QUEUED/STARTED/COMPLETED/FAILED`, `TRANSFER_RETAINED`, `TRANSFER_REASSEMBLED`, `TRANSFER_CUSTODY_ACK`, `TRANSFER_EXPIRED`, `TRANSFER_OVERFLOW`, `TRANSFER_STORAGE_FAILED`, `TRANSFER_HASH_FAILED`, `ATT_RECOVERY_REQUIRED`, GATT completion/heartbeat timeouts, `PRIVATE_RETRY`, `PRIVATE_STORED`, and `PRIVATE_DELIVERED`. A local frame completion is not recipient confirmation. Old peers use whole frames and lack durable chunk custody acknowledgements.

Report a PASS/FAIL/UNTESTED row per scenario and variant, models/API levels, APK hash, initial/actual routes, exact failure timestamp, note count/duration, foreground/background state, arrival/receipt/playback results, churn, and capture folder. Share only focused evidence; omit message content and key material.
