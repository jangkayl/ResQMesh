# UI guidance

The UI uses Jetpack Compose and Material 3. Inspect the actual screen, ViewModel, and shared state before changing behavior; this page owns stable presentation rules rather than component-by-component descriptions.

## Component and state boundaries

- Reuse shared components and theme tokens before local styling.
- Calm Emergency OS is dark-first with a separate high-contrast Daylight preference; appearance affects presentation only.
- Night uses a neutral near-black canvas, opaque raised panels, restrained shadows, and visible 1 dp borders. Use tokens, vector icons, 8 dp rhythm, 16 dp gutters, 18–24 dp radii, and 48 dp touch targets.
- Cyan marks interaction, green a verified positive state, amber attention, and red only SOS/destructive states. Effects establish hierarchy; text and status symbols establish system truth.
- Use mission-first hierarchy, left-aligned page titles, compact bento groups, and a restrained navigation dock with persistent SOS. Ambient fields are decorative only and use 150–220 ms feedback transitions.
- New top-level screens use the reusable shell: Mission, Messages, Voice, and Mesh. SOS is a persistent action rather than a navigation destination.
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

Avoid showing “connected” from a BLE callback alone. Counters and selected peers should use the same payload-ready/reachability definitions as routing.

## Messaging and safety feedback

- A failed private send remains unsent/unstored and explains whether readiness or a usable key is missing without exposing cryptographic details.
- Distinguish queued, sending, delivered, failed, and blocked outcomes; do not imply peer receipt from local enqueue or transport initiation.
- SOS alerts and cancellation feedback must identify the relevant alert/sender once the model supports it.
- SOS uses one accessible slide after type selection; early release resets it and no second confirmation is shown.
- Never render debug plaintext, keys, ciphertext previews, or sensitive location content in the terminal/debug UI.

## Diagnostic terminal

- The in-app terminal is a bounded, session-only diagnostic view; it is not a replacement for a focused Logcat capture.
- Its categories are Connection, Sync, Transport, Routing, Security, System, and cross-category Alerts. Categories must be emitted explicitly for new diagnostics; the compatibility classifier exists only for older log sites.
- Direct-link summary rows use client/server lifecycle evidence, never an advertisement or a routed peer. Show peer name, shortened endpoint, role/generation, readiness state, and transport only.
- Display newest events first. When the user scrolls into older events, pause live follow, show a new-event count, and provide controls to jump to Latest or the latest Sync event.
- Keep heartbeat and relay chatter behind the Details control so connection state, setup failures, and topology changes remain readable by default. Last Sync is a one-time jump, not an instruction to resume live follow.

## Hidden legacy UI restoration checklist

- `OfflineMapPromptModal`, the legacy `RadarScreen`, and the debug terminal remain in source but are intentionally hidden from the current shell; do not delete their backing logic while the new design is evaluated.
- Before restoring one, choose and document its user entry point, reconnect the existing callbacks without changing mesh policy, and retain its accessibility label.
- Validate the restored flow's empty, permission-denied, and error states. For a transport-, SOS-, or location-driven screen, also run its corresponding focused phone test.

## Accessibility and interaction

- Provide readable contrast, touch targets, content descriptions, and text equivalents for color/status indicators.
- Keep error and recovery messages actionable and concise.
- Preserve user drafts when a recoverable send fails.
- Active public and private conversations anchor the latest messages above the composer, keep it above the IME, and follow the newest message when the conversation changes. Sent community bubbles show reader circles only from recorded `seenBy` receipts.
- Avoid rapid status flicker; state transitions should follow repository/link evidence rather than raw scan churn.
- Peer rows are fully clickable. Message requires an unblocked direct/relayed peer; “Known mesh path” is only a topology hint.
- Review both appearances independently. Active screens use theme tokens, never fixed dark surfaces or white text.

## Validation

For UI-only presentation changes, run focused Compose/local checks where available and inspect affected states. For any label driven by BLE, route, key, delivery, or SOS behavior, use the corresponding physical test in `validation.md`; a screenshot alone cannot prove the underlying state is correct.

The active civilian-first refactor is normally reviewed one screen at a time. The user may explicitly approve a continuous pass; phone review is still required before presentation behavior is accepted.
