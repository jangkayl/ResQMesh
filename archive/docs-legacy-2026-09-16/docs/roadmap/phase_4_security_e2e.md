---
tags: [roadmap, security, partial]
---
# Phase 4: private-message security

The current code already encrypts private-message content with a one-time AES-GCM key wrapped to a peer's RSA public key. The public key is propagated through the mesh. It is therefore inaccurate to say that all private messages are plaintext or that encryption has not started.

Still open:

- [ ] Authenticate a peer's public key and bind it to stable node identity.
- [ ] Define key lifecycle and recovery across app restarts.
- [ ] Assess replay and metadata exposure.
- [ ] Use an authenticated session key exchange before claiming per-session forward secrecy.
- [ ] Verify text, media, SOS, and relay behavior separately; do not assume one encrypted path covers every payload type.
- [ ] Align UI, README, and thesis claims with the verified threat model.

This security work follows the urgent [BLE connection repair](../ble_repair_pipeline.md) unless a concrete security defect changes priority.
