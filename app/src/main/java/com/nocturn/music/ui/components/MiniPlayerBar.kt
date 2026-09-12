package com.nocturn.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.player.NocturnPlayer
import com.nocturn.music.ui.theme.AppIcons
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.squircleCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiniPlayerBar(
    onBarClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null
) {
    val currentSong by NocturnPlayer.currentSong.collectAsState()
    val isPlaying by NocturnPlayer.isPlaying.collectAsState()

    if (currentSong == null) return

    val themeMode by SettingsRepository.themeMode.collectAsState()
    val isBlurEnabled by SettingsRepository.isBlurEnabled.collectAsState()

    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val blurActive = isRuntimeShaderSupported() && isBlurEnabled && backdrop != null
    val cardShape = remember { RoundedCornerShape(18.dp) }
    val floatingHighlight = remember(isDark) {
        if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    }

    val playIconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 0.95f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "playIconScale"
    )

    val surfaceContainerColor = MiuixTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.18f)
            )
            .then(
                if (blurActive) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = cardShape,
                        blurRadius = 25f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(
                                    color = if (isDark) {
                                        surfaceContainerColor.copy(alpha = 0.55f)
                                    } else {
                                        surfaceContainerColor.copy(alpha = 0.65f)
                                    }
                                )
                            )
                        ),
                        highlight = floatingHighlight
                    )
                } else {
                    Modifier
                        .squircleCard(18.dp)
                        .background(surfaceContainerColor.copy(alpha = 0.95f))
                }
            )
            .clip(cardShape)
            .clickable(onClick = onBarClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面卡片 (HyperOS 统一超椭圆样式)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .squircleCard(12.dp)
                    .background(Color(0xFF1E1E1E))
            ) {
                AsyncImage(
                    url = currentSong?.coverUrl,
                    contentDescription = currentSong?.title,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong?.title ?: "",
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.artist ?: "",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { NocturnPlayer.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = HyperBlue,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(playIconScale)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { NocturnPlayer.playNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.SkipNext,
                    contentDescription = "下一曲",
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onQueueClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Playlist,
                    contentDescription = "播放列表",
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
