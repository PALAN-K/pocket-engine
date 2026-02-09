package kr.co.palank.pocketmonitor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    onPrimaryContainer = Color.White,
    secondary = BlueLight,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = StatusRed,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = BlueDark,
    onPrimaryContainer = Color.White,
    secondary = BlueAccent,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = StatusRed,
    onError = Color.White,
)

@Immutable
data class PocketMonitorColors(
    val statusGreen: Color,
    val statusAmber: Color,
    val statusRed: Color,
    val statusGreenBg: Color,
    val statusAmberBg: Color,
    val statusRedBg: Color,
    val cardBackground: Color,
    val textSecondary: Color,
)

private val LightPocketMonitorColors = PocketMonitorColors(
    statusGreen = StatusGreen,
    statusAmber = StatusAmber,
    statusRed = StatusRed,
    statusGreenBg = StatusGreenBg,
    statusAmberBg = StatusAmberBg,
    statusRedBg = StatusRedBg,
    cardBackground = LightCard,
    textSecondary = LightTextSecondary,
)

private val DarkPocketMonitorColors = PocketMonitorColors(
    statusGreen = StatusGreen,
    statusAmber = StatusAmber,
    statusRed = StatusRed,
    statusGreenBg = StatusGreenBgDark,
    statusAmberBg = StatusAmberBgDark,
    statusRedBg = StatusRedBgDark,
    cardBackground = DarkCard,
    textSecondary = DarkTextSecondary,
)

val LocalPocketMonitorColors = staticCompositionLocalOf {
    LightPocketMonitorColors
}

@Composable
fun PocketMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val pocketMonitorColors = if (darkTheme) DarkPocketMonitorColors else LightPocketMonitorColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPocketMonitorColors provides pocketMonitorColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

object PocketMonitorExtendedTheme {
    val colors: PocketMonitorColors
        @Composable
        get() = LocalPocketMonitorColors.current
}
