package dev.nocturn.app.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.ThemeMode

/** Whether the expressive theme currently renders its dark palette. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Whether Android 12+ (API 31) dynamic color (Material You / Monet) is available. */
fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun resolveColorScheme(
    key: ColorSchemeKey,
    dark: Boolean,
    customSeed: Long,
    dynamicVariant: DynamicVariant = DynamicVariant.Expressive,
    context: Context,
    isEditMode: Boolean = false,
): ColorScheme = when (key) {
    ColorSchemeKey.M3eDynamic -> {
        val seed = if (isDynamicColorSupported() && !isEditMode) {
            runCatching {
                (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
            }.getOrNull()
        } else null
        generateM3eDynamicColorScheme(seed ?: Color(0xFF4F378B), dark, dynamicVariant)
    }
    ColorSchemeKey.M3Dynamic -> {
        val seed = if (isDynamicColorSupported() && !isEditMode) {
            runCatching {
                (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
            }.getOrNull()
        } else null
        generateM3DynamicColorScheme(seed ?: Color(0xFF6750A4), dark, dynamicVariant)
    }
    ColorSchemeKey.Custom -> generateCustomColorScheme(Color(customSeed), dark, dynamicVariant)
    ColorSchemeKey.M3eOfficial -> {
        if (dynamicVariant == DynamicVariant.Expressive) {
            if (dark) M3eDarkColors else M3eLightColors
        } else {
            generateM3eDynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
        }
    }
    ColorSchemeKey.M3Baseline -> {
        if (dynamicVariant == DynamicVariant.TonalSpot || dynamicVariant == DynamicVariant.Expressive) {
            if (dark) M3BaselineDarkColors else M3BaselineLightColors
        } else {
            generateM3DynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
        }
    }
    ColorSchemeKey.Amber -> {
        if (dynamicVariant == DynamicVariant.Expressive) {
            if (dark) AmberDarkColors else AmberLightColors
        } else {
            generateM3eDynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
        }
    }
    ColorSchemeKey.Ocean -> {
        if (dynamicVariant == DynamicVariant.Expressive) {
            if (dark) OceanDarkColors else OceanLightColors
        } else {
            generateM3eDynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
        }
    }
    ColorSchemeKey.Mint -> {
        if (dynamicVariant == DynamicVariant.Expressive) {
            if (dark) MintDarkColors else MintLightColors
        } else {
            generateM3eDynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
        }
    }
    ColorSchemeKey.Rose -> {
        if (dynamicVariant == DynamicVariant.Expressive) {
            if (dark) RoseDarkColors else RoseLightColors
        } else {
            generateM3eDynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
        }
    }
    ColorSchemeKey.NocturnClassic -> {
        if (dynamicVariant == DynamicVariant.Expressive) {
            if (dark) NocturnDarkColors else NocturnLightColors
        } else {
            generateM3eDynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
        }
    }
}

@Composable
fun NocturnTheme(
    themeMode: ThemeMode = ThemeMode.System,
    colorSchemeKey: ColorSchemeKey = ColorSchemeKey.M3eDynamic,
    customSeedColor: Long = 0xFF6750A4L,
    dynamicVariant: DynamicVariant = DynamicVariant.Expressive,
    dynamicColor: Boolean? = null,
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
    val effectiveKey = when {
        dynamicColor != null -> if (dynamicColor) ColorSchemeKey.M3eDynamic else ColorSchemeKey.NocturnClassic
        else -> colorSchemeKey
    }

    val colors = resolveColorScheme(
        key = effectiveKey,
        dark = dark,
        customSeed = customSeedColor,
        dynamicVariant = dynamicVariant,
        context = context,
        isEditMode = view.isInEditMode,
    )

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
