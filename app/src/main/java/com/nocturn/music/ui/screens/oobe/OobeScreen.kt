package com.nocturn.music.ui.screens.oobe

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.ui.theme.squircleCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TOTAL_OOBE_STEPS = 6

/**
 * HyperOS 风格开机引导 (OOBE) 全屏交互主界面
 * 负责调度 6 步配置流程、氛围光晕、水平阻尼转场、胶囊步进指示器与返回/跳过控制
 */
@Composable
fun OobeScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        if (currentStep > 0) {
            currentStep--
        } else {
            onComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        // HyperOS 柔光氛围流动背景
        OobeAmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶部功能栏 (标题与跳过操作)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧品牌指示或步骤标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MiuixTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Music,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "NOCTURN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        letterSpacing = 1.2.sp
                    )
                }

                // 右侧随时“跳过”操作
                if (currentStep < TOTAL_OOBE_STEPS - 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable { onComplete() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "跳过",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }

            // 中间可滚动内容区与步骤转场
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)))
                                .togetherWith(slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(250)))
                                .togetherWith(slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)))
                        }
                    },
                    label = "oobe-step-transition",
                    modifier = Modifier.fillMaxSize()
                ) { step ->
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (step) {
                            0 -> OobeWelcomeStep()
                            1 -> OobeEngineStep()
                            2 -> OobeLoginStep()
                            3 -> OobePreferencesStep()
                            4 -> OobePermissionsStep()
                            5 -> OobeCompleteStep()
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // 底部 HyperOS 步进导航条
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 药丸步进指示器
                OobeStepIndicator(
                    totalSteps = TOTAL_OOBE_STEPS,
                    currentStep = currentStep
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮栏 (上一步 / 下一步)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .squircleCard(26.dp)
                                .background(MiuixTheme.colorScheme.surface)
                                .clickable {
                                    if (currentStep > 0) currentStep--
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "上一步",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "上一步",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    // 主操作按钮
                    val isLastStep = currentStep == TOTAL_OOBE_STEPS - 1
                    val buttonText = when {
                        currentStep == 0 -> "开始配置"
                        isLastStep -> "开启 Nocturn"
                        else -> "下一步"
                    }

                    OobePrimaryButton(
                        text = buttonText,
                        onClick = {
                            if (isLastStep) {
                                onComplete()
                            } else {
                                currentStep++
                            }
                        },
                        modifier = Modifier.weight(if (currentStep > 0) 2f else 1f),
                        icon = if (isLastStep) MiuixIcons.Music else null
                    )
                }
            }
        }
    }
}
