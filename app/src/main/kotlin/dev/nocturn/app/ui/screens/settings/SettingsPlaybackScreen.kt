@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.data.model.selectableQualities
import dev.nocturn.app.ui.components.BarBackButton
import dev.nocturn.app.ui.components.BarTitle
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.MetaChip
import dev.nocturn.app.ui.components.SectionHeader
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.pressScale
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.components.scrolledPast
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.SettingsUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.localapi.client.AudioQuality

/**
 * Secondary settings page: Playback and audio quality preferences.
 */
@Composable
fun SettingsPlaybackScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onQualityChange: (AudioQuality) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledPast(140)
    val haptics = rememberHaptics()

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = collapsed) {
                BarBackButton(onBack = onBack, testTag = "settings-playback-back")
                BarTitle(text = "播放与音质", visible = collapsed)
            }
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-playback-screen"),
            contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomContentPadding()),
        ) {
            item(key = "hero") {
                SettingsHero(
                    title = "播放与音质",
                    subtitle = "选择首选在线流媒体音质与播放设置",
                )
            }

            item(key = "quality-header") {
                SectionHeader(
                    title = "首选音质",
                    subtitle = "当网络与账号权益允许时优先采用",
                )
                Spacer(Modifier.height(8.dp))
            }

            items(selectableQualities, key = { it.name }) { quality ->
                val selected = quality == state.quality
                QualityOptionCard(
                    quality = quality,
                    selected = selected,
                    onClick = {
                        haptics.step()
                        onQualityChange(quality)
                    },
                )
            }

            item(key = "quality-info") {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "音质权益与降级机制",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "无损与 Hi-Res 音质需要网易云音乐 VIP/黑胶账号权益支持。若当前歌曲未提供选定音质或账号权限不足，应用会自动以最高可用音质无缝播放。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOptionCard(
    quality: AudioQuality,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .pressScale(0.98f)
            .testTag("settings-playback-quality-${quality.name}"),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quality.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    qualityBadge(quality)?.let { badge ->
                        Spacer(Modifier.width(8.dp))
                        MetaChip(
                            text = badge,
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = contentColor,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = qualitySubtitle(quality),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun qualitySubtitle(quality: AudioQuality): String = when (quality) {
    AudioQuality.Standard -> "128kbps · 适合流量有限时使用"
    AudioQuality.Higher -> "192kbps · 清晰度与流量的均衡选择"
    AudioQuality.Exhigh -> "320kbps · 高码率 MP3，推荐主流日常"
    AudioQuality.Lossless -> "FLAC · 16bit/44.1kHz CD 级无损细节"
    AudioQuality.Hires -> "24bit/96kHz+ · 高解析度母带级别"
    else -> quality.displayName()
}

private fun qualityBadge(quality: AudioQuality): String? = when (quality) {
    AudioQuality.Lossless -> "VIP"
    AudioQuality.Hires -> "SVIP"
    else -> null
}

@Preview(name = "播放与音质 · 浅色", showBackground = true)
@Composable
private fun SettingsPlaybackScreenPreview() {
    NocturnTheme {
        SettingsPlaybackScreen(state = PreviewData.settingsState)
    }
}
