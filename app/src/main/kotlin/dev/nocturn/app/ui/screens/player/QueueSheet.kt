@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.nocturn.app.ui.components.BlurBehindWindow
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.state.PlayerUiState

/**
 * Bottom-sheet view of the play queue. The current row is highlighted (and animated by
 * [dev.nocturn.app.ui.components.PlaybackBars]) and selecting any row reports its index so the
 * shell can jump the engine to it.
 */
@Composable
internal fun PlayerQueueSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    // Open with the current song in view, one row of context above it.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (state.currentIndex - 1).coerceAtLeast(0),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BlurBehindWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "播放队列",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("player-queue-sheet"),
                    )
                    Text(
                        text = "共 ${state.queue.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.currentIndex >= 0 && state.queue.isNotEmpty()) {
                    Text(
                        text = "${state.currentIndex + 1} / ${state.queue.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.queue.isEmpty()) {
                EmptyState(
                    title = "队列是空的",
                    message = "从任意歌单开始播放，队列就会出现在这里",
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(state.queue) { index, song ->
                        SongRow(
                            song = song,
                            onClick = { onSongClick(index) },
                            index = index + 1,
                            isPlaying = index == state.currentIndex && state.playing,
                            showDuration = false,
                        )
                    }
                }
            }
        }
    }
}
