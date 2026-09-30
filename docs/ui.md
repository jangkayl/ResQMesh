# UI guidance

The UI uses Jetpack Compose and Material 3; inspect source before editing.

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
- Setup has no tag input. Ordinary names omit `[NODE]` and ID; peer details show ID. Inbox and chat headers show current names.
- Shared controls use semantic shapes, clear pressed/disabled states, and text or icon-independent status descriptions.
- Keep business, routing, and transport decisions out of composables. ViewModels/use cases expose UI state and user actions.
- Route composables collect state; reusable stateless UI lives in feature `ui/components`.

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

Relayed status requires a verified path through a ready first hop; cached topology is not live reachability. Unblock never implies Direct. UI surfaces re-resolve shared peer state.

Never show “connected” from a BLE callback alone. Counters use routing's readiness definitions.
Home may show an active mesh without a ready peer; direct-ready and checking need link evidence. Permissions describe OS grants, not Bluetooth or peer readiness. About and Privacy state Keystore and first-seen trust limits without claiming authenticated E2EE or delivery.

## Messaging and safety feedback

Public queue rejection/partial acceptance uses feedback; acceptance never implies recipient delivery.

- Failed private sends remain unsent and explain missing readiness/keys without exposing crypto details.
- Distinguish queued, sending, delivered, failed, and blocked outcomes; do not imply peer receipt from enqueue.
- Inbox rows do not mark private messages seen; visible chat bubbles do. Keep Unread selectable at zero and use filter-specific empty states.
- SOS alerts and cancellation feedback must identify the relevant alert/sender.
- Incidents distinguish local save, pending selection, confirmation, and sync uncertainty. Offers are self-reported by helpers. Unreachability never auto-reassigns. Detail sheets use 3 tabs (Briefing, Offers, Timeline), docked tactical actions, reachability dots, and capability chips.
- SOS uses one accessible slide ("Slide to Broadcast"); early release resets it.
- Radio plays recorded notes in order and shows the current speaker name. Off clears its queue; manual chat audio takes priority, then Radio resumes.
- SOS illumination works in both appearances.
- Never render debug plaintext, keys, ciphertext previews, or sensitive location in the debug UI.

## Background mesh status

- Device settings provides opt-in **Keep mesh active in background**. Service-active does not imply peer-ready.
- Its silent notification offers **Open ResQMesh** and **Go offline** using coarse repository state. Disabling only removes the background anchor; **Go offline** stops transport.

## Diagnostic terminal

- The terminal is bounded and session-only, not a Logcat replacement.
- Categories: Connection, Sync, Transport, Routing, Security, System, Alerts. Must emit categories explicitly.
- Direct summaries use lifecycle evidence, not advertisements; show peer, endpoint, role, readiness, and transport.
- Display newest events first. If scrolling older, pause follow, show new-event count, provide jumps to Latest/Sync.
- Keep heartbeat/relay chatter behind Details.

## Hidden legacy UI
- Legacy Radar/terminal stay behind Debugging Mode; Settings manages offline maps.
- Restored callbacks preserve accessibility labels without altering underlying mesh policies. Validate empty, permission-denied, and error states.

## Accessibility and interaction

- Provide readable contrast, touch targets, content descriptions, and text equivalents for color/status indicators.
- Keep startup errors visible with recovery. Map setup returns to the SOS alert.
- Preserve user drafts when a recoverable send fails.
- Conversations anchor latest messages above the IME and follow new messages. Community reader circles require recorded `seenBy` receipts.
- Avoid rapid status flicker; state transitions should follow repository/link evidence rather than raw scan churn.
- Home peer chips open the matching Network details by stable ID. Blocked peers remain in "Blocked Devices (Direct Link Denied)" with relay messaging when a route exists.
- Review both appearances independently. Active screens use theme tokens, never fixed dark surfaces or white text.

## Validation

Run focused UI checks and inspect affected states. BLE, route, key, delivery, and SOS labels require physical tests from `validation.md`.
