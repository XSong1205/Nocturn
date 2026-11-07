package dev.nocturn.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

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

val NocturnDarkColors = darkColorScheme(
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

val NocturnLightColors = lightColorScheme(
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
