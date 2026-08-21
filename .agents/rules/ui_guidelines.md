---
description: Design system and component-driven UI philosophy for the ResQMesh application.
trigger: "When the user asks to modify the UI, create new UI components, or change design styling."
tags: [resqmesh, ui, compose, frontend]
---

# UI/UX Guidelines: Component-Driven Architecture Philosophy
*   **Component-Driven:** All UI elements MUST be built using reusable, "shadcn-inspired" Jetpack Compose components located in `app/src/main/java/com/example/testresqmesh/ui/components/`.
*   **No Monolithic Screens:** Avoid massive files like the legacy `ChatScreen.kt`. Break down views into logical, bite-sized Compose functions.
*   **Global Application:** These rules apply to the entire application—every screen (Setup, Chat, Settings) must use the same component library and theming tokens.

## Styling Rules
*   **Tokens:** Strictly use colors, typography, and spacing defined in the `ui/theme/` package. Avoid hardcoding `Color()` or `dp` values directly in screens.
*   **Spacing:** Use consistent spacing increments (e.g., 4dp, 8dp, 16dp, 24dp).
*   **Corners & Shapes:** Maintain consistent border radii (e.g., 8dp for cards, 12dp for chat bubbles) across all components.

## Production-Grade Standards
*   **Navigation:** Use a professional Bottom Navigation Bar for core app areas (Radar, Comms, SOS, Profile).
*   **Visual Fidelity:** Incorporate animations (pulsing, sweeping radar), high-quality iconography, and clear visual feedback (shimmers, haptics).
*   **Security Visualization:** Always provide visual confirmation of security states (e.g., E2EE badges, lock icons) to build user trust in emergency scenarios.
*   **Contextual UI:** Interfaces should reflect the criticality of their function (e.g., high-contrast red for SOS alerts).
