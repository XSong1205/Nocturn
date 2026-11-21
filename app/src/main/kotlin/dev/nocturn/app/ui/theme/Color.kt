@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant

/**
 * "Nocturn" brand palette — warm coral light, neon lime and sky cyan accents over aubergine
 * night surfaces. Tuned for Material 3 Expressive: saturated containers and high-contrast
 * "hero" surfaces that let motion and shape carry the personality.
 */

private val Coral = Color(0xFFFFB4A8)
private val CoralDeep = Color(0xFFB02D1C)
private val CoralContainerDark = Color(0xFF8D2B1B)
private val CoralOnContainerDark = Color(0xFFFFDAD2)
private val LimeDark = Color(0xFFCDF56E)
private val LimeContainerDark = Color(0xFF354B00)
private val CyanDark = Color(0xFF7BD8FF)
private val CyanContainerDark = Color(0xFF005065)
private val Night = Color(0xFF100D14)
private val NightLow = Color(0xFF18141C)
private val NightContainer = Color(0xFF1C1720)

val NocturnDarkColors: ColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF3B0A02),
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = CoralOnContainerDark,
    inversePrimary = CoralDeep,
    secondary = LimeDark,
    onSecondary = Color(0xFF233500),
    secondaryContainer = LimeContainerDark,
    onSecondaryContainer = Color(0xFFD9FF8C),
    tertiary = CyanDark,
    onTertiary = Color(0xFF003543),
    tertiaryContainer = CyanContainerDark,
    onTertiaryContainer = Color(0xFFBEE9FF),
    background = Night,
    onBackground = Color(0xFFECE0E8),
    surface = Night,
    onSurface = Color(0xFFECE0E8),
    surfaceVariant = Color(0xFF50424A),
    onSurfaceVariant = Color(0xFFD5C2CA),
    surfaceTint = Coral,
    surfaceDim = Night,
    surfaceBright = Color(0xFF372F39),
    surfaceContainerLowest = Color(0xFF0B080E),
    surfaceContainerLow = NightLow,
    surfaceContainer = NightContainer,
    surfaceContainerHigh = Color(0xFF271F2A),
    surfaceContainerHighest = Color(0xFF322A35),
    outline = Color(0xFF9C8A93),
    outlineVariant = Color(0xFF50424A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFECE0E8),
    inverseOnSurface = Color(0xFF322A35),
    scrim = Color.Black,
)

val NocturnLightColors: ColorScheme = lightColorScheme(
    primary = CoralDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3B0A02),
    inversePrimary = Coral,
    secondary = Color(0xFF4C6B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9FF8C),
    onSecondaryContainer = Color(0xFF162000),
    tertiary = Color(0xFF006788),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBEE9FF),
    onTertiaryContainer = Color(0xFF001F2B),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF231A18),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF231A18),
    surfaceVariant = Color(0xFFF5DED8),
    onSurfaceVariant = Color(0xFF55433F),
    surfaceTint = CoralDeep,
    surfaceDim = Color(0xFFE8D6D0),
    surfaceBright = Color(0xFFFFF8F4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFBEAE4),
    surfaceContainerHigh = Color(0xFFF5E3DC),
    surfaceContainerHighest = Color(0xFFEFD9D2),
    outline = Color(0xFF88736D),
    outlineVariant = Color(0xFFDCC1BB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF3A302D),
    inverseOnSurface = Color(0xFFFFEDE8),
    scrim = Color.Black,
)

// -------------------------------------------------------------------------------------------------
// Material 3 Expressive (M3E) Official Schemes
// -------------------------------------------------------------------------------------------------

val M3eLightColors: ColorScheme by lazy { expressiveLightColorScheme() }

val M3eDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF6750A4),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color(0xFFD0BCFF),
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F24),
    surfaceContainerHigh = Color(0xFF2C292F),
    surfaceContainerHighest = Color(0xFF37343A),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF313033),
    scrim = Color.Black,
)

// -------------------------------------------------------------------------------------------------
// Material 3 Baseline (Standard Purple) Schemes
// -------------------------------------------------------------------------------------------------

val M3BaselineLightColors: ColorScheme by lazy { lightColorScheme() }
val M3BaselineDarkColors: ColorScheme by lazy { darkColorScheme() }

// -------------------------------------------------------------------------------------------------
// Preset Schemes: Amber, Ocean, Mint, Rose
// -------------------------------------------------------------------------------------------------

val AmberLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF7E5700),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDE9F),
    onPrimaryContainer = Color(0xFF271900),
    secondary = Color(0xFF6B5D3F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E1BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF4A6546),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCEBC4),
    onTertiaryContainer = Color(0xFF072109),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEEDFC8),
    onSurfaceVariant = Color(0xFF4E4534),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1E3),
    surfaceContainer = Color(0xFFF9EBDE),
    surfaceContainerHigh = Color(0xFFF3E5D8),
    surfaceContainerHighest = Color(0xFFEDE0D2),
    outline = Color(0xFF807562),
    outlineVariant = Color(0xFFD2C4AE),
)

val AmberDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFBA27),
    onPrimary = Color(0xFF422C00),
    primaryContainer = Color(0xFF5F4100),
    onPrimaryContainer = Color(0xFFFFDE9F),
    secondary = Color(0xFFD8C5A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF53452A),
    onSecondaryContainer = Color(0xFFF5E1BB),
    tertiary = Color(0xFFB0CFA9),
    onTertiary = Color(0xFF1C361C),
    tertiaryContainer = Color(0xFF334D30),
    onTertiaryContainer = Color(0xFFCCEBC4),
    background = Color(0xFF17130B),
    onBackground = Color(0xFFEBE0D2),
    surface = Color(0xFF17130B),
    onSurface = Color(0xFFEBE0D2),
    surfaceVariant = Color(0xFF4E4534),
    onSurfaceVariant = Color(0xFFD2C4AE),
    surfaceContainerLowest = Color(0xFF120E07),
    surfaceContainerLow = Color(0xFF201B12),
    surfaceContainer = Color(0xFF241F16),
    surfaceContainerHigh = Color(0xFF2F2920),
    surfaceContainerHighest = Color(0xFF3A342A),
    outline = Color(0xFF9B8F7B),
    outlineVariant = Color(0xFF4E4534),
)

val OceanLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006495),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF50606E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E5F5),
    onSecondaryContainer = Color(0xFF0C1D29),
    tertiary = Color(0xFF65587B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEBDDFB),
    onTertiaryContainer = Color(0xFF201634),
    background = Color(0xFFF7F9FD),
    onBackground = Color(0xFF171C20),
    surface = Color(0xFFF7F9FD),
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEFF4),
    surfaceContainerHigh = Color(0xFFE5E9EE),
    surfaceContainerHighest = Color(0xFFDFE3E9),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
)

val OceanDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FCDFF),
    onPrimary = Color(0xFF003450),
    primaryContainer = Color(0xFF004B72),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFB7C9D9),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF394956),
    onSecondaryContainer = Color(0xFFD3E5F5),
    tertiary = Color(0xFFCFC0E7),
    onTertiary = Color(0xFF362B4A),
    tertiaryContainer = Color(0xFF4D4162),
    onTertiaryContainer = Color(0xFFEBDDFB),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDFE3E9),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDFE3E9),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262B2E),
    surfaceContainerHighest = Color(0xFF313539),
    outline = Color(0xFF8B9297),
    outlineVariant = Color(0xFF41484D),
)

val MintLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E6D38),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F5B2),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF516352),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E8D3),
    onSecondaryContainer = Color(0xFF0F1F12),
    tertiary = Color(0xFF39656E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBF5),
    onTertiaryContainer = Color(0xFF001F25),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF171D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6ED),
    surfaceContainer = Color(0xFFEAF0E7),
    surfaceContainerHigh = Color(0xFFE4EBE1),
    surfaceContainerHighest = Color(0xFFDEE5DC),
    outline = Color(0xFF727A70),
    outlineVariant = Color(0xFFC1C9BE),
)

val MintDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8BD898),
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF005322),
    onPrimaryContainer = Color(0xFFA6F5B2),
    secondary = Color(0xFFB8CCB7),
    onSecondary = Color(0xFF243426),
    secondaryContainer = Color(0xFF3A4B3B),
    onSecondaryContainer = Color(0xFFD4E8D3),
    tertiary = Color(0xFFA1CED8),
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF1F4D56),
    onTertiaryContainer = Color(0xFFBCEBF5),
    background = Color(0xFF0F1510),
    onBackground = Color(0xFFDEE5DC),
    surface = Color(0xFF0F1510),
    onSurface = Color(0xFFDEE5DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceContainerLowest = Color(0xFF0A100C),
    surfaceContainerLow = Color(0xFF171D18),
    surfaceContainer = Color(0xFF1B211C),
    surfaceContainerHigh = Color(0xFF252C26),
    surfaceContainerHighest = Color(0xFF303631),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
)

val RoseLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF904261),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF3B001E),
    secondary = Color(0xFF73565F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2A151C),
    tertiary = Color(0xFF7E5636),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC5),
    onTertiaryContainer = Color(0xFF301400),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF21191B),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF21191B),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFBEAEC),
    surfaceContainerHigh = Color(0xFFF5E4E6),
    surfaceContainerHighest = Color(0xFFEFDFE1),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
)

val RoseDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFAFD0),
    onPrimary = Color(0xFF581133),
    primaryContainer = Color(0xFF732949),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFFE2BDC6),
    onSecondary = Color(0xFF412931),
    secondaryContainer = Color(0xFF593F48),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFF1BC95),
    onTertiary = Color(0xFF49280D),
    tertiaryContainer = Color(0xFF633F21),
    onTertiaryContainer = Color(0xFFFFDCC5),
    background = Color(0xFF191113),
    onBackground = Color(0xFFEFDFE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    surfaceContainerLowest = Color(0xFF130C0E),
    surfaceContainerLow = Color(0xFF21191B),
    surfaceContainer = Color(0xFF261D20),
    surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3C3235),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
)

// -------------------------------------------------------------------------------------------------
// Custom Color Scheme Generator (from Seed Color)
// -------------------------------------------------------------------------------------------------

fun generateCustomColorScheme(seedColor: Color, dark: Boolean): ColorScheme {
    val r = seedColor.red
    val g = seedColor.green
    val b = seedColor.blue
    return if (dark) {
        val prim = Color(
            red = (r + 0.35f).coerceAtMost(1f),
            green = (g + 0.35f).coerceAtMost(1f),
            blue = (b + 0.35f).coerceAtMost(1f),
        )
        val primContainer = Color(
            red = (r * 0.45f).coerceIn(0f, 1f),
            green = (g * 0.45f).coerceIn(0f, 1f),
            blue = (b * 0.45f).coerceIn(0f, 1f),
        )
        darkColorScheme(
            primary = prim,
            onPrimary = Color(0xFF1F0E35),
            primaryContainer = primContainer,
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            tertiary = Color(0xFFEFB8C8),
            surface = Color(0xFF141218),
            onSurface = Color(0xFFE6E0E9),
            surfaceContainerLow = Color(0xFF1D1B20),
            surfaceContainer = Color(0xFF211F24),
            surfaceContainerHigh = Color(0xFF2C292F),
            surfaceContainerHighest = Color(0xFF37343A),
        )
    } else {
        val primContainer = Color(
            red = (r * 0.3f + 0.7f).coerceIn(0f, 1f),
            green = (g * 0.3f + 0.7f).coerceIn(0f, 1f),
            blue = (b * 0.3f + 0.7f).coerceIn(0f, 1f),
        )
        lightColorScheme(
            primary = seedColor,
            onPrimary = Color.White,
            primaryContainer = primContainer,
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            tertiary = Color(0xFF7D5260),
            surface = Color(0xFFFEF7FF),
            onSurface = Color(0xFF1D1B20),
            surfaceContainerLow = Color(0xFFF7F2FA),
            surfaceContainer = Color(0xFFF3EDF7),
            surfaceContainerHigh = Color(0xFFECE6F0),
            surfaceContainerHighest = Color(0xFFE6E0E9),
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Swatch Preview Model & Colors Resolution
// -------------------------------------------------------------------------------------------------

data class PalettePreviewColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
)

fun resolvePalettePreview(
    key: ColorSchemeKey,
    customSeed: Long,
    dark: Boolean,
    context: Context?,
): PalettePreviewColors = when (key) {
    ColorSchemeKey.M3eDynamic -> {
        if (context != null && isDynamicColorSupported()) {
            runCatching {
                val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                PalettePreviewColors(
                    primary = scheme.primary,
                    secondary = scheme.secondary,
                    tertiary = scheme.tertiary,
                    surface = scheme.surface,
                )
            }.getOrElse {
                PalettePreviewColors(
                    primary = Color(0xFF6750A4),
                    secondary = Color(0xFF625B71),
                    tertiary = Color(0xFF7D5260),
                    surface = Color(0xFFFEF7FF),
                )
            }
        } else {
            PalettePreviewColors(
                primary = Color(0xFF6750A4),
                secondary = Color(0xFF625B71),
                tertiary = Color(0xFF7D5260),
                surface = Color(0xFFFEF7FF),
            )
        }
    }
    ColorSchemeKey.M3Dynamic -> {
        if (context != null && isDynamicColorSupported()) {
            runCatching {
                val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                PalettePreviewColors(
                    primary = scheme.primary,
                    secondary = scheme.secondaryContainer,
                    tertiary = scheme.tertiary,
                    surface = scheme.surface,
                )
            }.getOrElse {
                PalettePreviewColors(
                    primary = Color(0xFF6750A4),
                    secondary = Color(0xFFE8DEF8),
                    tertiary = Color(0xFF7D5260),
                    surface = Color(0xFFFEF7FF),
                )
            }
        } else {
            PalettePreviewColors(
                primary = Color(0xFF6750A4),
                secondary = Color(0xFFE8DEF8),
                tertiary = Color(0xFF7D5260),
                surface = Color(0xFFFEF7FF),
            )
        }
    }
    ColorSchemeKey.M3eOfficial -> PalettePreviewColors(
        primary = Color(0xFF4F378B),
        secondary = Color(0xFF4A4458),
        tertiary = Color(0xFF633B48),
        surface = Color(0xFFFEF7FF),
    )
    ColorSchemeKey.M3Baseline -> PalettePreviewColors(
        primary = Color(0xFF6750A4),
        secondary = Color(0xFF625B71),
        tertiary = Color(0xFF7D5260),
        surface = Color(0xFFFEF7FF),
    )
    ColorSchemeKey.Amber -> PalettePreviewColors(
        primary = Color(0xFFFFBA27),
        secondary = Color(0xFFD8C5A0),
        tertiary = Color(0xFFB0CFA9),
        surface = Color(0xFFFFF8F2),
    )
    ColorSchemeKey.Ocean -> PalettePreviewColors(
        primary = Color(0xFF0288D1),
        secondary = Color(0xFF81D4FA),
        tertiary = Color(0xFFCFC0E7),
        surface = Color(0xFFF7F9FD),
    )
    ColorSchemeKey.Mint -> PalettePreviewColors(
        primary = Color(0xFF2E7D32),
        secondary = Color(0xFF8BD898),
        tertiary = Color(0xFFA1CED8),
        surface = Color(0xFFF6FBF3),
    )
    ColorSchemeKey.Rose -> PalettePreviewColors(
        primary = Color(0xFFE91E63),
        secondary = Color(0xFFFFAFD0),
        tertiary = Color(0xFFF1BC95),
        surface = Color(0xFFFFF8F8),
    )
    ColorSchemeKey.NocturnClassic -> PalettePreviewColors(
        primary = CoralDeep,
        secondary = Color(0xFF4C6B00),
        tertiary = Color(0xFF006788),
        surface = Color(0xFFFFF8F4),
    )
    ColorSchemeKey.Custom -> {
        val seed = Color(customSeed)
        val scheme = generateCustomColorScheme(seed, dark = false)
        PalettePreviewColors(
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            surface = scheme.surface,
        )
    }
}
