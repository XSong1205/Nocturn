package dev.nocturn.app.ui.state

import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.Song

data class PlaylistUiState(
    val playlistId: Long = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val detail: PlaylistDetail? = null,
    val songs: List<Song> = emptyList(),
    val hasMore: Boolean = false,
    val likedSongIds: Set<Long> = emptySet(),
    val isLoggedIn: Boolean = false,
    val subscribing: Boolean = false,
    val subscribeError: String? = null,
    val nowPlayingSongId: Long? = null,
)

data class AlbumUiState(
    val albumId: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val detail: AlbumDetail? = null,
    val likedSongIds: Set<Long> = emptySet(),
    val nowPlayingSongId: Long? = null,
)

data class ArtistUiState(
    val artistId: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val detail: ArtistDetail? = null,
    val likedSongIds: Set<Long> = emptySet(),
    val nowPlayingSongId: Long? = null,
)
