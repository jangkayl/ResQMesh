# Bluetooth recovery phone card

Physical results: **UNTESTED**. Users install and operate the phones; local tests are not BLE proof.

## Build and setup

- APK: `app/build/outputs/apk/debug/app-debug.apk`. Final SHA-256/local check results are recorded below after validation. This working-tree build includes the existing conversation UI edits.
- Use the same APK on A/B, then A/B/C, then A/B/C/D/E. Record phone model, Android/API, existing block relationships, background setting, and local timestamps. Do not clear app data, keys, or blocks.
- Keep phones in one room, Bluetooth/required permissions enabled, and join normally. Use unblocked peers with previously established private keys. Verify payload READY and bidirectional text first.
- Capture with `powershell -ExecutionPolicy Bypass -File .\scripts\capture_ble_logcat.ps1 -DurationMinutes 10`. Repeat capture windows for longer tests.

## Steps and expected results

1. **Two-phone toggle:** On A, disable Bluetooth for ten seconds while in the mesh. Expect Bluetooth-off status and no connected/relayed peers rooted through A. Enable Bluetooth; do not press Refresh or Join. Expect searching, then at least one identified READY peer. Send public and private text both ways; require private receipts. Repeat ten times, alternating A/B. Include one toggle during setup and one during a recorded-note transfer. Incomplete notes may be interrupted; already durable pending text must retain its status and retry normally.
2. **Isolation:** With three phones, remove A's last ready neighbor by turning that neighbor's Bluetooth off. Keep an eligible unblocked third phone advertising nearby. A should resume discovery and connect automatically to an eligible peer. Restore the removed neighbor; healthy links must not repeatedly cycle.
3. **Relay:** Arrange A-B-C with verified relayed A/C traffic. Disable B's Bluetooth; confirm stale routes withdraw. Restore B and verify routes/receipts recover without Refresh. Do not infer relay from names alone: record the directed next hop.
4. **Five-phone startup:** Join A-E together and verify every phone reaches the others directly or through relays. Rotate Bluetooth OFF/ON through each phone, ten seconds off per cycle. Record direct neighbor count separately from total reachable nodes; the direct limit remains three.
5. **Cluster merge:** Form separate A/B/C and D/E groups before bringing them into the same room. Confirm the groups become mutually reachable using a bridge. Also add E to an established A-D group: a saturated node may reclaim only an idle redundant edge with a recent alternate path. Without safe capacity, it must defer rather than blindly tear down a useful link.
6. **Load:** Repeat one toggle while two senders exchange numbered text. Send an SOS and overlapping short recorded notes; verify SOS state convergence, receipts, no duplicate text, and no repeated teardown of progressing healthy links. Record queued/rejected traffic separately from delivery.
7. **Background:** Enable the existing background setting, press Home/lock the phone, toggle Bluetooth using phone settings, and return. Expect one foreground notification reflecting off/searching/connected state and automatic recovery. Repeat with background disabled while keeping the app foreground. Fully stopping the app retains its Join flow.
8. **Go offline:** While Bluetooth is off, choose Go offline. Enable Bluetooth; no mesh restart should occur. Also check permission removal/restoration via settings: suspend safely and reconcile when access returns without bypassing Android permission UI.

## Measurements and failure indicators

- Suggested targets: first usable neighbor within 30 seconds after STATE_ON or confirmed isolation; five-phone convergence within 60 seconds when eligible peers/capacity exist. These are acceptance targets, not guarantees. Scan-start throttling and busy radio setup can delay recovery.
- Fail if recovery requires Refresh/restart, OFF still shows stale connectivity, repeated events create duplicate servers/scans, old callbacks destroy replacements, healthy peers churn, blocked peers connect, private traffic falls back to broadcast, or durable pending text disappears.
- Capture `BLE_RECOVERY` transport state/generation and advertising/scan failures, `BLE_ADMISSION`, READY/CCCD, handshake/lock release, heartbeat timeouts, topology withdrawal/update, queue rejection, and delivery receipts. Inspect only the failure window; do not share message contents or keys.
- Report APK hash, device matrix, scenario/repetition, OFF/ON/READY timestamps, first recovery and full convergence times, text receipt counts, unexpected disconnects, and capture directory. Mark each scenario PASS/FAIL/UNTESTED.
- Advance to ten phones only after the two/three/five-phone card passes; repeat startup, merge, toggle, quiet, and simultaneous-sender workloads.

## Local validation identity

- Current working-tree APK on `fix/bluetooth-recovery-cluster-bridging-and-sos-ux`, version 1.0.1/code 2; identity checked 2026-10-02.
- APK SHA-256: `6969A1839D7AD21CBCC9204092F527476B251D4AAC671251AB4A128A7EA90C9D`.
- Earlier locally checked recovery APK: `78293628B17D16D412DD16CCF8A5525A2E6147D9E273D5762E7C9A984C02F36F`; use the current hash when reporting new phone runs.
- Passed `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug --max-workers=1 --console=plain`: 266 tests, zero failures/errors/skips; Lint zero errors, 147 warnings, three hints. Android tests compiled but were not executed on phones.
- Physical Bluetooth toggle, background, cluster merge, and traffic scenarios above remain **UNTESTED**.
