package dev.nocturn.app.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.state.PlayerUiState

/**
 * Top-level Desktop / PC layout scaffold.
 *
 * Coordinates:
 * - Left: Expandable [DesktopSidebar]
 * - Center: Dynamic routed content area
 * - Right: Collapsible [DesktopLyricsPanel] inspector
 * - Bottom: Full-width docked [DesktopPlayerBar]
 */
@Composable
fun DesktopScaffold(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onOpenLikedSongs: () -> Unit,
    onOpenRecentSongs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    userProfile: AccountProfile?,
    playerState: PlayerUiState,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenPlayer: () -> Unit,
    lyricsPanelOpen: Boolean,
    onToggleLyricsPanel: () -> Unit,
    queuePanelOpen: Boolean,
    onToggleQueuePanel: () -> Unit,
    modifier: Modifier = Modifier,
    volume: Float = 1f,
    onVolumeChange: (Float) -> Unit = {},
    isCurrentScreenSettings: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .testTag("desktop-scaffold"),
    ) {
        // Main working area (Sidebar + Content + Optional Lyrics Panel)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            DesktopSidebar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onOpenLikedSongs = onOpenLikedSongs,
                onOpenRecentSongs = onOpenRecentSongs,
                onOpenSettings = onOpenSettings,
                onOpenLogin = onOpenLogin,
                userProfile = userProfile,
                isCurrentScreenSettings = isCurrentScreenSettings,
            )

            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp,
            )

            // Center Content Stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                content()
            }

            // Right-side Lyrics Panel (smooth animated slide in/out)
            AnimatedVisibility(
                visible = lyricsPanelOpen && playerState.song != null,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
            ) {
                Row(Modifier.fillMaxHeight()) {
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                    )
                    DesktopLyricsPanel(
                        lyrics = playerState.lyrics,
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        onSeek = onSeek,
                        onClose = onToggleLyricsPanel,
                    )
                }
            }
        }

        // Docked bottom player bar
        DesktopPlayerBar(
            state = playerState,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeek = onSeek,
            onToggleLike = onToggleLike,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onOpenPlayer = onOpenPlayer,
            onToggleLyricsPanel = onToggleLyricsPanel,
            onToggleQueuePanel = onToggleQueuePanel,
            lyricsPanelOpen = lyricsPanelOpen,
            queuePanelOpen = queuePanelOpen,
            volume = volume,
            onVolumeChange = onVolumeChange,
        )
    }
}
