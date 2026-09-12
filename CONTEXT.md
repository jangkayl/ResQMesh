# ResQMesh Project Context & Pipeline

This file serves as the master context and pipeline tracker for AI coding assistants (Antigravity, Claude, Cursor, etc.). It explains what the project is, the core technologies, and references the specific architectural rules located in `docs/`.

## 1. Project Overview
**ResQMesh** is a decentralized, offline messaging application designed for disaster scenarios where cellular and Wi-Fi networks are unavailable. It relies exclusively on a **Bluetooth Low Energy (BLE) Dual-Role Scatternet Architecture** to allow devices to bridge and route messages (multi-hop mesh networking) between users who are out of direct physical range.

## 2. Tech Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Component-Driven Architecture)
- **Networking:** Native Android BLE (GATT Server/Client)
- **Architecture Pattern:** MVVM (Model-View-ViewModel)

## 3. Directory Structure Context
- `app/src/main/java/com/example/testresqmesh/core/network/` -> Contains all the native BLE networking logic (`NativeBleManager.kt`, `PayloadDispatcher`). This is the most complex part of the app.
- `app/src/main/java/com/example/testresqmesh/feature/` -> Feature modules (e.g., `comms/ui/` for chat screens, `comms/viewmodel/` for state).
- `app/src/main/java/com/example/testresqmesh/core/model/` -> Data classes and serializers.
- `docs/` -> Granular markdown files describing specific AI rules and system mechanics. (Open this folder in Obsidian as your Project Brain!)

## 4. Current Pipeline & Roadmap
### Phase 1: Core Mesh Routing (Completed)
- [x] Implement Dual-Role Scatternet (Devices act as both Client and Server).
- [x] Fix Trace Route visibility for multi-hop messages.
- [x] Optimize BLE speeds using MTU tracking and `CONNECTION_PRIORITY_HIGH`.
- [x] Implement Block/Unblock mechanic to manipulate routing paths dynamically.

### Phase 2: Range & Stability (In Progress)
- [ ] Investigate and implement Long-Range Mode (`PHY_LE_CODED`).
- [ ] Fix edge-case disconnection bugs (e.g., Samsung 3-node specific dropout issues).
- [ ] Improve battery efficiency during background scanning.

### Phase 3: Encryption & Security (Planned)
- [ ] Implement End-to-End (E2E) encryption using Elliptic Curve Cryptography.
- [ ] Ensure bridging nodes cannot read payloads intended for others.

## 5. Strict AI Rules
For a complete list of rules, see `docs/ai_rules.md`.
**CRITICAL:** Never write python scripts or use bash commands (`sed`, `awk`) to mass-edit code in this repository. AI must use native file-writing/replacing tools to preserve IDE rewind states.

---
**Note to AI:** When working on this codebase, always read the corresponding rule file in `docs/` before modifying complex systems like the BLE network.
