package com.nocturn.music.data.repository

import com.nocturn.music.data.api.EmbeddedNcmEngine
import com.nocturn.music.data.api.NcmApiClient
import com.nocturn.music.data.api.NeteaseApi
import com.nocturn.music.model.Album
import com.nocturn.music.model.ApiMode
import com.nocturn.music.model.Artist
import com.nocturn.music.model.BannerItem
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.model.SongLyric
import com.nocturn.music.model.UserProfile

object MusicRepository {
    private val lyricCache = mutableMapOf<Long, SongLyric>()
    private val playlistCache = mutableMapOf<Long, Playlist>()
    private val userPlaylistsCache = mutableMapOf<Long, List<Playlist>>()
    private var cachedBanners: List<BannerItem>? = null
    private var cachedToplists: List<Playlist>? = null
    private var cachedRecommendedPlaylists: List<Playlist>? = null
    private var cachedHotSongs: List<Song>? = null

    fun getCachedBanners(): List<BannerItem>? = cachedBanners
    fun getCachedToplists(): List<Playlist>? = cachedToplists
    fun getCachedRecommendedPlaylists(): List<Playlist>? = cachedRecommendedPlaylists
    fun getCachedHotSongs(): List<Song>? = cachedHotSongs
    fun getCachedUserPlaylists(uid: Long): List<Playlist>? = userPlaylistsCache[uid]
    fun hasHomeCache(): Boolean = !cachedBanners.isNullOrEmpty() || !cachedRecommendedPlaylists.isNullOrEmpty()

    fun clearHomeCache() {
        cachedBanners = null
        cachedToplists = null
        cachedRecommendedPlaylists = null
        cachedHotSongs = null
        userPlaylistsCache.clear()
    }

    private val isEmbedded: Boolean
        get() = SettingsRepository.apiMode.value == ApiMode.EMBEDDED

    suspend fun getBanners(forceRefresh: Boolean = false): List<BannerItem> {
        if (!forceRefresh && !cachedBanners.isNullOrEmpty()) {
            return cachedBanners!!
        }
        val res = if (isEmbedded) {
            val r = EmbeddedNcmEngine.getBanners()
            if (r.isNotEmpty()) r else NcmApiClient.getBanners()
        } else {
            val r = NcmApiClient.getBanners()
            if (r.isNotEmpty()) r else EmbeddedNcmEngine.getBanners()
        }
        if (res.isNotEmpty()) {
            cachedBanners = res
        }
        return if (res.isNotEmpty()) res else cachedBanners ?: emptyList()
    }

    suspend fun getToplists(forceRefresh: Boolean = false): List<Playlist> {
        if (!forceRefresh && !cachedToplists.isNullOrEmpty()) {
            return cachedToplists!!
        }
        val res = if (isEmbedded) {
            val r = EmbeddedNcmEngine.getToplists()
            if (r.isNotEmpty()) r else NcmApiClient.getToplists()
        } else {
            val r = NcmApiClient.getToplists()
            if (r.isNotEmpty()) r else EmbeddedNcmEngine.getToplists()
        }
        if (res.isNotEmpty()) {
            cachedToplists = res
        }
        return if (res.isNotEmpty()) res else cachedToplists ?: emptyList()
    }

    suspend fun getRecommendedPlaylists(limit: Int = 18, forceRefresh: Boolean = false): List<Playlist> {
        if (!forceRefresh && !cachedRecommendedPlaylists.isNullOrEmpty()) {
            return cachedRecommendedPlaylists!!
        }
        val res = if (isEmbedded) {
            val r = EmbeddedNcmEngine.getPersonalizedPlaylists(limit)
            if (r.isNotEmpty()) r else NcmApiClient.getPersonalizedPlaylists(limit)
        } else {
            val r = NcmApiClient.getPersonalizedPlaylists(limit)
            if (r.isNotEmpty()) r else EmbeddedNcmEngine.getPersonalizedPlaylists(limit)
        }
        if (res.isNotEmpty()) {
            cachedRecommendedPlaylists = res
        }
        return if (res.isNotEmpty()) res else cachedRecommendedPlaylists ?: emptyList()
    }

    suspend fun getHotSongs(forceRefresh: Boolean = false): List<Song> {
        if (!forceRefresh && !cachedHotSongs.isNullOrEmpty()) {
            return cachedHotSongs!!
        }
        val lists = getToplists(forceRefresh)
        if (lists.isNotEmpty()) {
            val firstChart = getPlaylistDetail(lists.first().id, forceRefresh)
            val songs = firstChart?.tracks?.take(10) ?: emptyList()
            if (songs.isNotEmpty()) {
                cachedHotSongs = songs
            }
            return songs
        }
        return cachedHotSongs ?: emptyList()
    }

    suspend fun getPlaylistDetail(id: Long, forceRefresh: Boolean = false): Playlist? {
        if (!forceRefresh) {
            playlistCache[id]?.let { return it }
        }
        val detail = if (isEmbedded) {
            EmbeddedNcmEngine.getPlaylistDetail(id) ?: NcmApiClient.getPlaylistDetail(id)
        } else {
            NcmApiClient.getPlaylistDetail(id) ?: EmbeddedNcmEngine.getPlaylistDetail(id)
        }
        if (detail != null) {
            playlistCache[id] = detail
        }
        return detail
    }

    suspend fun getAlbumDetail(id: Long, forceRefresh: Boolean = false): Playlist? {
        val cacheKey = -id // negative to prevent collisions
        if (!forceRefresh) {
            playlistCache[cacheKey]?.let { return it }
        }
        val detail = if (isEmbedded) {
            EmbeddedNcmEngine.getAlbumDetail(id) ?: NcmApiClient.getAlbumDetail(id)
        } else {
            NcmApiClient.getAlbumDetail(id) ?: EmbeddedNcmEngine.getAlbumDetail(id)
        }
        if (detail != null) {
            playlistCache[cacheKey] = detail
        }
        return detail
    }

    suspend fun getArtistDetail(id: Long, forceRefresh: Boolean = false): Playlist? {
        val cacheKey = -1_000_000_000L - id
        if (!forceRefresh) {
            playlistCache[cacheKey]?.let { return it }
        }
        val detail = if (isEmbedded) {
            EmbeddedNcmEngine.getArtistDetail(id) ?: NcmApiClient.getArtistDetail(id)
        } else {
            NcmApiClient.getArtistDetail(id) ?: EmbeddedNcmEngine.getArtistDetail(id)
        }
        if (detail != null) {
            playlistCache[cacheKey] = detail
        }
        return detail
    }

    suspend fun getLyric(songId: Long): SongLyric {
        lyricCache[songId]?.let { return it }
        val lyric = if (isEmbedded) {
            val res = EmbeddedNcmEngine.getSongLyric(songId)
            if (res.lines.isNotEmpty()) res else NcmApiClient.getSongLyric(songId)
        } else {
            val res = NcmApiClient.getSongLyric(songId)
            if (res.lines.isNotEmpty()) res else EmbeddedNcmEngine.getSongLyric(songId)
        }
        lyricCache[songId] = lyric
        return lyric
    }

    suspend fun getSongPlayUrl(songId: Long, level: String = "exhigh"): String {
        return if (isEmbedded) {
            val url = EmbeddedNcmEngine.getSongPlayUrl(songId, level)
            if (url.isNotBlank() && !url.contains("outer/url")) {
                url
            } else {
                val fallback = NcmApiClient.getSongPlayUrl(songId, level)
                if (fallback.isNotBlank() && !fallback.contains("outer/url")) fallback else url
            }
        } else {
            val url = NcmApiClient.getSongPlayUrl(songId, level)
            if (url.isNotBlank() && !url.contains("outer/url")) {
                url
            } else {
                val fallback = EmbeddedNcmEngine.getSongPlayUrl(songId, level)
                if (fallback.isNotBlank() && !fallback.contains("outer/url")) fallback else url
            }
        }
    }

    suspend fun syncCloudFavorites(): List<Song> {
        val profile = SettingsRepository.userProfile.value
        if (!profile.isLogin || profile.userId <= 0L) return emptyList()
        val playlists = getUserPlaylists(profile.userId)
        if (playlists.isEmpty()) return emptyList()

        val favPlaylist = playlists.firstOrNull { it.name.contains("喜欢的音乐") } ?: playlists.first()
        val detail = getPlaylistDetail(favPlaylist.id, forceRefresh = true)
        val tracks = detail?.tracks ?: emptyList()
        if (tracks.isNotEmpty()) {
            SettingsRepository.setFavoriteSongs(tracks)
        }
        return tracks
    }

    suspend fun getHotSearchKeywords(): List<String> {
        return if (isEmbedded) {
            EmbeddedNcmEngine.getHotSearchKeywords()
        } else {
            NcmApiClient.getHotSearchKeywords()
        }
    }

    suspend fun searchSongs(query: String, page: Int = 0): List<Song> {
        return if (isEmbedded) {
            val res = EmbeddedNcmEngine.searchSongs(query, page)
            if (res.isNotEmpty()) res else NcmApiClient.searchSongs(query, page)
        } else {
            val res = NcmApiClient.searchSongs(query, page)
            if (res.isNotEmpty()) res else EmbeddedNcmEngine.searchSongs(query, page)
        }
    }

    suspend fun searchPlaylists(query: String, page: Int = 0): List<Playlist> {
        return if (isEmbedded) {
            val res = EmbeddedNcmEngine.searchPlaylists(query, page)
            if (res.isNotEmpty()) res else NcmApiClient.searchPlaylists(query, page)
        } else {
            val res = NcmApiClient.searchPlaylists(query, page)
            if (res.isNotEmpty()) res else EmbeddedNcmEngine.searchPlaylists(query, page)
        }
    }

    suspend fun searchArtists(query: String, page: Int = 0): List<Artist> {
        return if (isEmbedded) {
            val res = EmbeddedNcmEngine.searchArtists(query, page)
            if (res.isNotEmpty()) res else NcmApiClient.searchArtists(query, page)
        } else {
            val res = NcmApiClient.searchArtists(query, page)
            if (res.isNotEmpty()) res else EmbeddedNcmEngine.searchArtists(query, page)
        }
    }

    suspend fun searchAlbums(query: String, page: Int = 0): List<Album> {
        return if (isEmbedded) {
            val res = EmbeddedNcmEngine.searchAlbums(query, page)
            if (res.isNotEmpty()) res else NcmApiClient.searchAlbums(query, page)
        } else {
            val res = NcmApiClient.searchAlbums(query, page)
            if (res.isNotEmpty()) res else EmbeddedNcmEngine.searchAlbums(query, page)
        }
    }

    suspend fun getUserPlaylists(uid: Long, forceRefresh: Boolean = false): List<Playlist> {
        if (!forceRefresh) {
            userPlaylistsCache[uid]?.let { return it }
        }
        val cookie = SettingsRepository.userProfile.value.cookie
        val res = if (isEmbedded) {
            val r = EmbeddedNcmEngine.getUserPlaylists(uid, cookie)
            if (r.isNotEmpty()) r else NcmApiClient.getUserPlaylists(uid, cookie)
        } else {
            val r = NcmApiClient.getUserPlaylists(uid, cookie)
            if (r.isNotEmpty()) r else EmbeddedNcmEngine.getUserPlaylists(uid, cookie)
        }
        if (res.isNotEmpty()) {
            userPlaylistsCache[uid] = res
        }
        return if (res.isNotEmpty()) res else userPlaylistsCache[uid] ?: emptyList()
    }

    suspend fun likeSong(songId: Long, like: Boolean): Boolean {
        val cookie = SettingsRepository.userProfile.value.cookie
        return if (isEmbedded) {
            EmbeddedNcmEngine.likeSong(songId, like, cookie)
        } else {
            NcmApiClient.likeSong(songId, like)
        }
    }

    // 登录与会话辅助接口 (优先使用用户自建增强 API，确保 key/qr/check 同源无缝闭环)
    suspend fun getQrKey(): String? {
        return NcmApiClient.getQrKey() ?: EmbeddedNcmEngine.getQrKey()
    }

    suspend fun getQrCreate(key: String): Pair<String, String>? {
        // 返回 Pair(qrurl, qrimg)
        val res = NcmApiClient.getQrCreate(key)
        if (res != null && res.second.isNotBlank()) return res
        val qrUrl = "https://music.163.com/login?codekey=$key"
        return Pair(qrUrl, "")
    }

    suspend fun checkQrStatus(key: String): Pair<Int, String> {
        val res = NcmApiClient.checkQrStatus(key)
        if (res.first != -1) return res
        return EmbeddedNcmEngine.checkQrStatus(key)
    }

    suspend fun getUserAccount(cookie: String): UserProfile? {
        return NcmApiClient.getUserAccount(cookie) ?: EmbeddedNcmEngine.getUserAccount(cookie)
    }

    suspend fun sendCaptcha(phone: String, ctcode: String = "86"): Pair<Boolean, String> {
        return if (isEmbedded) {
            val res = EmbeddedNcmEngine.sendCaptcha(phone, ctcode)
            if (res.first) res else NcmApiClient.sendCaptcha(phone, ctcode)
        } else {
            val res = NcmApiClient.sendCaptcha(phone, ctcode)
            if (res.first) res else EmbeddedNcmEngine.sendCaptcha(phone, ctcode)
        }
    }

    suspend fun loginWithCaptcha(phone: String, captcha: String, ctcode: String = "86"): Pair<Boolean, Pair<UserProfile?, String>> {
        return if (isEmbedded) {
            val res = EmbeddedNcmEngine.loginWithCaptcha(phone, captcha, ctcode)
            if (res.first) res else NcmApiClient.loginWithCaptcha(phone, captcha, ctcode)
        } else {
            val res = NcmApiClient.loginWithCaptcha(phone, captcha, ctcode)
            if (res.first) res else EmbeddedNcmEngine.loginWithCaptcha(phone, captcha, ctcode)
        }
    }

    fun logout() {
        SettingsRepository.clearUserProfile()
    }

    suspend fun getDailyRecommendSongs(): List<Song> {
        val toplists = getToplists()
        val hotList = toplists.firstOrNull { it.name.contains("热歌") || it.name.contains("飙升") }
            ?: toplists.firstOrNull()
        return if (hotList != null) {
            val detail = getPlaylistDetail(hotList.id)
            detail?.tracks?.take(30) ?: emptyList()
        } else {
            emptyList()
        }
    }

    suspend fun getDailyRecommendPlaylist(): Playlist {
        val songs = getDailyRecommendSongs()
        val today = java.text.SimpleDateFormat("MM月dd日", java.util.Locale.getDefault()).format(java.util.Date())
        return Playlist(
            id = -100L,
            name = "每日推荐 · $today",
            coverUrl = songs.firstOrNull()?.coverUrl ?: "",
            playCount = 0L,
            trackCount = songs.size,
            description = "根据您的音乐喜好智能推荐，每日 6:00 更新",
            creatorName = "云音乐官方",
            tags = listOf("每日推荐", "私享", "探索"),
            tracks = songs
        )
    }

    fun getFavoriteSongsPlaylist(favoriteSongs: List<Song>, userNickname: String = "我"): Playlist {
        return Playlist(
            id = -1L,
            name = "我喜欢的音乐",
            coverUrl = favoriteSongs.firstOrNull()?.coverUrl ?: "",
            playCount = 0L,
            trackCount = favoriteSongs.size,
            description = "红心收藏歌曲库，共 ${favoriteSongs.size} 首",
            creatorName = userNickname,
            tags = listOf("我喜欢", "收藏"),
            tracks = favoriteSongs
        )
    }
}
