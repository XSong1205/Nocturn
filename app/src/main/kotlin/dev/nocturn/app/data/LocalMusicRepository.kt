package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.MusicRepositoryException.Kind
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
import dev.nocturn.app.data.model.QrLoginState
import dev.nocturn.app.data.model.QrPollResult
import dev.nocturn.app.data.model.SearchAlbumsPage
import dev.nocturn.app.data.model.SearchArtistsPage
import dev.nocturn.app.data.model.SearchPlaylistsPage
import dev.nocturn.app.data.model.SearchSongsPage
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.Toplist
import dev.nocturn.localapi.client.AudioQuality
import dev.nocturn.localapi.client.LocalApiClient
import dev.nocturn.localapi.client.LocalApiEndpoint
import dev.nocturn.localapi.client.LocalApiException
import dev.nocturn.localapi.client.createLocalApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The production [MusicRepository]: the embedded LocalAPI (Node runtime + Ktor client) is started
 * lazily on first use, every response is normalised by [NeteaseJson] and the account cookie is
 * owned by [SessionStore] (encrypted at rest, injected per request, never exposed to the UI).
 *
 * Node can take tens of seconds to start on the first call; [api] shields callers behind one
 * mutex and maps startup failures to a Chinese [MusicRepositoryException]. All work runs on the
 * IO dispatcher because callers invoke the suspending API from the main thread.
 */
class LocalMusicRepository(private val context: Context) : MusicRepository {
    private val applicationContext = context.applicationContext
    private val sessionStore = SessionStore(applicationContext)
    private val authenticator = AccountAuthenticator(::api, sessionStore::save)
    private val clientLock = Mutex()

    @Volatile
    private var client: LocalApiClient? = null

    override val session: StateFlow<AccountProfile?> = sessionStore.session

    // ---------------------------------------------------------------------------------------
    // Discovery
    // ---------------------------------------------------------------------------------------

    override suspend fun homeFeed(): HomeFeed = withContext(Dispatchers.IO) {
        guarded {
            val cookie = cookieOrNull()
            val sessionActive = session.value != null
            val loggedIn = cookie != null && sessionActive
            coroutineScope {
                val playlistJob = async { attempt { fetchRecommendedPlaylists(cookie, loggedIn) } }
                val chartJob = async { attempt { fetchToplists() } }
                val dailyJob = if (cookie != null && sessionActive) {
                    async { attempt { fetchDailySongs(cookie) } }
                } else {
                    null
                }
                val songJob = async {
                    attempt {
                        val dailyResult = dailyJob?.await()?.getOrNull()
                        fetchPersonalizedSongs(cookie, loggedIn, dailyResult)
                    }
                }
                val playlists = playlistJob.await()
                val charts = chartJob.await()
                val daily = dailyJob?.await()
                val songs = songJob.await()
                // Degrade block by block, but never show an empty home page as "success".
                val attempted = listOfNotNull(
                    playlists.isSuccess,
                    charts.isSuccess,
                    songs.isSuccess,
                    daily?.isSuccess,
                )
                if (attempted.isNotEmpty() && attempted.all { !it }) {
                    throw listOfNotNull(
                        playlists.exceptionOrNull(),
                        charts.exceptionOrNull(),
                        songs.exceptionOrNull(),
                        daily?.exceptionOrNull(),
                    ).first()
                }
                HomeFeed(
                    playlists = playlists.getOrElse { emptyList() },
                    charts = charts.getOrElse { emptyList() },
                    dailySongs = daily?.getOrElse { emptyList() } ?: emptyList(),
                    personalizedSongs = songs.getOrElse { emptyList() },
                    isLoggedIn = loggedIn,
                )
            }
        }
    }

    /**
     * "为你推荐" playlists.
     *
     * `/personalized` is a public shelf: it ignores the account, so a signed-in listener would get
     * the same generic list as an anonymous one. The account-aware `/recommend/resource` ("每日推荐
     * 歌单") is used whenever a session cookie is available, and the public shelf — now also sent the
     * cookie where upstream honours it — stays as the fallback for anonymous users and empty days.
     */
    private suspend fun fetchRecommendedPlaylists(
        cookie: String?,
        loggedIn: Boolean,
    ): List<PlaylistSummary> {
        if (loggedIn && cookie != null) {
            val accountPlaylists = try {
                NeteaseJson.parsePersonalizedPlaylists(
                    requestOk(
                        LocalApiEndpoint.RecommendResource,
                        buildJsonObject { put("cookie", cookie) },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (accountPlaylists.isNotEmpty()) {
                return accountPlaylists.distinctBy { it.id }.take(RECOMMENDED_PLAYLIST_LIMIT)
            }
        }
        return NeteaseJson.parsePersonalizedPlaylists(
            requestOk(
                LocalApiEndpoint.Personalized,
                buildJsonObject {
                    put("limit", RECOMMENDED_PLAYLIST_LIMIT)
                    cookie?.let { put("cookie", it) }
                },
            ),
        )
    }

    private suspend fun fetchToplists(): List<Toplist> =
        NeteaseJson.parseToplists(requestOk(LocalApiEndpoint.Toplist))

    /**
     * "根据你喜爱的歌曲推荐" / "新歌推荐" songs.
     *
     * `/personalized/newsong` is a public shelf that returns static editorial new songs ("hot_server")
     * whether signed in or not. For signed-in listeners, the account's daily personalized recommendations
     * (`recommend/songs`) are used so the grid actually reflects the user's taste; `/personalized/newsong`
     * remains the fallback for anonymous listeners or when account recommendations are unavailable.
     */
    private suspend fun fetchPersonalizedSongs(
        cookie: String?,
        loggedIn: Boolean = false,
        dailyAccountSongs: List<Song>? = null,
    ): List<Song> {
        if (loggedIn && cookie != null) {
            if (!dailyAccountSongs.isNullOrEmpty()) {
                return dailyAccountSongs
            }
            val accountSongs = try {
                fetchDailySongs(cookie)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (accountSongs.isNotEmpty()) return accountSongs
        }
        return NeteaseJson.parsePersonalizedSongs(
            requestOk(
                LocalApiEndpoint.PersonalizedNewSongs,
                buildJsonObject {
                    put("limit", PERSONALIZED_SONG_LIMIT)
                    cookie?.let { put("cookie", it) }
                },
            ),
        )
    }

    private suspend fun fetchDailySongs(cookie: String): List<Song> =
        NeteaseJson.parseRecommendSongs(
            requestOk(
                LocalApiEndpoint.RecommendSongs,
                buildJsonObject {
                    put("afresh", false)
                    put("cookie", cookie)
                },
            ),
        )

    // ---------------------------------------------------------------------------------------
    // Playlists
    // ---------------------------------------------------------------------------------------

    override suspend fun playlistDetail(id: Long): PlaylistDetail = withContext(Dispatchers.IO) {
        guarded {
            require(id > 0) { "A positive playlist id is required." }
            val cookie = cookieOrNull()
            val body = requestOk(
                LocalApiEndpoint.PlaylistDetail,
                buildJsonObject {
                    put("id", id.toString())
                    cookie?.let { put("cookie", it) }
                },
            )
            NeteaseJson.parsePlaylistDetail(body, session.value?.id)
                ?: throw MusicRepositoryException(Kind.Empty, "没有找到该歌单。")
        }
    }

    /**
     * Pages through the complete `trackIds` list: the detail response's `tracks` preview is
     * truncated, so only the id window is used to request exactly one page of song details.
     */
    override suspend fun playlistSongs(
        id: Long,
        offset: Int,
        limit: Int,
    ): PlaylistSongsPage = withContext(Dispatchers.IO) {
        guarded {
            require(id > 0) { "A positive playlist id is required." }
            val safeOffset = offset.coerceIn(0, MAX_OFFSET)
            val safeLimit = limit.coerceIn(1, MAX_BATCH)
            val cookie = cookieOrNull()
            val detail = requestOk(
                LocalApiEndpoint.PlaylistDetail,
                buildJsonObject {
                    put("id", id.toString())
                    cookie?.let { put("cookie", it) }
                },
            )
            val trackIds = NeteaseJson.parsePlaylistTrackIds(detail)
            val window = trackIds.drop(safeOffset).take(safeLimit)
            if (window.isEmpty()) {
                NeteaseJson.playlistSongsPage(trackIds, emptyList(), safeOffset, safeLimit)
            } else {
                val songsBody = requestOk(
                    LocalApiEndpoint.SongDetail,
                    buildJsonObject {
                        put("ids", window.joinToString(","))
                        cookie?.let { put("cookie", it) }
                    },
                )
                NeteaseJson.playlistSongsPage(
                    trackIds,
                    NeteaseJson.parseSongs(songsBody.array("songs")),
                    safeOffset,
                    safeLimit,
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------

    override suspend fun searchSongs(
        keyword: String,
        offset: Int,
        limit: Int,
    ): SearchSongsPage = withContext(Dispatchers.IO) {
        guarded {
            val body = searchBody(SEARCH_TYPE_SONG, keyword, offset, limit)
            NeteaseJson.parseSearchSongsPage(body, offset.coerceAtLeast(0))
        }
    }

    override suspend fun searchPlaylists(
        keyword: String,
        offset: Int,
        limit: Int,
    ): SearchPlaylistsPage = withContext(Dispatchers.IO) {
        guarded {
            val body = searchBody(SEARCH_TYPE_PLAYLIST, keyword, offset, limit)
            NeteaseJson.parseSearchPlaylistsPage(body, offset.coerceAtLeast(0))
        }
    }

    override suspend fun searchAlbums(
        keyword: String,
        offset: Int,
        limit: Int,
    ): SearchAlbumsPage = withContext(Dispatchers.IO) {
        guarded {
            val body = searchBody(SEARCH_TYPE_ALBUM, keyword, offset, limit)
            NeteaseJson.parseSearchAlbumsPage(body, offset.coerceAtLeast(0))
        }
    }

    override suspend fun searchArtists(
        keyword: String,
        offset: Int,
        limit: Int,
    ): SearchArtistsPage = withContext(Dispatchers.IO) {
        guarded {
            val body = searchBody(SEARCH_TYPE_ARTIST, keyword, offset, limit)
            NeteaseJson.parseSearchArtistsPage(body, offset.coerceAtLeast(0))
        }
    }

    /**
     * Cloud search is preferred for richer results. A transport-level failure falls back to the
     * legacy search route once; the same [NeteaseJson] parsers accept both payload shapes.
     */
    private suspend fun searchBody(
        type: Int,
        keyword: String,
        offset: Int,
        limit: Int,
    ): JsonObject {
        val value = keyword.trim()
        require(value.isNotEmpty() && value.length <= MAX_KEYWORD) {
            "Search keywords must contain 1 to 200 characters."
        }
        val cookie = cookieOrNull()
        val parameters = buildJsonObject {
            put("keywords", value)
            put("type", type)
            put("limit", limit.coerceIn(1, MAX_BATCH))
            put("offset", offset.coerceIn(0, MAX_OFFSET))
            cookie?.let { put("cookie", it) }
        }
        return try {
            requestOk(LocalApiEndpoint.CloudSearch, parameters)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LocalApiException) {
            requestOk(LocalApiEndpoint.Search, parameters)
        }
    }

    override suspend fun searchSuggestions(keyword: String): List<String> =
        withContext(Dispatchers.IO) {
            guarded {
                val value = keyword.trim().take(MAX_KEYWORD)
                if (value.isEmpty()) {
                    emptyList()
                } else {
                    val cookie = cookieOrNull()
                    NeteaseJson.parseSearchSuggestions(
                        requestOk(
                            LocalApiEndpoint.SearchSuggest,
                            buildJsonObject {
                                put("keywords", value)
                                put("type", "mobile")
                                cookie?.let { put("cookie", it) }
                            },
                        ),
                    )
                }
            }
        }

    override suspend fun hotSearches(): List<String> = withContext(Dispatchers.IO) {
        guarded {
            val cookie = cookieOrNull()
            val parameters = buildJsonObject {
                cookie?.let { put("cookie", it) }
            }
            NeteaseJson.parseHotSearches(requestOk(LocalApiEndpoint.SearchHotDetail, parameters))
        }
    }

    override suspend fun defaultKeyword(): String? = withContext(Dispatchers.IO) {
        guarded {
            val cookie = cookieOrNull()
            val parameters = buildJsonObject {
                cookie?.let { put("cookie", it) }
            }
            NeteaseJson.parseDefaultKeyword(requestOk(LocalApiEndpoint.SearchDefault, parameters))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Album, artist and lyrics
    // ---------------------------------------------------------------------------------------

    override suspend fun albumDetail(id: Long): AlbumDetail = withContext(Dispatchers.IO) {
        guarded {
            require(id > 0) { "A positive album id is required." }
            val cookie = cookieOrNull()
            val body = requestOk(
                LocalApiEndpoint.Album,
                buildJsonObject {
                    put("id", id.toString())
                    cookie?.let { put("cookie", it) }
                },
            )
            NeteaseJson.parseAlbumDetail(body)
                ?: throw MusicRepositoryException(Kind.Empty, "没有找到该专辑。")
        }
    }

    /**
     * Identity/hot songs come from `/artists`; `/artist/detail` and `/artist/album` only enrich
     * the response, so a failure in either enrichment call leaves a usable detail page.
     */
    override suspend fun artistDetail(id: Long): ArtistDetail = withContext(Dispatchers.IO) {
        guarded {
            require(id > 0) { "A positive artist id is required." }
            val cookie = cookieOrNull()
            val identityParameters = buildJsonObject {
                put("id", id.toString())
                cookie?.let { put("cookie", it) }
            }
            coroutineScope {
                val artistJob = async { attempt { requestOk(LocalApiEndpoint.Artists, identityParameters) } }
                val detailJob = async { attempt { requestOk(LocalApiEndpoint.ArtistDetail, identityParameters) } }
                val albumsJob = async {
                    attempt {
                        requestOk(
                            LocalApiEndpoint.ArtistAlbum,
                            buildJsonObject {
                                put("id", id.toString())
                                put("limit", ARTIST_ALBUM_LIMIT)
                                put("offset", 0)
                                cookie?.let { put("cookie", it) }
                            },
                        )
                    }
                }
                val artist = artistJob.await()
                val detail = detailJob.await()
                val albums = albumsJob.await()
                if (artist.isFailure && detail.isFailure) {
                    throw (artist.exceptionOrNull() ?: detail.exceptionOrNull()!!)
                }
                NeteaseJson.parseArtistDetail(
                    artist.getOrNull(),
                    detail.getOrNull(),
                    albums.getOrNull(),
                ) ?: throw MusicRepositoryException(Kind.Empty, "没有找到该歌手。")
            }
        }
    }

    /** YRC word lyrics are preferred; a track without word timing keeps its ordinary LRC. */
    override suspend fun lyrics(songId: Long): Lyrics = withContext(Dispatchers.IO) {
        guarded {
            require(songId > 0) { "A positive song id is required." }
            val cookie = cookieOrNull()
            val parameters = buildJsonObject {
                put("id", songId.toString())
                cookie?.let { put("cookie", it) }
            }
            val primary = attempt {
                NeteaseJson.parseLyrics(requestOk(LocalApiEndpoint.LyricNew, parameters))
            }
            val primaryLyrics = primary.getOrNull()
            if (primaryLyrics != null && !primaryLyrics.isEmpty) {
                primaryLyrics
            } else {
                val fallback = attempt {
                    NeteaseJson.parseLyrics(requestOk(LocalApiEndpoint.Lyric, parameters))
                }
                val fallbackLyrics = fallback.getOrNull()
                when {
                    fallbackLyrics != null -> fallbackLyrics
                    primaryLyrics != null -> primaryLyrics
                    else -> throw (primary.exceptionOrNull() ?: fallback.exceptionOrNull()!!)
                }
            }
        }
    }

    /** Batch playable URLs; ids are sent in bounded comma-separated groups of 200. */
    override suspend fun streamUrls(
        ids: List<Long>,
        quality: AudioQuality,
    ): Map<Long, String?> = withContext(Dispatchers.IO) {
        guarded {
            val requested = ids.filter { it > 0 }.distinct()
            if (requested.isEmpty()) return@guarded emptyMap()
            val cookie = cookieOrNull()
            val resolved = LinkedHashMap<Long, String?>()
            for (chunk in requested.chunked(MAX_BATCH)) {
                val body = requestOk(
                    LocalApiEndpoint.SongUrl,
                    buildJsonObject {
                        put("id", chunk.joinToString(","))
                        put("level", quality.value)
                        cookie?.let { put("cookie", it) }
                    },
                )
                resolved.putAll(NeteaseJson.parseStreamUrls(body, chunk))
            }
            requested.associateWith { resolved[it] }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Account and session
    // ---------------------------------------------------------------------------------------

    override suspend fun createQrChallenge(): QrChallenge = withContext(Dispatchers.IO) {
        guarded {
            val keyBody = requestOk(LocalApiEndpoint.LoginQrKey)
            val key = NeteaseJson.parseQrKey(keyBody)
                ?: throw MusicRepositoryException(Kind.Service, "获取登录二维码失败，请稍后重试。")
            val createBody = requestOk(
                LocalApiEndpoint.LoginQrCreate,
                buildJsonObject {
                    put("key", key)
                    put("qrimg", true)
                },
            )
            QrChallenge(key = key, imageBase64 = NeteaseJson.parseQrImage(createBody))
        }
    }

    override suspend fun pollQrLogin(key: String): QrPollResult = withContext(Dispatchers.IO) {
        guarded {
            require(key.isNotBlank()) { "A QR login key is required." }
            // 800/801/802 are normal polling states, not business failures.
            val poll = NeteaseJson.parseQrPoll(
                request(
                    LocalApiEndpoint.LoginQrCheck,
                    buildJsonObject { put("key", key) },
                ),
            )
            if (poll.state != QrLoginState.Authorized) {
                QrPollResult(state = poll.state)
            } else {
                val cookie = poll.cookie
                    ?: throw MusicRepositoryException(Kind.Service, "登录状态异常，请重新扫码。")
                val profile = authenticator.saveValidatedCookie(cookie)
                QrPollResult(state = poll.state, profile = profile)
            }
        }
    }

    override suspend fun sendLoginCode(phone: String, countryCode: String) = withContext(Dispatchers.IO) {
        guarded { authenticator.sendCode(phone, countryCode) }
    }

    override suspend fun loginWithCode(phone: String, captcha: String, countryCode: String): AccountProfile =
        withContext(Dispatchers.IO) {
            guarded { authenticator.loginWithCode(phone, captcha, countryCode) }
        }

    override suspend fun loginWithPassword(phone: String, password: String, countryCode: String): AccountProfile =
        withContext(Dispatchers.IO) {
            guarded { authenticator.loginWithPassword(phone, password, countryCode) }
        }

    override suspend fun restoreSession(): AccountProfile? = withContext(Dispatchers.IO) {
        guarded { sessionStore.restore(api()) }
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        sessionStore.clear()
    }

    override suspend fun accountStats(): AccountStats = withContext(Dispatchers.IO) {
        guarded {
            val (cookie, _) = loginIdentity()
            val parameters = buildJsonObject { put("cookie", cookie) }
            coroutineScope {
                val accountJob = async { attempt { requestOk(LocalApiEndpoint.UserAccount, parameters) } }
                val subcountJob = async { attempt { requestOk(LocalApiEndpoint.UserSubcount, parameters) } }
                val account = accountJob.await()
                val subcount = subcountJob.await()
                if (account.isFailure && subcount.isFailure) {
                    throw (account.exceptionOrNull() ?: subcount.exceptionOrNull()!!)
                }
                NeteaseJson.parseAccountStats(subcount.getOrNull(), account.getOrNull())
            }
        }
    }

    override suspend fun userPlaylists(): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        guarded {
            val (cookie, profile) = loginIdentity()
            val playlists = mutableListOf<PlaylistSummary>()
            var offset = 0
            var page = 0
            var hasMore = true
            while (hasMore && page < MAX_PLAYLIST_PAGES) {
                val body = requestOk(
                    LocalApiEndpoint.UserPlaylist,
                    buildJsonObject {
                        put("uid", profile.id.toString())
                        put("limit", PLAYLIST_PAGE_SIZE)
                        put("offset", offset)
                        put("cookie", cookie)
                    },
                )
                val parsed = NeteaseJson.parseUserPlaylists(body)
                playlists += parsed.playlists
                offset += parsed.playlists.size
                hasMore = parsed.hasMore && parsed.playlists.isNotEmpty()
                page++
            }
            playlists.distinctBy { it.id }
        }
    }

    override suspend fun likedSongIds(): Set<Long> = withContext(Dispatchers.IO) {
        guarded {
            val (cookie, profile) = loginIdentity()
            NeteaseJson.parseLikedIds(
                requestOk(
                    LocalApiEndpoint.LikeList,
                    buildJsonObject {
                        put("uid", profile.id.toString())
                        put("cookie", cookie)
                    },
                ),
            )
        }
    }

    override suspend fun setLiked(songId: Long, liked: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            guarded {
                require(songId > 0) { "A positive song id is required." }
                val (cookie, _) = loginIdentity()
                requestOk(
                    LocalApiEndpoint.Like,
                    buildJsonObject {
                        put("id", songId.toString())
                        put("like", liked)
                        put("cookie", cookie)
                    },
                )
                liked
            }
        }

    override suspend fun likedSongs(): List<Song> = withContext(Dispatchers.IO) {
        guarded {
            val ids = likedSongIds().toList()
            if (ids.isEmpty()) {
                emptyList()
            } else {
                val (cookie, _) = loginIdentity()
                val songs = mutableListOf<Song>()
                for (chunk in ids.chunked(MAX_BATCH)) {
                    val body = requestOk(
                        LocalApiEndpoint.SongDetail,
                        buildJsonObject {
                            put("ids", chunk.joinToString(","))
                            cookie.let { put("cookie", it) }
                        },
                    )
                    songs += NeteaseJson.parseSongs(body.array("songs"))
                }
                val songsById = songs.associateBy { it.id }
                ids.mapNotNull { songsById[it] }
            }
        }
    }

    override suspend fun dailySongs(): List<Song> = withContext(Dispatchers.IO) {
        guarded {
            val (cookie, _) = loginIdentity()
            fetchDailySongs(cookie)
        }
    }

    override suspend fun personalizedSongs(): List<Song> = withContext(Dispatchers.IO) {
        guarded {
            val cookie = cookieOrNull()
            val loggedIn = cookie != null && session.value != null
            fetchPersonalizedSongs(cookie, loggedIn)
        }
    }

    override suspend fun recentSongs(): List<Song> = withContext(Dispatchers.IO) {
        guarded {
            val (cookie, _) = loginIdentity()
            NeteaseJson.parseRecentSongs(
                requestOk(
                    LocalApiEndpoint.RecentSongs,
                    buildJsonObject {
                        put("limit", RECENT_LIMIT)
                        put("cookie", cookie)
                    },
                ),
            )
        }
    }

    override suspend fun setPlaylistSubscribed(
        playlistId: Long,
        subscribed: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        guarded {
            require(playlistId > 0) { "A positive playlist id is required." }
            val (cookie, _) = loginIdentity()
            requestOk(
                LocalApiEndpoint.PlaylistSubscribe,
                buildJsonObject {
                    put("id", playlistId.toString())
                    put("t", if (subscribed) 1 else 2)
                    put("cookie", cookie)
                },
            )
            subscribed
        }
    }

    override fun close() {
        val current = client
        client = null
        runCatching { current?.close() }
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    /**
     * Joins the process-wide Node start once. The runtime may need ~30 seconds on first launch;
     * any startup failure is surfaced as a Chinese "service unavailable" error.
     */
    private suspend fun api(): LocalApiClient = clientLock.withLock {
        client ?: try {
            createLocalApi(applicationContext).also { client = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw MusicRepositoryException(
                Kind.Unavailable,
                "本机音乐服务启动失败，请重新打开应用后再试。",
                error,
            )
        }
    }

    private suspend fun request(
        endpoint: LocalApiEndpoint,
        parameters: JsonObject = JsonObject(emptyMap()),
    ): JsonObject = api().request(endpoint, parameters)

    private suspend fun requestOk(
        endpoint: LocalApiEndpoint,
        parameters: JsonObject = JsonObject(emptyMap()),
    ): JsonObject = NeteaseJson.ensureSuccess(request(endpoint, parameters))

    private suspend fun cookieOrNull(): String? = sessionStore.cookie()

    private suspend fun loginIdentity(): Pair<String, AccountProfile> {
        val profile = session.value
        val cookie = sessionStore.cookie()
        if (profile == null || cookie == null) {
            throw MusicRepositoryException(Kind.Service, "请先登录网易云账号后再使用此功能。")
        }
        return cookie to profile
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw error.toMusicRepositoryException()
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        const val SEARCH_TYPE_SONG = 1
        const val SEARCH_TYPE_ALBUM = 10
        const val SEARCH_TYPE_ARTIST = 100
        const val SEARCH_TYPE_PLAYLIST = 1000
        const val MAX_BATCH = 200
        const val MAX_OFFSET = 100_000
        const val MAX_KEYWORD = 200
        const val PLAYLIST_PAGE_SIZE = 100
        const val MAX_PLAYLIST_PAGES = 10
        const val ARTIST_ALBUM_LIMIT = 30
        const val RECENT_LIMIT = 100
        const val PERSONALIZED_SONG_LIMIT = 12
        const val RECOMMENDED_PLAYLIST_LIMIT = 12
    }
}
