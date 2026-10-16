# ResQMesh September 30 static UI preview

Last reviewed: 2026-10-04. Status: preserved static design reference. Snapshot inspected: 2026-09-30; applicability reviewed against source baseline `2e27013`.

The plugin is not synchronized automatically with current Compose source or the latest phone results. Its existing name and generated examples remain a September 30 snapshot; no plugin code was changed.

This local Figma Design plugin creates six editable 390 × 844 Night frames on the **currently selected page**. It reads no Figma content and makes no network requests. It adds one named parent frame and never removes existing layers. Run it again only if you want another copy.

## Import and run

1. Open the [ResQMesh UI Reference](https://www.figma.com/design/5a1fRNHvpwUC3qSTq0TQ2B/ResQMesh-UI-Reference?node-id=54-5) file in the Figma desktop app. Select the existing page where you want the mockups.
2. Choose **Plugins → Development → Import plugin from manifest…** and select this folder's `manifest.json`.
3. Run **Plugins → Development → ResQMesh Current UI Preview**.
4. Figma should select and zoom to **ResQMesh current UI - Night - Mission + Messages**, containing six frames. Expand it in Layers to edit the cards, labels, badges, shapes, and controls.

If Figma requests a plugin ID when importing, use **Plugins → Development → New plugin** to generate a local ID, then copy that ID into `manifest.json` and import again. Do not publish the plugin.

## What this represents

- Snapshot of `HomeScreenContent`, `MessagesInboxContent`, `PrivateChatHeader`, `PrivateMessageBubble`, `ChatInput`, and shared floating navigation, as inspected on 2026-09-30.
- Fixed example states: ready direct peers, searching, inbox with direct/relayed rows, empty inbox, delivered chat, and failed delivery chat.
- Fictional names and text only. This is a dated static design reference, not live mesh data or evidence that delivery succeeded.

The layer geometry follows Compose dimensions and tokens, but animated radar sweep, system insets, Android font rasterization, scrolling, and exact Material icon paths cannot be reproduced automatically by this simple plugin. Its controls and text remain editable in Figma. A device screenshot comparison remains open until the user runs the plugin and supplies a Figma capture.

For current UI ownership, behavior and evidence use [UI guidance](../../docs/ui.md) and [validation](../../docs/validation.md). A later visual refresh needs a separately scoped source/screenshot comparison.
