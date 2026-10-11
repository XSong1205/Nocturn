package com.nocturn.music.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive (M3E) Shape Library
 *
 * M3E 颠覆了常规 M3 的单一规整小圆角矩形，引入了高情绪价值的形状系统：
 * 1. Super-Pill (超大胶囊形态，用于浮岛工具栏、高光操作按钮)
 * 2. Asymmetric Tension (非对称张力角，如右上/左下大圆角对角拉伸，用于卡片与封面)
 * 3. Fluid Blob / Squircle (有机流体形态)
 */
object ExpressiveShapes {
    // 胶囊浮岛形态
    val FullPill = RoundedCornerShape(percent = 50)
    val SuperPill = RoundedCornerShape(32.dp)
    val IslandBar = RoundedCornerShape(26.dp)

    // 对比非对称张力卡片形状 (Asymmetric Tension Shapes)
    val AsymmetricCardLarge = RoundedCornerShape(
        topStart = CornerSize(32.dp),
        topEnd = CornerSize(12.dp),
        bottomEnd = CornerSize(32.dp),
        bottomStart = CornerSize(12.dp)
    )

    val AsymmetricCardSmall = RoundedCornerShape(
        topStart = CornerSize(20.dp),
        topEnd = CornerSize(8.dp),
        bottomEnd = CornerSize(20.dp),
        bottomStart = CornerSize(8.dp)
    )

    // 标准 M3E 大圆角容器
    val ContainerLarge = RoundedCornerShape(28.dp)
    val ContainerMedium = RoundedCornerShape(20.dp)
    val ContainerSmall = RoundedCornerShape(14.dp)
    val ContainerExtraSmall = RoundedCornerShape(8.dp)
}
