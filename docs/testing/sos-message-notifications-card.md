# SOS message notifications: phone card

Status: active procedure for D34. Local checks do not prove device behavior.

## Build identity

Branch `feat/sos-message-notifications`. Record the commit, version and APK hash used.

## Devices and setup

Use debug builds on phones A, B and C with notifications allowed. Turn on background mesh on the phone being tested. Set clocks automatically. The user installs, launches and operates the phones.

## Steps and expected results

1. **Background:** raise an SOS on A, silence it on B, then put B on the home screen. Send text from A. Expect one `SOS chat` notification on B with the sender and text, with sound.
2. **Own SOS:** with A on the home screen, reply from B. Expect the same on A.
3. **Media:** send a voice note and an image. Expect `Voice note` and `Photo` in the notification.
4. **One notification:** send five texts quickly. Expect them in one notification, with one sound in 30 seconds.
5. **Tap:** tap the notification. Expect that SOS chat, with no alarm, and the notification gone.
6. **Open chat:** keep B in that chat and send from A. Expect no notification. Send again after pressing Home, and expect one.
7. **Other SOS chat:** raise a second SOS, open it on B, and send in the first. Expect a notification for the first.
8. **Bell:** tap the bell on B, leave the chat, and send from A. Expect no notification and the existing one removed. Tap again, and expect notifications to return. Reopen the app and expect the setting kept.
9. **Back:** press Back from an unmuted chat, which silences the alarm, and send from A. Expect a notification.
10. **Settings:** turn off `SOS message notifications`. Expect none and a disabled bell. Raise a new SOS and expect its alarm.
11. **Other phones:** mute on B. Expect C and the sender unchanged, with no slower messages.
12. **End:** end the SOS. Expect its notification removed and the bell gone.
13. **Sync:** close C, send SOS messages, then start C. Expect the messages in the chat with no notification.
14. **Do Not Disturb:** turn it on and send a message. Expect the SOS alarm behaviour unchanged, with messages following the phone's normal rules.

## Failure indicators

Report FAIL for a missed notification with the mesh running, a notification for synced or muted messages, an alarm from a chat message, a muted new SOS alarm, a wrong chat on tap, repeated sound within 30 seconds or a crash.

## Markers

Search `CONVERSATION`, `CHAT_SYNC` and `AndroidRuntime` in the reported window.

## Report

Build identity, devices, Android versions, per-step pass or fail, and any bounded excerpt with no private content.
