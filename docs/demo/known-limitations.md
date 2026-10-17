# Known limitations (demo candidate #2)

Prepared: 2026-10-17 for the capstone demo. Applies to release APK `B84ACB1B…EA96` (`42f1660`). Say these out loud when presenting; they keep the claims honest. Sources: [status](../status.md), [decisions](../decisions.md) and [validation](../validation.md).

## What ResQMesh is

A capstone prototype for offline text, private messages, voice notes, SOS alerts and incident coordination between nearby Android phones using Bluetooth Low Energy (BLE). It needs no cellular service, internet or Wi-Fi. It is not a certified emergency system and must not replace official emergency services.

## Reliability

- **Intermittent relay loss (DELIVERY-01):** over long chains (A→B→C→D), a public or private message or a voice note sometimes doesn't reach the last phone. The cause is not yet known, and the investigation comes after the demo.
- **No measured rates yet:** the results so far are user-reported passes. Delivery rates, latency, range and capacity have not been measured. Demo counts come from the [demo freeze card](../testing/demo-freeze-card.md).
- **Small mesh:** each phone keeps at most 3 direct links. Five- or ten-phone networks and real-world range are untested (SCALE-01).
- **Delivery wording:** "Sent" means the phone accepted the message. Only DELIVERED means the recipient's phone confirmed it arrived.
- **Background:** background messaging works on the tested phones, but battery-saver and manufacturer restrictions can stop it on other devices.

## Security and privacy

- **Private messages are encrypted** (RSA-2048 key wrap plus AES-256-GCM), but sender authentication, forward secrecy and verified identities are **not** claimed. Keys are trusted the first time phones meet, and there is no QR or safety-number verification yet.
- **Public messages and SOS:** anyone nearby running the app can read them. SOS and incident events are signed. Public text is not.
- **Spam and flood protection** (per-sender rate limits, signed origins) is planned after the demo ([security plan](../plans/security-hardening.md)). A modified app could currently flood or spoof public traffic.
- **Blocking** is a link-routing tool, not a security feature. Relayed messages from a blocked phone may still arrive.
- Messages are stored on the phone without app-level database encryption (planned). Android's device encryption still applies.

## Scope

- BLE only: Wi-Fi Direct and Nearby Connections are inactive.
- Android only. Tested on Samsung SM-P615 (Android 13), OPPO CPH2127 (Android 12) and vivo V2424 (Android 14).
- Offline maps need a map package installed in advance.
- Text messages are limited to 1000 characters, and attachments to 2 MB.
- Reinstalling the app creates a new identity, so phones must exchange keys again.
