# Capstone research guide

This page separates defensible research claims from product aspirations. It is not a defense script and does not assign grades or predict panel reactions.

## Research purpose

Evaluate whether nearby Android phones can support useful offline emergency text/SOS communication through a native BLE relay prototype under documented device and test conditions. The work also explores how connection lifecycle, routing, delivery feedback, and privacy behavior affect reliability.

ResQMesh is both a capstone artifact and a production-oriented prototype. Production intent increases the need for honest limits; it does not turn prototype evidence into a guarantee.

## Core questions

1. Can two phones repeatedly establish a payload-ready link, exchange messages in both directions, disconnect, and recover?
2. Can a three-phone arrangement relay messages when the endpoints lack a direct usable link?
3. How do phone model, Android version, distance/obstruction, process restart, Bluetooth toggling, and traffic affect delivery and recovery?
4. Does the UI distinguish direct-ready, indirect, unresponsive, recently visible, and offline states accurately?
5. Do private-message failures stop safely without plaintext fallback or false delivery?
6. What measured limits prevent production use without further engineering?

## Evaluation method

Use versioned builds and the test levels in `validation.md`. For each meaningful run, record devices, Android versions, topology, distance/conditions, test steps, attempt count, successes/failures, approximate timestamps, and focused capture path. Repeat reliability measurements rather than selecting one successful demonstration.

Recommended metrics:

- Connection and payload-ready success rate.
- Bidirectional direct-message delivery rate.
- Multi-hop delivery and duplicate rate.
- Disconnect detection and recovery time.
- Message latency under stated payload/traffic conditions.
- SOS delivery/cancellation correctness.
- Private-message blocked, delivered, and decrypt-failure outcomes.
- Battery impact over a defined duration and device state.
- Failure distribution by device model and Android version.

Define the procedure and sample size before making comparative claims. Preserve raw local captures, but publish only privacy-safe summaries.

## Claims currently supportable

- The checkout implements native BLE advertising/scanning, GATT client/server communication, optional L2CAP payload transfer, Protobuf messages, graph-based routing, Room persistence, Compose UI, and hybrid private-message encryption behavior described in `architecture.md`.
- Focused builds/tests and several two-phone captures exist for specific lifecycle states and failures.
- Device observations can support statements limited to the tested build, phones, topology, and scenario.

## Claims not yet supportable

- Guaranteed range, throughput, capacity, latency, battery life, self-healing, or delivery rate.
- Reliable five-, ten-, or larger-node operation without a completed matrix.
- Precise distance or victim location from RSSI.
- Completed Wi-Fi Direct, Nearby Connections, or full protocol migration.
- Authenticated E2EE, forward secrecy, or production-grade identity/key management.
- Production readiness based only on compilation, unit tests, static review, or a successful demonstration.

## Research and product boundary

Ideas such as Wi-Fi Direct, store-and-forward delivery, battery-aware routing, or alternative protocols may be evaluated as future work. They enter active engineering only after a decision in `decisions.md` and an explicit priority in `status.md`. Historical Antigravity defense notes and blueprints are archived context, not evidence.
