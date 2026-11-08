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
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.playback.PlayableSong
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.util.formatFileSize
import dev.nocturn.localapi.client.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsViewModelTest {

    private fun createViewModel(): NocturnViewModel {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        app.container = object : AppContainer(app) {
            override val settings = SettingsStore(app)
            override val repository: MusicRepository = FakeMusicRepository()
            override val playback: PlaybackEngine = FakePlaybackEngine()
        }
        return NocturnViewModel(app)
    }

    @Test
    fun formatFileSizeFormatsBytesUnitsCorrectly() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("0 B", formatFileSize(-10L))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 KB", formatFileSize(1536L))
        assertEquals("10.0 MB", formatFileSize(10L * 1024L * 1024L))
        assertEquals("1.50 GB", formatFileSize((1.5 * 1024L * 1024L * 1024L).toLong()))
    }

    @Test
    fun navigationMethodsPushSettingsAndSubMenuScreens() {
        val model = createViewModel()

        assertEquals(emptyList<Screen>(), model.navigation.value.stack)

        model.openSettings()
        assertEquals(listOf(Screen.Settings), model.navigation.value.stack)

        model.openSettingsPlayback()
        assertEquals(listOf(Screen.Settings, Screen.SettingsPlayback), model.navigation.value.stack)

        model.back()
        assertEquals(listOf(Screen.Settings), model.navigation.value.stack)

        model.openSettingsAppearance()
        assertEquals(listOf(Screen.Settings, Screen.SettingsAppearance), model.navigation.value.stack)

        model.back()
        model.openSettingsGeneral()
        assertEquals(listOf(Screen.Settings, Screen.SettingsGeneral), model.navigation.value.stack)

        model.back()
        model.openSettingsStorage()
        assertEquals(listOf(Screen.Settings, Screen.SettingsStorage), model.navigation.value.stack)

        model.back()
        model.openSettingsAbout()
        assertEquals(listOf(Screen.Settings, Screen.SettingsAbout), model.navigation.value.stack)
    }

    @Test
    fun navigationMethodsDeduplicateConsecutivePushes() {
        val model = createViewModel()

        model.openSettings()
        model.openSettings()
        assertEquals(listOf(Screen.Settings), model.navigation.value.stack)

        model.openSettingsPlayback()
        model.openSettingsPlayback()
        assertEquals(listOf(Screen.Settings, Screen.SettingsPlayback), model.navigation.value.stack)

        model.back()
        model.openSettingsAppearance()
        model.openSettingsAppearance()
        assertEquals(listOf(Screen.Settings, Screen.SettingsAppearance), model.navigation.value.stack)

        model.back()
        model.openSettingsGeneral()
        model.openSettingsGeneral()
        assertEquals(listOf(Screen.Settings, Screen.SettingsGeneral), model.navigation.value.stack)

        model.back()
        model.openSettingsStorage()
        model.openSettingsStorage()
        assertEquals(listOf(Screen.Settings, Screen.SettingsStorage), model.navigation.value.stack)

        model.back()
        model.openSettingsAbout()
        model.openSettingsAbout()
        assertEquals(listOf(Screen.Settings, Screen.SettingsAbout), model.navigation.value.stack)
    }

    @Test
    fun selectTabWhenSameTabResetsBackStack() {
        val model = createViewModel()
        model.openLikedSongs()
        assertEquals(listOf(Screen.LikedSongs), model.navigation.value.stack)
        model.selectTab(AppTab.Home)
        assertEquals(AppTab.Home, model.navigation.value.tab)
        assertEquals(emptyList<Screen>(), model.navigation.value.stack)

        model.selectTab(AppTab.Library)
        model.openLikedSongs()
        assertEquals(listOf(Screen.LikedSongs), model.navigation.value.stack)
        // Selecting same tab with non-empty stack pops to root
        model.selectTab(AppTab.Library)
        assertEquals(AppTab.Library, model.navigation.value.tab)
        assertEquals(emptyList<Screen>(), model.navigation.value.stack)
    }

    @Test
    fun settingQualityUpdatesSettingsStateAndLibraryState() {
        val model = createViewModel()

        model.setQuality(AudioQuality.Lossless)
        assertEquals(AudioQuality.Lossless, model.settingsUi.value.quality)
        assertEquals(AudioQuality.Lossless, model.library.value.quality)
        assertEquals("无损", model.player.value.qualityLabel)
    }

    @Test
    fun settingThemeModeUpdatesSettingsStateAndLibraryState() {
        val model = createViewModel()

        model.setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, model.settingsUi.value.themeMode)
        assertEquals(ThemeMode.Dark, model.library.value.themeMode)

        model.setThemeMode(ThemeMode.Light)
        assertEquals(ThemeMode.Light, model.settingsUi.value.themeMode)
        assertEquals(ThemeMode.Light, model.library.value.themeMode)
    }

    @Test
    fun settingDynamicColorUpdatesSettingsStateAndLibraryState() {
        val model = createViewModel()

        model.setDynamicColor(false)
        assertFalse(model.settingsUi.value.dynamicColor)
        assertFalse(model.library.value.dynamicColor)

        model.setDynamicColor(true)
        assertTrue(model.settingsUi.value.dynamicColor)
        assertTrue(model.library.value.dynamicColor)
    }

    @Test
    fun settingHapticsUpdatesSettingsStateAndLibraryState() {
        val model = createViewModel()

        model.setHapticsEnabled(false)
        assertFalse(model.settingsUi.value.hapticsEnabled)
        assertFalse(model.library.value.hapticsEnabled)

        model.setHapticsEnabled(true)
        assertTrue(model.settingsUi.value.hapticsEnabled)
        assertTrue(model.library.value.hapticsEnabled)
    }

    @Test
    fun settingLyriconUpdatesSettingsStateAndLibraryState() {
        val model = createViewModel()

        // Verify initial default is true
        assertTrue(model.settingsUi.value.lyriconEnabled)
        assertTrue(model.library.value.lyriconEnabled)

        // Mutate to false
        model.setLyriconEnabled(false)
        assertFalse(model.settingsUi.value.lyriconEnabled)
        assertFalse(model.library.value.lyriconEnabled)

        // Mutate back to true
        model.setLyriconEnabled(true)
        assertTrue(model.settingsUi.value.lyriconEnabled)
        assertTrue(model.library.value.lyriconEnabled)
    }

    @Test
    fun initialLyriconEnabledReflectsSettingsStore() {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        val store = SettingsStore(app)
        store.lyriconEnabled = false

        val model = createViewModel()
        assertFalse(model.settingsUi.value.lyriconEnabled)
        assertFalse(model.library.value.lyriconEnabled)

        // Clean up
        store.lyriconEnabled = true
    }

    @Test
    fun settingLyriconEnabledPersistsToUnderlyingSettingsStore() {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        val model = createViewModel()

        model.setLyriconEnabled(false)
        val freshStore = SettingsStore(app)
        assertFalse(freshStore.lyriconEnabled)

        model.setLyriconEnabled(true)
        assertTrue(SettingsStore(app).lyriconEnabled)
    }

    @Test
    fun clearingSearchHistoryResetsHistoryCount() {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        val settingsStore = SettingsStore(app)
        settingsStore.recordSearch("TestQuery1")
        settingsStore.recordSearch("TestQuery2")

        val model = createViewModel()
        assertTrue(settingsStore.searchHistory().isNotEmpty())

        model.clearSearchHistory()
        assertEquals(0, model.settingsUi.value.searchHistoryCount)
        assertTrue(settingsStore.searchHistory().isEmpty())
        assertTrue(model.search.value.history.isEmpty())
    }

    @Test
    fun clearingCacheDeletesTemporaryFiles() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NocturnApplication
        val cacheDir = app.cacheDir
        val testFile = File(cacheDir, "sample-cache-file.tmp")
        testFile.writeText("sample cache content")
        assertTrue(testFile.exists())

        val externalCache = app.externalCacheDir
        val extTestFile = externalCache?.let {
            File(it, "sample-ext-cache.tmp").apply { writeText("external cache content") }
        }
        if (extTestFile != null) assertTrue(extTestFile.exists())

        val model = createViewModel()
        val job = model.clearCache()
        val duplicateJob = model.clearCache()
        // Concurrent invocation must return the exact same in-flight Job instance
        assertEquals(job, duplicateJob)
        job.join()

        assertFalse(testFile.exists())
        if (extTestFile != null) assertFalse(extTestFile.exists())
        assertFalse(model.settingsUi.value.isClearing)
        assertEquals(0L, model.settingsUi.value.cacheSizeBytes)
    }


    private class FakeMusicRepository : MusicRepository {
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
        override suspend fun likedSongs(): List<dev.nocturn.app.data.model.Song> = emptyList()
        override suspend fun dailySongs(): List<dev.nocturn.app.data.model.Song> = emptyList()
        override suspend fun personalizedSongs(): List<dev.nocturn.app.data.model.Song> = emptyList()
        override suspend fun recentSongs(): List<dev.nocturn.app.data.model.Song> = emptyList()
        override suspend fun setPlaylistSubscribed(playlistId: Long, subscribed: Boolean): Boolean = false
        override fun close() {}
    }

    private class FakePlaybackEngine : PlaybackEngine {
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
