# UI guidance

Last reviewed: 2026-10-04. Source baseline: `2e27013`. Use Compose/Material 3; inspect current source.

## Components and ownership

- Reuse shared components/theme tokens. Night uses black/grey panels and 1dp borders; Daylight uses white/grey with black text.
- Safety Orange (#FF5A00) marks actions; green positive, amber attention and red SOS. Use text/semantic shapes and at least 48dp targets.
- Home retains its larger name, plain location labels and network view within nearby sharing.
- Mission, Messages, Voice and Mesh use the shared shell. Mission/Messages/Voice have solid theme backgrounds and a prominent navigation SOS action without continuous pulse.
- Chat composers hide inline media tools while typing; the floating action bubble retains access. Network/topology uses cards and a 2D route map.
- The first-launch guide has three skippable animated pages, progress dots, no in-page Back and Settings replay.
- Setup has no tag input. Names omit technical tags; peer details show ID; headers resolve current names.
- Keep routing/transport/business decisions outside composables. Routes collect state; stateless components receive models/callbacks. ViewModels/use cases expose state/actions.
- Preserve Antigravity cards, icons, semantic colors and scoped conversations.

## Connection language

| Label | Required evidence |
| --- | --- |
| Online (Direct) | Payload READY plus recent inbound progress |
| Checking connection | Existing direct role lacks recent response while recovery is pending |
| Reachable / Relayed | Usable path through a READY first hop |
| Nearby | Recent advertisement without a usable direct link |
| Offline | Previously known peer has no direct, routed or nearby state |
| Connecting / Handshaking | Setup incomplete; no direct-ready/private-send claim |

Callbacks, cached topology, unblock and service-active do not independently prove readiness. Re-resolve shared state after changes. Permissions describe OS grants; an active mesh can still be searching for peers. About/Privacy explain Keystore and first-seen trust without claiming authenticated E2EE.

## Messaging, voice and emergency feedback

Acceptance, hop custody, recipient arrival and application confirmation are distinct. Public rejection/partial acceptance needs feedback. Private failures explain unavailable routes/keys, preserve drafts and never imply delivery.

Inbox rows do not mark private messages seen; visible chat bubbles do. Keep Unread selectable at zero with filter-specific empty states. Community reader circles require seenBy receipts.

Community, each Radio channel and each SOS isolate history/unread/drafts. Save off-channel Radio silently; autoplay only new selected-channel notes while monitoring. Tuning/Off clears bounded playback queues; old notes do not autoplay. Private audio stays manual. SOS pauses Radio; manual playback takes priority. Cancelled holds discard partial notes.

SOS creation uses deliberate slide-to-send; early release resets. Creation Cancel/Back returns to the hub. Sender thread Back preserves SOS and its reopen banner. End requires confirmation; receiver Back silences locally. Alert-specific cancellation cannot end another alert; terminal history stays read-only. Show queued, link-sent and neighbor-confirmed states separately.

SOS cards retain category/status/transmission, location accuracy, time and history. Header time fallback never proves GPS freshness. Reserve measured floating-header/reminder regions, reverse conversations and keep latest messages/composer above the IME. Short/large-text threads expose scrollable SOS controls; badges wrap. Ended badges say ENDED, not rescue completed.

Incidents use historical reporter names and ownership badges. Identity loading/errors explain unavailable controls. Details use one page and docked actions. Selected offers cannot be edited; withdrawal removes selection and requires fresh approval. Route loss never reassigns. Connection labels describe the selected helper's actual route.

Never display debug plaintext, keys, ciphertext previews or sensitive location.

## Sessions, navigation and diagnostics

Background mesh is opt-in. Home/notification distinguish Bluetooth off, permission required, starting, searching and connected. OFF clears readiness; Go offline cancels recovery.

Home peer chips open matching stable-ID Network details. Blocked peers stay in Blocked Devices (Direct Link Denied), with relay messaging when a route exists. Legacy Radar/terminal remains behind Debugging Mode; Settings owns offline maps.

The terminal is bounded and session-only. Explicit categories are Connection, Sync, Transport, Routing, Security, System and Alerts. Summaries show endpoint/role/generation/readiness/transport; put heartbeat/relay chatter under Details. Newest first, pause follow while reading, and provide Latest/Sync jumps.

## Accessibility and validation

Support both appearances, contrast, targets/labels, empty/error/denied states, small screens, landscape, large text, TalkBack and restoration. Startup errors offer recovery; map setup returns to its SOS. Prevent reminder/header/keyboard overlap and scan-driven flicker.

Functional phone successes are recorded in [validation](validation.md), alongside DELIVERY-01. They do not establish completed accessibility/novice usability, every channel/gesture regression or pressure/fallback scenario. Use the [presentation](testing/structure-organization-test-card.md) and [SOS/conversation](testing/sos-conversations-test-card.md) procedures. Keep measured outcomes in validation rather than adding transcripts here.
