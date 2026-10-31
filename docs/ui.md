# UI guidance

Last reviewed: 2026-10-06. Baseline: see [status](status.md). Use Compose/Material 3; inspect current source.

## Components and ownership

- Reuse shared components/theme tokens. Night uses black/grey panels and 1dp borders; Daylight uses white/grey with black text.
- Safety Orange (#FF5A00) marks actions; green positive, amber attention, grey offline and red SOS. Use text/semantic shapes and at least 48dp targets.
- Home retains its larger name, plain location labels and network view within nearby sharing.
- Mission, Messages, Voice and Mesh use the shared shell. Mission/Messages/Voice have solid theme backgrounds and a prominent navigation SOS action without continuous pulse.
- Chat composers hide inline media tools while typing; the floating action bubble retains access. They show the shared send budget ("13 of 15 left", plus "+1 in 4 s" while refilling); at 0 Send is disabled, reads "Wait N s" with a matching content description, and typed text is kept. Network/topology uses cards and a 2D route map. The SOS hub raise button shows "N of 3 left" (+1 per 20 s); at 0 it is disabled and reads "Wait N s".
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

SOS location row: per-SOS include (default on), Allow/Turn on, never blocks sending. SOS cards retain category/status/transmission, location accuracy, time and history. Header time fallback never proves GPS freshness. Reserve measured floating-header/reminder regions, reverse conversations and keep latest messages/composer above the IME. Short/large-text threads expose scrollable SOS controls; badges wrap. Ended badges say ENDED, not rescue completed.

Incidents use historical reporter names and ownership badges. Identity loading/errors explain unavailable controls. Details use one page and docked actions. Selected offers cannot be edited; withdrawal removes selection and requires fresh approval. Route loss never reassigns. Connection labels describe the selected helper's actual route.

Helper chat shortcuts qualify the historical label with the offer's stable mesh node ID. Hide shortcuts for self or missing IDs; reporter user IDs and signing keys cannot substitute for a mesh ID. Quick filters expose selected semantics and 48dp targets; label the search input for accessibility. Selected-helper route text wraps, and helper replacement controls grow from a 48dp minimum. Keep existing filtering, ordering and workflow authority.

Never display debug plaintext, keys, ciphertext previews or sensitive location.

## Sessions, navigation and diagnostics

Background mesh is opt-in. Home/notification distinguish Bluetooth off, permission required, starting, searching and connected. OFF clears readiness; Go offline cancels recovery. Home pill greys only for offline mesh.

Home peer chips open matching stable-ID Network details. Direct/routed peers going offline: grey, uncounted. Blocked peers stay in Blocked Devices (Direct Link Denied), with relay messaging when a route exists. Settings owns offline maps.

The developer console is a memory-only tabbed screen ([plan](plans/developer-mode.md)); release hides it until seven taps on the app version, and only with a configured PIN.

Screen sections live within their features; saveable state, launchers, effects and coroutine scopes stay with original owners. The map canvas owns one MapView/observer/subscription/cleanup. The [size ledger](../archive/docs-superseded-2026-10-16/handoffs/maintainability-size-inventory.md) records ownership; [validation](validation.md) records UI-02's existing navigation failures.

## Accessibility and validation

Support both appearances, contrast, targets/labels, empty/error/denied states, small screens, landscape, large text, TalkBack and restoration. Startup errors offer recovery; map setup returns to its SOS. Prevent reminder/header/keyboard overlap and scan-driven flicker.

Functional phone results live in [validation](validation.md); open issues in [status](status.md). They do not establish completed accessibility/novice usability, every channel/gesture regression or pressure/fallback scenario. Use the [SOS/conversation](testing/sos-conversations-test-card.md) procedure. Keep measured outcomes in validation rather than adding transcripts here.
