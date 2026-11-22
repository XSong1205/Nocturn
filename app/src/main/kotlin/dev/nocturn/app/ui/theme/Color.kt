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
// Color Science & HSL Utilities for Expressive Dynamic Palettes
// -------------------------------------------------------------------------------------------------

fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    var h = 0f
    if (delta != 0f) {
        h = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f
        if (h < 0f) h += 360f
    }

    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * l - 1f))

    return floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hNorm = ((h % 360f) + 360f) % 360f
    val sClamped = s.coerceIn(0f, 1f)
    val lClamped = l.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * lClamped - 1f)) * sClamped
    val x = c * (1f - kotlin.math.abs((hNorm / 60f) % 2f - 1f))
    val m = lClamped - c / 2f

    val (rPrime, gPrime, bPrime) = when ((hNorm / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (rPrime + m).coerceIn(0f, 1f),
        green = (gPrime + m).coerceIn(0f, 1f),
        blue = (bPrime + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/**
 * Generates an authentic Material 3 Expressive (M3E) Dynamic ColorScheme from a dynamic seed color
 * (such as wallpaper extraction).
 *
 * Compared to standard Material 3 (TonalSpot):
 * - Expressive primary has boosted chroma and vibrancy.
 * - Secondary features an expressive rotated hue (+60° by default) providing lively contrast for secondary actions.
 * - Tertiary introduces M3 Expressive's signature playful, high-chroma triadic/complementary accent (+160° by default).
 * - Surfaces and containers are warmly tinted with subtle seed undertones for rich, cohesive depth.
 */
fun generateM3eDynamicColorScheme(
    seedColor: Color,
    dark: Boolean,
    variant: DynamicVariant = DynamicVariant.Expressive,
): ColorScheme {
    val (h, s, _) = colorToHsl(seedColor)

    if (variant == DynamicVariant.Monochrome) {
        return if (dark) {
            darkColorScheme(
                primary = Color.White,
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF383838),
                onPrimaryContainer = Color.White,
                inversePrimary = Color(0xFF1B1B1B),
                secondary = Color(0xFFE2E2E2),
                onSecondary = Color(0xFF1B1B1B),
                secondaryContainer = Color(0xFF2E2E2E),
                onSecondaryContainer = Color(0xFFE2E2E2),
                tertiary = Color(0xFFC6C6C6),
                onTertiary = Color(0xFF1B1B1B),
                tertiaryContainer = Color(0xFF242424),
                onTertiaryContainer = Color(0xFFC6C6C6),
                background = Color(0xFF121212),
                onBackground = Color(0xFFE2E2E2),
                surface = Color(0xFF121212),
                onSurface = Color(0xFFE2E2E2),
                surfaceVariant = Color(0xFF242424),
                onSurfaceVariant = Color(0xFFC6C6C6),
                surfaceContainerLowest = Color(0xFF0A0A0A),
                surfaceContainerLow = Color(0xFF181818),
                surfaceContainer = Color(0xFF1E1E1E),
                surfaceContainerHigh = Color(0xFF262626),
                surfaceContainerHighest = Color(0xFF303030),
                outline = Color(0xFF8E8E8E),
                outlineVariant = Color(0xFF484848),
            )
        } else {
            lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE2E2E2),
                onPrimaryContainer = Color.Black,
                inversePrimary = Color.White,
                secondary = Color(0xFF424242),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFECECEC),
                onSecondaryContainer = Color(0xFF1B1B1B),
                tertiary = Color(0xFF5E5E5E),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE0E0E0),
                onTertiaryContainer = Color(0xFF1B1B1B),
                background = Color.White,
                onBackground = Color(0xFF1B1B1B),
                surface = Color.White,
                onSurface = Color(0xFF1B1B1B),
                surfaceVariant = Color(0xFFE7E7E7),
                onSurfaceVariant = Color(0xFF484848),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF7F7F7),
                surfaceContainer = Color(0xFFF1F1F1),
                surfaceContainerHigh = Color(0xFFEAEAEA),
                surfaceContainerHighest = Color(0xFFE2E2E2),
                outline = Color(0xFF757575),
                outlineVariant = Color(0xFFC6C6C6),
            )
        }
    }

    val (secondaryShift, tertiaryShift, chromaScale, surfaceSat) = when (variant) {
        DynamicVariant.Expressive -> listOf(60f, 160f, 1.15f, 0.08f)
        DynamicVariant.Vibrant -> listOf(25f, 120f, 1.40f, 0.12f)
        DynamicVariant.FruitSalad -> listOf(-55f, 145f, 1.25f, 0.09f)
        DynamicVariant.Content -> listOf(12f, 28f, 0.95f, 0.06f)
        DynamicVariant.TonalSpot -> listOf(0f, 60f, 0.70f, 0.04f)
        DynamicVariant.Monochrome -> listOf(0f, 0f, 0.0f, 0.0f)
    }

    val primarySat = (maxOf(s, 0.60f) * chromaScale).coerceIn(0f, 1f)
    val secondarySat = (maxOf(s * 0.55f, 0.35f) * chromaScale).coerceIn(0f, 1f)
    val tertiarySat = (maxOf(s, 0.65f) * chromaScale).coerceIn(0f, 1f)

    val hSecondary = (h + secondaryShift + 360f) % 360f
    val hTertiary = (h + tertiaryShift + 360f) % 360f

    return if (dark) {
        darkColorScheme(
            primary = hslToColor(h, primarySat, 0.80f),
            onPrimary = hslToColor(h, primarySat, 0.16f),
            primaryContainer = hslToColor(h, primarySat, 0.28f),
            onPrimaryContainer = hslToColor(h, (primarySat * 0.75f).coerceIn(0f, 1f), 0.90f),
            inversePrimary = hslToColor(h, primarySat, 0.40f),
            secondary = hslToColor(hSecondary, secondarySat, 0.78f),
            onSecondary = hslToColor(hSecondary, secondarySat, 0.18f),
            secondaryContainer = hslToColor(hSecondary, secondarySat, 0.30f),
            onSecondaryContainer = hslToColor(hSecondary, secondarySat, 0.90f),
            tertiary = hslToColor(hTertiary, tertiarySat, 0.80f),
            onTertiary = hslToColor(hTertiary, tertiarySat, 0.16f),
            tertiaryContainer = hslToColor(hTertiary, tertiarySat, 0.30f),
            onTertiaryContainer = hslToColor(hTertiary, tertiarySat, 0.90f),
            background = hslToColor(h, surfaceSat, 0.06f),
            onBackground = hslToColor(h, surfaceSat, 0.92f),
            surface = hslToColor(h, surfaceSat, 0.06f),
            onSurface = hslToColor(h, surfaceSat, 0.92f),
            surfaceVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.25f),
            onSurfaceVariant = hslToColor(h, surfaceSat, 0.78f),
            surfaceContainerLowest = hslToColor(h, surfaceSat, 0.04f),
            surfaceContainerLow = hslToColor(h, surfaceSat, 0.08f),
            surfaceContainer = hslToColor(h, surfaceSat, 0.12f),
            surfaceContainerHigh = hslToColor(h, surfaceSat, 0.16f),
            surfaceContainerHighest = hslToColor(h, surfaceSat, 0.20f),
            outline = hslToColor(h, surfaceSat, 0.55f),
            outlineVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.30f),
        )
    } else {
        lightColorScheme(
            primary = hslToColor(h, primarySat, 0.40f),
            onPrimary = Color.White,
            primaryContainer = hslToColor(h, (primarySat * 0.70f).coerceIn(0f, 1f), 0.88f),
            onPrimaryContainer = hslToColor(h, primarySat, 0.12f),
            inversePrimary = hslToColor(h, primarySat, 0.80f),
            secondary = hslToColor(hSecondary, secondarySat, 0.44f),
            onSecondary = Color.White,
            secondaryContainer = hslToColor(hSecondary, secondarySat, 0.88f),
            onSecondaryContainer = hslToColor(hSecondary, secondarySat, 0.14f),
            tertiary = hslToColor(hTertiary, tertiarySat, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = hslToColor(hTertiary, (tertiarySat * 0.75f).coerceIn(0f, 1f), 0.86f),
            onTertiaryContainer = hslToColor(hTertiary, tertiarySat, 0.12f),
            background = hslToColor(h, surfaceSat, 0.98f),
            onBackground = hslToColor(h, surfaceSat, 0.12f),
            surface = hslToColor(h, surfaceSat, 0.98f),
            onSurface = hslToColor(h, surfaceSat, 0.12f),
            surfaceVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.90f),
            onSurfaceVariant = hslToColor(h, surfaceSat, 0.30f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hslToColor(h, surfaceSat, 0.96f),
            surfaceContainer = hslToColor(h, surfaceSat, 0.93f),
            surfaceContainerHigh = hslToColor(h, surfaceSat, 0.90f),
            surfaceContainerHighest = hslToColor(h, surfaceSat, 0.86f),
            outline = hslToColor(h, surfaceSat, 0.50f),
            outlineVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.80f),
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Standard Material 3 (M3) Dynamic ColorScheme Generator
// -------------------------------------------------------------------------------------------------

/**
 * Generates a standard Material 3 Dynamic ColorScheme from a seed color.
 * Standard Material 3 maintains lower, calmer secondary chroma and tighter hue relationships.
 */
fun generateM3DynamicColorScheme(
    seedColor: Color,
    dark: Boolean,
    variant: DynamicVariant = DynamicVariant.TonalSpot,
): ColorScheme {
    val (h, s, _) = colorToHsl(seedColor)

    if (variant == DynamicVariant.Monochrome) {
        return generateM3eDynamicColorScheme(seedColor, dark, DynamicVariant.Monochrome)
    }

    val (secondaryShift, tertiaryShift, chromaScale, surfaceSat) = when (variant) {
        DynamicVariant.TonalSpot -> listOf(0f, 60f, 0.70f, 0.04f)
        DynamicVariant.Content -> listOf(8f, 20f, 0.85f, 0.05f)
        DynamicVariant.Expressive -> listOf(45f, 120f, 1.0f, 0.06f)
        DynamicVariant.Vibrant -> listOf(20f, 90f, 1.20f, 0.08f)
        DynamicVariant.FruitSalad -> listOf(-45f, 120f, 1.05f, 0.06f)
        DynamicVariant.Monochrome -> listOf(0f, 0f, 0.0f, 0.0f)
    }

    val primarySat = (maxOf(s, 0.50f) * chromaScale).coerceIn(0f, 1f)
    val secondarySat = (s * 0.35f * chromaScale).coerceIn(0f, 0.70f)
    val tertiarySat = (maxOf(s * 0.50f, 0.40f) * chromaScale).coerceIn(0f, 0.85f)

    val hSecondary = (h + secondaryShift + 360f) % 360f
    val hTertiary = (h + tertiaryShift + 360f) % 360f

    return if (dark) {
        darkColorScheme(
            primary = hslToColor(h, primarySat, 0.80f),
            onPrimary = hslToColor(h, primarySat, 0.16f),
            primaryContainer = hslToColor(h, primarySat, 0.28f),
            onPrimaryContainer = hslToColor(h, (primarySat * 0.75f).coerceIn(0f, 1f), 0.90f),
            inversePrimary = hslToColor(h, primarySat, 0.40f),
            secondary = hslToColor(hSecondary, secondarySat, 0.78f),
            onSecondary = hslToColor(hSecondary, secondarySat, 0.18f),
            secondaryContainer = hslToColor(hSecondary, secondarySat, 0.30f),
            onSecondaryContainer = hslToColor(hSecondary, secondarySat, 0.90f),
            tertiary = hslToColor(hTertiary, tertiarySat, 0.80f),
            onTertiary = hslToColor(hTertiary, tertiarySat, 0.16f),
            tertiaryContainer = hslToColor(hTertiary, tertiarySat, 0.30f),
            onTertiaryContainer = hslToColor(hTertiary, tertiarySat, 0.90f),
            background = hslToColor(h, surfaceSat, 0.06f),
            onBackground = hslToColor(h, surfaceSat, 0.92f),
            surface = hslToColor(h, surfaceSat, 0.06f),
            onSurface = hslToColor(h, surfaceSat, 0.92f),
            surfaceVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.25f),
            onSurfaceVariant = hslToColor(h, surfaceSat, 0.78f),
            surfaceContainerLowest = hslToColor(h, surfaceSat, 0.04f),
            surfaceContainerLow = hslToColor(h, surfaceSat, 0.08f),
            surfaceContainer = hslToColor(h, surfaceSat, 0.12f),
            surfaceContainerHigh = hslToColor(h, surfaceSat, 0.16f),
            surfaceContainerHighest = hslToColor(h, surfaceSat, 0.20f),
            outline = hslToColor(h, surfaceSat, 0.55f),
            outlineVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.30f),
        )
    } else {
        lightColorScheme(
            primary = hslToColor(h, primarySat, 0.40f),
            onPrimary = Color.White,
            primaryContainer = hslToColor(h, (primarySat * 0.70f).coerceIn(0f, 1f), 0.88f),
            onPrimaryContainer = hslToColor(h, primarySat, 0.12f),
            inversePrimary = hslToColor(h, primarySat, 0.80f),
            secondary = hslToColor(hSecondary, secondarySat, 0.44f),
            onSecondary = Color.White,
            secondaryContainer = hslToColor(hSecondary, secondarySat, 0.88f),
            onSecondaryContainer = hslToColor(hSecondary, secondarySat, 0.14f),
            tertiary = hslToColor(hTertiary, tertiarySat, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = hslToColor(hTertiary, (tertiarySat * 0.75f).coerceIn(0f, 1f), 0.86f),
            onTertiaryContainer = hslToColor(hTertiary, tertiarySat, 0.12f),
            background = hslToColor(h, surfaceSat, 0.98f),
            onBackground = hslToColor(h, surfaceSat, 0.12f),
            surface = hslToColor(h, surfaceSat, 0.98f),
            onSurface = hslToColor(h, surfaceSat, 0.12f),
            surfaceVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.90f),
            onSurfaceVariant = hslToColor(h, surfaceSat, 0.30f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hslToColor(h, surfaceSat, 0.96f),
            surfaceContainer = hslToColor(h, surfaceSat, 0.93f),
            surfaceContainerHigh = hslToColor(h, surfaceSat, 0.90f),
            surfaceContainerHighest = hslToColor(h, surfaceSat, 0.86f),
            outline = hslToColor(h, surfaceSat, 0.50f),
            outlineVariant = hslToColor(h, (surfaceSat * 1.5f).coerceIn(0f, 1f), 0.80f),
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Seed Color Extraction for Presets
// -------------------------------------------------------------------------------------------------

fun presetSeedColor(key: ColorSchemeKey): Color = when (key) {
    ColorSchemeKey.M3eDynamic, ColorSchemeKey.M3eOfficial -> Color(0xFF4F378B)
    ColorSchemeKey.M3Dynamic, ColorSchemeKey.M3Baseline -> Color(0xFF6750A4)
    ColorSchemeKey.Amber -> Color(0xFFFFBA27)
    ColorSchemeKey.Ocean -> Color(0xFF0288D1)
    ColorSchemeKey.Mint -> Color(0xFF2E7D32)
    ColorSchemeKey.Rose -> Color(0xFFE91E63)
    ColorSchemeKey.NocturnClassic -> CoralDeep
    ColorSchemeKey.Custom -> Color(0xFF6750A4)
}

// -------------------------------------------------------------------------------------------------
// Custom Color Scheme Generator (from Seed Color)
// -------------------------------------------------------------------------------------------------

fun generateCustomColorScheme(
    seedColor: Color,
    dark: Boolean,
    variant: DynamicVariant = DynamicVariant.Expressive,
): ColorScheme {
    val scheme = generateM3eDynamicColorScheme(seedColor, dark, variant)
    return if (dark || variant == DynamicVariant.Monochrome) scheme else scheme.copy(primary = seedColor)
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
    dynamicVariant: DynamicVariant = DynamicVariant.Expressive,
): PalettePreviewColors = when (key) {
    ColorSchemeKey.M3eDynamic -> {
        val seed = if (context != null && isDynamicColorSupported()) {
            runCatching {
                (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
            }.getOrNull()
        } else null
        val scheme = generateM3eDynamicColorScheme(seed ?: Color(0xFF4F378B), dark, dynamicVariant)
        PalettePreviewColors(
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            surface = scheme.surface,
        )
    }
    ColorSchemeKey.M3Dynamic -> {
        val seed = if (context != null && isDynamicColorSupported()) {
            runCatching {
                (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
            }.getOrNull()
        } else null
        val scheme = generateM3DynamicColorScheme(seed ?: Color(0xFF6750A4), dark, dynamicVariant)
        PalettePreviewColors(
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            surface = scheme.surface,
        )
    }
    ColorSchemeKey.Custom -> {
        val seed = Color(customSeed)
        val scheme = generateCustomColorScheme(seed, dark, dynamicVariant)
        PalettePreviewColors(
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            surface = scheme.surface,
        )
    }
    else -> {
        if (dynamicVariant == DynamicVariant.Expressive) {
            when (key) {
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
                ColorSchemeKey.M3eDynamic,
                ColorSchemeKey.M3Dynamic,
                ColorSchemeKey.Custom -> PalettePreviewColors(
                    primary = Color(0xFF4F378B),
                    secondary = Color(0xFF4A4458),
                    tertiary = Color(0xFF633B48),
                    surface = Color(0xFFFEF7FF),
                )
            }
        } else {
            val scheme = generateM3eDynamicColorScheme(presetSeedColor(key), dark, dynamicVariant)
            PalettePreviewColors(
                primary = scheme.primary,
                secondary = scheme.secondary,
                tertiary = scheme.tertiary,
                surface = scheme.surface,
            )
        }
    }
}
