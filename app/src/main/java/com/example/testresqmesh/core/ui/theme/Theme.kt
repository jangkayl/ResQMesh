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
    primary = Color(0xFF85E8D5),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF123F35),
    onPrimaryContainer = Color(0xFFB8F9E9),
    secondary = Color(0xFFB5C7FF),
    onSecondary = Color(0xFF16244A),
    secondaryContainer = Color(0xFF202B4A),
    onSecondaryContainer = Color(0xFFDEE5FF),
    background = Color(0xFF090A0C),
    surface = Color(0xFF151619),
    surfaceVariant = Color(0xFF1D1F23),
    onBackground = Color(0xFFF5F7FA),
    onSurface = Color(0xFFF5F7FA),
    onSurfaceVariant = Color(0xFFBEC3CC),
    error = Color(0xFFFF6670),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF4A1C22),
    onErrorContainer = Color(0xFFFFDAD9),
    outline = Color(0xFF51565F),
    outlineVariant = Color(0xFF303339),
    surfaceTint = Color(0xFF85E8D5)
)

private val DaylightOperationsColorScheme = lightColorScheme(
    primary = Color(0xFF006B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F2E5),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF365F9E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF001A41),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF0F6),
    onBackground = Color(0xFF17202C),
    onSurface = Color(0xFF17202C),
    onSurfaceVariant = Color(0xFF425466),
    error = Color(0xFFB42332),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9DE),
    onErrorContainer = Color(0xFF41000C),
    outline = Color(0xFF718096),
    outlineVariant = Color(0xFFC1CAD5),
    surfaceTint = Color(0xFF006B5B)
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
    success = Color(0xFF56D994),
    onSuccess = Color(0xFF002114),
    successContainer = Color(0xFF123D2A),
    onSuccessContainer = Color(0xFFA4F8C8),
    warning = Color(0xFFFFC870),
    onWarning = Color(0xFF321300),
    warningContainer = Color(0xFF4A3210),
    onWarningContainer = Color(0xFFFFDDB0),
    sos = Color(0xFFFF515B),
    onSos = Color(0xFFFFFFFF),
    sosContainer = Color(0xFF46191F),
    onSosContainer = Color(0xFFFFDADD),
    backgroundStart = Color(0xFF090A0C),
    backgroundEnd = Color(0xFF101216),
    glowPrimary = Color(0xFF50DCC4),
    glowSecondary = Color(0xFF7E9DFF),
    glassFill = Color(0xF218191D),
    glassTint = Color(0xFF202227),
    glassBorder = Color(0xFF383B42),
    glassShadow = Color(0x99000000)
)

private val DaylightOperationsExtendedColors = ResQExtendedColors(
    success = Color(0xFF087A4B),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC9F7DD),
    onSuccessContainer = Color(0xFF00391F),
    warning = Color(0xFF9A5600),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFE5BB),
    onWarningContainer = Color(0xFF301400),
    sos = Color(0xFFB42332),
    onSos = Color(0xFFFFFFFF),
    sosContainer = Color(0xFFFFD9DE),
    onSosContainer = Color(0xFF41000C),
    backgroundStart = Color(0xFFF7F9FC),
    backgroundEnd = Color(0xFFEAF3F7),
    glowPrimary = Color(0xFF15A98F),
    glowSecondary = Color(0xFF6388D7),
    glassFill = Color(0xFDFEFFFF),
    glassTint = Color(0xFFF3F7FA),
    glassBorder = Color(0xFFC8D5E0),
    glassShadow = Color(0x1A24364B)
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
