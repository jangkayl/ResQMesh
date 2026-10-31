# Voice note limit: phone card

Status: active procedure. Confirms the 30-second limit on real recorders. Local checks do not prove device behavior.

## Build identity

Branch `feat/voice-note-limit`. Record the commit, version and APK hash used.

## Devices and setup

Use phones A and B on the same build, payload READY, with microphone permission granted. The user installs, launches and operates the phones.

## Steps and expected results

1. **Radio limit:** on A, hold record past 30 seconds. At 30 seconds expect "LIMIT REACHED · RELEASE TO SEND". Release. Expect a complete note of about 30 seconds on B.
2. **Radio cancel at limit:** hold past 30 seconds, then slide away or leave the screen. Expect nothing sent.
3. **Private chat limit:** tap record and wait past 30 seconds. Expect recording to stop by itself and the note to stay pending. Press send. Expect complete playback on B.
4. **Public chat limit:** repeat step 3 in the community chat.
5. **Short note:** record 5 seconds in radio and chat. Expect the timer to count and the note to send as before.
6. **Repeat recording:** record another note right after a limit stop. Expect a new note, not the previous audio.
7. **Measurement:** note the transfer time of a full 30-second note, with devices and hop count.

## Failure indicators

Report FAIL for a crash, a note lost at the limit, audio cut well before 30 seconds, a screen stuck on recording, or a repeat that plays the previous note.

## Markers

Search `MediaHelper` log lines, `RECORD_FAILED` voice-note traces and `AndroidRuntime` in the reported window.

## Report

Build identity, devices, per-step pass or fail, the step 7 timing, and any bounded failure excerpt with no private content.
