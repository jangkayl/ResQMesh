# App ID rename card

Prepared: 2026-10-17. Status: awaiting user phone run. Branch `chore/app-id-rename`.

The application ID, namespace and Kotlin packages changed from `com.example.testresqmesh` to `edu.cit.resqmesh` (CIT-U). The Room schema folder and the scripts were renamed with them. No logic changed. The golden wire-format tests confirm that no class names travel over BLE, so renamed builds interoperate with each other.

Android treats `edu.cit.resqmesh` as a new app. Each phone starts with empty history and settings, and with a **new Keystore identity**: a new node ID and new keys. Every phone must therefore run this build, and phones must re-trust each other. The old app remains installed until the user removes it.

## Build identity

Use `scripts\device\manage_devices.ps1`, option 5, and press Enter to rebuild. Record the printed package, commit and SHA-256. The package must read `edu.cit.resqmesh`.

## Devices and setup

All test phones. Uninstall the old app on each one first: run `adb -s <serial> uninstall com.example.testresqmesh`, or remove it from Settings. Keep it if you still need its history, but then close it so two ResQMesh apps are not advertising at the same time. Turn Bluetooth and location on. Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 15`.

## Steps and expected results

1. **Fresh setup:** launch on each phone. Expected: the first-launch guide, permission prompts and name setup appear as for a new install. The app label and icon are unchanged.
2. **Identity:** Profile shows a #nodeId on each phone. A new ID compared with the old app is expected.
3. **READY and chat:** the phones reach payload READY, and public text arrives on the others.
4. **Private send:** A sends private text to B. Expected: it is DELIVERED after the keys are exchanged. A key prompt for B means a stale identity is still around: check that the old app is closed.
5. **SOS:** A raises an SOS, B receives the alarm, then A ends it.
6. **Incident:** A creates an incident, and it syncs to B and C.
7. **Background mode (if used):** enable background mesh, leave the app, and return. Expected: the service notification appears and stops when going offline. This checks the renamed service actions.
8. **Offline map:** if a map package was installed for the old app, reinstall it with `scripts\map\install_local_map_dev.ps1`, then open the map.

## Failure indicators and report

Report a FAIL for any of these:
- a crash or a Koin error on first launch
- the setup gates are missing
- the package shown is not `edu.cit.resqmesh`
- no READY between renamed phones
- private send keeps failing after key exchange
- the background service does not start or stop

Report per step: PASS, FAIL or UNTESTED; the printed build identity; and the capture folder. Never share message content or keys.
