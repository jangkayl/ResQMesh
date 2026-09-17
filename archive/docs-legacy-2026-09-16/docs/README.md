# Documentation index

The source code and a tested device trace take precedence over these notes. This index was checked against the `temp` checkout at `d7cd5e3` and its uncommitted changes on 2026-09-15. The BLE repair items are planned; they are not verified fixes.

## Current references

- [BLE repair pipeline](ble_repair_pipeline.md): prioritized implementation and validation checklist for the next conversation.
- [Session handoff](session_handoff.md): reusable opening prompt and context-preserving workflow for each repair phase.
- [Project summary](project_summary.md): what the app currently implements and what remains uncertain.
- [Bluetooth architecture](bluetooth_architecture.md): GATT link and optional L2CAP path.
- [Connection stability](connection_stability_architecture.md): current safeguards and known gaps.
- [Project status](project_status_tracker.md): evidence-based issue tracker.
- [Architecture deep dive](ARCHITECTURE_DEEP_DIVE.md) and [codebase index](codebase/codebase_index.md): source navigation.
- [Master pipeline](roadmap/master_pipeline.md): project-level repair order.
- [Feature priority checklist](roadmap/feature_priority_checklist.md): reliability-first feature gates and ideas outside the current scope.

## Historical or speculative material

The `blueprints/`, `debug/`, `antigravity_insights/`, `future_features/`, and older `plan/` documents contain proposals, past audits, or presentation drafts. They can help explain earlier decisions, but they do not define the current implementation or prove device reliability. In particular, documents proposing Wi-Fi Direct, RFCOMM, extended advertisements, a complete L2CAP migration, fixed Android connection limits, or guaranteed kilometer-scale range need separate device and API validation before they become project claims.

`docs/.obsidian/` contains local Obsidian workspace settings, not architecture rules.
