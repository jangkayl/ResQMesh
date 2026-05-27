package com.example.testresqmesh.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ModernDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Slate950,
    primaryContainer = Slate800,
    onPrimaryContainer = CyanSecondary,
    secondary = Slate700,
    onSecondary = Slate50,
    background = AppBackground,
    surface = AppSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    outline = AppBorder
)

@Composable
fun TestResQMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Force modern dark industrial theme
    val colorScheme = ModernDarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.background.toArgb()
        
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
