package dev.nocturn.app.data.model

/**
 * Immutable domain models shared by the repository, the view model and every screen.
 *
 * The models deliberately do not mirror upstream JSON shapes: the data layer normalises
 * NetEase Cloud Music responses (search, cloudsearch, playlist detail, album, artist, home
 * blocks) into these types so the interface never depends on legacy response formats.
 */

data class ArtistRef(
    val id: Long,
    val name: String,
)

data class AlbumRef(
    val id: Long,
    val name: String,
    val coverUrl: String?,
)

data class Song(
    val id: Long,
    val name: String,
    val artists: List<ArtistRef>,
    val album: AlbumRef?,
    val durationMs: Long,
    val fee: Int? = null,
    val coverUrl: String? = null,
    val alias: String? = null,
) {
    val artistText: String
        get() = artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }

    val cover: String?
        get() = coverUrl ?: album?.coverUrl

    val isVipOnly: Boolean
        get() = fee == 1

    val isUnplayable: Boolean
        get() = fee == 4

    val subtitle: String
        get() = buildString {
            append(artistText)
            alias?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
}

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val creatorName: String? = null,
    val description: String? = null,
    val updateFrequency: String? = null,
)

data class PlaylistDetail(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val description: String? = null,
    val creatorName: String? = null,
    val creatorAvatarUrl: String? = null,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val subscribed: Boolean = false,
    val canSubscribe: Boolean = true,
    val tags: List<String> = emptyList(),
    val updateFrequency: String? = null,
    val createTime: Long = 0,
) {
    fun summary() = PlaylistSummary(
        id = id,
        name = name,
        coverUrl = coverUrl,
        playCount = playCount,
        trackCount = trackCount,
        creatorName = creatorName,
        description = description,
        updateFrequency = updateFrequency,
    )
}

data class PlaylistSongsPage(
    val songs: List<Song>,
    val total: Int,
    val nextOffset: Int?,
)

data class Toplist(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val updateFrequency: String? = null,
    val description: String? = null,
    val trackCount: Int = 0,
    val tracks: List<Song> = emptyList(),
)

data class AlbumSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artistText: String = "",
    val trackCount: Int = 0,
    val publishTime: Long = 0,
)

data class AlbumDetail(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artists: List<ArtistRef> = emptyList(),
    val artistText: String = "",
    val publishTime: Long = 0,
    val company: String? = null,
    val description: String? = null,
    val songs: List<Song> = emptyList(),
)

data class ArtistSummary(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val fansCount: Long = 0,
)

data class ArtistDetail(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val coverUrl: String? = null,
    val briefDescription: String? = null,
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val fansCount: Long = 0,
    val hotSongs: List<Song> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
)

data class SearchSongsPage(
    val songs: List<Song>,
    val total: Int,
    val hasMore: Boolean,
)

data class SearchPlaylistsPage(
    val playlists: List<PlaylistSummary>,
    val total: Int,
    val hasMore: Boolean,
)

data class SearchAlbumsPage(
    val albums: List<AlbumSummary>,
    val total: Int,
    val hasMore: Boolean,
)

data class SearchArtistsPage(
    val artists: List<ArtistSummary>,
    val total: Int,
    val hasMore: Boolean,
)

data class HomeFeed(
    val playlists: List<PlaylistSummary> = emptyList(),
    val charts: List<Toplist> = emptyList(),
    val dailySongs: List<Song> = emptyList(),
    val personalizedSongs: List<Song> = emptyList(),
    val isLoggedIn: Boolean = false,
)

data class LyricWord(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
)

data class LyricLine(
    val startMs: Long,
    val text: String,
    val translation: String? = null,
    val durationMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val romanization: String? = null,
)

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Index of the last line whose start time is at or before [positionMs]. */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (lines[mid].startMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = Lyrics()
    }
}

data class AccountProfile(
    val id: Long,
    val nickname: String,
    val avatarUrl: String?,
    val backgroundUrl: String? = null,
    val signature: String? = null,
    val vipLevel: Int = 0,
)

data class AccountStats(
    val playlistCount: Int = 0,
    val followingCount: Int = 0,
    val followerCount: Int = 0,
)

enum class QrLoginState { WaitingForScan, WaitingForConfirmation, Authorized, Expired, Rejected }

data class QrChallenge(
    val key: String,
    val imageBase64: String?,
)

data class QrPollResult(
    val state: QrLoginState,
    val profile: AccountProfile? = null,
)
