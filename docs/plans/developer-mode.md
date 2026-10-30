# Developer console plan

Branch: `feat/developer-console`. Status: Stage 1 and live follow are pushed; Stage 2 (hop, drop, failure, transfer progress, voice, timeline) is built with local checks only; phone card pending. No push or PR without an explicit request. Current state lives in [status](../status.md); accepted policy is D30 in [decisions](../decisions.md).

## Goal

Replace the flat debug terminal with a tabbed console that shows who is connected, what was sent or received, where it was going, whether it was public or private, how it hopped and what failed, without lag and without changing mesh behavior.

**Hard rule:** no change to wire format, routing, relay, retry, crypto or connection decisions. New code only observes, and emits never throw.

## Decisions

| Area | Decision |
| --- | --- |
| Structure | Full screen, bottom tabs: Overview, Links, Packets, Voice, Errors, Logs |
| Style | App theme, `Spacing`, `ResQStatusChip`; monospace only for IDs and numbers |
| Packet row | 2-3 line card; tap for all fields and a same-phone timeline |
| Default traffic | Everything shown; heartbeats and transfer pieces collapse; failures never collapse |
| Tools | Search, filter chips, follow one packet, pause |
| Access | Badge with red dot for new ERROR entries; long-press opens Errors |
| Release | Hidden until seven taps on the app version, then a build-configured PIN; unavailable when no PIN is configured |
| Peers | On screen `Name (short id)`; exports carry short IDs only |
| Storage | Memory only; sanitized text and CSV export through the system file picker |
| Voice | Voice-note transfers and record/playback errors only; no live push-to-talk instrumentation |

The user plans to remove live push-to-talk later. Raise it when this work finishes.

## Release access policy (D30 detail)

The console only observes mesh behavior; traces stay in memory and exports hold no names. Release access needs a `resqmesh.devPin` of 8 or more ASCII characters in `local.properties`; a shorter or non-ASCII value fails the build. Only a PBKDF2WithHmacSHA1 hash (150,000 iterations, fixed project salt) is shipped; with no PIN the console is unavailable and a stored flag is ignored. The section stays hidden until seven taps on the app version. Five wrong PINs lock entry for 30 seconds, and rotating the PIN turns developer mode off. A hash in an APK stops only casual access. There is no push-to-talk instrumentation.

## Design

- `core/logging/trace/` (no Android imports): `TraceGate`, `TraceRing`, `PacketTrace`, `TraceStore`, `TraceEnums`.
- `feature/devtools/`: `DevToolsViewModel`, `TraceCollapser`, `TraceFilter`, `LogSanitizer`, `TraceExporter` and one UI file per tab plus `PacketRow` and `PacketDetail`.
- Lock-free rings by stream so heartbeats cannot evict errors: DATA 2000, CONTROL 1000, ERRORS 500, LOGS 1000, VOICE 300.
- Emits store small typed events with no string formatting on hot paths. Timestamp formatting and MAC redaction happen at read time.
- The ViewModel snapshots every 250 ms off the main thread only while the screen is subscribed, and skips when nothing changed. Pause freezes the published copy.
- `TraceGate.enabled` is pushed in from the application and `SetupViewModel`; `core/` never imports `DeveloperModePolicy`. Off means emits return at once and the rings clear.
- `packetKey = targetMessageId.ifBlank { id }` links receipts to their message timeline.
- `type` is an enum with OTHER, so peer-supplied strings are never formatted.

**Privacy.** Events never hold text, encrypted data or keys, audio, coordinates or public keys. Exceptions log `javaClass.simpleName`, not messages. `LogSanitizer` exports only strict `MARKER key=value` lines; other text becomes "free text omitted", name-like keys are redacted and full MACs shortened.

**Honest labels.** Public relayed packets show `hop N / TTL left M` only when TTL is above zero. Private packets use route step from the directed route. Public destination shows "all"; receipts and pieces show no sender. Link RSSI and advertisement RSSI are separate and never merged; show "unknown" when neither exists. The timeline covers this phone only.

## Stages

### Stage 1: screens, fast logger, export, release unlock (built)

1. Gate, rings, store, ViewModel, collapser, filters, with JVM tests.
2. Tabs, packet row and detail. Overview shows link, error and ring counts; Links shows all links with radio CONNECTED and payload READY distinct.
3. Badge dot, long-press and `openDevTools(tab)`.
4. Export, sanitizer and the `e.message` fixes.
5. Two observe-only emits: `OutboundFrameReporter` before its filter and `NativeInboundPipeline` after decode.
6. Release unlock: `resqmesh.devPin` in gitignored `local.properties` becomes a salted SHA-256 hash in `BuildConfig`; the plain PIN never ships. Debug falls back to `0000`. With no PIN in release, developer mode is unavailable and a stored flag is ignored.

### Stage 2: hop and drop data (built, local checks only)

Route fields at the emits, drop reasons at dispatcher and pipeline returns, emits at broadcast and raw-send rejections and at outbound delivery failure and expiry, piece progress through a `trace` lambda on `ReliableMeshTransfers`, voice errors from `MediaHelper` and transfer failures, a defaulted transport parameter, and the finished timeline.

### Stage 3: signal and queue stats (pending)

A link RSSI sampler that runs only while the screen is open, with one read in flight, one link per tick, about 5 s per link and no retry, and only when the link is READY and idle. Advertisement RSSI is recorded for server-role links. Queue and flight counts are sampled at 1 Hz through read-only getters. Throughput is derived from events. Its gate is no new disconnects.

## Risks

- A tracing decode adds load under congestion: rate limit it and run it only on failure paths.
- Reporter callbacks run on the main looper: keep them allocation-light.
- `readRemoteRssi` may drop callbacks; Stage 3 needs the no-new-disconnects check.
- A PIN hash in an APK stops only casual access and a short PIN can be brute-forced offline, so use a long PIN. Anyone unlocked sees peer names and traffic metadata, never content or keys.
- Foreground behavior while the file picker is open needs a phone check.
- Detekt limits and the `FunctionNaming` baseline need attention each stage.

## Verification

Each stage: unit tests, lint, detekt, `check_boundaries.ps1`, `check_docs.ps1`, the full suite once before commit, and review. The auditor checks Stage 1 export, privacy and the release PIN gate. A clean build is not BLE proof; each stage closes only with bounded phone evidence from the [phone card](../testing/developer-mode-card.md).
