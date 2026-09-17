package com.example.testresqmesh.core.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AuroraPurple600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0EAFF),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = AuroraCyan600,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    background = AuroraCanvasLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFF4F2F8),
    onBackground = Color(0xFF171426),
    onSurface = Color(0xFF171426),
    onSurfaceVariant = Color(0xFF777383),
    error = ResQRed700,
    onError = Color.White,
    errorContainer = ResQRed100,
    onErrorContainer = Color(0xFF7A271A),
    outline = Color(0xFFC9C5D3),
    outlineVariant = Color(0xFFEAE8F0),
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
    glowPrimary = Color.Transparent,
    glowSecondary = Color.Transparent,
    glassFill = Color.White,
    glassTint = Color.White,
    glassBorder = Color(0xFFE9E7EF),
    glassShadow = Color(0x160F0A25)
)

val LocalResQExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object ResQTheme {
    val colors: ResQExtendedColors
        @Composable get() = LocalResQExtendedColors.current
}

@Composable
fun TestResQMeshTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // The product intentionally uses a single light appearance. Keep the parameter temporarily
    // so existing previews and focused tests continue compiling while screens are migrated.
    @Suppress("UNUSED_VARIABLE")
    val ignoredDarkThemePreference = darkTheme
    val colorScheme = LightColorScheme
    val extendedColors = LightExtendedColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.surface.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
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
