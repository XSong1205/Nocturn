@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.LikeButton
import dev.nocturn.app.ui.components.PlayPauseButton
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.state.PlayerUiState
import dev.nocturn.app.ui.util.formatDuration

/**
 * Docked persistent player bar for desktop / PC / expanded windows.
 *
 * Provides a canonical 3-zone layout:
 * - Left: Song cover thumbnail (clickable to expand), title, artist, and favorite button.
 * - Center: Transport controls (Shuffle, Prev, Play/Pause, Next, Repeat) and fine-grained progress timeline.
 * - Right: Audio quality badge, interactive lyrics toggle, play queue toggle, and volume control slider.
 */
@Composable
fun DesktopPlayerBar(
    state: PlayerUiState,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenPlayer: () -> Unit,
    onToggleLyricsPanel: () -> Unit,
    onToggleQueuePanel: () -> Unit,
    modifier: Modifier = Modifier,
    lyricsPanelOpen: Boolean = false,
    queuePanelOpen: Boolean = false,
    volume: Float = 1f,
    onVolumeChange: (Float) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val song = state.song

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .testTag("desktop-player-bar"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp,
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // ==================== Left Zone (Song Details & Like) ====================
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (song != null) {
                        CoverArt(
                            imageUrl = song.cover,
                            contentDescription = song.name,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(54.dp)
                                .clickable(onClick = {
                                    haptics.tap()
                                    onOpenPlayer()
                                })
                                .testTag("desktop-player-cover"),
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = {
                                    haptics.tap()
                                    onOpenPlayer()
                                }),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = song.artistText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        LikeButton(
                            liked = state.liked,
                            onToggle = onToggleLike,
                            modifier = Modifier.testTag("desktop-player-like"),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Text(
                            text = "未在播放音乐",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ==================== Center Zone (Controls & Timeline) ====================
                Column(
                    modifier = Modifier
                        .weight(1.8f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Transport row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Shuffle
                        IconButton(
                            onClick = {
                                haptics.toggle(on = !state.shuffle)
                                onToggleShuffle()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("desktop-player-shuffle"),
                        ) {
                            val shuffleTint by animateColorAsState(
                                targetValue = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "shuffle-tint",
                            )
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = if (state.shuffle) "随机播放：开启" else "随机播放：关闭",
                                tint = shuffleTint,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Previous
                        IconButton(
                            onClick = {
                                haptics.step()
                                onPrevious()
                            },
                            enabled = song != null,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("desktop-player-previous"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "上一首",
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Play/Pause
                        PlayPauseButton(
                            playing = state.playing,
                            onClick = onTogglePlay,
                            size = 40.dp,
                            modifier = Modifier.testTag("desktop-player-toggle-play"),
                        )

                        // Next
                        IconButton(
                            onClick = {
                                haptics.step()
                                onNext()
                            },
                            enabled = song != null,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("desktop-player-next"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "下一首",
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Repeat
                        IconButton(
                            onClick = {
                                haptics.step()
                                onCycleRepeat()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("desktop-player-repeat"),
                        ) {
                            val repeatTint by animateColorAsState(
                                targetValue = if (state.repeatMode != RepeatMode.Off) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "repeat-tint",
                            )
                            AnimatedContent(
                                targetState = state.repeatMode == RepeatMode.One,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                                },
                                label = "repeat-icon",
                            ) { one ->
                                Icon(
                                    imageVector = if (one) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                    contentDescription = when (state.repeatMode) {
                                        RepeatMode.Off -> "循环播放：关闭"
                                        RepeatMode.All -> "循环播放：列表循环"
                                        RepeatMode.One -> "循环播放：单曲循环"
                                    },
                                    tint = repeatTint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    // Progress timeline row
                    var seekPreview by remember { mutableStateOf<Float?>(null) }
                    val currentProgress = (seekPreview ?: state.progress).coerceIn(0f, 1f)
                    val displayedPositionMs = if (seekPreview != null && state.durationMs > 0) {
                        (seekPreview!! * state.durationMs.toFloat()).toLong()
                    } else {
                        state.positionMs
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatDuration(displayedPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Slider(
                            value = currentProgress,
                            onValueChange = { seekPreview = it },
                            onValueChangeFinished = {
                                seekPreview?.let(onSeek)
                                seekPreview = null
                            },
                            enabled = song != null && state.durationMs > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp)
                                .testTag("desktop-player-seek"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )

                        Text(
                            text = formatDuration(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ==================== Right Zone (Quality, Lyrics, Queue, Volume) ====================
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    // Audio quality badge
                    if (song != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                text = state.qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // Toggle Lyrics Panel
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onToggleLyricsPanel()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("desktop-toggle-lyrics"),
                    ) {
                        val tint by animateColorAsState(
                            targetValue = if (lyricsPanelOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "lyrics-tint",
                        )
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = if (lyricsPanelOpen) "关闭歌词面板" else "打开歌词面板",
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Toggle Queue Drawer
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onToggleQueuePanel()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("desktop-toggle-queue"),
                    ) {
                        val tint by animateColorAsState(
                            targetValue = if (queuePanelOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "queue-tint",
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = if (queuePanelOpen) "关闭播放列表" else "打开播放列表",
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Volume slider with mute toggle
                    var preMuteVolume by remember { mutableFloatStateOf(1f) }
                    val isMuted = volume <= 0.001f

                    IconButton(
                        onClick = {
                            haptics.toggle(on = isMuted)
                            if (isMuted) {
                                onVolumeChange(preMuteVolume.coerceAtLeast(0.2f))
                            } else {
                                preMuteVolume = volume
                                onVolumeChange(0f)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("desktop-volume-button"),
                    ) {
                        Icon(
                            imageVector = when {
                                isMuted -> Icons.AutoMirrored.Filled.VolumeMute
                                volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isMuted) "取消静音" else "静音",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .width(84.dp)
                            .height(18.dp)
                            .testTag("desktop-volume-slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
            }
        }
    }
}
