@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.nocturn.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.data.model.selectableImmerseTypes
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
import dev.nocturn.localapi.client.ImmerseType

/**
 * Secondary settings page: Playback and audio quality preferences.
 */
@Composable
fun SettingsPlaybackScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onQualityChange: (AudioQuality) -> Unit = {},
    onImmerseTypeChange: (ImmerseType) -> Unit = {},
    onUnblockEnabledChange: (Boolean) -> Unit = {},
    onScrobbleEnabledChange: (Boolean) -> Unit = {},
    onRelayPlayStateEnabledChange: (Boolean) -> Unit = {},
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
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

                item(key = "immerse-section") {
                    AnimatedVisibility(
                        visible = state.quality == AudioQuality.Sky,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            SectionHeader(
                                title = "沉浸声环绕声类型",
                                subtitle = "仅在选择沉浸环绕声音质（Sky）时生效",
                            )
                            Spacer(Modifier.height(8.dp))
                            for (type in selectableImmerseTypes) {
                                val selected = type == state.immerseType
                                ImmerseOptionCard(
                                    type = type,
                                    selected = selected,
                                    onClick = {
                                        haptics.step()
                                        onImmerseTypeChange(type)
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "unblock-header") {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(
                        title = "第三方歌曲解锁",
                        subtitle = "解决因版权受限或歌曲缺失导致的无法播放",
                    )
                    Spacer(Modifier.height(8.dp))
                }

                item(key = "unblock-switch") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                            ) {
                                Text(
                                    text = "开启第三方音源匹配解锁",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "当歌曲无版权或官方链接失效时，尝试使用第三方音源匹配解锁播放",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.unblockEnabled,
                                onCheckedChange = {
                                    haptics.toggle(it)
                                    onUnblockEnabledChange(it)
                                },
                                modifier = Modifier.testTag("settings-playback-unblock-switch"),
                            )
                        }
                    }
                }

                item(key = "tracking-header") {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(
                        title = "数据同步与打卡",
                        subtitle = "云端播放状态与听歌排行",
                    )
                    Spacer(Modifier.height(8.dp))
                }

                item(key = "setting-scrobble") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "听歌打卡",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "播放超 30 秒或进度超 50% 时自动上报更新网易云听歌排行与记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = state.scrobbleEnabled,
                                onCheckedChange = {
                                    haptics.tap()
                                    onScrobbleEnabledChange(it)
                                },
                                modifier = Modifier.testTag("settings-playback-scrobble-switch"),
                            )
                        }
                    }
                }

                item(key = "setting-relay-play-state") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "实时状态同步",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "同步播放进度、模式与会话状态，保持多端接力与状态一致",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = state.relayPlayStateEnabled,
                                onCheckedChange = {
                                    haptics.tap()
                                    onRelayPlayStateEnabledChange(it)
                                },
                                modifier = Modifier.testTag("settings-playback-relay-switch"),
                            )
                        }
                    }
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
                                Icons.Rounded.Info,
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
                                    text = "• 无损、Hi-Res、高清臻音、臻音全景声及沉浸环绕声需要 VIP/黑胶权益；超清母带与杜比全景声需要 SVIP 黑胶权益。\n" +
                                        "• 杜比全景声需要播放设备硬件支持，系统会自动附加 PC 识别参数以保证返回正常码率。\n" +
                                        "• 沉浸环绕声（Sky）支持多种混音声道类型，可在上方选择偏好类型（默认为 c51）。\n" +
                                        "• 若当前歌曲未提供选定音质或账号权限不足，应用会自动以最高可用音质无缝播放。",
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
                    Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ImmerseOptionCard(
    type: ImmerseType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
    } else {
        null
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .pressScale(0.98f)
            .testTag("settings-playback-immerse-${type.name}"),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Rounded.SurroundSound,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = type.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "参数代码: ${type.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
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
    AudioQuality.Jyeffect -> "空间环绕 · 智能增强空间细节与听感"
    AudioQuality.Dolby -> "空间音频 · 多声道沉浸体验（需设备支持）"
    AudioQuality.Vivid -> "全景声场 · 臻音全景声声学重构"
    AudioQuality.Jymaster -> "顶级母带 · 原生超高解析母带级还原"
    AudioQuality.Sky -> "沉浸声 · 多声道环绕声效体验"
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
