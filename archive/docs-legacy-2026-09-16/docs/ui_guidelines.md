---
tags: [ui, compose, guidelines]
---
# UI/UX Architecture & Guidelines
Current design guidance. Read the actual screen and ViewModel before treating a UI behavior below as implemented; the BLE lifecycle and Radar labels are still under repair.

*Up:* [[project_summary]]

## 1. Core Paradigm: Component-Driven Compose
ResQMesh utilizes Jetpack Compose exclusively for its UI. The overarching philosophy is **Component-Driven UI**:
- Screens should never be massive, monolithic files.
- Break complex views down into isolated, reusable `@Composable` functions (e.g., `MessageBubble`, `TraceRouteModal`, `MeshNodeBadge`).
- Do not pass heavy ViewModels down into leaf composables. Extract state at the screen level and pass simple lambda functions (`(String) -> Unit`) and primitives down to the child components (State Hoisting).

## 2. Design Language
- **Modern & Utilitarian:** The app must look professional, clean, and function clearly under stress. Avoid excessively flashy animations that drain the battery.
- **Visual Network Cues:**
  - **Direct Links:** Shown normally.
  - **Mesh Hops (Indirect):** Must clearly display a "MESH HOP" badge.
  - **Trace Routes:** Messages that traversed multiple hops should have an accessible "Trace Route" button to visualize the path. (Ensure `msg.isMine` is NOT restricting incoming trace route visibility!).

## 3. UI State Management
- Use `StateFlow` in ViewModels to expose UI state securely.
- Ensure the UI reactively updates when `knownNodes` or the `networkGraph` changes.
- The "New Mesh Thread" modal can show directly linked and graph-reachable nodes from `uiState.knownNodes`. A stale or provisional topology entry must not be presented as a verified live route.

## 4. Accessibility & Feedback
- Incorporate clear haptic feedback for connection events and emergency (SOS) broadcasts.
- Use explicit visual states (loading spinners, offline indicators, sent/delivered checkmarks) to prevent user confusion during volatile network conditions.
