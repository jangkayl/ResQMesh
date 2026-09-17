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
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AuroraPurple300,
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF3B1B68),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = AuroraCyan200,
    onSecondary = Color(0xFF083344),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFCFFAFE),
    background = AuroraCanvasDark,
    surface = Color(0xFF171B2F),
    surfaceVariant = Color(0xFF20263E),
    onBackground = Color(0xFFF8F7FF),
    onSurface = Color(0xFFF8F7FF),
    onSurfaceVariant = Color(0xFFC9CBE0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF777C96),
    outlineVariant = Color(0xFF343A55),
    surfaceTint = AuroraPurple300
)

private val LightColorScheme = lightColorScheme(
    primary = AuroraPurple600,
    onPrimary = Color.White,
    primaryContainer = AuroraPurple100,
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = AuroraCyan600,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    background = AuroraCanvasLight,
    surface = Color(0xFFFDFDFF),
    surfaceVariant = Color(0xFFF0EFF8),
    onBackground = Color(0xFF171426),
    onSurface = Color(0xFF171426),
    onSurfaceVariant = Color(0xFF686579),
    error = ResQRed700,
    onError = Color.White,
    errorContainer = ResQRed100,
    onErrorContainer = Color(0xFF7A271A),
    outline = Color(0xFFC9C6D8),
    outlineVariant = Color(0xFFE7E4F0),
    surfaceTint = AuroraPurple600
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
    val onSosContainer: androidx.compose.ui.graphics.Color,
    val backgroundStart: androidx.compose.ui.graphics.Color,
    val backgroundEnd: androidx.compose.ui.graphics.Color,
    val glowPrimary: androidx.compose.ui.graphics.Color,
    val glowSecondary: androidx.compose.ui.graphics.Color,
    val glassFill: androidx.compose.ui.graphics.Color,
    val glassTint: androidx.compose.ui.graphics.Color,
    val glassBorder: androidx.compose.ui.graphics.Color,
    val glassShadow: androidx.compose.ui.graphics.Color
)

private val LightExtendedColors = ResQExtendedColors(
    success = ResQGreen700,
    onSuccess = Color.White,
    successContainer = ResQGreen100,
    onSuccessContainer = Color(0xFF054F31),
    warning = ResQAmber700,
    onWarning = Color.White,
    warningContainer = ResQAmber100,
    onWarningContainer = Color(0xFF7A2E0E),
    sos = ResQRed700,
    onSos = Color.White,
    sosContainer = ResQRed100,
    onSosContainer = Color(0xFF7A271A),
    backgroundStart = AuroraCanvasLight,
    backgroundEnd = AuroraCanvasLightEnd,
    glowPrimary = Color(0x337C3AED),
    glowSecondary = Color(0x2E06B6D4),
    glassFill = Color(0xB8FFFFFF),
    glassTint = Color(0x70F1ECFF),
    glassBorder = Color(0xD9FFFFFF),
    glassShadow = Color(0x260F0A25)
)

private val DarkExtendedColors = ResQExtendedColors(
    success = Color(0xFF75E0A7),
    onSuccess = Color(0xFF053321),
    successContainer = Color(0xFF085D3A),
    onSuccessContainer = Color(0xFFDCFAE6),
    warning = Color(0xFFFEC84B),
    onWarning = Color(0xFF4E1D09),
    warningContainer = Color(0xFF7A2E0E),
    onWarningContainer = Color(0xFFFEF0C7),
    sos = Color(0xFFFFB4AB),
    onSos = Color(0xFF690005),
    sosContainer = Color(0xFF93000A),
    onSosContainer = Color(0xFFFFDAD6),
    backgroundStart = AuroraCanvasDark,
    backgroundEnd = AuroraCanvasDarkEnd,
    glowPrimary = Color(0x407C3AED),
    glowSecondary = Color(0x3506B6D4),
    glassFill = Color(0x9E20263E),
    glassTint = Color(0x522E2353),
    glassBorder = Color(0x38FFFFFF),
    glassShadow = Color(0x73000000)
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
