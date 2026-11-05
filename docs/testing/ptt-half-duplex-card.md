# PTT half-duplex: phone card

Status: active procedure. Confirms that received voice no longer leaks into new recordings. Local checks do not prove device behavior.

## Build identity

Branch `fix/ptt-half-duplex`. Record the commit, version and APK hash used.

## Devices and setup

Use phones A and B (C for steps 19–21) on the same build, payload READY, microphone permission granted, Radio mode on the same channel with monitoring on and the loudspeaker at normal volume. The user installs, launches and operates the phones. For every step, listen to the note the other phone receives.

## Core (2 phones)

1. **Interrupt playback:** A sends a ~20 s note. Halfway through, B holds talk for 5 s saying something recognizable, then releases. Expect B's speaker to go quiet at once, A to receive only B's voice, and A's note to continue on B from about where it stopped.
2. **Arrival while talking:** B holds talk; A sends a short note. Expect silence on B while holding, then A's note after release.
3. **Several arrivals:** B holds talk while A sends 3 notes. Expect all 3 after release, once each, in order.
4. **Quick tap:** while A's note plays, B taps talk for under 1 s. Expect pause then resume, no crash, no broken note sent.
5. **Normal note:** with nothing playing, B records 5 s. Expect the same behavior as before.

## Edge cases (2 phones)

6. **Limit while holding:** B holds past 30 s while A sends a note. Expect "LIMIT REACHED", A's note to start playing while B still holds, and B's 30 s note to contain no A audio.
7. **Cancel:** while A's note plays, B holds talk and slides off. Expect nothing sent and A's note to continue.
8. **Leave while holding:** B holds talk and presses Back or Home. Expect nothing sent; returning does not leave playback stuck silent.
9. **Monitoring toggle:** during playback B records, then turns monitoring off and on. Expect no crash and normal playback of new notes.
10. **Rapid repeats:** B records twice quickly while A keeps sending. Expect each note to contain only B's voice and playback never stuck.
11. **Screen lock:** B records while the screen times out or locks. Expect a clean stop and working playback after unlock.
12. **Interruption:** a phone call or alarm interrupts B while recording. Expect no crash and playback recovery.

## Chat and SOS (2 phones)

13. **Private chat:** B plays a voice note, then taps record. Expect the voice note to stop and the recording to contain only B's voice.
14. **Bubble taps:** while recording, tap voice bubbles. Expect nothing to play until recording stops.
15. **Public chat:** repeat steps 13–14.
16. **SOS chat:** repeat step 13.
17. **Radio arrival during chat recording:** expect silence until recording stops.
18. **Siren:** start or receive an SOS siren on B, then record. Expect siren behavior as before; note whether it is audible in the recording (unchanged by this fix).

## Three phones

19. **Two senders:** A and C send while B holds talk. Expect both after release, once each, in arrival order.
20. **Relay:** A → B → C with C out of A's range; B records while relaying A's note. Expect C still to receive A's note and B's note to C to contain no A voice.
21. **Busy channel:** all three talk at roughly the same time for several rounds. Expect no crash, clean single-voice notes and no stuck silence.

## Failure indicators

Report FAIL for another voice audible in a sent note, a queued note lost or played twice, playback stuck silent after release, a note restarting from the beginning instead of resuming, a crash, or a screen stuck on recording.

## Markers

Search `MediaHelper` log lines, `RECORD_FAILED` / `PLAYBACK_FAILED` voice traces and `AndroidRuntime` in the reported window.

## Report

Build identity, phone models and Android versions, A/B/C roles, per-step pass or fail, failure times, and any bounded failure excerpt with no private content.
