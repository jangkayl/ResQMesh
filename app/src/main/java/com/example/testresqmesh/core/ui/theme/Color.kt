package com.example.testresqmesh.core.ui.theme

import androidx.compose.ui.graphics.Color

// --- ResQMesh Redesign Palette (Family Friendly) ---
val PrimaryRed = Color(0xFFFF3B30) // Apple-style Emergency Red
val SoftCoral = Color(0xFFFF7B6E)
val WarmWhite = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFF9FAFB) // Apple-style soft background
val LightGray = Color(0xFFE5E7EB) // Borders and dividers
val MediumGray = Color(0xFF8E8E93) // Secondary text
val DarkGray = Color(0xFF1C1C1E) // Primary text

// --- Background & Surface ---
val AppBackground = OffWhite
val AppSurface = WarmWhite
val AppSurfaceVariant = LightGray
val AppBorder = LightGray

// --- Text Colors ---
val TextPrimary = DarkGray
val TextSecondary = MediumGray
val TextMuted = MediumGray.copy(alpha = 0.6f)

// --- Functional ---
val SuccessGreen = Color(0xFF34C759)
val WarningAmber = Color(0xFFFF9500)
val ErrorRed = PrimaryRed

// --- Branding & Legacy Alignment ---
val CyanPrimary = PrimaryRed // Alias for build stability during migration
val RedPrimary = PrimaryRed
val WhiteFull = WarmWhite
val GrayLight = OffWhite
val GrayBorder = LightGray
val TextMutedFull = TextMuted
