# Live push-to-talk removal: phone card

Status: active procedure. Confirms that removing live push-to-talk left recorded voice notes and transfers intact. Local checks do not prove device behavior.

## Build identity

Branch `refactor/remove-live-ptt`. Record the commit, version and APK hash used.

## Devices and setup

Use phones A and B on the same build, payload READY, with microphone permission granted. Clear nothing. The user installs, launches and operates the phones.

## Steps and expected results

1. **Monitor on:** on A and B, tune to the same radio channel. Turn the monitor on at B, then hold to record on A and release. Expect the note to autoplay on B with A shown as speaker.
2. **Monitor off:** turn B's monitor off and send again. Expect no autoplay; the note appears in channel history.
3. **Channel change:** with B's monitor on, change channel and back. Expect no replay of earlier notes.
4. **Cancelled press:** hold, then scroll or leave the screen before release. Expect nothing sent.
5. **Private voice note:** A records a note over 4 KiB to B. Expect complete playback on B and delivery shown on A.
6. **Image:** A sends an image to B. Expect it to arrive complete.
7. **Wording:** while holding, expect "RECORDING VOICE NOTE" and "HOLD TO RECORD" in idle. Profile and permission text mention voice notes, not PTT.
8. **Developer console:** the Packets tab has no `LIVE_AUDIO` filter.

## Failure indicators

Report FAIL for a crash or Koin error on launch or on opening the radio screen, no autoplay in step 1, a partial note sent in step 4, or a stalled transfer in steps 5 or 6.

## Markers

Search `AndroidRuntime` for crashes, and transfer and voice-note trace events in the reported window. No live-audio markers exist any more.

## Report

Build identity, devices, per-step pass or fail, and any bounded failure excerpt with no private content.
