# UI guidance

The UI uses Jetpack Compose and Material 3. Inspect the actual screen, ViewModel, and shared state before changing behavior; this page owns stable presentation rules rather than component-by-component descriptions.

## Component and state boundaries

- Reuse shared components under `core/ui/components` and theme tokens before adding local styling.
- The civilian-first system is light-only: a pale-white/lavender canvas, large near-black headings, purple emphasis, cyan identity accents, green truthful status banners, and raised white cards. Red is reserved for genuine emergency and destructive actions.
- Use the reference-inspired mobile hierarchy: generous whitespace, strong left-aligned page titles, single-purpose circular or rounded-square actions, and a white capsule navigation dock with SOS floating above it. Do not preserve earlier screen layouts merely by recoloring them.
- The visual system is stable across all supported Android versions and does not depend on system dark mode, blur, or other version-specific rendering effects.
- New top-level screens use the reusable three-destination shell: Home, Messages, and Network, with SOS as a persistent action rather than a navigation destination.
- Shared controls use at least 48 dp touch targets, semantic shapes, and text or icon-independent status descriptions.
- Keep business, routing, and transport decisions out of composables. ViewModels/use cases expose UI state and user actions.
- Prefer immutable screen state and explicit loading, success, empty, warning, and error states.
- Do not perform blocking storage/network work during composition.
- Preserve previews or focused UI tests where they provide meaningful coverage.

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
- Never render debug plaintext, keys, ciphertext previews, or sensitive location content in the terminal/debug UI.

## Accessibility and interaction

- Provide readable contrast, touch targets, content descriptions, and text equivalents for color/status indicators.
- Keep error and recovery messages actionable and concise.
- Preserve user drafts when a recoverable send fails.
- Avoid rapid status flicker; state transitions should follow repository/link evidence rather than raw scan churn.

## Validation

For UI-only presentation changes, run focused Compose/local checks where available and inspect affected states. For any label driven by BLE, route, key, delivery, or SOS behavior, use the corresponding physical test in `validation.md`; a screenshot alone cannot prove the underlying state is correct.

The active civilian-first refactor is normally reviewed one screen at a time. The user may explicitly approve a continuous pass; phone review is still required before presentation behavior is accepted.
