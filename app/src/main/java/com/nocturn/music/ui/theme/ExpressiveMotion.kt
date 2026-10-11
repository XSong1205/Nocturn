package com.nocturn.music.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Material 3 Expressive (M3E) Motion Physics System
 *
 * M3E的核心原则是彻底抛弃僵硬的简单时钟过渡，采用基于物理真实世界的弹簧刚度(Stiffness)
 * 与阻尼比(Damping Ratio)，赋予界面元素自然的回弹、挤压与张力感。
 */
@Immutable
data class ExpressiveMotion(
    // 空间位移与大幅度过渡 (切页、全屏抽屉升起)
    val spatialSpatial: AnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow
    ),
    // 快速触摸回弹、按钮挤压反馈 (Touch & Squish)
    val fastSpatial: AnimationSpec<Float> = spring(
        dampingRatio = 0.65f, // 更富有弹性的Bounce感
        stiffness = Spring.StiffnessMedium
    ),
    // 形状变形 (Shape Morphing)
    val morph: AnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 380f
    ),
    // 极快微动效 (图标切换、高亮跳跃)
    val fastBouncy: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    ),
    // 渐变透明度缓动 (配合弹性位移使用)
    val fadeSpec: AnimationSpec<Float> = tween(
        durationMillis = 280,
        easing = FastOutSlowInEasing
    )
)

val LocalExpressiveMotion = staticCompositionLocalOf { ExpressiveMotion() }
