---
tags: [codebase, index, current]
---
# Codebase index

Source root: app/src/main/java/com/example/testresqmesh/. This index is checked against temp/d7cd5e3 and the working tree on 2026-09-15. The app uses native Android BLE, Kotlin, Protobuf, Room, coroutines, and Jetpack Compose.

- [Core layer](core_layer.md): BLE link managers, payload dispatcher, identity, crypto, and UI-wide building blocks.
- [Data layer](data_layer.md): Room and repository/graph routing.
- [Feature layer](feature_layer.md): chat, Radar, SOS, setup, and profile screens/ViewModels.
- [BLE repair pipeline](../ble_repair_pipeline.md): next implementation order.

Network flow: NativeBleManager/GATT/L2CAP -> PayloadDispatcher and handlers -> MeshRepository and MeshRouter -> Room/StateFlow -> feature ViewModels -> Compose UI. A radio-level connection does not establish app-level readiness or message delivery. The BLE pipeline explains the current lifecycle gaps.
