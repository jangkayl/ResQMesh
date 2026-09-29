# UI guidance

The UI uses Jetpack Compose and Material 3; inspect source before editing.

## Component and state boundaries

- Reuse shared components and theme tokens before local styling.
- Tactical & Utilitarian / High-Vis Safety Dark is dark-first with a Field Light Daylight preference; appearance affects presentation only.
- Night uses Pitch Black (#000000) for OLED efficiency, Carbon/Dark Grey raised panels, restrained shadows, and 1dp borders. Daylight uses white and steel grey backgrounds with black text.
- Both use Safety Orange (#FF5A00) for interactions/accents, green for positive states, amber for attention, and red for SOS. Use tokens, vector icons, 8dp rhythm, 16dp gutters, 18-24dp radii, and 48dp targets.
- Use left-aligned titles, compact groups, and icon-only navigation with persistent SOS.
- New top-level screens use the reusable shell: Mission, Messages, Voice, and Mesh. SOS is a persistent action rather than a navigation destination.
- Mission, Messages, and Voice use a solid theme background. Their navigation SOS action stays visually prominent without a continuous pulse; dedicated SOS screens retain their emergency treatment.
- **Chat Composers** hide inline media tools while typing; a floating action bubble retains access.
- **Network & Topology** uses operator cards and a 2D route map instead of raw logs.
- Setup uses a radar splash and civilian identity card.
- First-launch guide: three skippable animated pages, progress dots, no in-page Back; Settings replay.
- Setup has no tag input. Ordinary names omit `[NODE]` and ID; peer details show ID. Inbox and chat headers show current names.
- Shared controls use semantic shapes, clear pressed/disabled states, and text or icon-independent status descriptions.
- Keep business, routing, and transport decisions out of composables. ViewModels/use cases expose UI state and user actions.
- Keep route-level composables responsible for state collection and side effects; move reusable stateless presentation into feature `ui/components` files.
- Keep state immutable/nonblocking; retain tests.

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

## Messaging and safety feedback

- Failed private sends remain unsent and explain missing readiness/keys without exposing crypto details.
- Distinguish queued, sending, delivered, failed, and blocked outcomes; do not imply peer receipt from enqueue.
- SOS alerts and cancellation feedback must identify the relevant alert/sender.
- SOS uses one accessible slide ("Slide to Broadcast"); early release resets it.
- Radio plays recorded notes in order and shows the current speaker name. Off clears its queue; manual chat audio takes priority, then Radio resumes.
- SOS background uses unified multi-layered emergency illumination (sunburst halo, ambient wash, beacon rings, reticle) across both Daylight and Night operations.
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
- Keep error and recovery messages actionable and concise.
- Preserve user drafts when a recoverable send fails.
- Conversations anchor latest messages above the IME and follow new messages. Community reader circles require recorded `seenBy` receipts.
- Avoid rapid status flicker; state transitions should follow repository/link evidence rather than raw scan churn.
- Peer rows are clickable. Blocked devices are grouped under "Blocked Devices (Direct Link Denied)" and can be messaged via mesh hops ("MESSAGE VIA MESH HOP") to test multi-hop relay routing while direct links remain denied.
- Review both appearances independently. Active screens use theme tokens, never fixed dark surfaces or white text.

## Validation

Run focused UI checks and inspect affected states. BLE, route, key, delivery, and SOS labels require physical tests from `validation.md`.

The active civilian-first refactor is normally reviewed one screen at a time. The user may explicitly approve a continuous pass; phone review is still required before presentation behavior is accepted.
