# ResQMesh project context

Current checkout: temp at d7cd5e3 with uncommitted edits present on 2026-09-15. ResQMesh is an offline Android messaging and emergency mesh prototype using native BLE advertising/scanning, dual-role GATT links, and an optional L2CAP path after GATT PSM discovery. It uses Kotlin, Compose, Protobuf, Room, coroutines, and a graph-based MeshRouter. Google Nearby Connections and Wi-Fi Direct are not part of the current implementation.

Read [docs/README.md](docs/README.md) for current documentation, [docs/ble_repair_pipeline.md](docs/ble_repair_pipeline.md) for the next repair order, and [docs/project_status_tracker.md](docs/project_status_tracker.md) for evidence and unresolved issues. Older roadmap and blueprint files are historical or speculative unless validated against source and devices.

Current priority: reproduce and repair the GATT connect/disconnect/reconnect/no-progress symptom. The working-tree edits partly address duplicate links and connect-lock stalls, but callback ownership, complete cleanup, server notification serialization, and operation sequencing remain open. Record exact device versions, callbacks, and test results before calling a fix complete.

For source navigation, use [docs/ARCHITECTURE_DEEP_DIVE.md](docs/ARCHITECTURE_DEEP_DIVE.md) and [docs/codebase/codebase_index.md](docs/codebase/codebase_index.md). Do not overwrite uncommitted source edits when beginning a new conversation.
