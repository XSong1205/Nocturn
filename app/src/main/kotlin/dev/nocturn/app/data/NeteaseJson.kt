package dev.nocturn.app.data

import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AccountStats
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.AlbumRef
import dev.nocturn.app.data.model.AlbumSummary
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.ArtistRef
import dev.nocturn.app.data.model.ArtistSummary
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.data.model.LyricWord
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.PlaylistSongsPage
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.QrLoginState
import dev.nocturn.app.data.model.SearchAlbumsPage
import dev.nocturn.app.data.model.SearchArtistsPage
import dev.nocturn.app.data.model.SearchPlaylistsPage
import dev.nocturn.app.data.model.SearchSongsPage
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.Toplist
import dev.nocturn.localapi.client.LyricParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Pure NetEase JSON to domain-model normalization.
 *
 * The embedded API layer returns upstream provider JSON unchanged: song fields differ between
 * legacy search (`artists`/`album`/`duration`) and cloud search or detail responses
 * (`ar`/`al`/`dt`), playlist pages arrive as either a `trackIds` list or a truncated `tracks`
 * preview, and account payloads are wrapped differently by `login/status`. None of those shapes
 * may leak into the UI, and none of these functions touch the network, Android or disk, so they
 * are covered by plain JVM unit tests.
 *
 * Every parser is defensive: a malformed entry is skipped instead of failing an entire page, but
 * a response that cannot be understood at all throws [MalformedResponseException] so callers can
 * show a retry state instead of inventing empty content.
 */
internal object NeteaseJson {
    /**
     * NetEase signals business results (expired login, removed content, rate limits) through the
     * JSON `code` field while still answering HTTP 200. A non-success code becomes a typed error.
     */
    fun ensureSuccess(body: JsonObject): JsonObject {
        val code = body.int("code") ?: throw MalformedResponseException("响应缺少状态码。")
        if (code != 200) throw NeteaseBusinessException(code)
        return body
    }

    // ---------------------------------------------------------------------------------------
    // Songs
    // ---------------------------------------------------------------------------------------

    /** Accepts both legacy (`artists`/`album`/`duration`) and cloud (`ar`/`al`/`dt`) song shapes. */
    fun parseSong(value: JsonObject): Song? {
        val id = value.long("id")?.takeIf { it > 0 } ?: return null
        val name = value.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artists = parseArtists(value["ar"] ?: value["artists"])
        val album = parseAlbum(value)
        val duration = value.long("dt") ?: value.long("duration") ?: 0L
        return Song(
            id = id,
            name = name,
            artists = artists,
            album = album,
            durationMs = duration.coerceAtLeast(0L),
            fee = value.int("fee"),
            coverUrl = value.string("picUrl")?.trim()?.takeIf { it.isNotEmpty() },
            alias = parseAlias(value),
        )
    }

    fun parseSongs(value: JsonElement?): List<Song> = when (value) {
        is JsonArray -> value.mapNotNull { (it as? JsonObject)?.let(::parseSong) }.distinctBy { it.id }
        is JsonObject -> parseSongs(value.array("songs") ?: value.array("data") ?: value.array("tracks"))
        else -> emptyList()
    }

    /**
     * Batch `song/url/v1` response. Every requested id is present in the result; unavailable
     * tracks map to null so the caller can skip them without a second request.
     */
    fun parseStreamUrls(body: JsonObject, requestedIds: List<Long>): Map<Long, String?> {
        val urls = LinkedHashMap<Long, String?>()
        requestedIds.filter { it > 0 }.distinct().forEach { urls[it] = null }
        val entries = body.array("data") ?: return urls
        for (element in entries) {
            val entry = element as? JsonObject ?: continue
            val id = entry.long("id")?.takeIf { it > 0 && urls.containsKey(it) } ?: continue
            urls[id] = sanitizeStreamUrl(entry.string("url"))
        }
        return urls
    }

    private fun sanitizeStreamUrl(value: String?): String? {
        val url = value?.trim() ?: return null
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    // ---------------------------------------------------------------------------------------
    // Home feed blocks
    // ---------------------------------------------------------------------------------------

    /**
     * Playlist shelves: `/personalized` returns `{result:[...]}`, the account-aware
     * `/recommend/resource` returns `{recommend:[...]}`; other home blocks use `{list:[...]}` or
     * `{data:[...]}`.
     */
    fun parsePersonalizedPlaylists(body: JsonObject): List<PlaylistSummary> {
        val entries = body.array("result")
            ?: body.array("recommend")
            ?: body.array("data")
            ?: body.array("playlists")
        return parsePlaylistSummaries(entries)
    }

    /** Charts carry a short `tracks` preview; the complete list is only available via playlist APIs. */
    fun parseToplists(body: JsonObject): List<Toplist> {
        val entries = body.array("list")
            ?: body.obj("result")?.array("list")
            ?: body.obj("data")?.array("list")
            ?: return emptyList()
        return entries.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.long("id")?.takeIf { it > 0 } ?: return@mapNotNull null
            val name = item.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val tracks = parseSongs(item.array("tracks"))
            Toplist(
                id = id,
                name = name,
                coverUrl = item.string("coverImgUrl")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: item.string("picUrl")?.trim()?.takeIf { it.isNotEmpty() },
                updateFrequency = item.string("updateFrequency")?.trim()?.takeIf { it.isNotEmpty() },
                description = item.string("description")?.trim()?.takeIf { it.isNotEmpty() },
                trackCount = item.int("trackCount") ?: tracks.size,
                tracks = tracks,
            )
        }.distinctBy { it.id }
    }

    // ---------------------------------------------------------------------------------------
    // Playlists
    // ---------------------------------------------------------------------------------------

    fun parsePlaylistSummaries(value: JsonElement?): List<PlaylistSummary> = when (value) {
        is JsonArray -> value.mapNotNull { (it as? JsonObject)?.let(::parsePlaylistSummary) }.distinctBy { it.id }
        is JsonObject -> parsePlaylistSummaries(
            value.array("playlists") ?: value.array("playlist") ?: value.array("result"),
        )
        else -> emptyList()
    }

    fun parsePlaylistSummary(value: JsonObject): PlaylistSummary? {
        val id = value.long("id")?.takeIf { it > 0 } ?: return null
        val name = value.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val creator = value.obj("creator")
        return PlaylistSummary(
            id = id,
            name = name,
            coverUrl = value.string("coverImgUrl")?.trim()?.takeIf { it.isNotEmpty() }
                ?: value.string("picUrl")?.trim()?.takeIf { it.isNotEmpty() },
            playCount = (value.long("playCount") ?: 0L).coerceAtLeast(0L),
            trackCount = (value.int("trackCount") ?: 0).coerceAtLeast(0),
            creatorName = creator?.string("nickname")?.trim()?.takeIf { it.isNotEmpty() }
                ?: value.string("creatorName")?.trim()?.takeIf { it.isNotEmpty() },
            description = value.string("description")?.trim()?.takeIf { it.isNotEmpty() },
            updateFrequency = value.string("updateFrequency")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun parsePlaylistDetail(body: JsonObject, selfUserId: Long? = null): PlaylistDetail? {
        val playlist = body.obj("playlist")
            ?: body.obj("data")?.obj("playlist")
            ?: return null
        val id = playlist.long("id")?.takeIf { it > 0 } ?: return null
        val name = playlist.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val creator = playlist.obj("creator")
        val creatorId = creator?.long("userId") ?: creator?.long("id")
        val trackIds = playlist.array("trackIds")
        val tags = playlist.array("tags").orEmpty().mapNotNull { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        return PlaylistDetail(
            id = id,
            name = name,
            coverUrl = playlist.string("coverImgUrl")?.trim()?.takeIf { it.isNotEmpty() }
                ?: playlist.string("picUrl")?.trim()?.takeIf { it.isNotEmpty() },
            description = playlist.string("description")?.trim()?.takeIf { it.isNotEmpty() },
            creatorName = creator?.string("nickname")?.trim()?.takeIf { it.isNotEmpty() },
            creatorAvatarUrl = creator?.string("avatarUrl")?.trim()?.takeIf { it.isNotEmpty() },
            playCount = (playlist.long("playCount") ?: 0L).coerceAtLeast(0L),
            trackCount = (playlist.int("trackCount") ?: trackIds?.size ?: 0).coerceAtLeast(0),
            subscribed = playlist.bool("subscribed") ?: false,
            canSubscribe = selfUserId == null || creatorId == null || creatorId != selfUserId,
            tags = tags,
            updateFrequency = playlist.string("updateFrequency")?.trim()?.takeIf { it.isNotEmpty() },
            createTime = (playlist.long("createTime") ?: 0L).coerceAtLeast(0L),
        )
    }

    /**
     * The complete ordered track-id list from `playlist/detail`. The `tracks` preview in the same
     * response may be truncated and must never be used as the playlist's song list.
     */
    fun parsePlaylistTrackIds(body: JsonObject): List<Long> {
        val playlist = body.obj("playlist")
            ?: body.obj("data")?.obj("playlist")
            ?: throw MalformedResponseException("歌单详情缺少 playlist 字段。")
        val entries = playlist.array("trackIds")
            ?: throw MalformedResponseException("歌单详情缺少 trackIds 字段。")
        return entries.mapNotNull { element ->
            (element as? JsonObject)?.long("id")?.takeIf { it > 0 }
        }
    }

    /**
     * Selects one page from the complete id list and restores the provider's order. Song details
     * that the provider could not return are skipped, but pagination still advances by the selected
     * window so a single missing track cannot cause an infinite "load more" loop.
     */
    fun playlistSongsPage(
        trackIds: List<Long>,
        songs: List<Song>,
        offset: Int,
        limit: Int,
    ): PlaylistSongsPage {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(0)
        val selected = trackIds.drop(safeOffset).take(safeLimit)
        val songsById = songs.associateBy { it.id }
        val nextOffset = (safeOffset + selected.size).takeIf { it < trackIds.size }
        return PlaylistSongsPage(
            songs = selected.mapNotNull { songsById[it] },
            total = trackIds.size,
            nextOffset = nextOffset,
        )
    }

    /** One page of `user/playlist`; `more` signals that another offset should be requested. */
    fun parseUserPlaylists(body: JsonObject): PlaylistPage {
        val entries = body.array("playlist")
            ?: body.obj("data")?.array("playlist")
            ?: body.array("playlists")
            ?: JsonArray(emptyList())
        val hasMore = body.bool("more") ?: body.obj("data")?.bool("more") ?: false
        return PlaylistPage(parsePlaylistSummaries(entries), hasMore)
    }

    // ---------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------

    // Cloud search and legacy search both answer under `result`; the requested type is a separate
    // key (`songs`, `playlists`, `albums`, `artists`) with its own `<type>Count` total.
    fun parseSearchSongsPage(body: JsonObject, offset: Int): SearchSongsPage {
        val result = searchResult(body)
        val songs = parseSongs(result.array("songs"))
        val total = result.int("songCount") ?: songs.size
        return SearchSongsPage(songs, total, inferHasMore(result, offset, songs.size, total))
    }

    fun parseSearchPlaylistsPage(body: JsonObject, offset: Int): SearchPlaylistsPage {
        val result = searchResult(body)
        val playlists = parsePlaylistSummaries(result.array("playlists"))
        val total = result.int("playlistCount") ?: playlists.size
        return SearchPlaylistsPage(playlists, total, inferHasMore(result, offset, playlists.size, total))
    }

    fun parseSearchAlbumsPage(body: JsonObject, offset: Int): SearchAlbumsPage {
        val result = searchResult(body)
        val albums = parseAlbumSummaries(result.array("albums"))
        val total = result.int("albumCount") ?: albums.size
        return SearchAlbumsPage(albums, total, inferHasMore(result, offset, albums.size, total))
    }

    fun parseSearchArtistsPage(body: JsonObject, offset: Int): SearchArtistsPage {
        val result = searchResult(body)
        val artists = parseArtistSummaries(result.array("artists"))
        val total = result.int("artistCount") ?: artists.size
        return SearchArtistsPage(artists, total, inferHasMore(result, offset, artists.size, total))
    }

    fun parseSearchSuggestions(body: JsonObject): List<String> {
        val result = body.obj("result") ?: body.obj("data") ?: body
        val matches = result.array("allMatch") ?: result.array("suggest") ?: result.array("songs")
        val fromMatches = matches.orEmpty().mapNotNull { element ->
            when (element) {
                is JsonObject -> element.string("keyword") ?: element.string("name")
                is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull
                else -> null
            }
        }
        val fromOrder = result.array("order").orEmpty().mapNotNull { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        }
        return (fromMatches + fromOrder)
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }

    fun parseHotSearches(body: JsonObject): List<String> {
        val entries = body.array("data")
            ?: body.obj("result")?.array("hots")
            ?: body.array("hots")
            ?: return emptyList()
        return entries.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            item.string("searchWord") ?: item.string("first") ?: item.string("keyword")
                ?: item.string("name")
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_HOT_SEARCHES)
    }

    fun parseDefaultKeyword(body: JsonObject): String? {
        val data = body.obj("data") ?: body
        return (data.string("showKeyword") ?: data.string("realkeyword")
            ?: data.string("keyword") ?: body.string("showKeyword"))
            ?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ---------------------------------------------------------------------------------------
    // Album and artist details
    // ---------------------------------------------------------------------------------------

    fun parseAlbumDetail(body: JsonObject): AlbumDetail? {
        val album = body.obj("album") ?: return null
        val id = album.long("id")?.takeIf { it > 0 } ?: return null
        val name = album.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artists = parseArtists(album["artists"] ?: album["artist"])
        val songs = parseSongs(album.array("songs") ?: body.array("songs"))
        return AlbumDetail(
            id = id,
            name = name,
            coverUrl = album.string("picUrl")?.trim()?.takeIf { it.isNotEmpty() },
            artists = artists,
            artistText = artists.joinToString(" / ") { it.name },
            publishTime = (album.long("publishTime") ?: 0L).coerceAtLeast(0L),
            company = album.string("company")?.trim()?.takeIf { it.isNotEmpty() },
            description = album.string("description")?.trim()?.takeIf { it.isNotEmpty() },
            songs = songs,
        )
    }

    /**
     * Combines `/artists` (identity, hot songs), `/artist/detail` (newer fan/description fields) and
     * `/artist/album` (albums). Identity fields come from the first usable artist object; fan count
     * and the richer biography prefer the detail response, so a partial enrichment failure only
     * loses those extra fields.
     */
    fun parseArtistDetail(
        artistBody: JsonObject?,
        detailBody: JsonObject?,
        albumBody: JsonObject?,
    ): ArtistDetail? {
        val identity = firstArtistObject(
            artistBody?.obj("artist"),
            artistBody?.obj("data")?.obj("artist"),
        )
        val enrichment = firstArtistObject(
            detailBody?.obj("data")?.obj("artist"),
            detailBody?.obj("artist"),
            detailBody?.obj("data"),
        )
        val artist = identity ?: enrichment ?: return null
        val id = artist.long("id")?.takeIf { it > 0 } ?: return null
        val name = artist.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val hotSongs = parseSongs(artistBody?.array("hotSongs") ?: artistBody?.array("songs"))
        val albums = parseAlbumSummaries(
            albumBody?.array("hotAlbums")
                ?: albumBody?.array("albums")
                ?: albumBody?.obj("data")?.array("hotAlbums")
                ?: albumBody?.obj("data")?.array("albums"),
        )
        val avatar = identity?.string("picUrl") ?: identity?.string("avatarUrl")
            ?: identity?.string("img1v1Url") ?: enrichment?.string("picUrl")
            ?: enrichment?.string("avatarUrl") ?: enrichment?.string("img1v1Url")
        val briefDescription = enrichment?.string("briefDesc")
            ?: enrichment?.string("briefDescription")
            ?: identity?.string("briefDesc") ?: identity?.string("briefDescription")
        return ArtistDetail(
            id = id,
            name = name,
            avatarUrl = avatar?.trim()?.takeIf { it.isNotEmpty() },
            coverUrl = (enrichment?.string("cover") ?: identity?.string("cover"))
                ?.trim()?.takeIf { it.isNotEmpty() },
            briefDescription = briefDescription?.trim()?.takeIf { it.isNotEmpty() },
            albumCount = (identity?.int("albumSize") ?: enrichment?.int("albumSize") ?: albums.size)
                .coerceAtLeast(0),
            songCount = (identity?.int("musicSize") ?: enrichment?.int("musicSize") ?: hotSongs.size)
                .coerceAtLeast(0),
            fansCount = (enrichment?.long("fansCount") ?: identity?.long("fansCount") ?: 0L)
                .coerceAtLeast(0L),
            hotSongs = hotSongs,
            albums = albums,
        )
    }

    private fun firstArtistObject(vararg candidates: JsonObject?): JsonObject? =
        candidates.firstOrNull { it != null && it.long("id") != null && it.string("name") != null }

    fun parseAlbumSummaries(value: JsonElement?): List<AlbumSummary> = when (value) {
        is JsonArray -> value.mapNotNull { (it as? JsonObject)?.let(::parseAlbumSummary) }
            .distinctBy { it.id }
        is JsonObject -> parseAlbumSummaries(value.array("albums") ?: value.array("hotAlbums"))
        else -> emptyList()
    }

    fun parseAlbumSummary(value: JsonObject): AlbumSummary? {
        val id = value.long("id")?.takeIf { it > 0 } ?: return null
        val name = value.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artists = parseArtists(value["artists"] ?: value["artist"])
        return AlbumSummary(
            id = id,
            name = name,
            coverUrl = value.string("picUrl")?.trim()?.takeIf { it.isNotEmpty() },
            artistText = artists.joinToString(" / ") { it.name },
            trackCount = (value.int("size") ?: value.int("trackCount") ?: 0).coerceAtLeast(0),
            publishTime = (value.long("publishTime") ?: 0L).coerceAtLeast(0L),
        )
    }

    fun parseArtistSummaries(value: JsonElement?): List<ArtistSummary> = when (value) {
        is JsonArray -> value.mapNotNull { (it as? JsonObject)?.let(::parseArtistSummary) }
            .distinctBy { it.id }
        is JsonObject -> parseArtistSummaries(value.array("artists"))
        else -> emptyList()
    }

    fun parseArtistSummary(value: JsonObject): ArtistSummary? {
        val id = value.long("id")?.takeIf { it > 0 } ?: return null
        val name = value.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ArtistSummary(
            id = id,
            name = name,
            avatarUrl = (value.string("picUrl") ?: value.string("avatarUrl")
                ?: value.string("img1v1Url"))?.trim()?.takeIf { it.isNotEmpty() },
            albumCount = (value.int("albumSize") ?: 0).coerceAtLeast(0),
            songCount = (value.int("musicSize") ?: 0).coerceAtLeast(0),
            fansCount = (value.long("fansCount") ?: 0L).coerceAtLeast(0L),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Lyrics
    // ---------------------------------------------------------------------------------------

    /** Reuses the shared YRC/LRC parser and maps it onto the UI-facing lyric model. */
    fun parseLyrics(body: JsonObject): Lyrics {
        val parsed = LyricParser.parse(body)
        if (parsed.instrumental) return Lyrics.EMPTY
        val lines = parsed.lines.mapNotNull { line ->
            val text = line.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            LyricLine(
                startMs = line.startTimeMs.coerceAtLeast(0L),
                text = text,
                translation = line.translation?.trim()?.takeIf { it.isNotEmpty() },
                durationMs = line.durationMs,
                words = line.words.map { LyricWord(it.startTimeMs, it.durationMs, it.text) },
                romanization = line.romanization?.trim()?.takeIf { it.isNotEmpty() },
            )
        }.sortedBy { it.startMs }
        return if (lines.isEmpty()) Lyrics.EMPTY else Lyrics(lines)
    }

    // ---------------------------------------------------------------------------------------
    // Account and session
    // ---------------------------------------------------------------------------------------

    /**
     * Parses `login/status` (wrapped in `data`) and `user/account` responses. A business code of
     * 301 means the stored cookie is anonymous again; other non-200 codes are real failures.
     */
    fun parseLoginStatus(body: JsonObject): AccountProfile? {
        val data = body.obj("data") ?: body
        val code = data.int("code") ?: body.int("code")
            ?: throw MalformedResponseException("登录状态响应缺少状态码。")
        if (code == 301) return null
        if (code != 200) throw NeteaseBusinessException(code)
        val profile = data.obj("profile")
            ?: data.obj("account")?.obj("profile")
            ?: return null
        return parseProfile(profile) ?: throw MalformedResponseException("登录状态响应缺少用户信息。")
    }

    fun parseProfile(value: JsonObject): AccountProfile? {
        val id = (value.long("userId") ?: value.long("id"))?.takeIf { it > 0 } ?: return null
        val nickname = value.string("nickname")?.trim()?.takeIf { it.isNotEmpty() }
            ?: value.string("userName")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "网易云用户"
        return AccountProfile(
            id = id,
            nickname = nickname,
            avatarUrl = value.string("avatarUrl")?.trim()?.takeIf { it.isNotEmpty() },
            backgroundUrl = value.string("backgroundUrl")?.trim()?.takeIf { it.isNotEmpty() },
            signature = value.string("signature")?.trim()?.takeIf { it.isNotEmpty() },
            vipLevel = (value.int("vipType") ?: value.int("vipLevel") ?: 0).coerceAtLeast(0),
        )
    }

    /** Merge of `user/subcount` counts (playlists) and profile follow counts. */
    fun parseAccountStats(subcountBody: JsonObject?, accountBody: JsonObject?): AccountStats {
        val subcount = subcountBody?.let(::unwrapData)
        val profile = accountBody?.let(::profileOf)
        val created = subcount?.int("createPlaylistCount")
        val collected = subcount?.int("collectPlaylistCount")
        val playlistCount = if (created != null || collected != null) {
            (created ?: 0) + (collected ?: 0)
        } else {
            firstInt(subcount, "playlistCount", "subPlaylistCount") ?: 0
        }
        val following = firstInt(subcount, "followedUserCount", "followingCount")
            ?: firstInt(profile, "followedUserCount", "followingCount") ?: 0
        val followers = firstInt(subcount, "followerCount", "followers", "followeds")
            ?: firstInt(profile, "followerCount", "followers", "followeds") ?: 0
        return AccountStats(
            playlistCount = playlistCount.coerceAtLeast(0),
            followingCount = following.coerceAtLeast(0),
            followerCount = followers.coerceAtLeast(0),
        )
    }

    fun parseLikedIds(body: JsonObject): Set<Long> {
        val entries = body.array("ids") ?: body.obj("data")?.array("ids") ?: return emptySet()
        return entries.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.toLongOrNull() }
            .filter { it > 0 }
            .toSet()
    }

    /** `record/recent/song` nests each song under a recent-play envelope (`data`). */
    fun parseRecentSongs(body: JsonObject): List<Song> {
        val data = body["data"]
        val entries: List<JsonElement> = when (data) {
            is JsonObject -> data.array("list")?.toList()
                ?: body.array("list")?.toList()
                ?: emptyList()
            is JsonArray -> data.toList()
            else -> body.array("list")?.toList() ?: emptyList()
        }
        val fromEnvelope = entries.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val song = entry.obj("data") ?: entry.obj("song") ?: entry
            parseSong(song)
        }
        return if (fromEnvelope.isNotEmpty()) {
            fromEnvelope.distinctBy { it.id }
        } else {
            parseSongs(data).distinctBy { it.id }
        }
    }

    /** `recommend/songs` returns `data.dailySongs`; older payloads use `data.recommend`. */
    fun parseRecommendSongs(body: JsonObject): List<Song> {
        val data = body.obj("data")
        val entries = data?.array("dailySongs")
            ?: data?.array("recommend")
            ?: data?.array("songs")
            ?: body.array("songs")
            ?: body.array("dailySongs")
            ?: return emptyList()
        return parseSongs(entries)
    }

    /**
     * `personalized/newsong` wraps each track as `{id, name, picUrl, song:{...}}`. The nested legacy
     * song carries artists, album, duration and fee; the wrapper's `picUrl` is the card cover.
     */
    fun parsePersonalizedSongs(body: JsonObject): List<Song> {
        val entries = body.array("result") ?: body.array("data") ?: return emptyList()
        return entries.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val song = item.obj("song")?.let(::parseSong) ?: parseSong(item) ?: return@mapNotNull null
            val cover = item.string("picUrl")?.trim()?.takeIf { it.isNotEmpty() }
            if (song.coverUrl == null && cover != null) song.copy(coverUrl = cover) else song
        }.distinctBy { it.id }
    }

    // ---------------------------------------------------------------------------------------
    // QR login
    // ---------------------------------------------------------------------------------------

    fun parseQrKey(body: JsonObject): String? {
        val data = body.obj("data") ?: body
        return (data.string("unikey") ?: data.string("key"))
            ?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Strips the `data:image/png;base64,` prefix so callers can decode plain Base64. */
    fun parseQrImage(body: JsonObject): String? {
        val data = body.obj("data") ?: body
        val image = (data.string("qrimg") ?: data.string("qrImage"))?.trim() ?: return null
        if (image.isEmpty()) return null
        return image.replace(DATA_URL_PREFIX, "").takeIf { it.isNotEmpty() }
    }

    fun parseQrPoll(body: JsonObject): QrPoll {
        val code = body.int("code") ?: throw MalformedResponseException("登录二维码响应缺少状态码。")
        val state = when (code) {
            800 -> QrLoginState.Expired
            801 -> QrLoginState.WaitingForScan
            802 -> QrLoginState.WaitingForConfirmation
            803 -> QrLoginState.Authorized
            else -> QrLoginState.Rejected
        }
        // Only a successful authorization may expose the account cookie; other states are polling.
        val cookie = if (state == QrLoginState.Authorized) {
            body.string("cookie")?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        return QrPoll(state, code, cookie)
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private fun parseArtists(value: JsonElement?): List<ArtistRef> {
        val elements = when (value) {
            is JsonArray -> value
            is JsonObject -> JsonArray(listOf(value))
            else -> return emptyList()
        }
        return elements.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val name = element.string("name")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    ArtistRef(element.long("id") ?: element.long("userId") ?: 0L, name)
                }
                is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull
                    ?.trim()?.takeIf { it.isNotEmpty() }?.let { ArtistRef(0L, it) }
                else -> null
            }
        }
    }

    private fun parseAlbum(value: JsonObject): AlbumRef? {
        val album = value.obj("al") ?: value.obj("album") ?: return null
        val name = album.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return AlbumRef(
            id = album.long("id") ?: 0L,
            name = name,
            coverUrl = (album.string("picUrl") ?: album.string("blurPicUrl"))
                ?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseAlias(value: JsonObject): String? {
        val aliases = value.array("alia") ?: value.array("alias")
        val names = aliases.orEmpty().mapNotNull { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        if (names.isNotEmpty()) return names.distinct().joinToString(" / ")
        return value.string("alias")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun searchResult(body: JsonObject): JsonObject =
        body.obj("result") ?: body.obj("data") ?: throw MalformedResponseException("搜索响应缺少 result 字段。")

    private fun inferHasMore(result: JsonObject, offset: Int, pageSize: Int, total: Int): Boolean {
        result.bool("hasMore")?.let { return it }
        result.bool("more")?.let { return it }
        return total > 0 && offset.coerceAtLeast(0) + pageSize < total
    }

    private fun unwrapData(body: JsonObject): JsonObject = body.obj("data") ?: body

    private fun profileOf(body: JsonObject): JsonObject? = body.obj("profile")
        ?: body.obj("data")?.obj("profile")
        ?: body.obj("data")?.takeIf { it.long("userId") != null }
        ?: body.takeIf { it.long("userId") != null }

    private fun firstInt(value: JsonObject?, vararg keys: String): Int? {
        if (value == null) return null
        for (key in keys) {
            value.int(key)?.let { return it }
        }
        return null
    }

    private val DATA_URL_PREFIX = Regex("^data:[^,]*;base64,", RegexOption.IGNORE_CASE)
    private const val MAX_SUGGESTIONS = 10
    private const val MAX_HOT_SEARCHES = 20
}

/** One `user/playlist` page plus the provider's "there is another page" flag. */
internal data class PlaylistPage(
    val playlists: List<PlaylistSummary>,
    val hasMore: Boolean,
)

/** Raw QR polling result before the account cookie is validated and stored. */
internal data class QrPoll(
    val state: QrLoginState,
    val code: Int,
    val cookie: String?,
)

// -------------------------------------------------------------------------------------------
// JSON helpers shared by the repository and the parsers above.
// -------------------------------------------------------------------------------------------

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

/** Raw primitive content for strings, numbers and booleans; null for objects, arrays and JSON null. */
internal fun JsonObject.content(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Primitive content only when the provider used a JSON string. */
internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.long(key: String): Long? = content(key)?.trim()?.toLongOrNull()

internal fun JsonObject.int(key: String): Int? = content(key)?.trim()?.toIntOrNull()

internal fun JsonObject.bool(key: String): Boolean? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    primitive.booleanOrNull?.let { return it }
    return when (primitive.contentOrNull?.trim()?.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}
