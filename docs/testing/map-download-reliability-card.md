# Map download reliability: phone card

Status: active procedure. Confirms cancel, retry and resume on real networks and records the first measured speed. Local checks do not prove device behavior.

## Build identity

Branch `fix/map-download-reliability`. Use a debug build, because release builds omit app Logcat markers. Record the commit, version and APK hash used.

## Devices and setup

Use one phone on Wi-Fi, with the offline map deleted in Profile before each timed run. Run the PC baseline on the same Wi-Fi first. The user installs, launches and operates the phone.

```bash
curl -L -o /dev/null -w "%{speed_download} bytes/s, %{time_total}s\n" https://github.com/jangkayl/ResQMesh/releases/download/map-cebu-v1/cebu-v1.zip
```

## Steps and expected results

1. **Timed download:** download the map and stay on the screen. Expect progress to rise smoothly to 100% and the map to install. Note the logged speed and time per phase.
2. **Cancel and restart:** start a download, cancel at about 30%, then download again. Expect progress to restart from 0% with one download running. Cancel may take up to 30 seconds if the network has already stalled.
3. **Network drop:** start a download and turn Wi-Fi off for about 5 seconds, then back on. Expect it to continue from the staged bytes, or to show the interrupted error if all attempts ran out. Pressing Retry must then continue from the staged bytes.
4. **App killed:** force-stop the app at about 50%, reopen it and press Download. Expect progress to continue near 50%.
5. **Offline use:** turn on airplane mode and open the SOS map. Expect the map to display.

## Failure indicators

Report FAIL for a crash, two `Download attempt` results for one started attempt, progress that restarts after a resumed download, a download that keeps running after Cancel, a stuck progress screen, or a map that fails offline.

## Markers

Search `MapPackageDownloader` and `DefaultHttpTransport` log lines and `AndroidRuntime` in the reported window. `Download attempt` lines give bytes, duration and average speed.

## Report

Build identity, phone model, Wi-Fi conditions, PC baseline, per-step pass or fail, the step 1 phase timings, and any bounded failure excerpt.
