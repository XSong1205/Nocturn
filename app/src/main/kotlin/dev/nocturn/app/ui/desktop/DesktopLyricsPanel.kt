package dev.nocturn.app.ui.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.ui.components.rememberHaptics

/**
 * Modern side-panel inspector for synchronized real-time lyrics on desktop / PC.
 *
 * Allows users to follow lyrics alongside browsing playlists or home feeds, featuring:
 * - Real-time active-line highlight and auto-scroll centering
 * - Click-to-seek jump on individual lines
 * - Bilingual translation and romanization toggles
 */
@Composable
fun DesktopLyricsPanel(
    lyrics: Lyrics,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var showTranslation by remember { mutableStateOf(true) }
    var showRomanization by remember { mutableStateOf(false) }

    val hasTranslation = remember(lyrics) { lyrics.lines.any { !it.translation.isNullOrBlank() } }
    val hasRomanization = remember(lyrics) { lyrics.lines.any { !it.romanization.isNullOrBlank() } }

    val activeIndex by remember(lyrics.lines, positionMs) {
        derivedStateOf {
            lyrics.lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
        }
    }

    val listState = rememberLazyListState()

    // Smoothly follow active lyric line
    LaunchedEffect(activeIndex) {
        if (lyrics.lines.isNotEmpty() && activeIndex in lyrics.lines.indices) {
            val target = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = target,
                scrollOffset = 0,
            )
        }
    }

    Surface(
        modifier = modifier
            .width(360.dp)
            .fillMaxHeight()
            .testTag("desktop-lyrics-panel"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "实时歌词",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hasTranslation) {
                        FilterChip(
                            selected = showTranslation,
                            onClick = {
                                haptics.toggle(on = !showTranslation)
                                showTranslation = !showTranslation
                            },
                            label = { Text("译", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("desktop-lyrics-translation"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }

                    if (hasRomanization) {
                        FilterChip(
                            selected = showRomanization,
                            onClick = {
                                haptics.toggle(on = !showRomanization)
                                showRomanization = !showRomanization
                            },
                            label = { Text("音", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("desktop-lyrics-romanization"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.tap()
                            onClose()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("desktop-lyrics-close"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            // Lyrics List
            if (lyrics.lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "纯音乐，请欣赏",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("desktop-lyrics-list"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { index, line -> "${line.startMs}_$index" },
                    ) { index, line ->
                        val isActive = index == activeIndex
                        val activeColor by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            label = "lyric-color",
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics.tap()
                                    if (durationMs > 0) {
                                        onSeek((line.startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = line.text,
                                style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = activeColor,
                            )

                            if (showTranslation && !line.translation.isNullOrBlank()) {
                                Text(
                                    text = line.translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = activeColor.copy(alpha = if (isActive) 0.85f else 0.4f),
                                )
                            }

                            if (showRomanization && !line.romanization.isNullOrBlank()) {
                                Text(
                                    text = line.romanization,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = activeColor.copy(alpha = if (isActive) 0.7f else 0.35f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
