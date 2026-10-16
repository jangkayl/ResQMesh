# Presentation organization regression

Last reviewed: 2026-10-04. Source baseline: `2e27013`. Status: active procedure. Current APK/version/full hash and the qualitative report are in [validation](../validation.md); historical candidates below are not the current build.

Basic messaging/receipts, notes, SOS, recovery and background use were reported on this APK. Exact navigation/history/draft, notification, theme/accessibility and every regression step remain unconfirmed; intermittent relay loss stays open.

## Build identity

- Branch: `refactor/mvvm-presentation-organization`; organization is now committed as `2e27013`. Its original pre-change baseline was `d9fff4dd697f6a95eb7a1fbda39263788eea8bfc`.
- Version: `1.0.1` / code `2`; debug build with the existing L2CAP flag enabled.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `D4638F9C5835016E136A0A33E1A8F3457A48D9B455CB0F1AD876D296DAE4E686`.
- Devices: user-selected A/B; add relay B between A/C for the three-phone check. Record models and Android/API versions.

## Scope and recorded local evidence

App navigation, UI state, scoped-conversation screens, shared presentation, and Home/network/inbox components have new file ownership. Networking, repositories, database, services, models, helpers, dependency wiring, and configuration remain unchanged: 217 protected files match their baseline hashes. Activity/ViewModel bodies and pre-existing untracked files are preserved.

All 283 original Kotlin files were accounted for; 33 declaration groups were relocated without changing bodies, except qualified references and visibility needed across files. Debug and Android-test APK builds, docs, diff hygiene, and the structure guard passed. Lint passed with zero errors, 147 warnings, and three hints. A simulated mismatched BLE hash was rejected by the guard without changing source.

Twenty-four existing presentation tests passed in a separate focused runner. Standard unit-test compilation remains blocked by the pre-existing untracked `PrivateReconnectDeliveryTest.kt`; all 18 compilation diagnostics match the pre-change baseline. This is not a full-suite pass. The October 4 qualitative report covers basic functions; it does not complete every regression step.

## Setup

Install this exact APK on each selected phone yourself, preserving app data. Keep existing identity, conversation history, block relationships, and background preferences. Use test messages without personal information. Start with both phones in the foreground and grant the existing required permissions.

## Steps and expected results

1. Launch both phones and complete the existing setup flow. Go online and wait for a verified direct-ready peer. Home/network views must retain their previous appearance, peer status, and matching detail actions. No duplicate peer rows or new session starts should result from navigation.
2. Exchange public and private text in both directions. Open the private chat, return to the inbox, and reopen it. Messages, current names, drafts, unread counts, and headers must remain consistent. Recipient arrival and receipt-confirmed delivery are separate observations; queue acceptance alone must not appear as delivered.
3. Open Community, then Radio history. Select the existing Radio channel and exchange one short voice note. Channel history, playback controls, and Community history must remain independent. Navigating away/back must not duplicate playback or sending.
4. Create an SOS on A. Open its hub/thread on B. Back on A must preserve its active SOS; Back/silence on B must silence locally without ending A's alert. Reopen it and end it from A using the existing confirmation. Terminal history must retain its existing behavior.
5. While viewing Home, inbox, and network details, use the existing background option, press Home, then return. Verify the same session survives according to its existing setting. Use **Go offline** and confirm the session stops. Repeat with background mode disabled. Do not clear data or force-stop as a substitute for these checks.
6. Open an available private/SOS notification, including a cold launch. Retain the existing permission/identity setup gate and correct destination.
7. On A–B–C with an existing verified indirect A–C route, exchange private text through B. Compare arrival/receipt state with the prior build. Use the existing [voice/relay card](ble-voice-transfer-test-card.md) for controlled relay loss/recovery.

## Failures and reporting

Report each step as PASS, FAIL, or UNTESTED with APK hash, device/API matrix, timestamps, foreground/background setting, and whether the link was direct or relayed. Failures include crashes, missing actions/history/drafts, changed Back behavior, repeated sends/playback, false delivery/status, or session changes caused by navigation.

For failures, provide the focused time window and markers: `BLE_MESH`, READY/link generation, `PRIVATE_DISPATCH`, `PRIVATE_STORED`, `PRIVATE_RECEIPT`, `PRIVATE_DELIVERED`, and `SOS_SYNC`. Do not include private message contents, keys, or ciphertext previews.
