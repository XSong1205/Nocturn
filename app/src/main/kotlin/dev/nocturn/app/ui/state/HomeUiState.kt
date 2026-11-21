package dev.nocturn.app.ui.state

import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.Toplist

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val greeting: String = "夜深了",
    val recommendedPlaylists: List<PlaylistSummary> = emptyList(),
    val charts: List<Toplist> = emptyList(),
    val dailySongs: List<Song> = emptyList(),
    val personalizedSongs: List<Song> = emptyList(),
    val radarPlaylist: PlaylistSummary? = null,
    val isLoggedIn: Boolean = false,
    val isVip: Boolean = false,
    val likedSongIds: Set<Long> = emptySet(),
    val nowPlayingSongId: Long? = null,
) {
    val showFreeListenButton: Boolean
        get() = !isVip

    val hasContent: Boolean
        get() = recommendedPlaylists.isNotEmpty() ||
            charts.isNotEmpty() || dailySongs.isNotEmpty() || personalizedSongs.isNotEmpty() ||
            radarPlaylist != null
}
