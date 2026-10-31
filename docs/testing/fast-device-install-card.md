# Fast device install card

Prepared: 2026-10-31. Status: pending. This changes the install tooling only, with no app source or BLE change.

`scripts\device\manage_devices.ps1` now packages only the target phones' ABIs through `-PdevAbi`, installs on all phones at once and prints build and install times. A plain Gradle build still includes all four ABIs.

## Build identity

Use `scripts\device\manage_devices.ps1`, option 5, and press Enter to rebuild. Record the printed package, variant, ABIs, commit and SHA-256.

## Devices and setup

Use two or more phones with Wireless Debugging or USB connected, and note which connection each one uses. For step 4, have one phone hold the other variant.

## Steps and expected results

1. **Debug on all phones:** run option 5 and choose Debug. Expected: the build identity shows `ABIs : arm64-v8a` and the yellow test-build warning, the phones install at once, and each phone launches the app.
2. **Release on all phones:** run option 5 and choose Release on phones that already hold release. Expected: the same result, with an APK of about 14 MB.
3. **Single phone:** run option 6. Expected: the build targets that phone's ABI, and install and launch succeed.
4. **Signature conflict:** run option 5 with one phone holding the other variant. Expected: the other phones finish first. The script then asks for `YES` for that phone only, and any other answer skips it.
5. **Failed phone:** turn off Wireless Debugging on one phone just after the install starts. Expected: that phone reports a failure and the others still finish.

6. **Auto-reconnect:** on a paired phone, turn Wireless Debugging off and on so its port changes, then start the script. Expected: the phone connects without option 2, and its old address is gone from the device list.
7. **New phone:** on a phone that was never paired, start the script. Expected: a yellow "Could not connect" line. Use option 11 once, then repeat step 6 on that phone.

## Failure indicators

- A phone with Wireless Debugging on is missing at startup, with no "Could not connect" line. Windows may be blocking mDNS, so try setting the Wi-Fi network to Private.
- A phone is reported as updated but runs the old build.
- `INSTALL_FAILED_NO_MATCHING_ABIS` appears after a fresh rebuild.
- The script hangs after one phone drops.
- The final count does not match the phones that launched.

## Markers and report fields

No app Logcat markers apply. Report the devices and connection types, variant, ABIs, APK size, the printed build time, the "All installs finished" time and any failed phone.
