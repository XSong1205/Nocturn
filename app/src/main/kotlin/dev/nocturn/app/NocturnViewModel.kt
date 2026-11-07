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
import dev.nocturn.app.data.model.HomeFeed
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
import dev.nocturn.app.ui.state.PlayerUiState
import dev.nocturn.app.ui.state.PlaylistUiState
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.state.SearchTab
import dev.nocturn.app.ui.state.SearchUiState
import dev.nocturn.app.ui.util.greetingForHour
import dev.nocturn.localapi.client.AudioQuality
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
            dynamicColor = settings.dynamicColor,
            quality = settings.quality,
            hapticsEnabled = settings.hapticsEnabled,
        ),
    )
    val library: StateFlow<LibraryUiState> = mutableLibrary.asStateFlow()

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

    private var queue: List<Song> = emptyList()
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

    init {
        observePlayback()
        loadHome()
        refreshLibrary()
        loadSearchLanding()
        viewModelScope.launch {
            repository.session.collect { profile ->
                if (profile != null) {
                    mutableLibrary.update { it.copy(loggedIn = true, profile = profile) }
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
                    mutableHome.update { it.copy(isLoggedIn = false, dailySongs = emptyList()) }
                    applyLikedState()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------------------

    fun selectTab(tab: AppTab) {
        mutableNavigation.update { it.copy(tab = tab, stack = emptyList()) }
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
        mutableNavigation.update { it.copy(stack = it.stack + Screen.Playlist(playlistId)) }
        pruneDetailStates()
        if (playlistStates[playlistId]?.detail == null) loadPlaylist(playlistId)
    }

    fun openAlbum(albumId: Long) {
        mutableNavigation.update { it.copy(stack = it.stack + Screen.Album(albumId)) }
        pruneDetailStates()
        if (albumStates[albumId]?.detail == null) loadAlbum(albumId)
    }

    fun openArtist(artistId: Long) {
        mutableNavigation.update { it.copy(stack = it.stack + Screen.Artist(artistId)) }
        pruneDetailStates()
        if (artistStates[artistId]?.detail == null) loadArtist(artistId)
    }

    /**
     * Drops detail state for pages that are no longer on the stack. Called on pushes and tab
     * switches rather than on back, so a page that is animating out still has its content.
     */
    private fun pruneDetailStates() {
        val stack = mutableNavigation.value.stack
        playlistStates.retain(stack.filterIsInstance<Screen.Playlist>().map { it.id }.toSet())
        albumStates.retain(stack.filterIsInstance<Screen.Album>().map { it.id }.toSet())
        artistStates.retain(stack.filterIsInstance<Screen.Artist>().map { it.id }.toSet())
    }

    fun openRecentSongs() {
        mutableNavigation.update { it.copy(stack = it.stack + Screen.RecentSongs) }
    }

    fun openLikedSongs() {
        mutableNavigation.update { it.copy(stack = it.stack + Screen.LikedSongs) }
        loadLikedSongs()
    }

    /** Daily recommendations are part of the home feed, so their page needs no extra request. */
    fun openDailySongs() {
        mutableNavigation.update { it.copy(stack = it.stack + Screen.DailySongs) }
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
                isLoggedIn = feed.isLoggedIn,
                likedSongIds = likedSongIds.toSet(),
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------

    private fun loadSearchLanding() {
        viewModelScope.launch {
            mutableSearch.update { it.copy(history = settings.searchHistory()) }
            val hot = runCatching { repository.hotSearches() }.getOrDefault(emptyList())
            val default = runCatching { repository.defaultKeyword() }.getOrNull()
            mutableSearch.update {
                it.copy(hotKeywords = hot, defaultKeyword = default, history = settings.searchHistory())
            }
        }
    }

    fun updateSearchQuery(query: String) {
        mutableSearch.update { it.copy(query = query.take(80)) }
        suggestionRequest?.cancel()
        if (query.isBlank()) {
            mutableSearch.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionRequest = viewModelScope.launch {
            delay(250)
            runCatching { repository.searchSuggestions(query.trim()) }
                .onSuccess { suggestions ->
                    mutableSearch.update { it.copy(suggestions = suggestions.take(8)) }
                }
        }
    }

    fun expandSearch() {
        mutableSearch.update { it.copy(expanded = true) }
    }

    fun collapseSearch() {
        mutableSearch.update { it.copy(expanded = false) }
    }

    fun submitSearch(keyword: String = mutableSearch.value.query) {
        val value = keyword.trim()
        if (value.isEmpty()) return
        settings.recordSearch(value)
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
                if (mutableHome.value.dailySongs.isEmpty()) loadHome()
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
                loadHome()
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
                    loadHome()
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
            mutableHome.update { it.copy(isLoggedIn = false) }
            applyLikedState()
            notify("已退出登录")
        }
    }

    fun setQuality(quality: AudioQuality) {
        settings.quality = quality
        mutableLibrary.update { it.copy(quality = quality) }
        mutablePlayer.update { it.copy(qualityLabel = quality.displayName()) }
        notify("音质已切换为${quality.displayName()}")
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        mutableLibrary.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        settings.dynamicColor = enabled
        mutableLibrary.update { it.copy(dynamicColor = enabled) }
    }

    /** Turns the app-wide tactile layer on or off and persists the choice. */
    fun setHapticsEnabled(enabled: Boolean) {
        settings.hapticsEnabled = enabled
        mutableLibrary.update { it.copy(hapticsEnabled = enabled) }
    }

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
                val urls = repository.streamUrls(songs.map { it.id }, settings.quality)
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
        mutablePlayer.update { it.copy(shuffle = enabled) }
        notify(if (enabled) "随机播放已开启" else "随机播放已关闭")
    }

    fun cycleRepeatMode() {
        val next = when (engine.snapshot.value.repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        engine.setRepeatMode(next)
        mutablePlayer.update { it.copy(repeatMode = next) }
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
                if (liked) likedSongIds.add(song.id) else likedSongIds.remove(song.id)
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
        val songChanged = song != null && song.id != previousSong?.id
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
