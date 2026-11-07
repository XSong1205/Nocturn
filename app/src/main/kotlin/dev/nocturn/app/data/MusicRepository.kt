package dev.nocturn.app.data

import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AccountStats
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.HomeFeed
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.PlaylistSongsPage
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.QrChallenge
import dev.nocturn.app.data.model.QrPollResult
import dev.nocturn.app.data.model.SearchAlbumsPage
import dev.nocturn.app.data.model.SearchArtistsPage
import dev.nocturn.app.data.model.SearchPlaylistsPage
import dev.nocturn.app.data.model.SearchSongsPage
import dev.nocturn.app.data.model.Song
import dev.nocturn.localapi.client.AudioQuality
import kotlinx.coroutines.flow.StateFlow

/**
 * The single data source for the client. Implementations talk to the embedded LocalAPI
 * (API Enhanced) through [dev.nocturn.localapi.client.LocalApiClient], normalise JSON into
 * the [dev.nocturn.app.data.model] domain models and keep the account cookie on device.
 *
 * Every method suspends: callers must not invoke these on the main thread. Errors are
 * raised as [MusicRepositoryException] with a user-presentable Chinese message so the UI can
 * render a retry state without leaking upstream response bodies (they may contain account
 * data).
 */
interface MusicRepository {
    /** Anonymous-friendly discovery feed: account-aware recommended playlists and charts. */
    suspend fun homeFeed(): HomeFeed

    suspend fun playlistDetail(id: Long): PlaylistDetail

    suspend fun playlistSongs(id: Long, offset: Int = 0, limit: Int = 50): PlaylistSongsPage

    suspend fun searchSongs(keyword: String, offset: Int = 0, limit: Int = 30): SearchSongsPage

    suspend fun searchPlaylists(keyword: String, offset: Int = 0, limit: Int = 30): SearchPlaylistsPage

    suspend fun searchAlbums(keyword: String, offset: Int = 0, limit: Int = 30): SearchAlbumsPage

    suspend fun searchArtists(keyword: String, offset: Int = 0, limit: Int = 30): SearchArtistsPage

    /** Keyboard completion candidates for the search field. */
    suspend fun searchSuggestions(keyword: String): List<String>

    /** Trending search keywords for the idle search page. */
    suspend fun hotSearches(): List<String>

    /** The service's rotating default keyword, shown as a chip on the idle search page. */
    suspend fun defaultKeyword(): String?

    suspend fun albumDetail(id: Long): AlbumDetail

    suspend fun artistDetail(id: Long): ArtistDetail

    /** Parsed, time-stamped lyrics (YRC/LRC with translation when available). */
    suspend fun lyrics(songId: Long): Lyrics

    /** Batch playable URLs for a queue. Keys are song IDs; null values mean "no source". */
    suspend fun streamUrls(ids: List<Long>, quality: AudioQuality): Map<Long, String?>

    // ---------------------------------------------------------------------------------------
    // Account and session
    // ---------------------------------------------------------------------------------------

    val session: StateFlow<AccountProfile?>

    fun isLoggedIn(): Boolean = session.value != null

    suspend fun createQrChallenge(): QrChallenge

    /** Polls the QR key once. On [dev.nocturn.app.data.model.QrLoginState.Authorized] the
     *  implementation persists the returned cookie (encrypted at rest) and updates [session]. */
    suspend fun pollQrLogin(key: String): QrPollResult

    suspend fun sendLoginCode(phone: String, countryCode: String = "86")

    suspend fun loginWithCode(phone: String, captcha: String, countryCode: String = "86"): AccountProfile

    suspend fun loginWithPassword(phone: String, password: String, countryCode: String = "86"): AccountProfile

    /** Restores and validates the stored account session. Null means anonymous. */
    suspend fun restoreSession(): AccountProfile?

    suspend fun logout()

    suspend fun accountStats(): AccountStats

    suspend fun userPlaylists(): List<PlaylistSummary>

    suspend fun likedSongIds(): Set<Long>

    /** Likes or unlikes a song for the logged-in account; returns the resulting state. */
    suspend fun setLiked(songId: Long, liked: Boolean): Boolean

    suspend fun likedSongs(): List<Song>

    suspend fun dailySongs(): List<Song>

    /** `personalized/newsong`: "根据你喜爱的歌曲推荐". Anonymous-friendly; personalised when logged in. */
    suspend fun personalizedSongs(): List<Song>

    suspend fun recentSongs(): List<Song>

    suspend fun setPlaylistSubscribed(playlistId: Long, subscribed: Boolean): Boolean

    /** Releases the HTTP client. The embedded Node runtime is process-owned and keeps running. */
    fun close()
}

/** A repository failure with a short, user-presentable Chinese message. */
class MusicRepositoryException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { Offline, Service, Unavailable, Empty, Unknown }
}
