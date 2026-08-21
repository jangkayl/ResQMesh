---
name: mobile-android-design
description: Master Material Design 3, Fluid Physics, and the "Linear Aesthetic" for building stunning, adaptive Android applications in Jetpack Compose.
tags: [android, compose, material3, linear-aesthetic, hig]
---

# Android Mobile Design: The Ultimate Aesthetic

This skill dictates how to build User Interfaces in the ResQMesh project using a hybrid of Google's **Material Design 3 (Material You)**, **Apple's Human Interface Guidelines (Physics/Gestures)**, and the **Linear Aesthetic (Glassmorphism & Micro-animations)**.

## When to Use This Skill
- Designing Android app interfaces following Material Design 3 adaptive layouts.
- Implementing Android-specific gestures (swipe-to-dismiss, physics-based scrolling).
- Upgrading standard UI to the "Linear Aesthetic" (dark mode, glass blur, glowing borders).
- Building accessible Android interfaces (Dynamic Color, contrast ratios).

## 1. The Linear Aesthetic (Visuals)
Standard Material 3 can look flat. We elevate it using these rules:
- **No Flat Colors:** Instead of a flat background for cards, use extremely subtle translucent layers (Glassmorphism) over a dark canvas.
- **Glowing Borders:** Use `Modifier.border` with a 1dp gradient brush to create a subtle glow effect around premium components.
- **Deep Dark Mode:** True `#000000` backgrounds for OLED screens, with `#1A1A1A` elevated surface layers.

## 2. Apple HIG Physics (Interactions)
Android animations often feel robotic. We enforce fluid, physics-based interactions:
- **Spring Physics:** Never use `tween` for user interactions. Always use `spring(stiffness = Spring.StiffnessLow)` for bouncing and scaling.
- **Haptic Feedback:** Every button press must trigger a subtle haptic vibration.
- **Oversized Hitboxes:** Use `Modifier.minimumInteractiveComponentSize()` to ensure all clickable elements are at least 48x48dp, even if they visually appear smaller.

## 3. Quick Start Component: The Fluid Card
When asked to build a modern card component, use this template:

```kotlin
@Composable
fun FluidItemCard(
    itemText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Spring Animation State for Press Down Effect
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale) // Fluid physics
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        // Linear Aesthetic glowing border
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = itemText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
```

## 4. Navigation & Adaptive Layouts
- **Navigation Compose:** Use standard Android Navigation Compose patterns, avoiding deeply nested backstacks.
- **Adaptive:** Ensure UI scales beautifully on tablets and foldables by utilizing `WindowSizeClass` and avoiding hardcoded `dp` widths.
