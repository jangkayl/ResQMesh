# ResQMesh workspace context

This file is an entry point for coding assistants. The current source uses native Android BLE GATT client/server links with optional L2CAP payload transport. It does not implement Google Nearby Connections, Wi-Fi Direct, BLE 5.4 PAwR, or a fixed five-hop guarantee.

Before network work, read [documentation index](docs/README.md), [Bluetooth architecture](docs/bluetooth_architecture.md), [project status](docs/project_status_tracker.md), and [BLE repair pipeline](docs/ble_repair_pipeline.md). Before UI work, read [UI guidelines](docs/ui_guidelines.md) and check the current screen/ViewModel source.

Treat source review as a hypothesis and physical-device tests as reliability evidence. Preserve the uncommitted BLE, repository, model, and Radar changes until reviewed. Keep documentation aligned with actual code and measured behavior. Historical plans in docs/blueprints/ and older roadmap phases are not implementation requirements.
