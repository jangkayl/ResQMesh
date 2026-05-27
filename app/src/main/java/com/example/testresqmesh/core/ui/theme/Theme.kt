package com.example.testresqmesh.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HumanitarianLightColorScheme = lightColorScheme(
    primary = PrimaryRed,
    onPrimary = WarmWhite,
    primaryContainer = SoftCoral.copy(alpha = 0.2f),
    onPrimaryContainer = PrimaryRed,
    secondary = SoftCoral,
    onSecondary = WarmWhite,
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
    // Redesign forces a friendly light theme for accessibility and comfort
    val colorScheme = HumanitarianLightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        // Clean white status bar with dark icons for a modern Apple-like look
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.background.toArgb()
        
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = true // Dark icons
        controller.isAppearanceLightNavigationBars = true // Dark icons
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
