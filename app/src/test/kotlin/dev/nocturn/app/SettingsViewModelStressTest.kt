package dev.nocturn.app

import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
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
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.playback.PlayableSong
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.localapi.client.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Empirical adversarial stress testing for ViewModel state synchronization and isolation
 * with regards to [NocturnViewModel.setLyriconEnabled].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsViewModelStressTest {

    private lateinit var app: NocturnApplication

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication() as NocturnApplication
        app.getSharedPreferences("nocturn-settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun createViewModel(): NocturnViewModel {
        app.container = object : AppContainer(app) {
            override val settings = SettingsStore(app)
            override val repository: MusicRepository = MockMusicRepo()
            override val playback: PlaybackEngine = MockPlayback()
        }
        return NocturnViewModel(app)
    }

    @Test
    fun togglingLyriconPreservesAllLibraryUiStateFields() {
        val model = createViewModel()

        // Modify preferences to non-default
        model.setQuality(AudioQuality.Lossless)
        model.setThemeMode(ThemeMode.Dark)
        model.setDynamicColor(false)
        model.setHapticsEnabled(false)

        val beforeLibrary = model.library.value
        assertEquals(AudioQuality.Lossless, beforeLibrary.quality)
        assertEquals(ThemeMode.Dark, beforeLibrary.themeMode)
        assertFalse(beforeLibrary.dynamicColor)
        assertFalse(beforeLibrary.hapticsEnabled)
        assertTrue(beforeLibrary.lyriconEnabled)

        // Mutate lyricon
        model.setLyriconEnabled(false)

        val afterLibrary = model.library.value
        assertFalse(afterLibrary.lyriconEnabled)
        assertEquals(AudioQuality.Lossless, afterLibrary.quality)
        assertEquals(ThemeMode.Dark, afterLibrary.themeMode)
        assertFalse(afterLibrary.dynamicColor)
        assertFalse(afterLibrary.hapticsEnabled)

        // Only lyriconEnabled should differ
        assertEquals(beforeLibrary.copy(lyriconEnabled = false), afterLibrary)

        // Mutate back
        model.setLyriconEnabled(true)
        assertEquals(beforeLibrary, model.library.value)
    }

    @Test
    fun togglingLyriconPreservesAllSettingsUiStateFields() {
        val model = createViewModel()

        model.setQuality(AudioQuality.Standard)
        model.setThemeMode(ThemeMode.Light)
        model.setDynamicColor(false)
        model.setHapticsEnabled(false)

        val beforeSettings = model.settingsUi.value
        assertEquals(AudioQuality.Standard, beforeSettings.quality)
        assertEquals(ThemeMode.Light, beforeSettings.themeMode)
        assertFalse(beforeSettings.dynamicColor)
        assertFalse(beforeSettings.hapticsEnabled)
        assertTrue(beforeSettings.lyriconEnabled)

        model.setLyriconEnabled(false)

        val afterSettings = model.settingsUi.value
        assertFalse(afterSettings.lyriconEnabled)
        assertEquals(beforeSettings.copy(lyriconEnabled = false), afterSettings)

        model.setLyriconEnabled(true)
        assertEquals(beforeSettings, model.settingsUi.value)
    }

    @Test
    fun rapidRepeatedToggleSynchronizationStress() {
        val model = createViewModel()
        val store = SettingsStore(app)

        val cycles = 50
        for (i in 1..cycles) {
            val target = (i % 2 == 1) // true/false alternating
            model.setLyriconEnabled(target)

            assertEquals("Cycle $i: SettingsStore must match target", target, store.lyriconEnabled)
            assertEquals("Cycle $i: library.lyriconEnabled must match target", target, model.library.value.lyriconEnabled)
            assertEquals("Cycle $i: settingsUi.lyriconEnabled must match target", target, model.settingsUi.value.lyriconEnabled)
        }
    }

    private class MockMusicRepo : MusicRepository {
        override val session: StateFlow<AccountProfile?> = MutableStateFlow(null)
        override suspend fun homeFeed(): HomeFeed = HomeFeed()
        override suspend fun playlistDetail(id: Long): PlaylistDetail = error("Not needed")
        override suspend fun playlistSongs(id: Long, offset: Int, limit: Int): PlaylistSongsPage = error("Not needed")
        override suspend fun searchSongs(keyword: String, offset: Int, limit: Int): SearchSongsPage = error("Not needed")
        override suspend fun searchPlaylists(keyword: String, offset: Int, limit: Int): SearchPlaylistsPage = error("Not needed")
        override suspend fun searchAlbums(keyword: String, offset: Int, limit: Int): SearchAlbumsPage = error("Not needed")
        override suspend fun searchArtists(keyword: String, offset: Int, limit: Int): SearchArtistsPage = error("Not needed")
        override suspend fun searchSuggestions(keyword: String): List<String> = emptyList()
        override suspend fun hotSearches(): List<String> = emptyList()
        override suspend fun defaultKeyword(): String? = null
        override suspend fun albumDetail(id: Long): AlbumDetail = error("Not needed")
        override suspend fun artistDetail(id: Long): ArtistDetail = error("Not needed")
        override suspend fun lyrics(songId: Long): Lyrics = error("Not needed")
        override suspend fun streamUrls(ids: List<Long>, quality: AudioQuality): Map<Long, String?> = emptyMap()
        override suspend fun createQrChallenge(): QrChallenge = error("Not needed")
        override suspend fun pollQrLogin(key: String): QrPollResult = error("Not needed")
        override suspend fun sendLoginCode(phone: String, countryCode: String) {}
        override suspend fun loginWithCode(phone: String, captcha: String, countryCode: String): AccountProfile = error("Not needed")
        override suspend fun loginWithPassword(phone: String, password: String, countryCode: String): AccountProfile = error("Not needed")
        override suspend fun restoreSession(): AccountProfile? = null
        override suspend fun logout() {}
        override suspend fun accountStats(): AccountStats = error("Not needed")
        override suspend fun userPlaylists(): List<PlaylistSummary> = emptyList()
        override suspend fun likedSongIds(): Set<Long> = emptySet()
        override suspend fun setLiked(songId: Long, liked: Boolean): Boolean = false
        override suspend fun likedSongs(): List<Song> = emptyList()
        override suspend fun dailySongs(): List<Song> = emptyList()
        override suspend fun personalizedSongs(): List<Song> = emptyList()
        override suspend fun recentSongs(): List<Song> = emptyList()
        override suspend fun setPlaylistSubscribed(playlistId: Long, subscribed: Boolean): Boolean = false
        override fun close() {}
    }

    private class MockPlayback : PlaybackEngine {
        override val snapshot: StateFlow<PlaybackSnapshot> = MutableStateFlow(PlaybackSnapshot())
        override suspend fun connect() {}
        override fun setQueue(songs: List<PlayableSong>, startIndex: Int) {}
        override fun play() {}
        override fun pause() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) {}
        override fun setRepeatMode(mode: RepeatMode) {}
        override fun setShuffle(enabled: Boolean) {}
        override fun clearError() {}
        override fun release() {}
    }
}
