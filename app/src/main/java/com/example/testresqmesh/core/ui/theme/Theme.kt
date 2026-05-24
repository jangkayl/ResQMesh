package com.example.testresqmesh.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = InboxAccentBlue,
    onPrimary = InboxTextPrimary,
    secondary = InboxActiveTab,
    onSecondary = InboxTextPrimary,
    background = InboxBackground,
    surface = InboxSurface,
    onBackground = InboxTextPrimary,
    onSurface = InboxTextPrimary,
    error = ErrorRed,
    outline = InboxDivider
)

private val LightColorScheme = lightColorScheme(
    primary = InboxAccentBlue,
    onPrimary = InboxTextPrimary,
    secondary = InboxActiveTab,
    onSecondary = InboxTextPrimary,
    background = InboxBackground,
    surface = InboxSurface,
    onBackground = InboxTextPrimary,
    onSurface = InboxTextPrimary,
    error = ErrorRed,
    outline = InboxDivider
)

@Composable
fun TestResQMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For this design, we'll force the dark-ish industrial theme as it's the ResQ aesthetic
    val colorScheme = DarkColorScheme 
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
