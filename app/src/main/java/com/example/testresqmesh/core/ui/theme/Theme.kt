package com.example.testresqmesh.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// The ultimate Tactical Glass Dark Mode Theme
private val TacticalDarkColorScheme = darkColorScheme(
    primary = TacticalCyberBlue,
    onPrimary = TacticalTextPrimary,
    
    tertiary = TacticalNeonGreen, // Used for E2E secure indicators
    onTertiary = TacticalBackground,

    background = TacticalBackground,
    onBackground = TacticalTextPrimary,
    
    surface = TacticalSurface,
    onSurface = TacticalTextPrimary,
    surfaceVariant = TacticalSurfaceRaised,
    onSurfaceVariant = TacticalTextSecondary,
    
    error = TacticalCrimsonRed,
    onError = TacticalTextPrimary,
    errorContainer = TacticalErrorMuted,
    onErrorContainer = TacticalTextPrimary,
    
    outline = TacticalBorder,
    outlineVariant = TacticalBorderLight
)

// We force Dark Mode as the primary aesthetic for this app
private val TacticalLightColorScheme = TacticalDarkColorScheme

@Composable
fun TestResQMeshTheme(
    darkTheme: Boolean = true, // Forced Dark Mode for Linear aesthetic
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled to enforce strict custom branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TacticalDarkColorScheme
        else -> TacticalLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
