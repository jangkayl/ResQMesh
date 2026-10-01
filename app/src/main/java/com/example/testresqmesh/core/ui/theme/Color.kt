package com.example.testresqmesh.core.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// RESQMESH UI/UX: HIGH-VIS SAFETY DARK
// ==========================================

// --- Signature Dark Safety Orange & Modern Social Palette ---
val SafetyOrange = Color(0xFFFF5A00) // Original Signature Dark Safety Orange
val SafetyOrangeDark = Color(0xFFCC4800)
val SafetyOrangeLight = Color(0xFFFF7A29)
val SafetyOrangeContainerDark = Color(0xFF331405) // Deep warm burnt orange container for dark mode
val SafetyOrangeContainerLight = Color(0xFFFFECE0) // Warm peach/cream container for light mode

val BrandOrange = SafetyOrange
val BrandOrangeDark = SafetyOrangeDark
val BrandOrangeLight = SafetyOrangeLight

// ModernBlue redirected to warm brand orange (strictly NO blue)
val ModernBlue = SafetyOrange
val ModernBlueDark = SafetyOrangeDark
val ModernBlueLight = SafetyOrangeLight
val ModernBlueMuted = SafetyOrangeContainerLight

val ModernMint = Color(0xFF10B981) // Emerald Mint for Direct Peer Link
val ModernMintLight = Color(0xFF34D399)
val ModernMintContainer = Color(0xFFD1FAE5)

// Warm Amber Gold for Relayed Mesh Nodes (NO blue)
val ModernSky = Color(0xFFFF9500)
val ModernSkyLight = Color(0xFFFFB340)
val ModernSkyContainer = Color(0xFFFEF3C7)

val ModernCoral = Color(0xFFEF4444) // Coral Red for Emergency SOS
val ModernCoralLight = Color(0xFFF87171)
val ModernCoralContainer = Color(0xFFFEE2E2)

val ModernAmber = Color(0xFFF59E0B) // Amber for Handshaking / Checking
val ModernAmberLight = Color(0xFFFBBF24)
val ModernAmberContainer = Color(0xFFFEF3C7)

// --- Pure Neutral Dark Mode (Charcoal Black, strictly ZERO blue) ---
val SlateNightCanvas = Color(0xFF101012)
val SlateNightSurface = Color(0xFF19191C)
val SlateNightElevated = Color(0xFF242429)
val SlateNightBorder = Color(0xFF303036)

// --- Backward compatibility aliases ---
val TacticalBlack = Color(0xFF101012)
val TacticalCarbon = Color(0xFF19191C)
val TacticalCarbonElevated = Color(0xFF242429)
val TacticalCarbonBorder = Color(0xFF303036)

// --- Semantic Safety Colors ---
val SignalGreen = ModernMint // Active Radio / Connected
val SignalRed = ModernCoral // SOS / Destructive Actions
val SignalAmber = ModernAmber // Warning / Checking Connection

// --- Typography Colors (Warm neutral, ZERO blue) ---
val TextPrimary = Color(0xFFF8F7F5)
val TextSecondary = Color(0xFFA09E9B)
val TextTertiary = Color(0xFF706E6B)
val TextOnAccent = Color(0xFFFFFFFF)

// --- Legacy Colors (Kept temporarily to prevent compile errors during migration) ---
val AuroraPurple800 = Color(0xFF4C1D95)
val AuroraPurple700 = Color(0xFF5B21B6)
val AuroraPurple600 = Color(0xFF7C3AED)
val AuroraPurple500 = Color(0xFF8B5CF6)
val AuroraPurple300 = Color(0xFFC4B5FD)
val AuroraPurple100 = Color(0xFFEDE9FE)
val AuroraPurple50 = Color(0xFFF7F5FF)

val AuroraCyan600 = SafetyOrangeDark
val AuroraCyan500 = SafetyOrange
val AuroraCyan200 = ModernSkyContainer

val AuroraCanvasLight = Color(0xFFFAF8F5)
val AuroraCanvasLightEnd = Color(0xFFF5EFE9)
val AuroraCanvasDark = Color(0xFF101012)
val AuroraCanvasDarkEnd = Color(0xFF19191C)

val ResQBlue700 = SafetyOrangeDark
val ResQBlue600 = SafetyOrange
val ResQBlue300 = SafetyOrangeLight
val ResQBlue100 = SafetyOrangeContainerLight

val ResQRed700 = Color(0xFFB42318)
val ResQRed600 = Color(0xFFD92D20)
val ResQRed100 = Color(0xFFFEE4E2)

val ResQGreen700 = Color(0xFF067647)
val ResQGreen600 = Color(0xFF079455)
val ResQGreen100 = Color(0xFFDCFAE6)

val ResQAmber700 = Color(0xFFB54708)
val ResQAmber500 = Color(0xFFF79009)
val ResQAmber100 = Color(0xFFFEF0C7)

val ResQSlate950 = Color(0xFF101012)
val ResQSlate900 = Color(0xFF19191C)
val ResQSlate800 = Color(0xFF242429)
val ResQSlate700 = Color(0xFF303036)
val ResQSlate500 = Color(0xFF706E6B)
val ResQSlate300 = Color(0xFFA09E9B)
val ResQSlate200 = Color(0xFFE4E1DC)
val ResQSlate100 = Color(0xFFF2EFEA)
val ResQSlate50 = Color(0xFFFAF8F5)

val InboxBackground = Color(0xFF101012)
val InboxSurface = Color(0xFF19191C)
val InboxDivider = Color(0xFF303036)
val InboxTextPrimary = Color(0xFFF8F7F5)
val InboxTextSecondary = Color(0xFFA09E9B)

val InboxAccentBlue = SafetyOrange
val InboxActiveTab = Color(0xFF242429)

val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)

val Zinc50 = Color(0xFFFAFAFA)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc500 = Color(0xFF71717A)
val Zinc600 = Color(0xFF52525B)
val Zinc700 = Color(0xFF3F3F46)
val Zinc800 = Color(0xFF27272A)
val Zinc900 = Color(0xFF18181B)
val Zinc950 = Color(0xFF09090B)

val ResQBlue = TacticalBlack
val ResQBlueLight = TacticalCarbon
val ResQBlueAccent = SafetyOrange

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
