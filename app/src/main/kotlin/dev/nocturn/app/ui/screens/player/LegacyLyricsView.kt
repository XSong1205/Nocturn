package dev.nocturn.app.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.ui.components.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Basic renderer for Android versions missing the tracing API used by Accompanist. */
@Composable
internal fun LegacyLyricsView(
    lyrics: Lyrics,
    position: IntState,
    onLineClick: ((LyricLine) -> Unit)?,
    showTranslation: Boolean,
    showRomanization: Boolean,
) {
    val activeIndex by remember(lyrics, position) {
        derivedStateOf { lyrics.indexAt(position.intValue.toLong()) }
    }
    val listState = rememberLazyListState(activeIndex.coerceAtLeast(0))
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var browsing by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    LaunchedEffect(dragged) {
        if (dragged) {
            browsing = true
        } else if (browsing) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            delay(3_500)
            browsing = false
        }
    }
    LaunchedEffect(activeIndex, browsing, dragged) {
        if (!browsing && !dragged && activeIndex >= 0) listState.scrollToItem(activeIndex)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("player-lyrics-list"),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val active = index == activeIndex
                val onClick = onLineClick?.let { seek ->
                    { haptics.confirm(); seek(line); browsing = false }
                }
                LegacyLyricsRow(line, active, position, onClick, showTranslation, showRomanization)
            }
        }
        if (browsing) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) {
                TextButton(
                    onClick = {
                        haptics.tap()
                        scope.launch {
                            listState.stopScroll()
                            browsing = false
                        }
                    },
                    modifier = Modifier.testTag("lyrics-resume-follow"),
                ) { Text("回到当前歌词") }
            }
        }
    }
}

@Composable
private fun LegacyLyricsRow(
    line: LyricLine,
    active: Boolean,
    position: IntState,
    onClick: (() -> Unit)?,
    showTranslation: Boolean,
    showRomanization: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val words = remember(line) { line.words.takeIf { words ->
        words.isNotEmpty() && words.joinToString("") { it.text }.trim() == line.text
    }.orEmpty() }
    val highlightedCount by remember(words, active, position) {
        derivedStateOf {
            if (active) words.indexOfLast { it.startMs <= position.intValue.toLong() } + 1 else 0
        }
    }
    val mainText = remember(line.text, words, highlightedCount, colors.primary) {
        buildAnnotatedString {
            if (words.isEmpty()) append(line.text)
            else words.forEachIndexed { index, word ->
                if (index < highlightedCount) withStyle(SpanStyle(color = colors.primary)) { append(word.text) }
                else append(word.text)
            }
        }
    }
    val action = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { selected = active }.then(action).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = mainText,
            color = if (active && words.isEmpty()) colors.primary else colors.onSurfaceVariant,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
        if (showTranslation) line.translation?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
        if (showRomanization) line.romanization?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
