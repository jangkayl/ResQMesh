package com.example.testresqmesh.core.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val None = 0.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    // Mobile screens use a compact operational rhythm. Keep the larger values for
    // deliberately spacious empty/emergency states, not ordinary lists.
    val Large = 20.dp
    val ExtraLarge = 28.dp
    val Huge = 48.dp
    val Giant = 64.dp
}

object ResQSize {
    val MinimumTouchTarget = 48.dp
    val BottomBarHeight = 80.dp
    val SosAction = 64.dp
    val ContentMaxWidth = 680.dp
}

/** Shared, short interaction timing. Compose respects the platform animation-duration setting. */
object ResQMotion {
    const val PressMillis = 150
    const val ScreenMillis = 220
}
