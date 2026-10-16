# Startup lag card (PERF-01)

Prepared: 2026-10-17. Status: not yet run. Local checks pass (assemble, all JVM tests, Lint, androidTest compile, detekt, boundary, docs). Prepared from uncommitted work on `refactor/batch-c-hygiene`, debug APK `F06E5C84086E79B9520E888126966DF1704DD7E0929ABBA5568538D2A49378A6`; record the commit once committed.

The user reports that the app feels laggy when opened. Source review found main-thread work at startup and during the first screens. Changes:
- Splash: 3 s fixed wait cut to 0.8 s. The pulse-border alpha is now read in the draw phase. The logo moved to `drawable-nodpi`.
- Keystore and identity work moved to `Dispatchers.Default`: `IncidentViewModel`, `LocalIdentityManager`, cached incident public key, cached node ID and fingerprint.
- UI-only `throttleLatest(400 ms)` on scanned-device collectors in the Radar and Comms ViewModels.
- `AppLogger` ring buffer that publishes only while the debug terminal observes it.

Not changed: the coordinator, routing, READY, heartbeat, keys, wire format and BLE threading. Moving BLE scan and GATT work off the main looper is deferred because of mesh risk.

## Build identity

Measure the **before** and **after** builds with the same variant on the same phone. Prefer a release build, because a debug build makes Compose slower by itself. Record each commit and APK SHA-256 (`Get-FileHash <apk>`). Install over the existing app without clearing data. The user installs and operates the phones.

## Devices and setup

Use phone A (the one where lag was noticed) and phone B, which stays advertising nearby. Bluetooth on, screen awake.

## Steps and expected results

1. **Cold start ×3:** run `adb shell am force-stop com.example.testresqmesh`, then `adb shell am start -W -n com.example.testresqmesh/.MainActivity`. Record `TotalTime`. Expect about 2 s less than before, mostly from the splash, and a smoother splash animation.
2. **Home jank:** run `adb shell dumpsys gfxinfo com.example.testresqmesh reset`, idle on Home for 30 s with B nearby, then run `adb shell dumpsys gfxinfo com.example.testresqmesh`. Record the Janky frames %. Expect it to be lower than before.
3. **Skipped frames:** in the capture, count `Choreographer` `Skipped N frames` lines during launch. Expect fewer, smaller bursts.
4. **Identity:** open Incidents. Expect the identity and signing controls to leave loading normally. A first-install run should also complete identity setup.
5. **Mesh regression:** A and B reach READY. Private text A→B is DELIVERED. SOS A→B alarms on B. Radar shows B; the list may update up to 0.4 s later than before.
6. **Debug terminal (debug build):** open it. Expect current logs to appear and Clear to work.

## Failure indicators and report

Report FAIL for:
- a crash
- identity or incident controls stuck on loading
- a missing peer, READY, receipt or SOS
- a splash that is visually broken (hub border missing)
- an empty debug terminal while logs are being produced

Report: commits and APK hashes before and after, phone models and API levels, TotalTime ×3 for each build, Janky % for each build, Skipped-frames lines, and PASS/FAIL per step. Never share message content or keys.
