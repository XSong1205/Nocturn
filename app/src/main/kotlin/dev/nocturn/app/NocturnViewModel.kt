package dev.nocturn.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.MusicRepositoryException
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.FreeListenClaimResult
import dev.nocturn.app.data.model.FreeListenRights
import dev.nocturn.app.data.model.HomeFeed
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.PlaylistSongsPage
import dev.nocturn.app.data.model.QrLoginState
import dev.nocturn.app.data.model.SearchAlbumsPage
import dev.nocturn.app.data.model.SearchArtistsPage
import dev.nocturn.app.data.model.SearchPlaylistsPage
import dev.nocturn.app.data.model.SearchSongsPage
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.data.model.displayName
import dev.nocturn.app.data.model.isDynamic
import dev.nocturn.app.playback.PlayableSong
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.app.ui.state.AlbumUiState
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.state.ArtistUiState
import dev.nocturn.app.ui.state.DetailStateStore
import dev.nocturn.app.ui.state.HomeUiState
import dev.nocturn.app.ui.state.LibraryUiState
import dev.nocturn.app.ui.state.LikedSongsUiState
import dev.nocturn.app.ui.state.LoginMode
import dev.nocturn.app.ui.state.LoginUiState
import dev.nocturn.app.ui.state.MiniPlayerUiState
import dev.nocturn.app.ui.state.NavState
import dev.nocturn.app.ui.state.PlaybackMode
import dev.nocturn.app.ui.state.PlayerUiState
import dev.nocturn.app.ui.state.PlaylistUiState
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.state.SearchTab
import dev.nocturn.app.ui.state.SearchUiState
import dev.nocturn.app.ui.state.SettingsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import dev.nocturn.app.ui.util.greetingForHour
import dev.nocturn.localapi.client.AudioQuality
import dev.nocturn.localapi.client.ImmerseType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One application-scoped view model drives the whole shell: navigation, screen states, account
 * sign-in and the playback session. Screens stay stateless and receive immutable state plus
 * callbacks from [dev.nocturn.app.ui.NocturnApp].
 */
class NocturnViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NocturnApplication).container
    private val repository: MusicRepository = container.repository
    private val engine: PlaybackEngine = container.playback
    private val settings: SettingsStore = container.settings

    private val mutableNavigation = MutableStateFlow(NavState())
    val navigation: StateFlow<NavState> = mutableNavigation.asStateFlow()

    private val mutableHome = MutableStateFlow(
        HomeUiState(greeting = greetingForHour()),
    )
    val home: StateFlow<HomeUiState> = mutableHome.asStateFlow()

    private val mutableSearch = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = mutableSearch.asStateFlow()

    private val mutableLibrary = MutableStateFlow(
        LibraryUiState(
            themeMode = settings.themeMode,
            colorSchemeKey = settings.colorSchemeKey,
            customSeedColor = settings.customSeedColor,
            dynamicVariant = settings.dynamicVariant,
            dynamicColor = settings.dynamicColor,
            quality = settings.quality,
            hapticsEnabled = settings.hapticsEnabled,
            lyriconEnabled = settings.lyriconEnabled,
        ),
    )
    val library: StateFlow<LibraryUiState> = mutableLibrary.asStateFlow()

    private val mutableSettings = MutableStateFlow(
        SettingsUiState(
            quality = settings.quality,
            immerseType = settings.immerseType,
            unblockEnabled = settings.unblockEnabled,
            themeMode = settings.themeMode,
            colorSchemeKey = settings.colorSchemeKey,
            customSeedColor = settings.customSeedColor,
            dynamicVariant = settings.dynamicVariant,
            dynamicColor = settings.dynamicColor,
            hapticsEnabled = settings.hapticsEnabled,
            lyriconEnabled = settings.lyriconEnabled,
            scrobbleEnabled = settings.scrobbleEnabled,
            relayPlayStateEnabled = settings.relayPlayStateEnabled,
            searchHistoryCount = settings.searchHistory().size,
            version = appVersionName(),
        ),
    )
    val settingsUi: StateFlow<SettingsUiState> = mutableSettings.asStateFlow()

    // Detail routes keep one state per back-stack entry, keyed by id, so stacked pages of the same
    // kind (album -> artist -> album) never overwrite each other.
    private val playlistStates = DetailStateStore<PlaylistUiState>()
    val playlists: StateFlow<Map<Long, PlaylistUiState>> = playlistStates.states

    private val albumStates = DetailStateStore<AlbumUiState>()
    val albums: StateFlow<Map<Long, AlbumUiState>> = albumStates.states

    private val artistStates = DetailStateStore<ArtistUiState>()
    val artists: StateFlow<Map<Long, ArtistUiState>> = artistStates.states

    private val mutableLikedSongs = MutableStateFlow(LikedSongsUiState())
    val likedSongs: StateFlow<LikedSongsUiState> = mutableLikedSongs.asStateFlow()

    private val mutablePlayer = MutableStateFlow(PlayerUiState())
    val player: StateFlow<PlayerUiState> = mutablePlayer.asStateFlow()

    private val mutableMiniPlayer = MutableStateFlow(MiniPlayerUiState())
    val miniPlayer: StateFlow<MiniPlayerUiState> = mutableMiniPlayer.asStateFlow()

    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    private val mutableReselectEvents = MutableSharedFlow<AppTab>(extraBufferCapacity = 1)
    val tabReselectEvents: SharedFlow<AppTab> = mutableReselectEvents.asSharedFlow()

    private val mutableFreeListenRights = MutableStateFlow<FreeListenRights?>(null)
    val freeListenRights: StateFlow<FreeListenRights?> = mutableFreeListenRights.asStateFlow()

    private val mutableIsFreeListenLoading = MutableStateFlow(false)
    val isFreeListenLoading: StateFlow<Boolean> = mutableIsFreeListenLoading.asStateFlow()

    private val mutableAutoClaimFreeListen = MutableStateFlow(settings.autoClaimFreeListen)
    val autoClaimFreeListen: StateFlow<Boolean> = mutableAutoClaimFreeListen.asStateFlow()

    private val mutableShowFreeListenDialog = MutableStateFlow(false)
    val showFreeListenDialog: StateFlow<Boolean> = mutableShowFreeListenDialog.asStateFlow()

    private var freeListenMonitorJob: Job? = null

    private var queue: List<Song> = emptyList()
    private var savedQueueBeforeHeartbeat: List<Song>? = null
    private var currentPlaybackMode: PlaybackMode = PlaybackMode.Sequence
    private var activePlaylistId: Long? = null
    private val likedSongIds = linkedSetOf<Long>()
    private var searchRequest: Job? = null
    private var suggestionRequest: Job? = null
    private var playbackRequest: Job? = null
    private var lyricsRequest: Job? = null
    private var currentLyricsSongId: Long? = null
    private var loginPoll: Job? = null
    private var phoneLogin: Job? = null
    private var codeCooldown: Job? = null
    private var codeAvailableAt = 0L
    private var libraryRequest: Job? = null
    private var homeLoaded = false
    private var libraryLoaded = false

    private var currentSessionId: String = generatePlaybackSessionId()
    private var trackedSong: Song? = null
    private var trackedPlayedMs: Long = 0L
    private var lastSnapshotNanos: Long = 0L
    private var wasPlaying: Boolean = false
    private var hasScrobbledCurrent: Boolean = false

    init {
        observePlayback()
        loadHome()
        refreshLibrary()
        loadSearchLanding()
        startFreeListenMonitor()
        viewModelScope.launch {
            repository.session.collect { profile ->
                if (profile != null) {
                    val wasLoggedIn = mutableLibrary.value.loggedIn
                    mutableLibrary.update { it.copy(loggedIn = true, profile = profile) }
                    mutableHome.update { it.copy(isLoggedIn = true, isVip = profile.vipLevel > 0) }
                    if (!wasLoggedIn) {
                        loadHome(refresh = true)
                        loadSearchLanding()
                    }
                } else if (mutableLibrary.value.loggedIn || mutableLibrary.value.profile != null) {
                    likedSongIds.clear()
                    mutableLibrary.update {
                        it.copy(
                            loggedIn = false,
                            profile = null,
                            stats = null,
                            playlists = emptyList(),
                            recentSongs = emptyList(),
                            likedCount = null,
                        )
                    }
                    mutableHome.update { it.copy(isLoggedIn = false, isVip = false, dailySongs = emptyList()) }
                    applyLikedState()
                    loadHome(refresh = true)
                    loadSearchLanding()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------------------

    fun selectTab(tab: AppTab) {
        if (mutableNavigation.value.tab == tab) {
            // Reselecting current tab: pop to root if currently on a sub-screen
            if (mutableNavigation.value.stack.isNotEmpty()) {
                mutableNavigation.update { it.copy(stack = emptyList()) }
                pruneDetailStates()
            } else {
                // Already at root of active tab: notify reselect (e.g. scroll list to top)
                mutableReselectEvents.tryEmit(tab)
            }
            return
        }
        // Switching tabs preserves existing back stacks for all tabs
        mutableNavigation.update { it.copy(tab = tab) }
        pruneDetailStates()
        when (tab) {
            AppTab.Home -> if (!homeLoaded) loadHome()
            AppTab.Library -> if (!libraryLoaded) refreshLibrary()
            AppTab.Search -> Unit
        }
    }

    // Re-opening a page that is still on the stack reuses its loaded state; a failed or missing
    // entry is fetched again.
    fun openPlaylist(playlistId: Long) {
        activePlaylistId = playlistId
        val target = Screen.Playlist(playlistId)
        if (mutableNavigation.value.stack.lastOrNull() == target) return
        mutableNavigation.update { it.copy(stack = it.stack + target) }
        pruneDetailStates()
        if (playlistStates[playlistId]?.detail == null) loadPlaylist(playlistId)
    }

    fun openAlbum(albumId: Long) {
        val target = Screen.Album(albumId)
        if (mutableNavigation.value.stack.lastOrNull() == target) return
        mutableNavigation.update { it.copy(stack = it.stack + target) }
        pruneDetailStates()
        if (albumStates[albumId]?.detail == null) loadAlbum(albumId)
    }

    fun openArtist(artistId: Long) {
        val target = Screen.Artist(artistId)
        if (mutableNavigation.value.stack.lastOrNull() == target) return
        mutableNavigation.update { it.copy(stack = it.stack + target) }
        pruneDetailStates()
        if (artistStates[artistId]?.detail == null) loadArtist(artistId)
    }

    /**
     * Drops detail state for pages that are no longer on any tab stack. Called on pushes and tab
     * switches rather than on back, so a page that is animating out still has its content.
     */
    private fun pruneDetailStates() {
        val allScreens = mutableNavigation.value.allScreens
        playlistStates.retain(allScreens.filterIsInstance<Screen.Playlist>().map { it.id }.toSet())
        albumStates.retain(allScreens.filterIsInstance<Screen.Album>().map { it.id }.toSet())
        artistStates.retain(allScreens.filterIsInstance<Screen.Artist>().map { it.id }.toSet())
    }

    fun openRecentSongs() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.RecentSongs) it
            else it.copy(stack = it.stack + Screen.RecentSongs)
        }
    }

    fun openLikedSongs() {
        if (mutableNavigation.value.stack.lastOrNull() == Screen.LikedSongs) return
        mutableNavigation.update { it.copy(stack = it.stack + Screen.LikedSongs) }
        loadLikedSongs()
    }

    /** Daily recommendations are part of the home feed, so their page needs no extra request. */
    fun openDailySongs() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.DailySongs) it
            else it.copy(stack = it.stack + Screen.DailySongs)
        }
    }

    fun openSettings() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.Settings) it
            else it.copy(stack = it.stack + Screen.Settings)
        }
        refreshCacheSize()
    }

    fun openSettingsPlayback() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.SettingsPlayback) it
            else it.copy(stack = it.stack + Screen.SettingsPlayback)
        }
    }

    fun openSettingsAppearance() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.SettingsAppearance) it
            else it.copy(stack = it.stack + Screen.SettingsAppearance)
        }
    }

    fun openSettingsGeneral() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.SettingsGeneral) it
            else it.copy(stack = it.stack + Screen.SettingsGeneral)
        }
    }

    fun openSettingsStorage() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.SettingsStorage) it
            else it.copy(stack = it.stack + Screen.SettingsStorage)
        }
        refreshCacheSize()
    }

    fun openSettingsAbout() {
        mutableNavigation.update {
            if (it.stack.lastOrNull() == Screen.SettingsAbout) it
            else it.copy(stack = it.stack + Screen.SettingsAbout)
        }
    }

    fun openPlayer() {
        mutablePlayer.update { it.copy(visible = true) }
        mutablePlayer.value.song?.let { song ->
            if (currentLyricsSongId != song.id) {
                loadLyrics(song)
            }
        }
    }

    fun collapsePlayer() {
        mutablePlayer.update { it.copy(visible = false) }
    }

    /** Returns true when the event was consumed. */
    fun back(): Boolean {
        val nav = mutableNavigation.value
        if (mutablePlayer.value.visible) {
            collapsePlayer()
            return true
        }
        if (nav.stack.isNotEmpty()) {
            mutableNavigation.update { it.copy(stack = it.stack.dropLast(1)) }
            return true
        }
        if (nav.tab == AppTab.Search && mutableSearch.value.expanded) {
            mutableSearch.update { it.copy(expanded = false) }
            return true
        }
        return false
    }

    // ---------------------------------------------------------------------------------------
    // Home
    // ---------------------------------------------------------------------------------------

    fun loadHome(refresh: Boolean = false) {
        viewModelScope.launch {
            homeLoaded = true
            mutableHome.update {
                it.copy(
                    loading = !it.hasContent && !refresh,
                    refreshing = refresh,
                    error = null,
                )
            }
            try {
                val feed = repository.homeFeed()
                applyHomeFeed(feed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableHome.update {
                    it.copy(loading = false, refreshing = false, error = error.userMessage())
                }
            }
        }
    }

    fun refreshHome() = loadHome(refresh = true)

    private fun applyHomeFeed(feed: HomeFeed) {
        mutableHome.update {
            it.copy(
                loading = false,
                refreshing = false,
                error = null,
                greeting = greetingForHour(),
                recommendedPlaylists = feed.playlists,
                charts = feed.charts,
                dailySongs = feed.dailySongs,
                personalizedSongs = feed.personalizedSongs,
                radarPlaylist = feed.radarPlaylist,
                isLoggedIn = feed.isLoggedIn,
                isVip = (repository.session.value?.vipLevel ?: 0) > 0,
                likedSongIds = likedSongIds.toSet(),
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------

    private fun loadSearchLanding() {
        viewModelScope.launch {
            val history = settings.searchHistory()
            mutableSearch.update { it.copy(history = history) }
            mutableSettings.update { it.copy(searchHistoryCount = history.size) }
            val hot = runCatching { repository.hotSearches() }.getOrDefault(emptyList())
            val default = runCatching { repository.defaultKeyword() }.getOrNull()
            mutableSearch.update {
                it.copy(
                    hotKeywords = hot,
                    defaultKeyword = default,
                    history = settings.searchHistory(),
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        mutableSearch.update { it.copy(query = query.take(80), submittedKeyword = null) }
        suggestionRequest?.cancel()
        if (query.isBlank()) {
            mutableSearch.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionRequest = viewModelScope.launch {
            delay(150)
            runCatching { repository.searchSuggestions(query.trim()) }
                .onSuccess { suggestions ->
                    if (mutableSearch.value.query.trim() == query.trim()) {
                        mutableSearch.update { it.copy(suggestions = suggestions.take(10)) }
                    }
                }
        }
    }

    fun expandSearch() {
        mutableSearch.update { it.copy(expanded = true) }
        val current = mutableSearch.value.query.trim()
        if (current.isNotEmpty() && mutableSearch.value.suggestions.isEmpty()) {
            updateSearchQuery(current)
        }
    }

    fun collapseSearch() {
        mutableSearch.update { it.copy(expanded = false) }
    }

    fun submitSearch(keyword: String = mutableSearch.value.query) {
        val value = keyword.trim().ifEmpty { mutableSearch.value.defaultKeyword?.keyword?.trim().orEmpty() }
        if (value.isEmpty()) return
        settings.recordSearch(value)
        val history = settings.searchHistory()
        mutableSettings.update { it.copy(searchHistoryCount = history.size) }
        mutableSearch.update {
            it.copy(
                query = value,
                expanded = true,
                submittedKeyword = value,
                loading = true,
                error = null,
                suggestions = emptyList(),
                songs = emptyList(),
                playlists = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                hasMore = false,
                history = settings.searchHistory(),
                likedSongIds = likedSongIds.toSet(),
            )
        }
        loadSearchTab(mutableSearch.value.tab, reset = true)
    }

    fun selectSearchTab(tab: SearchTab) {
        if (tab == mutableSearch.value.tab) return
        mutableSearch.update { it.copy(tab = tab) }
        if (mutableSearch.value.resultCount(tab) == 0 &&
            mutableSearch.value.submittedKeyword != null
        ) {
            loadSearchTab(tab, reset = true)
        }
    }

    fun retrySearch() {
        if (mutableSearch.value.submittedKeyword != null) {
            loadSearchTab(mutableSearch.value.tab, reset = true)
        } else {
            loadSearchLanding()
        }
    }

    fun clearSearchHistory() {
        settings.clearSearchHistory()
        mutableSearch.update { it.copy(history = emptyList()) }
        mutableSettings.update { it.copy(searchHistoryCount = 0) }
        notify("搜索历史已清除")
    }

    fun onSearchBack(): Boolean {
        val state = mutableSearch.value
        if (state.expanded || state.submittedKeyword != null) {
            if (state.expanded) {
                mutableSearch.update { it.copy(expanded = false) }
            } else {
                mutableSearch.update { it.copy(submittedKeyword = null, songs = emptyList(), playlists = emptyList(), albums = emptyList(), artists = emptyList(), hasMore = false, query = "") }
            }
            return true
        }
        return false
    }

    private fun loadSearchTab(tab: SearchTab, reset: Boolean) {
        val keyword = mutableSearch.value.submittedKeyword ?: return
        searchRequest?.cancel()
        searchRequest = viewModelScope.launch {
            mutableSearch.update { it.copy(tab = tab, loading = true, error = null) }
            try {
                when (tab) {
                    SearchTab.Songs -> {
                        val page: SearchSongsPage = repository.searchSongs(keyword, 0, 30)
                        mutableSearch.update {
                            it.copy(songs = page.songs, hasMore = page.hasMore, loading = false)
                        }
                    }
                    SearchTab.Playlists -> {
                        val page: SearchPlaylistsPage = repository.searchPlaylists(keyword, 0, 30)
                        mutableSearch.update {
                            it.copy(playlists = page.playlists, hasMore = page.hasMore, loading = false)
                        }
                    }
                    SearchTab.Albums -> {
                        val page: SearchAlbumsPage = repository.searchAlbums(keyword, 0, 30)
                        mutableSearch.update {
                            it.copy(albums = page.albums, hasMore = page.hasMore, loading = false)
                        }
                    }
                    SearchTab.Artists -> {
                        val page: SearchArtistsPage = repository.searchArtists(keyword, 0, 30)
                        mutableSearch.update {
                            it.copy(artists = page.artists, hasMore = page.hasMore, loading = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableSearch.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun loadMoreSearch() {
        val state = mutableSearch.value
        val keyword = state.submittedKeyword ?: return
        if (state.loading || state.loadingMore || !state.hasMore) return
        val offset = when (state.tab) {
            SearchTab.Songs -> state.songs.size
            SearchTab.Playlists -> state.playlists.size
            SearchTab.Albums -> state.albums.size
            SearchTab.Artists -> state.artists.size
        }
        viewModelScope.launch {
            mutableSearch.update { it.copy(loadingMore = true) }
            try {
                when (state.tab) {
                    SearchTab.Songs -> {
                        val page = repository.searchSongs(keyword, offset, 30)
                        mutableSearch.update {
                            it.copy(songs = it.songs + page.songs, hasMore = page.hasMore, loadingMore = false)
                        }
                    }
                    SearchTab.Playlists -> {
                        val page = repository.searchPlaylists(keyword, offset, 30)
                        mutableSearch.update {
                            it.copy(playlists = it.playlists + page.playlists, hasMore = page.hasMore, loadingMore = false)
                        }
                    }
                    SearchTab.Albums -> {
                        val page = repository.searchAlbums(keyword, offset, 30)
                        mutableSearch.update {
                            it.copy(albums = it.albums + page.albums, hasMore = page.hasMore, loadingMore = false)
                        }
                    }
                    SearchTab.Artists -> {
                        val page = repository.searchArtists(keyword, offset, 30)
                        mutableSearch.update {
                            it.copy(artists = it.artists + page.artists, hasMore = page.hasMore, loadingMore = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableSearch.update {
                    it.copy(loadingMore = false, error = error.userMessage())
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Playlist / album / artist / liked songs
    // ---------------------------------------------------------------------------------------

    fun loadPlaylist(playlistId: Long) {
        playlistStates.put(playlistId, PlaylistUiState(playlistId = playlistId, loading = true))
        viewModelScope.launch {
            try {
                val detail: PlaylistDetail = repository.playlistDetail(playlistId)
                val page: PlaylistSongsPage = repository.playlistSongs(playlistId, 0, 50)
                playlistStates.update(playlistId) {
                    it.copy(
                        loading = false,
                        detail = detail,
                        songs = page.songs,
                        hasMore = page.nextOffset != null,
                        likedSongIds = likedSongIds.toSet(),
                        isLoggedIn = repository.isLoggedIn(),
                        nowPlayingSongId = mutablePlayer.value.song?.id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                playlistStates.update(playlistId) { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun loadMorePlaylist(playlistId: Long) {
        val state = playlistStates[playlistId] ?: return
        val detail = state.detail ?: return
        if (state.loading || state.loadingMore || !state.hasMore) return
        viewModelScope.launch {
            playlistStates.update(playlistId) { it.copy(loadingMore = true) }
            try {
                val page = repository.playlistSongs(detail.id, state.songs.size, 50)
                playlistStates.update(playlistId) {
                    it.copy(
                        songs = it.songs + page.songs,
                        hasMore = page.nextOffset != null,
                        loadingMore = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                playlistStates.update(playlistId) { it.copy(loadingMore = false, error = error.userMessage()) }
            }
        }
    }

    fun togglePlaylistSubscription(playlistId: Long) {
        val detail = playlistStates[playlistId]?.detail ?: return
        if (!repository.isLoggedIn()) {
            notify("登录后可收藏歌单")
            return
        }
        viewModelScope.launch {
            playlistStates.update(playlistId) { it.copy(subscribing = true, subscribeError = null) }
            try {
                val subscribed = repository.setPlaylistSubscribed(detail.id, !detail.subscribed)
                playlistStates.update(playlistId) {
                    it.copy(
                        subscribing = false,
                        detail = it.detail?.copy(subscribed = subscribed),
                    )
                }
                notify(if (subscribed) "已收藏歌单" else "已取消收藏")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                playlistStates.update(playlistId) {
                    it.copy(subscribing = false, subscribeError = error.userMessage())
                }
            }
        }
    }

    fun loadAlbum(albumId: Long) {
        albumStates.put(albumId, AlbumUiState(albumId = albumId, loading = true))
        viewModelScope.launch {
            try {
                val detail: AlbumDetail = repository.albumDetail(albumId)
                albumStates.update(albumId) {
                    it.copy(
                        loading = false,
                        detail = detail,
                        likedSongIds = likedSongIds.toSet(),
                        nowPlayingSongId = mutablePlayer.value.song?.id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                albumStates.update(albumId) { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun loadArtist(artistId: Long) {
        artistStates.put(artistId, ArtistUiState(artistId = artistId, loading = true))
        viewModelScope.launch {
            try {
                val detail: ArtistDetail = repository.artistDetail(artistId)
                artistStates.update(artistId) {
                    it.copy(
                        loading = false,
                        detail = detail,
                        likedSongIds = likedSongIds.toSet(),
                        nowPlayingSongId = mutablePlayer.value.song?.id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                artistStates.update(artistId) { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun loadLikedSongs() {
        mutableLikedSongs.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val songs = repository.likedSongs()
                mutableLikedSongs.update {
                    it.copy(
                        loading = false,
                        songs = songs,
                        likedSongIds = likedSongIds.toSet(),
                        nowPlayingSongId = mutablePlayer.value.song?.id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableLikedSongs.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Library and account
    // ---------------------------------------------------------------------------------------

    fun refreshLibrary() {
        libraryRequest?.cancel()
        libraryRequest = viewModelScope.launch {
            libraryLoaded = true
            mutableLibrary.update { it.copy(loading = it.profile == null && !it.loggedIn, error = null) }
            try {
                val profile = repository.restoreSession()
                if (profile == null) {
                    mutableLibrary.update {
                        it.copy(
                            loading = false,
                            loggedIn = false,
                            profile = null,
                            stats = null,
                            playlists = emptyList(),
                            recentSongs = emptyList(),
                            likedCount = null,
                        )
                    }
                    mutableHome.update { it.copy(isLoggedIn = false) }
                    likedSongIds.clear()
                    applyLikedState()
                    return@launch
                }
                val stats = runCatching { repository.accountStats() }.getOrNull()
                val playlists = runCatching { repository.userPlaylists() }.getOrDefault(emptyList())
                val recent = runCatching { repository.recentSongs() }.getOrDefault(emptyList())
                val liked = runCatching { repository.likedSongIds() }.getOrDefault(emptySet())
                likedSongIds.clear()
                likedSongIds.addAll(liked)
                mutableLibrary.update {
                    it.copy(
                        loading = false,
                        loggedIn = true,
                        profile = profile,
                        stats = stats,
                        playlists = playlists,
                        recentSongs = recent,
                        likedCount = liked.size,
                        error = null,
                    )
                }
                applyLikedState()
                loadHome(refresh = true)
                loadSearchLanding()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableLibrary.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun startLogin() {
        mutableLibrary.update {
            it.copy(
                login = it.login.copy(
                    visible = true,
                    loading = false,
                    challenge = null,
                    state = QrLoginState.WaitingForScan,
                    error = null,
                ),
            )
        }
        if (mutableLibrary.value.login.mode == LoginMode.Qr) startQrChallenge()
    }

    fun selectLoginMode(mode: LoginMode) {
        val login = mutableLibrary.value.login
        if (login.mode != LoginMode.Qr && (login.loading || login.sendingCode)) return
        loginPoll?.cancel()
        mutableLibrary.update {
            it.copy(login = it.login.copy(
                mode = mode,
                challenge = null,
                state = QrLoginState.WaitingForScan,
                authorizedProfile = null,
                error = null,
            ))
        }
        if (mode == LoginMode.Qr) startQrChallenge()
    }

    fun sendLoginCode(phone: String, countryCode: String) {
        val state = mutableLibrary.value.login
        if (!state.visible || state.mode != LoginMode.Code || state.sendingCode ||
            state.cooldownSeconds > 0 || phoneLogin?.isActive == true) return
        if (!validLoginPhone(phone, countryCode)) {
            setLoginError("请输入正确的手机号和区号。")
            return
        }
        mutableLibrary.update { it.copy(login = it.login.copy(sendingCode = true, error = null)) }
        phoneLogin = viewModelScope.launch {
            try {
                repository.sendLoginCode(phone, countryCode)
                codeAvailableAt = SystemClock.elapsedRealtime() + 60_000L
                codeCooldown?.cancel()
                codeCooldown = viewModelScope.launch {
                    while (true) {
                        val remaining = ((codeAvailableAt - SystemClock.elapsedRealtime() + 999L) / 1_000L)
                            .toInt().coerceAtLeast(0)
                        mutableLibrary.update { it.copy(login = it.login.copy(cooldownSeconds = remaining)) }
                        if (remaining == 0) break
                        delay(1_000)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setLoginError(error.userMessage())
            } finally {
                mutableLibrary.update { it.copy(login = it.login.copy(sendingCode = false)) }
            }
        }
    }

    fun submitPhoneLogin(phone: String, countryCode: String, credential: String) {
        val state = mutableLibrary.value.login
        if (!state.visible || state.mode == LoginMode.Qr || state.loading ||
            state.sendingCode || phoneLogin?.isActive == true) return
        if (!validLoginPhone(phone, countryCode)) {
            setLoginError("请输入正确的手机号和区号。")
            return
        }
        if (state.mode == LoginMode.Code && !Regex("^[0-9]{4,8}$").matches(credential)) {
            setLoginError("请输入 4 至 8 位验证码。")
            return
        }
        if (state.mode == LoginMode.Password && credential.length !in 1..128) {
            setLoginError("请输入密码。")
            return
        }
        mutableLibrary.update { it.copy(login = it.login.copy(loading = true, error = null)) }
        phoneLogin = viewModelScope.launch {
            try {
                val profile = if (state.mode == LoginMode.Code) {
                    repository.loginWithCode(phone, credential, countryCode)
                } else {
                    repository.loginWithPassword(phone, credential, countryCode)
                }
                libraryRequest?.cancel()
                mutableLibrary.update {
                    it.copy(loggedIn = true, profile = profile, login = LoginUiState())
                }
                refreshLibrary()
                loadHome(refresh = true)
                loadSearchLanding()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setLoginError(error.userMessage())
            } finally {
                mutableLibrary.update { it.copy(login = it.login.copy(loading = false)) }
            }
        }
    }

    private fun validLoginPhone(phone: String, countryCode: String): Boolean =
        Regex("^[0-9]{5,15}$").matches(phone) && Regex("^[1-9][0-9]{0,3}$").matches(countryCode)

    private fun setLoginError(message: String) {
        mutableLibrary.update { it.copy(login = it.login.copy(error = message)) }
    }

    fun refreshLoginQr() {
        mutableLibrary.update {
            it.copy(login = it.login.copy(loading = true, challenge = null, error = null))
        }
        startQrChallenge()
    }

    fun closeLogin() {
        val login = mutableLibrary.value.login
        if (login.mode != LoginMode.Qr && (login.loading || login.sendingCode)) return
        loginPoll?.cancel()
        mutableLibrary.update { it.copy(login = LoginUiState(cooldownSeconds = it.login.cooldownSeconds)) }
    }

    private fun startQrChallenge() {
        loginPoll?.cancel()
        loginPoll = viewModelScope.launch {
            try {
                val challenge = repository.createQrChallenge()
                mutableLibrary.update {
                    it.copy(
                        login = it.login.copy(
                            loading = false,
                            challenge = challenge,
                            state = QrLoginState.WaitingForScan,
                        ),
                    )
                }
                pollLogin(challenge.key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableLibrary.update {
                    it.copy(
                        login = it.login.copy(
                            loading = false,
                            error = error.userMessage(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun pollLogin(key: String) {
        while (true) {
            delay(2_000)
            val result = try {
                repository.pollQrLogin(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                    mutableLibrary.update {
                        it.copy(login = it.login.copy(error = error.userMessage()))
                    }
                    return
            }
            mutableLibrary.update { it.copy(login = it.login.copy(state = result.state)) }
            when (result.state) {
                QrLoginState.Authorized -> {
                    libraryRequest?.cancel()
                    mutableLibrary.update {
                        it.copy(
                            loggedIn = true,
                            profile = result.profile,
                            login = it.login.copy(authorizedProfile = result.profile, loading = false),
                        )
                    }
                    delay(700)
                    mutableLibrary.update { it.copy(login = LoginUiState()) }
                    refreshLibrary()
                    loadHome(refresh = true)
                    loadSearchLanding()
                    return
                }
                QrLoginState.Expired, QrLoginState.Rejected -> return
                else -> Unit
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notify(error.userMessage())
                return@launch
            }
            likedSongIds.clear()
            mutableLibrary.update {
                it.copy(
                    loggedIn = false,
                    profile = null,
                    stats = null,
                    playlists = emptyList(),
                    recentSongs = emptyList(),
                    likedCount = null,
                    error = null,
                )
            }
            mutableHome.update { it.copy(isLoggedIn = false, dailySongs = emptyList()) }
            applyLikedState()
            loadHome(refresh = true)
            loadSearchLanding()
            notify("已退出登录")
        }
    }

    fun setQuality(quality: AudioQuality) {
        settings.quality = quality
        mutableLibrary.update { it.copy(quality = quality) }
        mutableSettings.update { it.copy(quality = quality) }
        mutablePlayer.update { it.copy(qualityLabel = quality.displayName()) }
        notify("音质已切换为${quality.displayName()}")
    }

    fun setImmerseType(type: ImmerseType) {
        settings.immerseType = type
        mutableSettings.update { it.copy(immerseType = type) }
        notify("沉浸声类型已切换为${type.displayName()}")
    }

    fun setUnblockEnabled(enabled: Boolean) {
        settings.unblockEnabled = enabled
        mutableSettings.update { it.copy(unblockEnabled = enabled) }
        notify(if (enabled) "已开启第三方歌曲解锁" else "已关闭第三方歌曲解锁")
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        mutableLibrary.update { it.copy(themeMode = mode) }
        mutableSettings.update { it.copy(themeMode = mode) }
    }

    fun setColorScheme(scheme: ColorSchemeKey, seed: Long? = null) {
        settings.colorSchemeKey = scheme
        if (seed != null) {
            settings.customSeedColor = seed
        }
        val isDyn = scheme.isDynamic
        settings.dynamicColor = isDyn
        val currentSeed = settings.customSeedColor
        mutableLibrary.update {
            it.copy(
                colorSchemeKey = scheme,
                customSeedColor = currentSeed,
                dynamicColor = isDyn,
            )
        }
        mutableSettings.update {
            it.copy(
                colorSchemeKey = scheme,
                customSeedColor = currentSeed,
                dynamicColor = isDyn,
            )
        }
    }

    fun setDynamicVariant(variant: DynamicVariant) {
        settings.dynamicVariant = variant
        mutableLibrary.update { it.copy(dynamicVariant = variant) }
        mutableSettings.update { it.copy(dynamicVariant = variant) }
    }

    fun resetColorScheme() {
        setColorScheme(ColorSchemeKey.M3eDynamic)
        setDynamicVariant(DynamicVariant.Expressive)
    }

    fun setDynamicColor(enabled: Boolean) {
        settings.dynamicColor = enabled
        val scheme = settings.colorSchemeKey
        mutableLibrary.update { it.copy(dynamicColor = enabled, colorSchemeKey = scheme) }
        mutableSettings.update { it.copy(dynamicColor = enabled, colorSchemeKey = scheme) }
    }

    /** Turns the app-wide tactile layer on or off and persists the choice. */
    fun setHapticsEnabled(enabled: Boolean) {
        settings.hapticsEnabled = enabled
        mutableLibrary.update { it.copy(hapticsEnabled = enabled) }
        mutableSettings.update { it.copy(hapticsEnabled = enabled) }
    }

    fun setLyriconEnabled(enabled: Boolean) {
        settings.lyriconEnabled = enabled
        mutableLibrary.update { it.copy(lyriconEnabled = enabled) }
        mutableSettings.update { it.copy(lyriconEnabled = enabled) }
    }

    fun setScrobbleEnabled(enabled: Boolean) {
        settings.scrobbleEnabled = enabled
        mutableSettings.update { it.copy(scrobbleEnabled = enabled) }
    }

    fun setRelayPlayStateEnabled(enabled: Boolean) {
        settings.relayPlayStateEnabled = enabled
        mutableSettings.update { it.copy(relayPlayStateEnabled = enabled) }
    }

    fun openRoaming() {
        notify("私人漫游功能正在开发中，敬请期待")
    }

    fun playPlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                val songs = playlistStates[playlistId]?.songs?.takeIf { it.isNotEmpty() }
                    ?: repository.playlistSongs(playlistId, 0, 50).songs
                if (songs.isNotEmpty()) {
                    playSongs(songs, 0)
                } else {
                    notify("歌单为空或暂无可用歌曲")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notify("播放失败，请稍后重试")
            }
        }
    }

    @Volatile
    private var clearCacheJob: Job? = null

    fun refreshCacheSize(): Job = viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val size = computeCacheDirSize(context.cacheDir) + computeCacheDirSize(context.externalCacheDir)
        val historyCount = settings.searchHistory().size
        mutableSettings.update {
            it.copy(
                cacheSizeBytes = size,
                searchHistoryCount = historyCount,
            )
        }
    }

    fun clearCache(): Job {
        val existing = clearCacheJob
        if (existing != null && existing.isActive) return existing
        val job = viewModelScope.launch(Dispatchers.IO) {
            mutableSettings.update { it.copy(isClearing = true) }
            val context = getApplication<Application>()
            deleteDirectoryContents(context.cacheDir)
            deleteDirectoryContents(context.externalCacheDir)
            val size = computeCacheDirSize(context.cacheDir) + computeCacheDirSize(context.externalCacheDir)
            mutableSettings.update { it.copy(cacheSizeBytes = size, isClearing = false) }
            notify("缓存已清除")
        }
        clearCacheJob = job
        return job
    }

    private fun computeCacheDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    private fun deleteDirectoryContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        runCatching {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    private fun appVersionName(): String = runCatching {
        val context = getApplication<Application>()
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.28"
    }.getOrDefault("0.1.28")

    // ---------------------------------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------------------------------

    fun playSongs(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        startPlayback(songs, startIndex.coerceIn(0, songs.lastIndex))
    }

    fun playQueueIndex(index: Int) {
        if (queue.isEmpty()) return
        startPlayback(queue, index.coerceIn(0, queue.lastIndex))
    }

    fun playAll(songs: List<Song>) = playSongs(songs, 0)

    private fun startPlayback(songs: List<Song>, startIndex: Int) {
        playbackRequest?.cancel()
        playbackRequest = viewModelScope.launch {
            mutablePlayer.update { it.copy(buffering = true, error = null) }
            mutableMiniPlayer.update { it.copy(buffering = true) }
            try {
                if (!connectEngine()) {
                    failPlayback("播放服务未就绪，请稍后重试。")
                    return@launch
                }
                val urls = repository.streamUrls(
                    songs.map { it.id },
                    settings.quality,
                    settings.immerseType,
                    settings.unblockEnabled,
                )
                val playable = songs.mapNotNull { song ->
                    urls[song.id]?.takeIf { it.isNotBlank() }?.let { PlayableSong.from(song, it) }
                }
                if (playable.isEmpty()) {
                    failPlayback("暂时没有可用音源，这首歌可能需要会员或登录。")
                    return@launch
                }
                val startSong = songs[startIndex]
                val engineIndex = playable.indexOfFirst { it.id == startSong.id }
                    .takeIf { it >= 0 } ?: 0
                if (currentPlaybackMode != PlaybackMode.Heartbeat) {
                    currentPlaybackMode = when {
                        engine.snapshot.value.shuffle -> PlaybackMode.Shuffle
                        engine.snapshot.value.repeatMode == RepeatMode.One -> PlaybackMode.SingleCycle
                        else -> PlaybackMode.Sequence
                    }
                }
                queue = songs
                engine.setQueue(playable, engineIndex)
                engine.play()
                mutablePlayer.update {
                    it.copy(
                        song = playable.getOrNull(engineIndex)?.let { p -> songs.firstOrNull { s -> s.id == p.id } },
                        buffering = true,
                        error = null,
                        queue = songs,
                        currentIndex = startIndex,
                        liked = startSong.id in likedSongIds,
                        qualityLabel = settings.quality.displayName(),
                        playbackMode = currentPlaybackMode,
                    )
                }
                mutableMiniPlayer.update {
                    it.copy(song = startSong, buffering = true, progress = 0f)
                }
                rememberPlayed(startSong)
                loadLyrics(startSong)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failPlayback(error.userMessage())
            }
        }
    }

    private fun failPlayback(message: String) {
        mutablePlayer.update { it.copy(buffering = false, playing = false, error = message) }
        mutableMiniPlayer.update { it.copy(buffering = false) }
        notify(message)
    }

    fun togglePlayback() {
        viewModelScope.launch {
            val snapshot = engine.snapshot.value
            if (snapshot.currentSongId == null) {
                if (queue.isNotEmpty()) startPlayback(queue, 0) else notify("先选一首歌吧")
                return@launch
            }
            if (!connectEngine()) {
                notify("播放服务未就绪")
                return@launch
            }
            if (snapshot.playing) engine.pause() else engine.play()
        }
    }

    fun next() {
        engine.next()
    }

    fun previous() {
        engine.previous()
    }

    fun seek(fraction: Float) {
        val duration = mutablePlayer.value.durationMs
        if (duration <= 0) return
        engine.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
    }

    fun toggleShuffle() {
        val enabled = !engine.snapshot.value.shuffle
        engine.setShuffle(enabled)
        currentPlaybackMode = if (enabled) PlaybackMode.Shuffle else PlaybackMode.Sequence
        mutablePlayer.update { it.copy(shuffle = enabled, playbackMode = currentPlaybackMode) }
        notify(if (enabled) "随机播放已开启" else "随机播放已关闭")
    }

    fun cycleRepeatMode() {
        val next = when (engine.snapshot.value.repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        engine.setRepeatMode(next)
        currentPlaybackMode = when (next) {
            RepeatMode.One -> PlaybackMode.SingleCycle
            else -> if (engine.snapshot.value.shuffle) PlaybackMode.Shuffle else PlaybackMode.Sequence
        }
        mutablePlayer.update { it.copy(repeatMode = next, playbackMode = currentPlaybackMode) }
    }

    /**
     * Cycles playback mode in order:
     * 顺序播放(列表循环) -> 心动模式 -> 随机播放 -> 单曲循环
     * Backs up current queue upon entering heartbeat mode, and restores it when leaving heartbeat mode.
     */
    fun cyclePlaybackMode() {
        when (currentPlaybackMode) {
            PlaybackMode.Sequence -> {
                val current = mutablePlayer.value.song
                if (current == null) {
                    currentPlaybackMode = PlaybackMode.Shuffle
                    engine.setShuffle(true)
                    engine.setRepeatMode(RepeatMode.All)
                    mutablePlayer.update {
                        it.copy(playbackMode = PlaybackMode.Shuffle, shuffle = true, repeatMode = RepeatMode.All)
                    }
                    notify("已切换为随机播放")
                    return
                }
                savedQueueBeforeHeartbeat = queue.toList()
                viewModelScope.launch {
                    try {
                        val favoritePid = mutableLibrary.value.playlists.firstOrNull {
                            it.specialType == 5 || it.name.contains("喜欢")
                        }?.id ?: 0L
                        val pid = if (activePlaylistId != null && activePlaylistId!! > 0) activePlaylistId!! else favoritePid
                        val recs = repository.intelligenceList(
                            songId = current.id,
                            playlistId = if (pid > 0) pid else current.id,
                        )
                        if (recs.isNotEmpty()) {
                            val newQueue = if (recs.any { it.id == current.id }) recs else listOf(current) + recs
                            val urls = repository.streamUrls(
                                newQueue.map { it.id },
                                settings.quality,
                                settings.immerseType,
                                settings.unblockEnabled,
                            )
                            val playable = newQueue.mapNotNull { song ->
                                urls[song.id]?.takeIf { it.isNotBlank() }?.let { PlayableSong.from(song, it) }
                            }
                            if (playable.isNotEmpty()) {
                                val engineIndex = playable.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                                queue = newQueue
                                engine.setQueue(playable, engineIndex)
                                engine.setShuffle(false)
                                engine.setRepeatMode(RepeatMode.All)
                                currentPlaybackMode = PlaybackMode.Heartbeat
                                mutablePlayer.update {
                                    it.copy(
                                        playbackMode = PlaybackMode.Heartbeat,
                                        shuffle = false,
                                        repeatMode = RepeatMode.All,
                                        queue = newQueue,
                                    )
                                }
                                notify("已开启心动模式")
                                return@launch
                            }
                        }
                        notify("心动模式推荐失败，请稍后重试")
                    } catch (e: Exception) {
                        notify("开启心动模式失败：${e.userMessage()}")
                    }
                }
            }
            PlaybackMode.Heartbeat -> {
                val saved = savedQueueBeforeHeartbeat
                savedQueueBeforeHeartbeat = null
                if (saved != null && saved.isNotEmpty()) {
                    queue = saved
                    val current = mutablePlayer.value.song
                    viewModelScope.launch {
                        val urls = repository.streamUrls(
                            saved.map { it.id },
                            settings.quality,
                            settings.immerseType,
                            settings.unblockEnabled,
                        )
                        val playable = saved.mapNotNull { song ->
                            urls[song.id]?.takeIf { it.isNotBlank() }?.let { PlayableSong.from(song, it) }
                        }
                        if (playable.isNotEmpty()) {
                            val idx = current?.let { c -> playable.indexOfFirst { it.id == c.id } }
                                ?.takeIf { it >= 0 } ?: 0
                            engine.setQueue(playable, idx)
                        }
                    }
                }
                currentPlaybackMode = PlaybackMode.Shuffle
                engine.setShuffle(true)
                engine.setRepeatMode(RepeatMode.All)
                mutablePlayer.update {
                    it.copy(
                        playbackMode = PlaybackMode.Shuffle,
                        shuffle = true,
                        repeatMode = RepeatMode.All,
                        queue = queue,
                    )
                }
                notify("已切换为随机播放")
            }
            PlaybackMode.Shuffle -> {
                currentPlaybackMode = PlaybackMode.SingleCycle
                engine.setShuffle(false)
                engine.setRepeatMode(RepeatMode.One)
                mutablePlayer.update {
                    it.copy(
                        playbackMode = PlaybackMode.SingleCycle,
                        shuffle = false,
                        repeatMode = RepeatMode.One,
                    )
                }
                notify("已切换为单曲循环")
            }
            PlaybackMode.SingleCycle -> {
                currentPlaybackMode = PlaybackMode.Sequence
                engine.setShuffle(false)
                engine.setRepeatMode(RepeatMode.All)
                mutablePlayer.update {
                    it.copy(
                        playbackMode = PlaybackMode.Sequence,
                        shuffle = false,
                        repeatMode = RepeatMode.All,
                    )
                }
                notify("已切换为列表循环")
            }
        }
    }

    /**
     * Starts heartbeat mode seeded from a specific playlist (e.g. "我喜欢的音乐").
     */
    fun startHeartbeatMode(playlist: dev.nocturn.app.data.model.PlaylistSummary? = null) {
        viewModelScope.launch {
            try {
                val targetPlaylist = playlist ?: mutableLibrary.value.playlists.firstOrNull {
                    it.specialType == 5 || it.name.contains("喜欢")
                }
                if (targetPlaylist == null) {
                    notify("未找到红心歌单，无法开启心动模式")
                    return@launch
                }
                savedQueueBeforeHeartbeat = queue.toList()
                activePlaylistId = targetPlaylist.id

                val songs = repository.playlistSongs(targetPlaylist.id, 0).songs
                if (songs.isEmpty()) {
                    notify("歌单为空，无法开启心动模式")
                    return@launch
                }
                val current = mutablePlayer.value.song
                val seedSong = if (current != null && songs.any { it.id == current.id }) current else songs.first()

                val recs = repository.intelligenceList(songId = seedSong.id, playlistId = targetPlaylist.id)
                val fullList = if (recs.isNotEmpty()) {
                    if (recs.any { it.id == seedSong.id }) recs else listOf(seedSong) + recs
                } else {
                    songs
                }
                playSongs(fullList, 0)
                currentPlaybackMode = PlaybackMode.Heartbeat
                engine.setShuffle(false)
                engine.setRepeatMode(RepeatMode.All)
                mutablePlayer.update {
                    it.copy(
                        playbackMode = PlaybackMode.Heartbeat,
                        shuffle = false,
                        repeatMode = RepeatMode.All,
                    )
                }
                notify("已开启心动模式")
            } catch (e: Exception) {
                notify("开启心动模式失败：${e.userMessage()}")
            }
        }
    }

    fun toggleLike(song: Song) {
        if (!repository.isLoggedIn()) {
            notify("登录后即可收藏歌曲")
            return
        }
        viewModelScope.launch {
            val target = song.id !in likedSongIds
            try {
                val liked = repository.setLiked(song.id, target)
                if (liked) {
                    val reordered = linkedSetOf(song.id).apply { addAll(likedSongIds) }
                    likedSongIds.clear()
                    likedSongIds.addAll(reordered)
                    mutableLikedSongs.update { current ->
                        if (current.songs.isNotEmpty() && current.songs.none { it.id == song.id }) {
                            current.copy(songs = listOf(song) + current.songs)
                        } else {
                            current
                        }
                    }
                } else {
                    likedSongIds.remove(song.id)
                }
                applyLikedState()
                mutableLibrary.update { it.copy(likedCount = likedSongIds.size) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notify("收藏失败：${error.userMessage()}")
            }
        }
    }

    fun toggleLikeCurrent() {
        mutablePlayer.value.song?.let { toggleLike(it) }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            engine.snapshot.collect { snapshot -> applyPlaybackSnapshot(snapshot) }
        }
    }

    private fun applyPlaybackSnapshot(snapshot: PlaybackSnapshot) {
        val song = snapshot.currentSongId?.let { id -> queue.firstOrNull { it.id == id } }
            ?: queue.getOrNull(snapshot.currentIndex)
        val duration = snapshot.durationMs.takeIf { it > 0 } ?: song?.durationMs ?: 0
        val previousSong = mutablePlayer.value.song
        val previousPlaying = mutablePlayer.value.playing
        val songChanged = song != null && song.id != previousSong?.id
        val playingChanged = snapshot.playing != previousPlaying
        mutablePlayer.update {
            it.copy(
                song = song ?: it.song,
                playing = snapshot.playing,
                buffering = snapshot.buffering,
                positionMs = snapshot.positionMs,
                positionSampledAtNanos = snapshot.positionSampledAtNanos,
                positionDiscontinuity = snapshot.positionDiscontinuity,
                playbackSpeed = snapshot.playbackSpeed,
                durationMs = duration,
                shuffle = snapshot.shuffle,
                repeatMode = snapshot.repeatMode,
                playbackMode = when {
                    currentPlaybackMode == PlaybackMode.Heartbeat -> PlaybackMode.Heartbeat
                    snapshot.shuffle -> PlaybackMode.Shuffle
                    snapshot.repeatMode == RepeatMode.One -> PlaybackMode.SingleCycle
                    else -> PlaybackMode.Sequence
                },
                queue = queue,
                currentIndex = queue.indexOfFirst { item -> item.id == song?.id }
                    .takeIf { index -> index >= 0 } ?: it.currentIndex,
                liked = song?.id?.let { id -> id in likedSongIds } ?: it.liked,
                qualityLabel = settings.quality.displayName(),
                error = snapshot.error,
            )
        }
        mutableMiniPlayer.update {
            it.copy(
                song = song ?: it.song,
                playing = snapshot.playing,
                buffering = snapshot.buffering,
                progress = if (duration > 0) {
                    (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                } else {
                    0f
                },
            )
        }
        applyNowPlaying(song?.id)
        updatePlaybackTracking(song, snapshot, songChanged, playingChanged)
        if (song != null && (songChanged || currentLyricsSongId != song.id)) {
            rememberPlayed(song)
            loadLyrics(song)
        } else if (song == null) {
            currentLyricsSongId = null
        }
    }

    private fun applyNowPlaying(songId: Long?) {
        mutableHome.update { it.copy(nowPlayingSongId = songId) }
        mutableSearch.update { it.copy(nowPlayingSongId = songId) }
        playlistStates.updateAll { it.copy(nowPlayingSongId = songId) }
        albumStates.updateAll { it.copy(nowPlayingSongId = songId) }
        artistStates.updateAll { it.copy(nowPlayingSongId = songId) }
        mutableLikedSongs.update { it.copy(nowPlayingSongId = songId) }
        mutableLibrary.update { it.copy(nowPlayingSongId = songId) }
    }

    private fun updatePlaybackTracking(
        currentSong: Song?,
        snapshot: PlaybackSnapshot,
        songChanged: Boolean,
        playingChanged: Boolean,
    ) {
        val now = System.nanoTime()
        val currentTracked = trackedSong

        if (songChanged || (currentSong == null && currentTracked != null)) {
            if (currentTracked != null && !hasScrobbledCurrent && settings.scrobbleEnabled) {
                val playedSec = (trackedPlayedMs / 1000).toInt()
                val totalSec = ((currentTracked.durationMs) / 1000).toInt()
                val meets30s = playedSec >= 30
                val meets50pct = totalSec > 0 && playedSec >= (totalSec / 2)
                if (meets30s || meets50pct) {
                    hasScrobbledCurrent = true
                    triggerScrobble(currentTracked, playedSec, totalSec)
                }
            }
            trackedSong = currentSong
            trackedPlayedMs = 0L
            hasScrobbledCurrent = false
            lastSnapshotNanos = now
            wasPlaying = snapshot.playing

            if (currentSong != null && settings.relayPlayStateEnabled) {
                triggerPlayStateSubmit(currentSong.id, (snapshot.positionMs / 1000).toInt())
            }
        } else if (currentSong != null) {
            if (wasPlaying && !snapshot.buffering && lastSnapshotNanos > 0L) {
                val deltaMs = (now - lastSnapshotNanos) / 1_000_000L
                if (deltaMs in 1..5000) {
                    trackedPlayedMs += deltaMs
                }
            }
            lastSnapshotNanos = now
            wasPlaying = snapshot.playing

            if (playingChanged && settings.relayPlayStateEnabled) {
                triggerPlayStateSubmit(currentSong.id, (snapshot.positionMs / 1000).toInt())
            }
        }
    }

    private fun triggerScrobble(song: Song, playedSec: Int, totalSec: Int) {
        val quality = settings.quality
        viewModelScope.launch {
            try {
                repository.scrobble(
                    songId = song.id,
                    timeSeconds = playedSec,
                    totalSeconds = totalSec.takeIf { it > 0 },
                    sourceId = null,
                    source = "list",
                    songName = song.name,
                    artistName = song.artists.joinToString("/") { it.name },
                    level = quality,
                )
            } catch (_: Exception) {
                // Background telemetry; best effort
            }
        }
    }

    private fun triggerPlayStateSubmit(songId: Long, progressSec: Int) {
        val mode = when {
            mutablePlayer.value.repeatMode == RepeatMode.One -> "single_cycle"
            mutablePlayer.value.shuffle -> "random"
            else -> "list_loop"
        }
        val sessionId = currentSessionId
        viewModelScope.launch {
            try {
                repository.submitPlayState(
                    songId = songId,
                    sessionId = sessionId,
                    progressSeconds = progressSec,
                    playMode = mode,
                )
            } catch (_: Exception) {
                // Background telemetry; best effort
            }
        }
    }

    private fun generatePlaybackSessionId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }

    private suspend fun connectEngine(): Boolean {
        if (engine.snapshot.value.currentSongId != null) return true
        return runCatching { engine.connect() }.isSuccess
    }

    private fun loadLyrics(song: Song) {
        currentLyricsSongId = song.id
        lyricsRequest?.cancel()
        mutablePlayer.update { it.copy(lyrics = Lyrics.EMPTY, lyricsLoading = true) }
        lyricsRequest = viewModelScope.launch {
            try {
                val lyrics = repository.lyrics(song.id)
                if (mutablePlayer.value.song?.id == song.id) {
                    mutablePlayer.update { it.copy(lyrics = lyrics, lyricsLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (mutablePlayer.value.song?.id == song.id) {
                    mutablePlayer.update { it.copy(lyrics = Lyrics.EMPTY, lyricsLoading = false) }
                }
            }
        }
    }

    private fun rememberPlayed(song: Song) {
        val recent = (listOf(song) + mutableLibrary.value.recentSongs.filterNot { it.id == song.id })
            .take(20)
        if (mutableLibrary.value.loggedIn) {
            mutableLibrary.update { it.copy(recentSongs = recent) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------

    private fun applyLikedState() {
        val snapshot = likedSongIds.toSet()
        mutableHome.update { it.copy(likedSongIds = snapshot) }
        mutableSearch.update { it.copy(likedSongIds = snapshot) }
        playlistStates.updateAll { it.copy(likedSongIds = snapshot) }
        albumStates.updateAll { it.copy(likedSongIds = snapshot) }
        artistStates.updateAll { it.copy(likedSongIds = snapshot) }
        mutableLikedSongs.update { it.copy(likedSongIds = snapshot) }
        mutableLibrary.update { it.copy(likedSongIds = snapshot) }
        mutablePlayer.update {
            it.copy(liked = it.song?.id?.let { id -> id in snapshot } ?: it.liked)
        }
    }

    private fun notify(message: String) {
        mutableMessages.tryEmit(message)
    }

    // ---------------------------------------------------------------------------------------
    // Free Listen
    // ---------------------------------------------------------------------------------------

    fun openFreeListenDialog() {
        mutableShowFreeListenDialog.value = true
        refreshFreeListenRights()
    }

    fun closeFreeListenDialog() {
        mutableShowFreeListenDialog.value = false
    }

    fun setAutoClaimFreeListen(enabled: Boolean) {
        settings.autoClaimFreeListen = enabled
        mutableAutoClaimFreeListen.value = enabled
        if (enabled) {
            viewModelScope.launch {
                checkAndAutoClaimFreeListen(isStartup = true)
            }
        }
    }

    fun refreshFreeListenRights() {
        viewModelScope.launch {
            mutableIsFreeListenLoading.value = true
            try {
                val rights = repository.getFreeListenRights()
                mutableFreeListenRights.value = rights
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Ignore transient lookup failures
            } finally {
                mutableIsFreeListenLoading.value = false
            }
        }
    }

    fun claimFreeListen() {
        if (mutableIsFreeListenLoading.value) return
        viewModelScope.launch {
            mutableIsFreeListenLoading.value = true
            try {
                val result = repository.claimFreeListen()
                if (result.success) {
                    val updatedRights = runCatching { repository.getFreeListenRights() }.getOrNull()
                    if (updatedRights != null) {
                        mutableFreeListenRights.value = updatedRights
                    }
                    notify(result.message.ifBlank { "免费听时长领取成功！" })
                } else {
                    notify(result.message.ifBlank { "领取失败，请稍后重试" })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: MusicRepositoryException) {
                notify(error.message ?: "领取失败")
                if (!repository.isLoggedIn()) {
                    closeFreeListenDialog()
                    startLogin()
                }
            } catch (error: Exception) {
                notify(error.message ?: "领取失败，请稍后重试")
            } finally {
                mutableIsFreeListenLoading.value = false
            }
        }
    }

    private fun startFreeListenMonitor() {
        freeListenMonitorJob?.cancel()
        freeListenMonitorJob = viewModelScope.launch {
            delay(2000)
            checkAndAutoClaimFreeListen(isStartup = true)
            while (true) {
                delay(5 * 60 * 1000L)
                checkAndAutoClaimFreeListen(isStartup = false)
            }
        }
    }

    private suspend fun checkAndAutoClaimFreeListen(isStartup: Boolean) {
        if (!settings.autoClaimFreeListen) return
        val isVip = (repository.session.value?.vipLevel ?: 0) > 0
        if (isVip) return

        try {
            val rights = repository.getFreeListenRights()
            mutableFreeListenRights.value = rights
            val remainingMs = rights.remainingMs
            val shouldClaim = isStartup || remainingMs <= 15 * 60 * 1000L
            if (shouldClaim && !rights.upperLimit) {
                val result = repository.claimFreeListen()
                if (result.success) {
                    val updated = repository.getFreeListenRights()
                    mutableFreeListenRights.value = updated
                    notify("自动领取成功：${result.message}")
                } else if (!isStartup) {
                    notify("自动领取失败：${result.message}")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!isStartup && settings.autoClaimFreeListen) {
                notify("自动领取异常：${error.message ?: "请检查网络"}")
            }
        }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is MusicRepositoryException -> message ?: "音乐服务暂时不可用，请稍后重试。"
        else -> "网络似乎开小差了，请检查网络后重试。"
    }

    override fun onCleared() {
        super.onCleared()
        // The container is process-scoped: closing the repository or releasing the media session
        // here would break recreated activities, so ownership stays with NocturnApplication.
    }
}
