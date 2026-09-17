---
tags: [agent, guidance, current]
---
# Repository guidance for coding assistants

This page is project guidance, not an alternative to the user's current instructions. Check source and device evidence before accepting a design claim in any document.

- For BLE changes, read [Bluetooth architecture](bluetooth_architecture.md), [project status](project_status_tracker.md), and [BLE repair pipeline](ble_repair_pipeline.md). Preserve and inspect existing uncommitted edits.
- For UI changes, read [UI guidelines](ui_guidelines.md) and inspect the actual screen and ViewModel.
- Distinguish direct connected, app-level READY, indirect reachable, provisional, and recently seen states.
- Prefer stable NodeIdentity IDs over truncated names or BLE MACs for peer matching.
- Keep network logging useful for callback and generation tracing without recording message plaintext or key material.
- Describe proposals and static-analysis findings as such. Close a BLE issue after code review, a build/focused test, and a physical-device test.
- Use the editing and testing tools available in the active environment. Old references to Antigravity-only tools and nonexistent .agents/rules paths do not apply to this checkout.

The developer's requested scope always controls the work.
