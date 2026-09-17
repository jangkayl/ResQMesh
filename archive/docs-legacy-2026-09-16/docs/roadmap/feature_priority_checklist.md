---
tags: [roadmap, current, feature-priority]
---
# Feature priority checklist

Status: planning only. Reviewed against the `temp` checkout at `d7cd5e3` and its uncommitted changes on 2026-09-15. Source review does not prove phone reliability. This document is local-only with the rest of `docs/`.

## Project goal and decision rule

ResQMesh is an Android prototype for offline emergency communication between nearby phones. Its essential demonstration is that a text message or SOS can reach another phone over a direct BLE link or through a relay, survive disconnect/reconnect, and report delivery honestly. A feature earns priority only if it improves that demonstration or fixes a misleading safety/security claim. Large files, precise location claims, and extra radios are outside the current reliability goal.

Use [the BLE repair pipeline](../ble_repair_pipeline.md) for detailed connection work and [the issue tracker](../project_status_tracker.md) for evidence. Do not mark any gate complete from a build alone.

## P0 — Repair the existing mesh before adding features

- [ ] Review and preserve the current uncommitted BLE, repository, model, and Radar edits; record the exact test baseline.
- [ ] Reproduce the reported connect/disconnect/reconnect/no-progress failure on two physical phones with callback and queue logs.
- [ ] Make one connection lifecycle owner, generation-aware callbacks/timers, complete disconnect cleanup, and an explicit app-level `READY` state.
- [ ] Serialize GATT setup and outgoing writes/notifications by callback; bound retries, queues, and inbound frame sizes.
- [ ] Make L2CAP failure recover to a verified GATT path; remove stale sockets only when the failing socket owns the current slot.
- [ ] Align topology refresh and expiry, process empty neighbor snapshots, and prevent stale or duplicate relays from claiming delivery.
- [ ] Verify SOS broadcast/cancel and private/public text along a three-phone A–B–C route after B disconnects and returns.
- [ ] Record results for repeated two-phone lifecycle tests, three-phone forwarding, then five/ten-phone capacity tests on the available devices. Record failures as well as successes.

**Exit gate:** text and SOS continue to work after repeated reconnection; forwarding and link-loss behavior are measured on phones; the known no-progress symptom is resolved or its remaining failure rate is documented. Do not start optional feature work before this gate.

## P1 — Protect the emergency and private-chat behavior already present

- [ ] Give SOS and small text control traffic priority over image/audio and routine topology chatter without starving any queue forever.
- [ ] Tie SOS cancellation and location callbacks to the specific alert so one cancellation cannot clear another sender's alert or send after local cancellation.
- [ ] Make missing recipient keys and encryption failures explicit failed/queued private sends; never silently send private content as plaintext.
- [ ] Encrypt private location coordinates with private text/media, stop logging decrypted plaintext, and define a deliberate local backup policy.
- [ ] Bind a recipient public key to a verified identity, or remove/qualify the current `E2EE` UI and documentation claim until authentication is implemented.
- [ ] Show a clear state for queued, sent, delivered, and failed messages; verify that the UI matches actual network events.

**Exit gate:** emergency traffic is not buried by bulk traffic; alerts cancel correctly; private-chat security labels and delivery states match measured behavior.

## P2 — Add only if the core mesh passes P0/P1

### Persistent outbox and delayed delivery — first candidate

- [ ] Define which text/SOS payloads may be stored and retried when no route exists; distinguish “queued locally” from “delivered.”
- [ ] Bound storage, retry count, message age, and duplicate forwarding; decide expiry based on the emergency use case rather than assuming 72 hours.
- [ ] Persist enough state to survive process restart and reconnect; test isolated clusters meeting later through a moving phone.
- [ ] Protect private queued content and clear it when delivered, expired, or deleted by the user.

This directly supports disconnected disaster clusters, but it adds complexity to routing and delivery receipts. It is a candidate, not a prerequisite for demonstrating a stable three-phone mesh.

### Battery preservation — measured, limited candidate

- [ ] Measure scan/advertise/relay power use on capstone devices before selecting thresholds.
- [ ] Prefer reducing bulk media and routine traffic while retaining SOS reception, forwarding, and an escape path for low-battery relays.
- [ ] Test whether any relay reduction isolates peers; do not automatically `DETACH` at 15% without evidence.

### RSSI guidance — optional presentation aid

- [ ] If requested, show a conservative stronger/weaker signal trend for a directly scanned peer.
- [ ] Test movement, body obstruction, and different phones; label the value as signal strength, not distance or victim coordinates.

## P3 — Not needed for the current project concept or release

These ideas can remain research notes. Do not put them on the implementation path unless a measured need and new scope justify them.

- **Wi-Fi Direct, SoftAP, or another high-bandwidth transport:** unnecessary for reliable text/SOS relaying. Reconsider only if large photos or map packages become a required, measured use case. It would need separate discovery, setup, permissions, fallback, and device testing; no fixed transfer-time promise.
- **Virtual private mesh that hides civilian peers:** risks reducing available relays. If responder confidentiality is required later, study authenticated encrypted channels over the shared relay graph instead.
- **Precise RSSI heatmaps or 3D SOS triangulation:** signal strength does not establish reliable location from this prototype; avoid rescue-grade location claims.
- **Offline map tile distribution, drone data mules, live audio upgrades, and high-resolution media:** useful only after the core emergency path is stable and the deployment scenario actually requires them.
- **Protocol replacement or full L2CAP migration for performance:** do not undertake solely because a blueprint proposes it. Measure the current GATT/L2CAP path and identify a specific blocking limit first.

## Documentation and scope gate

- [ ] Keep [the older future-feature roadmap](../future_features/future_features_roadmap.md) and `antigravity_insights/` as speculative design history, not active requirements.
- [ ] Update README, presentation, and thesis statements to distinguish implemented behavior, planned work, and measured phone results.
- [ ] Revisit this priority list only after the P0/P1 evidence is recorded or the project goal changes.
