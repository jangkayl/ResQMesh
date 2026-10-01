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
    onPrimary = Color.White,
    primaryContainer = SafetyOrangeContainerDark,
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = SlateNightElevated,
    onSecondary = TextPrimary,
    secondaryContainer = SlateNightBorder,
    onSecondaryContainer = TextPrimary,
    background = SlateNightCanvas,
    surface = SlateNightSurface,
    surfaceVariant = SlateNightElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = SignalRed,
    onError = Color.White,
    errorContainer = Color(0xFF4A1C22),
    onErrorContainer = Color(0xFFFFDAD9),
    outline = SlateNightBorder,
    outlineVariant = Color(0xFF2C2C32),
    surfaceTint = SafetyOrange
)

private val DaylightOperationsColorScheme = lightColorScheme(
    primary = SafetyOrange,
    onPrimary = Color.White,
    primaryContainer = SafetyOrangeContainerLight,
    onPrimaryContainer = SafetyOrangeDark,
    secondary = Color(0xFFEFECE7),
    onSecondary = Color(0xFF181817),
    secondaryContainer = Color(0xFFF5F2ED),
    onSecondaryContainer = Color(0xFF181817),
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EFEA),
    onBackground = Color(0xFF181817),
    onSurface = Color(0xFF181817),
    onSurfaceVariant = Color(0xFF6B6864),
    error = SignalRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFD9DE),
    onErrorContainer = Color(0xFF41000C),
    outline = Color(0xFFDCD5CE),
    outlineVariant = Color(0xFFEAE4DC),
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
    success = ModernMint,
    onSuccess = Color.White,
    successContainer = Color(0xFF064E3B),
    onSuccessContainer = Color(0xFFA7F3D0),
    warning = ModernAmber,
    onWarning = Color.Black,
    warningContainer = Color(0xFF78350F),
    onWarningContainer = Color(0xFFFDE68A),
    sos = ModernCoral,
    onSos = Color.White,
    sosContainer = Color(0xFF7F1D1D),
    onSosContainer = Color(0xFFFECACA),
    backgroundStart = SlateNightCanvas,
    backgroundEnd = SlateNightSurface,
    glowPrimary = SafetyOrange,
    glowSecondary = Color(0xFFFF7A29),
    glassFill = Color(0xEE18181C),
    glassTint = SlateNightElevated,
    glassBorder = Color(0x2EFFFFFF),
    glassShadow = Color(0x66000000)
)

private val DaylightOperationsExtendedColors = ResQExtendedColors(
    success = ModernMint,
    onSuccess = Color.White,
    successContainer = Color(0xFFD1FAE5),
    onSuccessContainer = Color(0xFF065F46),
    warning = ModernAmber,
    onWarning = Color.Black,
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF92400E),
    sos = ModernCoral,
    onSos = Color.White,
    sosContainer = Color(0xFFFEE2E2),
    onSosContainer = Color(0xFF991B1B),
    backgroundStart = Color(0xFFFAF8F5),
    backgroundEnd = Color(0xFFF5EFE9),
    glowPrimary = SafetyOrange,
    glowSecondary = Color(0xFFFF8B4D),
    glassFill = Color(0xF2FFFFFF),
    glassTint = Color(0xFFF3EFEA),
    glassBorder = Color(0x26000000),
    glassShadow = Color(0x18000000)
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
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
