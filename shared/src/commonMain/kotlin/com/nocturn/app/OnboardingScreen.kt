package com.nocturn.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Music
/**
 * HyperOS 风格 OOBE 引导页。
 * 背景用两层动态径向渐变（模拟 HyperCeiler glow.glsl 的流动效果），
 * 开始按钮为圆形模糊玻璃底 + 箭头（Miuix blur 库实现）。
 */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    val backdrop = rememberLayerBackdrop()

    // 背景流动渐变
    val transition = rememberInfiniteTransition(label = "oobe-glow")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase2",
    )
    // 光斑脉动
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // logo 上下浮动
    val logoFloat = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoFloat.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }
    val logoOffsetY = (12f * (0.5f - logoFloat.value)).dp

    // 按钮弹跳入场
    val btnEnter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        btnEnter.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1A1A2E),
                    Color(0xFF232355),
                    Color(0xFF34406F),
                    Color(0xFF4A5A8A),
                    Color(0xFF6A5C8C),
                ),
                center = Offset(0.3f + 0.4f * phase1, 0.2f + 0.3f * phase2),
                radius = 1200f,
            )
        ),
    ) {
        // 辅助光斑，增强流动感（脉动缩放）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size((300 * pulse).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(0.5f * (1 - phase2), 0.5f * phase1),
                        radius = 500f,
                    ),
                    CircleShape,
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo（浮动）
            Icon(
                imageVector = MiuixIcons.Music,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .padding(16.dp)
                    .offset(y = logoOffsetY),
                tint = Color.White,
            )
            Text(
                text = "Nocturn",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "网易云音乐第三方客户端",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )

            // 圆形模糊箭头按钮（HyperOS 风格），弹跳入场
            Box(
                modifier = Modifier
                    .padding(top = 56.dp)
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = 0.6f + 0.4f * btnEnter.value
                        scaleY = 0.6f + 0.4f * btnEnter.value
                        alpha = btnEnter.value
                    }
                    .textureBlur(
                        backdrop = backdrop,
                        shape = CircleShape,
                        blurRadius = 40f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(
                                    Color.White.copy(alpha = 0.25f),
                                    BlurBlendMode.Screen,
                                ),
                            ),
                            saturation = 1.1f,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onStart,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Forward,
                        contentDescription = "开始",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
