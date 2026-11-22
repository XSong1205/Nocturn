package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.MusicRepositoryException.Kind
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AccountStats
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.DefaultSearchKeyword
import dev.nocturn.app.data.model.FreeListenClaimResult
import dev.nocturn.app.data.model.FreeListenRights
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
import dev.nocturn.localapi.client.ImmerseType
import dev.nocturn.localapi.client.LocalApiClient
import dev.nocturn.localapi.client.LocalApiEndpoint
import dev.nocturn.localapi.client.LocalApiException
import dev.nocturn.localapi.client.createLocalApi
import dev.nocturn.localapi.client.relayPlayStateSubmit
import dev.nocturn.localapi.client.scrobble
import dev.nocturn.localapi.client.scrobbleV1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                val homepageJob = async { attempt { fetchHomepageBlocks(cookie) } }
                val songJob = async {
                    attempt {
                        val homepageResult = homepageJob.await().getOrNull()
                        fetchPersonalizedSongs(cookie, loggedIn, homepageResult)
                    }
                }
                val playlists = playlistJob.await()
                val charts = chartJob.await()
                val daily = dailyJob?.await()
                val songs = songJob.await()
                val homepageBlocks = homepageJob.await().getOrNull()

                // Resolve radar playlist:
                // 1) From recommended playlists whose name contains "雷达"
                // 2) From homepage block HOMEPAGE_BLOCK_MGC_PLAYLIST
                val recommendedList = playlists.getOrElse { emptyList() }
                val radarFromPlaylists = recommendedList.firstOrNull { it.name.contains("雷达") }
                val radarPlaylist = radarFromPlaylists
                    ?: homepageBlocks?.let { NeteaseJson.parseHomepageRadarPlaylists(it) }?.firstOrNull()

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

                val chartList = charts.getOrElse { emptyList() }
                val dailyList = daily?.getOrElse { emptyList() } ?: emptyList()
                val songList = songs.getOrElse { emptyList() }

                val hasAnyContent = recommendedList.isNotEmpty() ||
                    chartList.isNotEmpty() ||
                    dailyList.isNotEmpty() ||
                    songList.isNotEmpty() ||
                    radarPlaylist != null

                if (!hasAnyContent) {
                    val firstFailure = listOfNotNull(
                        playlists.exceptionOrNull(),
                        charts.exceptionOrNull(),
                        songs.exceptionOrNull(),
                        daily?.exceptionOrNull(),
                    ).firstOrNull()
                    if (firstFailure != null) {
                        throw firstFailure
                    }
                }

                HomeFeed(
                    playlists = recommendedList,
                    charts = chartList,
                    dailySongs = dailyList,
                    personalizedSongs = songList,
                    radarPlaylist = radarPlaylist,
                    isLoggedIn = loggedIn,
                )
            }
        }
    }

    private suspend fun fetchHomepageBlocks(cookie: String?): JsonObject =
        requestOk(
            LocalApiEndpoint.Homepage,
            buildJsonObject {
                put("refresh", true)
                cookie?.let { put("cookie", it) }
            },
        )

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
     * In logged-in state, we parse the real personalized style recommendation block (`HOMEPAGE_BLOCK_STYLE_RCMD`)
     * from `/homepage/block/page` so it NEVER duplicates `dailySongs`.
     * If unavailable or logged out, it falls back to `/personalized/newsong` (推荐新音乐).
     */
    private suspend fun fetchPersonalizedSongs(
        cookie: String?,
        loggedIn: Boolean = false,
        homepageBlocks: JsonObject? = null,
    ): List<Song> {
        if (homepageBlocks != null) {
            val styleSongs = NeteaseJson.parseHomepageStyleSongs(homepageBlocks)
            if (styleSongs.isNotEmpty()) {
                return styleSongs.take(PERSONALIZED_SONG_LIMIT)
            }
        }
        return NeteaseJson.parsePersonalizedSongs(
            requestOk(
                LocalApiEndpoint.PersonalizedNewSongs,
                buildJsonObject {
                    put("limit", PERSONALIZED_SONG_LIMIT)
                    cookie?.let { put("cookie", it) }
                },
            ),
        ).take(PERSONALIZED_SONG_LIMIT)
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

    override suspend fun defaultKeyword(): DefaultSearchKeyword? = withContext(Dispatchers.IO) {
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
        immerseType: ImmerseType?,
        unblock: Boolean,
    ): Map<Long, String?> = withContext(Dispatchers.IO) {
        guarded {
            val requested = ids.filter { it > 0 }.distinct()
            if (requested.isEmpty()) return@guarded emptyMap()
            var cookie = cookieOrNull()
            if (quality == AudioQuality.Dolby) {
                cookie = if (cookie.isNullOrBlank()) "os=pc" else if (!cookie.contains("os=")) "$cookie; os=pc" else cookie
            }
            val resolved = LinkedHashMap<Long, String?>()
            for (chunk in requested.chunked(MAX_BATCH)) {
                val body = requestOk(
                    LocalApiEndpoint.SongUrl,
                    buildJsonObject {
                        put("id", chunk.joinToString(","))
                        put("level", quality.value)
                        if (quality == AudioQuality.Sky && immerseType != null) {
                            put("immerseType", immerseType.value)
                        }
                        if (unblock) {
                            put("unblock", true)
                        }
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
            val (cookie, profile) = loginIdentity()
            val parameters = buildJsonObject { put("cookie", cookie) }
            val detailParameters = buildJsonObject {
                put("uid", profile.id.toString())
                put("cookie", cookie)
            }
            coroutineScope {
                val detailJob = async { attempt { requestOk(LocalApiEndpoint.UserDetail, detailParameters) } }
                val subcountJob = async { attempt { requestOk(LocalApiEndpoint.UserSubcount, parameters) } }
                val levelJob = async { attempt { requestOk(LocalApiEndpoint.UserLevel, parameters) } }
                val listenTotalJob = async { attempt { requestOk(LocalApiEndpoint.ListenDataTotal, parameters) } }
                val accountJob = async { attempt { requestOk(LocalApiEndpoint.UserAccount, parameters) } }

                val detail = detailJob.await()
                val subcount = subcountJob.await()
                val level = levelJob.await()
                val listenTotal = listenTotalJob.await()
                val account = accountJob.await()

                if (detail.isFailure && subcount.isFailure && account.isFailure) {
                    throw (detail.exceptionOrNull() ?: subcount.exceptionOrNull() ?: account.exceptionOrNull()!!)
                }
                NeteaseJson.parseAccountStats(
                    subcountBody = subcount.getOrNull(),
                    accountBody = account.getOrNull(),
                    detailBody = detail.getOrNull(),
                    levelBody = level.getOrNull(),
                    listenTotalBody = listenTotal.getOrNull(),
                )
            }
        }
    }

    override suspend fun intelligenceList(
        songId: Long,
        playlistId: Long,
        startSongId: Long?,
    ): List<Song> = withContext(Dispatchers.IO) {
        guarded {
            require(songId > 0) { "A positive song id is required." }
            require(playlistId > 0) { "A positive playlist id is required." }
            val cookie = cookieOrNull()
            val parameters = buildJsonObject {
                put("id", songId.toString())
                put("pid", playlistId.toString())
                startSongId?.takeIf { it > 0 }?.let { put("sid", it.toString()) }
                put("count", 20)
                cookie?.let { put("cookie", it) }
            }
            val body = requestOk(LocalApiEndpoint.PlaymodeIntelligence, parameters)
            NeteaseJson.parseIntelligenceList(body)
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
            val homepage = runCatching { fetchHomepageBlocks(cookie) }.getOrNull()
            fetchPersonalizedSongs(cookie, loggedIn, homepage)
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

    override suspend fun getFreeListenRights(): FreeListenRights = withContext(Dispatchers.IO) {
        guarded {
            var cookie = cookieOrNull()
            if (cookie.isNullOrBlank() && session.value == null) {
                // If unlogged and no guest cookie registered yet, obtain and persist one
                runCatching {
                    val guest = request(LocalApiEndpoint.GuestLogin)
                    val guestCookie = guest.string("cookie")
                    if (!guestCookie.isNullOrBlank()) {
                        sessionStore.saveAnonymousCookie(guestCookie)
                        cookie = guestCookie
                    }
                }
            }
            val parameters = buildJsonObject {
                cookie?.takeIf { it.isNotBlank() }?.let { put("cookie", it) }
            }
            val response = request(LocalApiEndpoint.AdListeningRights, parameters)
            NeteaseJson.parseFreeListenRights(response)
        }
    }

    override suspend fun claimFreeListen(): FreeListenClaimResult = withContext(Dispatchers.IO) {
        guarded {
            var cookie = cookieOrNull()
            if (cookie.isNullOrBlank()) {
                // Try anonymous/guest registration if no active user session
                val guest = request(LocalApiEndpoint.GuestLogin)
                val guestCookie = guest.string("cookie")
                if (!guestCookie.isNullOrBlank()) {
                    sessionStore.saveAnonymousCookie(guestCookie)
                    cookie = guestCookie
                }
            }

            // Step 1: Pre-fetch anti-cheat token (best-effort)
            runCatching {
                request(
                    LocalApiEndpoint.RegisterCheckToken,
                    buildJsonObject { put("refresh", true) },
                )
            }

            // Step 2 & 3: Claim listening rights (automatically fetches ad and reqId if omitted)
            val parameters = buildJsonObject {
                cookie?.takeIf { it.isNotBlank() }?.let { put("cookie", it) }
            }
            var response = request(LocalApiEndpoint.AdListeningRightsGain, parameters)
            var code = response.int("code") ?: 200

            // If unlogged and existing guest token expired or invalid (301), refresh guest token and retry once
            if (session.value == null && code == 301) {
                val guest = request(LocalApiEndpoint.GuestLogin)
                val newGuestCookie = guest.string("cookie")
                if (!newGuestCookie.isNullOrBlank()) {
                    sessionStore.saveAnonymousCookie(newGuestCookie)
                    cookie = newGuestCookie
                    runCatching {
                        request(
                            LocalApiEndpoint.RegisterCheckToken,
                            buildJsonObject { put("refresh", true) },
                        )
                    }
                    response = request(
                        LocalApiEndpoint.AdListeningRightsGain,
                        buildJsonObject { put("cookie", newGuestCookie) },
                    )
                    code = response.int("code") ?: 200
                }
            }

            if (code == 301 || (code != 200 && cookie == null)) {
                throw MusicRepositoryException(Kind.Service, "请先登录网易云账号后再领取免费听歌时长。")
            }
            val result = NeteaseJson.parseFreeListenClaim(response)
            if (!result.success && cookie == null) {
                throw MusicRepositoryException(Kind.Service, "游客领取失败，请先登录网易云账号后再试。")
            }
            result
        }
    }

    override suspend fun scrobble(
        songId: Long,
        timeSeconds: Int,
        totalSeconds: Int?,
        sourceId: Long?,
        source: String,
        songName: String?,
        artistName: String?,
        level: AudioQuality?,
    ): Boolean = withContext(Dispatchers.IO) {
        guarded {
            require(songId > 0 && timeSeconds > 0) { "A positive song ID and positive played time are required." }
            val cookie = cookieOrNull()
            try {
                val result = api().scrobbleV1(
                    id = songId,
                    time = timeSeconds,
                    total = totalSeconds,
                    sourceId = sourceId,
                    source = source,
                    name = songName,
                    artist = artistName,
                    level = level,
                    vip = (session.value?.vipLevel ?: 0) > 0,
                    cookie = cookie,
                )
                result["code"]?.jsonPrimitive?.content == "200"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val fallback = api().scrobble(
                    id = songId,
                    sourceId = sourceId,
                    time = timeSeconds,
                    cookie = cookie,
                )
                fallback["code"]?.jsonPrimitive?.content == "200"
            }
        }
    }

    override suspend fun submitPlayState(
        songId: Long,
        sessionId: String?,
        progressSeconds: Int,
        playMode: String,
    ): Boolean = withContext(Dispatchers.IO) {
        guarded {
            require(songId > 0) { "A positive song ID is required." }
            val cookie = cookieOrNull()
            val result = api().relayPlayStateSubmit(
                id = songId,
                sessionId = sessionId,
                progress = progressSeconds,
                playMode = playMode,
                cookie = cookie,
            )
            result["code"]?.jsonPrimitive?.content == "200"
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
        val cookie = sessionStore.accountCookie()
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
