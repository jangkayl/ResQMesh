---
name: compose-shadcn-ui
description: >-
  Senior-level runbook for designing and building highly optimized, reusable "shadcn-style" UI components in Android Jetpack Compose.
tags: [compose, ui, design-system, shadcn]
---

# Jetpack Compose: Shadcn-Style UI Engineering

This skill dictates how to build User Interfaces in the ResQMesh project. It assumes a senior-level understanding of Android Jetpack Compose, state hoisting, and efficient recomposition.

## 1. The Core Philosophy (Shadcn on Android)
Unlike traditional Android XML or heavy Material3 monolithic components, we build lightweight, strictly decoupled, heavily customizable micro-components (similar to `shadcn/ui` in the web world). 

### Rules of Engagement:
1. **Never hardcode global styling inside a screen.** Every screen must rely entirely on `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `MaterialTheme.shapes`.
2. **Dynamic Theming:** If a color needs to be tweaked, it must be tweaked in `Theme.kt`, NOT in the `Modifier.background()` of a specific screen. This ensures 100% consistency and instant dark/light mode switching.
3. **Slot APIs:** Components should accept `content: @Composable () -> Unit` to allow maximum flexibility (just like React children).

## 2. Global Color & Styling System (Dynamic)
To ensure the UI is global and easily adjustable, you must rely on the Android `LocalComposition` tree.

**Correct Implementation:**
```kotlin
// Uses the global theme token. If the theme changes, this automatically updates!
Box(
    modifier = Modifier
        .background(color = MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
)
```

**Incorrect Implementation:**
```kotlin
// HARDCODED TRASH! Will break in dark mode and is a nightmare to update.
Box(
    modifier = Modifier
        .background(color = Color(0xFF1E1E1E)) 
)
```

## 3. Creating a Shadcn-Style Component (Template)
When the user asks you to create a new component (like a Button, Input Field, or Card), follow this exact senior-engineer pattern.

**Example: `ResQCard.kt`**
```kotlin
@Composable
fun ResQCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .then(clickableModifier)
            .padding(16.dp)
    ) {
        content()
    }
}
```
*Why this is senior-level:* It extracts padding, border, and shape logic into a reusable block. It exposes a `Modifier` as the first parameter (mandatory for Compose best practices), and uses a Slot API (`content`) so developers can put whatever they want inside it.

## 4. Optimization & Efficiency
To prevent frame drops and ensure a buttery-smooth 120fps UI:
- **State Hoisting:** Never store `remember { mutableStateOf() }` deep inside a visual component unless it is purely visual state (like an animation). Hoist state to the ViewModel.
- **Immutable Data Classes:** Always use `@Immutable` or `@Stable` on UI State data classes so the Compose Compiler can skip recomposition.
- **Lazy Lists:** Always use `key = { it.id }` in `LazyColumn` items to prevent the entire list from recomposing when one item changes.

## 5. Execution Protocol
When asked to build a UI feature:
1. First, define the underlying Theme tokens (Colors, Typography) if they are missing.
2. Second, build the "Shadcn-style" decoupled micro-components in `ui/components/`.
3. Finally, assemble the components in the main screen file.
