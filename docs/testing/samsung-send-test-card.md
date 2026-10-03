# Samsung outgoing GATT and receipt validation

User operates devices. Do not clear app data, keys, blocks, or message history.

User reports Samsung sending now works. Counts, role/transport coverage, and timing captures were not supplied; broader validation remains open.

## Build

- Candidate: version 1.0.1, code 2, current working tree (uncommitted).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `95F1CBD26C6590F002AEE78D25DA377CC8556F1E9BEE285AD6A881250E38A21B`.
- Installed failure APK on SM-P615: `F627902AB140CDFBC1FA902325CEE7927F80B6C144E7DD2D8D58F16CFA9F327A`.
- Local: debug build and Android instrumentation compilation passed; instrumentation was not run. All 350 isolated unit tests passed (zero failures/errors/skips). Standard full suite is blocked by the existing untracked `PrivateReconnectDeliveryTest.kt` and `BleLifecycleSupervisorTest.kt`; an external temporary Gradle init script excludes only those files. This does not establish a full-suite pass. Lint initially crashed during analysis; a separate single-worker, fresh-daemon retry passed. Documentation and diff checks passed.

## Devices and setup

Before commit, a clean snapshot containing only tracked/staged files passed the normal debug build, all 280 unit tests (no exclusions, failures, errors, or skips), and Android instrumentation compilation. The earlier 350-test run also included unrelated untracked tests. Instrumentation remains unexecuted.

A = Samsung SM-P615, Android 13/API 33. B = CPH2127 or CPH2219, Android 12/API 31. C = V2424/API 34 for relay/load. Install the same candidate APK on all selected devices yourself. Keep apps foreground and screens awake for the first run; put unused devices offline through the app.

Record installed hash, device/API, selected names/IDs, start/failure times, Samsung CLIENT/SERVER role, and GATT/L2CAP transport from logs. Start `scripts/capture_ble_logcat.ps1 -DurationMinutes 10` on the connected devices. Capture technical markers only when sharing results; omit message content, keys, and ciphertext.

## Steps and expected results

1. **Direct quiet pair:** Wait for identified READY. Send ten short numbered private messages A→B, then ten B→A; open each chat. Expect one row per message, arrival, DELIVERED, then SEEN. No persistent PENDING/FAILED after a healthy send or SENT after confirmed receipt.
2. **Both roles/transports:** Repeat with Samsung SERVER and CLIENT where the normal election produces them. Confirm transport in logs. The candidate has no user-facing force-role/GATT-only switch: mark an unobserved role or fallback UNTESTED, rather than assuming restarting forces it. A separate controlled QA setup is required if natural fallback never occurs. L2CAP-only success does not close GATT validation.
3. **Concurrent sends:** Exchange ten messages each way simultaneously. Then send a recorded Radio note while exchanging five private texts each way. Receipts should pass through reserved control capacity; normal texts must continue. A currently active large GATT frame can still delay waiting traffic.
4. **Reconnect:** B goes offline, then returns. Repeat five cycles without clearing data; send once during loss and once after READY. Pending work should recover within the receipt/retry windows, with no duplicate rows/notifications or overwritten SEEN state. A true timeout must retire the old link once; a late callback must not complete a replacement transfer.
5. **Relay:** Arrange A–C–B with A/B indirect; send five messages each way, remove/restore C, then repeat. Retries may have different wire IDs but must share the logical message ID. Receipts must follow directed hops and never private-broadcast.

Failure indicators: repeated timeout on the same generation, sustained queue rejection, false READY after local retirement, missing rows, duplicate notifications, receipt state regression, or lost healthy peer links during server renewal. An unresolved old server indication can defer reuse of that address while other server peers remain; record `SERVER_CALLBACK_WAIT` and whether an alternate path recovers.

## Timing and report

Measure Send→recipient display and Send→sender confirmation separately for each message, using a common video/clock for cross-device timing. Device elapsed clocks are not synchronized. Compare median/p95 against the previous APK only on equivalent role, transport, topology, and workload; do not reinstall the failing build solely to create a baseline.

Markers: `PRIVATE_SEND_REQUEST`, `PRIVATE_PERSISTED`, `PRIVATE_DISPATCH`, `PRIVATE_STORED`, `PRIVATE_RECEIPT`, `PRIVATE_RETRY`, `PRIVATE_DELIVERED`, `PRIVATE_SEEN`, READY, `SERVER_CALLBACK_WAIT`, `SERVER_REGISTRATION_RENEW`, GATT completion failure/timeout, queue rejection, and L2CAP promotion/loss.

Report PASS/FAIL/UNTESTED per step, hashes, models, roles/transports, counts, timings, exact failing time, logical message ID, and capture folder. Phone reliability and absence of latency regression remain unverified until this run.
