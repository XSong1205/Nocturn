package com.nocturn.music.ui.screens.oobe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.squircleCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.nocturn.music.ui.theme.AppIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS 风格流动氛围光晕背景组件
 * 采用多阶柔光渐变并在后台微幅呼吸起伏，呈现具有沉浸质感的开机向导基调
 */
@Composable
fun OobeAmbientGlow(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val infiniteTransition = rememberInfiniteTransition(label = "oobe-glow")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow-pulse"
    )

    val offsetShift by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow-shift"
    )

    val primaryGlow = if (isDark) Color(0xFF1E5BFF).copy(alpha = 0.22f) else Color(0xFF3B82F6).copy(alpha = 0.12f)
    val purpleGlow = if (isDark) Color(0xFF7C3AED).copy(alpha = 0.18f) else Color(0xFF8B5CF6).copy(alpha = 0.10f)
    val cyanGlow = if (isDark) Color(0xFF06B6D4).copy(alpha = 0.14f) else Color(0xFF0EA5E9).copy(alpha = 0.08f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 主光晕 (顶部中心偏蓝)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow, Color.Transparent),
                center = Offset(width * 0.5f + offsetShift, height * 0.18f),
                radius = width * 0.75f * pulseScale
            )
        )

        // 辅光晕 (右侧偏紫)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(purpleGlow, Color.Transparent),
                center = Offset(width * 0.85f, height * 0.45f - offsetShift),
                radius = width * 0.65f * pulseScale
            )
        )

        // 辅光晕 (左下偏青)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanGlow, Color.Transparent),
                center = Offset(width * 0.15f - offsetShift, height * 0.75f),
                radius = width * 0.6f * pulseScale
            )
        )
    }
}

/**
 * HyperOS 经典药丸胶囊步进指示器
 * 激活状态平滑展开为 22dp 胶囊，未激活为 7dp 圆点
 */
@Composable
fun OobeStepIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val targetWidth: Dp = if (isActive) 22.dp else 7.dp
            val animatedWidth by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "step-width-$index"
            )

            val targetColor = if (isActive) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
            }
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(280),
                label = "step-color-$index"
            )

            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(animatedWidth)
                    .clip(RoundedCornerShape(3.5.dp))
                    .background(animatedColor)
            )
        }
    }
}

/**
 * HyperOS 连续平滑超椭圆选择卡片
 * 支持高亮选中边框、徽章角标与微触感按压弹性缩放
 */
@Composable
fun OobeSelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badgeText: String? = null,
    extraContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(120),
        label = "card-scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
        animationSpec = tween(250),
        label = "card-border"
    )

    val surfaceBg = if (selected) {
        MiuixTheme.colorScheme.surface
    } else {
        MiuixTheme.colorScheme.surface.copy(alpha = 0.75f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .squircleCard(18.dp)
            .background(surfaceBg)
            .border(
                width = if (selected) 1.8.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!badgeText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 10.sp,
                                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 选择标记指示器
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary else Color.Transparent
                        )
                        .border(
                            width = if (selected) 0.dp else 1.5.dp,
                            color = if (selected) Color.Transparent else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = "已选择",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(10.dp))
                extraContent()
            }
        }
    }
}

/**
 * HyperOS 风格高保真主操作按钮
 */
@Composable
fun OobePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = tween(120),
        label = "btn-scale"
    )

    val bgColor = if (enabled) HyperBlue else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.25f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .squircleCard(26.dp)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            if (icon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
