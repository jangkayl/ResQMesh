# Capstone research guide

Last reviewed: 2026-10-04. Source baseline: `2e27013`.

Separate defensible research claims from product aspirations. This is not a defense script, a grade prediction or an assertion that a pilot has completed.

## Research purpose

Evaluate whether nearby Android phones can support useful offline emergency text/SOS and incident coordination through native BLE relays under documented conditions. Examine how lifecycle, routing, feedback and private-message policy affect outcomes.

Production intent raises the evidence requirement; it does not turn a prototype into a guarantee.

## Current achievements and unresolved outcome

Source implements native BLE/GATT, optional L2CAP, Protobuf, directed graph routing, Room persistence, origin outboxes, resumable transfer journals, Compose, Keystore-based private encryption, signed incident/SOS state and offline MapLibre/PMTiles.

On 2026-10-04 the user reported successful A–B–C–D text arrival, private delivery/read receipts, complete recorded playback and SOS arrival on the current APK. Recovery, background, incident lifecycle/sync, independent SOS cancellation, blocking and offline map use were also confirmed qualitatively. See the [candidate and evidence limits](validation.md).

The same report identifies intermittent public/private message or voice loss at D. Successful demonstrations establish capability; they do not establish consistent delivery. DELIVERY-01 remains open, with no diagnosed cause. Do not describe the four-phone chain as a reliable-capacity experiment or infer a success percentage.

## Core questions

1. How repeatedly do phones establish READY links, deliver in both directions and recover?
2. How do verified A–B–C and A–B–C–D relay paths affect arrival and receipt confirmation?
3. How do device/API, obstruction, restart, toggles, background state and traffic affect loss and delay?
4. Does the UI distinguish direct, checking, routed, nearby and offline states accurately?
5. Do private-message failures stop without plaintext fallback or false delivery?
6. Do incident/helper and independent SOS states converge after reconnect?
7. Which measured limits prevent operational use?

## Evaluation method

Use versioned builds and the [physical procedures](validation.md). Record models/API, topology and actual transport, distance/conditions, workload, attempt count, sent/received/confirmed counts, duplicates, timestamps and focused captures. Define sample size and procedure before comparing builds. Include failures, unavailable conditions and untested cases.

Measure READY success, bidirectional delivery, relay loss/duplicates, disconnect detection and recovery, arrival/receipt/playback latency, SOS cancellation, incident convergence, key refusal and battery over a stated duration. Cross-phone timing requires a shared reference. USB-powered captures do not establish battery behavior.

Keep raw captures local and publish privacy-safe summaries. Existing test counts and emulator results apply to their named source snapshots, not automatically to the current phone build.

## Claims still requiring evidence

- Guaranteed range, throughput, capacity, latency, battery, self-healing or delivery rate.
- Controlled five/ten-phone reliability or consistent four-phone delivery.
- Authenticated E2EE, forward secrecy or production identity/key security.
- Precise distance/victim location from RSSI.
- Wi-Fi Direct, Nearby Connections or completed transport migration.
- Validated novice usability, accessibility, public-beta readiness or a completed pilot.

Incident functional success is separate from the archived UX study proposal: five novice participants, four-of-five uncoached completion, helper intent within five seconds and no accidental destructive/false-delivery interpretations. This study and small-screen, 200% text, TalkBack, restoration and both-theme checks remain unconfirmed.

## Future-work boundary

Implemented outboxes/journals must not be relabeled as wholly future store-and-forward. Stronger disconnected-cluster delivery, alternate-route repair/replication, resource requests, safety check-ins, location-confidence policies, battery-aware routing and new transports remain deferred unless separately prioritized and authorized.

Use [decisions](decisions.md) for accepted choices, [status](status.md) for active work and the [archive](../archive/docs-superseded-2026-10-04/README.md) for historical proposals.
