@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Centres glyphs inside the line box, which keeps CJK text optically centred in chips and rows. */
private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun TextStyle.tuned(
    weight: FontWeight,
    lineHeight: TextUnit = this.lineHeight,
    letterSpacing: TextUnit = this.letterSpacing,
): TextStyle = copy(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    lineHeightStyle = CenteredLineHeight,
)

/**
 * Expressive type tuned for Chinese UI copy.
 *
 * Display and headline sizes are heavier and tighter. Body and label styles drop the Latin-oriented
 * positive tracking (CJK glyphs already carry their own side bearings) and gain a little line
 * height so multi-line Chinese copy does not feel cramped. The `*Emphasized` variants are set
 * explicitly so hero moments are always one step heavier than their base style instead of falling
 * back to the token defaults.
 */
val NocturnTypography = Typography(
    displayLarge = Base.displayLarge.tuned(FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = Base.displayMedium.tuned(FontWeight.Bold, letterSpacing = (-0.75).sp),
    displaySmall = Base.displaySmall.tuned(FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.tuned(FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.tuned(FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.tuned(FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.tuned(FontWeight.SemiBold, lineHeight = 30.sp, letterSpacing = (-0.1).sp),
    titleMedium = Base.titleMedium.tuned(FontWeight.Medium, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.tuned(FontWeight.Medium, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodyLarge = Base.bodyLarge.tuned(FontWeight.Normal, lineHeight = 26.sp, letterSpacing = 0.1.sp),
    bodyMedium = Base.bodyMedium.tuned(FontWeight.Normal, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodySmall = Base.bodySmall.tuned(FontWeight.Normal, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelLarge = Base.labelLarge.tuned(FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = Base.labelMedium.tuned(FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = Base.labelSmall.tuned(FontWeight.Medium, letterSpacing = 0.2.sp),
    displayLargeEmphasized = Base.displayLarge.tuned(FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
    displayMediumEmphasized = Base.displayMedium.tuned(FontWeight.ExtraBold, letterSpacing = (-0.9).sp),
    displaySmallEmphasized = Base.displaySmall.tuned(FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineLargeEmphasized = Base.headlineLarge.tuned(FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineMediumEmphasized = Base.headlineMedium.tuned(FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmallEmphasized = Base.headlineSmall.tuned(FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLargeEmphasized = Base.titleLarge.tuned(FontWeight.Bold, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    titleMediumEmphasized = Base.titleMedium.tuned(FontWeight.Bold, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmallEmphasized = Base.titleSmall.tuned(FontWeight.Bold, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodyLargeEmphasized = Base.bodyLarge.tuned(FontWeight.Medium, lineHeight = 26.sp, letterSpacing = 0.1.sp),
    bodyMediumEmphasized = Base.bodyMedium.tuned(FontWeight.Medium, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodySmallEmphasized = Base.bodySmall.tuned(FontWeight.Medium, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelLargeEmphasized = Base.labelLarge.tuned(FontWeight.Bold, letterSpacing = 0.1.sp),
    labelMediumEmphasized = Base.labelMedium.tuned(FontWeight.Bold, letterSpacing = 0.2.sp),
    labelSmallEmphasized = Base.labelSmall.tuned(FontWeight.Bold, letterSpacing = 0.2.sp),
)

/** Playful, rounded shape scale used throughout the app. */
val NocturnShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    largeIncreased = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(36.dp),
    extraLargeIncreased = RoundedCornerShape(44.dp),
    extraExtraLarge = RoundedCornerShape(52.dp),
)

/** Common ad-hoc text styles that are not part of the type scale. */
object NocturnText {
    /** Latin badges such as "VIP"; the wide tracking only suits short uppercase words. */
    val eyebrow = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
        lineHeightStyle = CenteredLineHeight,
    )

    /** Pill labels that mostly carry Chinese copy ("编辑精选", "12 首歌曲"). */
    val chip = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = CenteredLineHeight,
    )

    val numeric = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
        lineHeightStyle = CenteredLineHeight,
    )
}
