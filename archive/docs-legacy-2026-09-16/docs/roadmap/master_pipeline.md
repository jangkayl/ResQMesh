---
tags: [roadmap, current]
---
# ResQMesh master pipeline

The current execution order is [BLE connection repair](../ble_repair_pipeline.md). Earlier phase documents in this folder describe design intent and past work; their Completed labels are historical and do not establish current phone reliability.

1. [ ] Baseline: review uncommitted edits, build the app, reproduce the connect/disconnect/reconnect/no-progress symptom, and capture callback logs.
2. [ ] Connection ownership: per-link state and generation, explicit READY state, and stale-callback protection.
3. [ ] Complete cleanup: all client/server/forced disconnect paths clear only their owned link state.
4. [ ] Callback-driven sending and GATT setup: server onNotificationSent, client write completion, CCCD/service/PSM sequencing, bounded operation timeouts.
5. [ ] Deterministic recovery: one direct-peer limit, collision handling, backoff, watchdog behavior, and L2CAP-to-GATT fallback.
6. [ ] Validation: two-phone lifecycle tests, three-phone forwarding, then five- and ten-phone capacity tests across capstone devices.
7. [ ] Security and documentation: authenticate private-message keys or narrow E2EE claims; align README and thesis with verified behavior.
8. [ ] Reassess later features using [the feature priority checklist](feature_priority_checklist.md) after stability and emergency/private-chat gates pass. Wi-Fi Direct and other large-scope ideas are not required for the current project goal.

Use [project status](../project_status_tracker.md) for issue IDs and completion evidence. Protocol comparison and migration documents under blueprints/ are proposals, not commitments to migrate.
