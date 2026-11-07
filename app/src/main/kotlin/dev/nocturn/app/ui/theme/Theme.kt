package dev.nocturn.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.nocturn.app.data.model.ThemeMode

/** Whether the expressive theme currently renders its dark palette. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Whether Android 12+ (API 31) dynamic color (Material You / Monet) is available. */
fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun NocturnTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && isDynamicColorSupported() && !view.isInEditMode -> {
            runCatching {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }.getOrElse { if (dark) NocturnDarkColors else NocturnLightColors }
        }
        dark -> NocturnDarkColors
        else -> NocturnLightColors
    }
    CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = MotionScheme.expressive(),
            shapes = NocturnShapes,
            typography = NocturnTypography,
        ) {
            // Pages draw on transparent containers over the ambient backdrop, so nothing above
            // them sets a content colour; without this, untinted icons default to black in dark.
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        }
    }
}
