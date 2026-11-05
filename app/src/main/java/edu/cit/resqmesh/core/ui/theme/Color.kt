package edu.cit.resqmesh.core.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// RESQMESH UI/UX: HIGH-VIS SAFETY DARK
// ==========================================

// --- Signature Dark Safety Orange & Modern Social Palette ---
val SafetyOrange = Color(0xFFFF5A00) // Original Signature Dark Safety Orange
val SafetyOrangeDark = Color(0xFFCC4800)
val SafetyOrangeContainerDark = Color(0xFF331405) // Deep warm burnt orange container for dark mode
val SafetyOrangeContainerLight = Color(0xFFFFECE0) // Warm peach/cream container for light mode

// ModernBlue redirected to warm brand orange (strictly NO blue)
val ModernBlue = SafetyOrange

val ModernMint = Color(0xFF10B981) // Emerald Mint for Direct Peer Link

// Orange for relayed (multi-hop) mesh peers; distinct from ModernAmber checking/connecting
val RelayOrange = Color(0xFFFF9500)

val ModernCoral = Color(0xFFEF4444) // Coral Red for Emergency SOS

val ModernAmber = Color(0xFFF59E0B) // Amber for Handshaking / Checking

// --- Pure Neutral Dark Mode (Charcoal Black, strictly ZERO blue) ---
val SlateNightCanvas = Color(0xFF101012)
val SlateNightSurface = Color(0xFF19191C)
val SlateNightElevated = Color(0xFF242429)
val SlateNightBorder = Color(0xFF303036)

// --- Backward compatibility aliases ---
val TacticalBlack = Color(0xFF101012)
val TacticalCarbon = Color(0xFF19191C)

// --- Semantic Safety Colors ---
val SignalGreen = ModernMint // Active Radio / Connected
val SignalRed = ModernCoral // SOS / Destructive Actions
val SignalAmber = ModernAmber // Warning / Checking Connection

// --- Typography Colors (Warm neutral, ZERO blue) ---
val TextPrimary = Color(0xFFF8F7F5)
val TextSecondary = Color(0xFFA09E9B)

val WarningAmber = Color(0xFFF59E0B)

