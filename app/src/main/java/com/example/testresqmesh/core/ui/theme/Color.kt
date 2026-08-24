package com.example.testresqmesh.core.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================
// TACTICAL GLASS AESTHETIC PRIMITIVES
// =========================================

// Backgrounds
val TacticalBackground = Color(0xFF000000) // Pure OLED Black
val TacticalSurface = Color(0xFF0F0F13)    // Extremely dark tactical gray/blue
val TacticalSurfaceRaised = Color(0xFF16161B) // For elevated cards
val TacticalSurfaceOverlay = Color(0xFF202026) // Modals, bottom sheets

// Text
val TacticalTextPrimary = Color(0xFFFFFFFF) // Pure White
val TacticalTextSecondary = Color(0xFFA1A1AA) // Zinc 400
val TacticalTextMuted = Color(0xFF52525B) // Zinc 600

// Borders (Glowing/Glass)
val TacticalBorder = Color(0xFF27272A) // Zinc 800
val TacticalBorderLight = Color(0xFF3F3F46) // Zinc 700

// Accents (Neon Tactical)
val TacticalCyberBlue = Color(0xFF00E5FF) // Main network activity
val TacticalNeonGreen = Color(0xFF00FF41) // E2E Security / Direct Peer
val TacticalWarning = Color(0xFFFFB300)   // Warning
val TacticalCrimsonRed = Color(0xFFFF2A2A) // SOS / Critical
val TacticalErrorMuted = Color(0xFF4A0E0E) // For background of error states

// Status Colors
val SuccessGreen = TacticalNeonGreen
val ErrorRed = TacticalCrimsonRed
val WarningAmber = TacticalWarning
