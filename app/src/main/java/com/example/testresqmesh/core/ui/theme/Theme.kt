package com.example.testresqmesh.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

private val DarkColorScheme = darkColorScheme(
    primary = ResQBlue300,
    onPrimary = ResQSlate950,
    primaryContainer = ResQBlue700,
    onPrimaryContainer = ResQBlue100,
    secondary = androidx.compose.ui.graphics.Color(0xFFB9C6CC),
    onSecondary = ResQSlate950,
    secondaryContainer = ResQSlate800,
    onSecondaryContainer = ResQSlate50,
    background = ResQSlate950,
    surface = ResQSlate900,
    surfaceVariant = ResQSlate800,
    onBackground = ResQSlate50,
    onSurface = ResQSlate50,
    onSurfaceVariant = ResQSlate300,
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    outline = ResQSlate700,
    outlineVariant = ResQSlate800,
    surfaceTint = ResQBlue300
)

private val LightColorScheme = lightColorScheme(
    primary = ResQBlue700,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = ResQBlue100,
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF083344),
    secondary = ResQSlate700,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = ResQSlate100,
    onSecondaryContainer = ResQSlate900,
    background = ResQSlate50,
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = ResQSlate100,
    onBackground = ResQSlate950,
    onSurface = ResQSlate950,
    onSurfaceVariant = ResQSlate700,
    error = ResQRed700,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = ResQRed100,
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF7A271A),
    outline = ResQSlate300,
    outlineVariant = ResQSlate200,
    surfaceTint = ResQBlue700
)

@Immutable
data class ResQExtendedColors(
    val success: androidx.compose.ui.graphics.Color,
    val onSuccess: androidx.compose.ui.graphics.Color,
    val successContainer: androidx.compose.ui.graphics.Color,
    val onSuccessContainer: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val onWarning: androidx.compose.ui.graphics.Color,
    val warningContainer: androidx.compose.ui.graphics.Color,
    val onWarningContainer: androidx.compose.ui.graphics.Color,
    val sos: androidx.compose.ui.graphics.Color,
    val onSos: androidx.compose.ui.graphics.Color,
    val sosContainer: androidx.compose.ui.graphics.Color,
    val onSosContainer: androidx.compose.ui.graphics.Color
)

private val LightExtendedColors = ResQExtendedColors(
    success = ResQGreen700,
    onSuccess = androidx.compose.ui.graphics.Color.White,
    successContainer = ResQGreen100,
    onSuccessContainer = androidx.compose.ui.graphics.Color(0xFF054F31),
    warning = ResQAmber700,
    onWarning = androidx.compose.ui.graphics.Color.White,
    warningContainer = ResQAmber100,
    onWarningContainer = androidx.compose.ui.graphics.Color(0xFF7A2E0E),
    sos = ResQRed700,
    onSos = androidx.compose.ui.graphics.Color.White,
    sosContainer = ResQRed100,
    onSosContainer = androidx.compose.ui.graphics.Color(0xFF7A271A)
)

private val DarkExtendedColors = ResQExtendedColors(
    success = androidx.compose.ui.graphics.Color(0xFF75E0A7),
    onSuccess = androidx.compose.ui.graphics.Color(0xFF053321),
    successContainer = androidx.compose.ui.graphics.Color(0xFF085D3A),
    onSuccessContainer = androidx.compose.ui.graphics.Color(0xFFDCFAE6),
    warning = androidx.compose.ui.graphics.Color(0xFFFEC84B),
    onWarning = androidx.compose.ui.graphics.Color(0xFF4E1D09),
    warningContainer = androidx.compose.ui.graphics.Color(0xFF7A2E0E),
    onWarningContainer = androidx.compose.ui.graphics.Color(0xFFFEF0C7),
    sos = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onSos = androidx.compose.ui.graphics.Color(0xFF690005),
    sosContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onSosContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
)

val LocalResQExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object ResQTheme {
    val colors: ResQExtendedColors
        @Composable get() = LocalResQExtendedColors.current
}

@Composable
fun TestResQMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.surface.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalResQExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ResQShapes,
            content = content
        )
    }
}
