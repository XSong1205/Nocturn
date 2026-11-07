package dev.nocturn.app.ui.state

import dev.nocturn.app.data.model.AlbumSummary
import dev.nocturn.app.data.model.ArtistSummary
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.Song

enum class SearchTab(val label: String) {
    Songs("单曲"),
    Playlists("歌单"),
    Albums("专辑"),
    Artists("歌手"),
}

data class SearchUiState(
    val query: String = "",
    val expanded: Boolean = false,
    val submittedKeyword: String? = null,
    val tab: SearchTab = SearchTab.Songs,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hotKeywords: List<String> = emptyList(),
    val defaultKeyword: String? = null,
    val history: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val songs: List<Song> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val hasMore: Boolean = false,
    val likedSongIds: Set<Long> = emptySet(),
    val isLoggedIn: Boolean = false,
    val nowPlayingSongId: Long? = null,
) {
    val hasResults: Boolean
        get() = songs.isNotEmpty() || playlists.isNotEmpty() || albums.isNotEmpty() ||
            artists.isNotEmpty()

    fun resultCount(tab: SearchTab): Int = when (tab) {
        SearchTab.Songs -> songs.size
        SearchTab.Playlists -> playlists.size
        SearchTab.Albums -> albums.size
        SearchTab.Artists -> artists.size
    }
}
