# Startup lag card (PERF-01)

Prepared: 2026-10-17. Status: steps 1–3 measured by the agent over adb on one phone (results in [validation](../validation.md)). Steps 4–6 still need the user, and step 5 needs phone B. Local checks pass (assemble, all JVM tests, Lint, androidTest compile, detekt, boundary, docs). The changes are committed as `3b234bf` on `perf/startup-lag`. Debug APKs built 2026-10-17 into `build/perf01/`: before `65435cd` `1A734DAF11D53997576C9D0C639C89498DFA1FA19C4CA5B4E5F1DF9D407BA9D3`, after `3b234bf` `5846FAD2CBE89851760C70107B8DA45BD41AF38745A9087107FB76BAFFDD6170`. The pre-commit debug APK `F06E5C84…` is superseded.

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

1. **Cold start ×3:** run `adb shell am force-stop edu.cit.resqmesh`, then `adb shell am start -W -n edu.cit.resqmesh/.MainActivity`. Record `TotalTime`. Expect about 2 s less than before, mostly from the splash, and a smoother splash animation.
2. **Home jank:** run `adb shell dumpsys gfxinfo edu.cit.resqmesh reset`, idle on Home for 30 s with B nearby, then run `adb shell dumpsys gfxinfo edu.cit.resqmesh`. Record the Janky frames %. Expect it to be lower than before.
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
