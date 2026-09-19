package com.example.testresqmesh.core.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.edit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val NightOperationsColorScheme = darkColorScheme(
    primary = SafetyOrange,
    onPrimary = TacticalBlack,
    primaryContainer = TacticalCarbonElevated,
    onPrimaryContainer = SafetyOrange,
    secondary = TacticalCarbonElevated,
    onSecondary = TextPrimary,
    secondaryContainer = TacticalCarbonBorder,
    onSecondaryContainer = TextPrimary,
    background = TacticalBlack,
    surface = TacticalCarbon,
    surfaceVariant = TacticalCarbonElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = SignalRed,
    onError = TacticalBlack,
    errorContainer = Color(0xFF4A1C22),
    onErrorContainer = Color(0xFFFFDAD9),
    outline = TacticalCarbonBorder,
    outlineVariant = Color(0xFF303339),
    surfaceTint = SafetyOrange
)

private val DaylightOperationsColorScheme = lightColorScheme(
    primary = SafetyOrange,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E8F0), // Light Steel Grey
    onPrimaryContainer = SafetyOrange,
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF000000),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF334155),
    error = SignalRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9DE),
    onErrorContainer = Color(0xFF41000C),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    surfaceTint = SafetyOrange
)

/** A local appearance preference. It intentionally has no transport or mesh effect. */
enum class AppAppearance(val preferenceValue: String) {
    Night("night"),
    Daylight("daylight");

    companion object {
        private const val PreferencesName = "resqmesh_ui_preferences"
        private const val AppearanceKey = "appearance"

        fun load(context: Context): AppAppearance = when (
            context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .getString(AppearanceKey, Night.preferenceValue)
        ) {
            Daylight.preferenceValue -> Daylight
            else -> Night
        }

        fun save(context: Context, appearance: AppAppearance) {
            context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .edit { putString(AppearanceKey, appearance.preferenceValue) }
        }
    }
}

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

private val NightOperationsExtendedColors = ResQExtendedColors(
    success = SignalGreen,
    onSuccess = TacticalBlack,
    successContainer = Color(0xFF00331A),
    onSuccessContainer = Color(0xFF80F3B8),
    warning = SignalAmber,
    onWarning = TacticalBlack,
    warningContainer = Color(0xFF4D3300),
    onWarningContainer = Color(0xFFFFD580),
    sos = SignalRed,
    onSos = TacticalBlack,
    sosContainer = Color(0xFF4A0000),
    onSosContainer = Color(0xFFFF8A80),
    backgroundStart = TacticalBlack,
    backgroundEnd = TacticalCarbon,
    glowPrimary = SafetyOrange,
    glowSecondary = SignalRed,
    glassFill = Color(0xE6121212),
    glassTint = TacticalCarbonElevated,
    glassBorder = TacticalCarbonBorder,
    glassShadow = Color(0xCC000000)
)

private val DaylightOperationsExtendedColors = ResQExtendedColors(
    success = SignalGreen,
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC9F7DD),
    onSuccessContainer = Color(0xFF00391F),
    warning = SignalAmber,
    onWarning = Color(0xFF000000),
    warningContainer = Color(0xFFFFE5BB),
    onWarningContainer = Color(0xFF301400),
    sos = SignalRed,
    onSos = Color(0xFFFFFFFF),
    sosContainer = Color(0xFFFFD9DE),
    onSosContainer = Color(0xFF41000C),
    backgroundStart = Color(0xFFF8FAFC),
    backgroundEnd = Color(0xFFE2E8F0),
    glowPrimary = SafetyOrange,
    glowSecondary = SignalRed,
    glassFill = Color(0xFDFEFFFF),
    glassTint = Color(0xFFF1F5F9),
    glassBorder = Color(0xFFCBD5E1),
    glassShadow = Color(0x1A0F172A)
)

val LocalResQExtendedColors = staticCompositionLocalOf { NightOperationsExtendedColors }

object ResQTheme {
    val colors: ResQExtendedColors
        @Composable get() = LocalResQExtendedColors.current
}

@Composable
fun TestResQMeshTheme(
    appearance: AppAppearance = AppAppearance.Night,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // Keep the legacy preview/test argument while the persisted local preference is introduced.
    val selectedAppearance = darkTheme?.let { if (it) AppAppearance.Night else AppAppearance.Daylight }
        ?: appearance
    val isNight = selectedAppearance == AppAppearance.Night
    val colorScheme = if (isNight) NightOperationsColorScheme else DaylightOperationsColorScheme
    val extendedColors = if (isNight) NightOperationsExtendedColors else DaylightOperationsExtendedColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.surface.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
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
