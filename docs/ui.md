# UI guidance

Use Jetpack Compose and Material 3; inspect source.

## Component and state boundaries

- Reuse shared components and theme tokens before local styling.
- Dark-first tactical UI has a daylight preference; appearance affects presentation only.
- Night uses black backgrounds, grey raised panels, and 1dp borders. Daylight uses white/grey backgrounds with black text.
- Use Safety Orange (#FF5A00) for actions, green for positive states, amber for attention, red for SOS, theme tokens, and 48dp targets.
- Home: larger name, plain location labels, network view inside nearby sharing.
- Mission, Messages, Voice, and Mesh use the shared shell; SOS stays a persistent action.
- Mission, Messages, and Voice use a solid theme background. Their navigation SOS action stays visually prominent without a continuous pulse; dedicated SOS screens retain their emergency treatment.
- **Chat Composers** hide inline media tools while typing; a floating action bubble retains access.
- **Network & Topology** uses cards and a 2D route map.
- First-launch guide: three skippable animated pages, progress dots, no in-page Back; Settings replay.
- Setup has no tag input. Names omit technical tags; peer details show ID. Headers resolve current names.
- Controls use semantic shapes, pressed/disabled feedback, and textual status.
- Keep business, routing, and transport decisions out of composables. ViewModels/use cases expose UI state and user actions.
- Routes collect state; stateless UI lives in `ui/components`.

## Connection language

UI labels must reflect verified application state:

| State | Meaning |
| --- | --- |
| Online (Direct) | A direct role is payload-ready and has recent inbound progress |
| Checking connection | A direct role exists but recent peer response is missing while recovery is pending |
| Reachable / Relayed | A route exists through another peer; do not present it as direct |
| Nearby | Advertising/recently observed without a usable direct link |
| Offline | A previously known peer is not direct-ready, routed, or currently nearby |
| Connecting / Handshaking | Radio/setup work is incomplete; private send and direct-ready claims remain unavailable |

Relayed status needs a verified path through a ready first hop. Cached topology/unblock cannot imply Direct; re-resolve shared state.

BLE callbacks alone cannot imply connected; counters use routing readiness.
Home may show an active mesh without a ready peer; direct-ready and checking need link evidence. Permissions describe OS grants, not Bluetooth or peer readiness. About and Privacy state Keystore and first-seen trust limits without claiming authenticated E2EE or delivery.

## Messaging and safety feedback

Public queue rejection/partial acceptance uses feedback; acceptance never implies recipient delivery.

- Failed private sends remain unsent and explain missing readiness/keys without exposing crypto details.
- Distinguish queued, sending, delivered, failed, and blocked outcomes; do not imply peer receipt from enqueue.
- Inbox rows do not mark private messages seen; visible chat bubbles do. Keep Unread selectable at zero and use filter-specific empty states.
- SOS threads isolate simultaneous alerts, replies, unread counts, and drafts. Show queued/link-sent/neighbor-confirmed state separately. Cancellation names its alert; local silence never cancels it.
- Incidents show historical reporter names and owner badges. Identity loading/errors explain unavailable controls. Selection locks offer editing; withdrawal removes selection. Route loss never reassigns. Details use one page, docked actions, and verified connection labels.
- SOS creation keeps the deliberate slide; early release resets. Sender Back keeps SOS active; its banner reopens the thread. End my SOS requires confirmation. Receiver Back silences locally. Ended threads retain read-only history.
- Voice separates channels from Community; hide unscoped SOS/voice history. Save received channels; autoplay only new selected-channel Radio notes. Tuning/Off clears bounded queue. Private audio stays manual; cancelled holds discard notes. SOS pauses Radio; manual playback takes priority.
- Preserve Antigravity cards/icons. Reserve the active-SOS capsule above navigation, or a top strip in conversations with IME open. Short/large-text SOS threads expose scrollable controls via “SOS controls”. Badges wrap with readable theme text; activity describes retained events/latest-state sync.
- Never render debug plaintext, keys, ciphertext previews, or sensitive location in the debug UI.

## Background mesh status

- Device settings provides opt-in **Keep mesh active in background**. Service-active does not imply peer-ready.
- Its silent notification offers **Open ResQMesh** and **Go offline** using coarse repository state. Disabling only removes the background anchor; **Go offline** stops transport.

## Diagnostic terminal

- Terminal: bounded, session-only.
- Categories: Connection, Sync, Transport, Routing, Security, System, Alerts. Must emit categories explicitly.
- Direct summaries show owned lifecycle, endpoint, role, readiness, transport.
- Newest first; pause follow while reading older events; provide Latest/Sync jumps.
- Keep heartbeat/relay chatter behind Details.

## Hidden legacy UI
- Legacy Radar/terminal stay behind Debugging Mode; Settings manages offline maps.
- Validate restored actions, accessibility, empty, denied, and error states.

## Accessibility and interaction

- Provide contrast, accessible targets/labels, and textual status.
- Startup errors offer recovery; map setup returns to its SOS.
- Preserve user drafts when a recoverable send fails.
- Conversations anchor latest messages above the IME and follow new messages. Community reader circles require recorded `seenBy` receipts.
- Avoid flicker; transitions follow repository/link evidence, not scan churn.
- Home peer chips open the matching Network details by stable ID. Blocked peers remain in "Blocked Devices (Direct Link Denied)" with relay messaging when a route exists.
- Verify both appearances using theme tokens.

## Validation

Run focused UI checks and inspect affected states. BLE, route, key, delivery, and SOS labels require physical tests from `validation.md`.
