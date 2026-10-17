# ResQMesh quick user guide

For demo audiences and pilot testers. ResQMesh lets nearby Android phones exchange messages, voice notes, SOS alerts and incident reports over Bluetooth when there is no cellular signal or internet. Read the [known limitations](known-limitations.md) first.

## Install and first launch

1. Install the APK you were given. Compare its SHA-256 if asked. If Android warns about unknown sources, allow it for this install only.
2. Open ResQMesh. The setup asks for:
   - **Nearby devices:** finds and talks to nearby phones over Bluetooth.
   - **Location:** Android requires it for Bluetooth discovery. It is only shared when you choose to share it.
   - **Notifications and microphone:** depending on your Android version and whether you use voice notes.
3. Turn on Bluetooth and Location when asked, then tap **Check again**.
4. **Choose your name** (up to 32 characters). Nearby people will see it.

## Everyday use

- **Home:** your status and incident coordination.
- **Messages:**
  - Public radio channels that everyone nearby can read.
  - Private chats. Encrypted messages, with receipts: DELIVERED and then READ.
  - SOS conversations.
- **Voice:** record and send short voice notes.
- **Mesh:** nearby phones, direct and relayed links, and per-device options such as blocking a direct link.
- Messages can hop through other phones running ResQMesh to reach someone out of direct range. Keep the app open, or turn on **Keep mesh active in background**.

## Sending an SOS

Press and hold the **SOS** button for two seconds, or slide to send on the SOS screen. Nearby phones sound an alarm and can reply in the SOS conversation. End the SOS when you're safe. Use SOS only for real danger, and during testing only with agreed harmless test alerts.

## Incidents

Report an incident (for example medical, fire, trapped or security) with a short title and description. Nearby users can offer help. The reporter chooses a helper, and the helper confirms. Either side can withdraw, and the reporter can choose someone else. Mark the incident resolved, or cancel it, when it's finished. The history stays on every phone.

## Good practice

- Keep Bluetooth on and battery saver off for ResQMesh.
- "Sent" means your phone accepted the message. Wait for **DELIVERED** to know it arrived.
- If someone's key changes, the app pauses private messages to them until you accept the change. Only accept if you trust that it's really them; a reinstall is the usual reason.
- Don't include passwords or highly sensitive information; this is a prototype.

## Reporting a problem

Note the time, which phones were involved, what you did and what you expected, and tell the ResQMesh team (the capstone group running the demo or pilot). Never send screenshots of private messages or keys.
