package com.nocturn.music.data.api

import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.Album
import com.nocturn.music.model.Artist
import com.nocturn.music.model.BannerItem
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.model.SongLyric
import com.nocturn.music.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object NcmApiClient {
    const val DEFAULT_API_URL = "https://ncmapi.rpixel.online"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getBaseUrl(): String {
        val configured = SettingsRepository.customApiUrl.value.trim().trimEnd('/')
        return if (configured.isNotBlank()) configured else DEFAULT_API_URL
    }

    private suspend fun executeGet(path: String, cookie: String = ""): String = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val separator = if (path.startsWith("/")) "" else "/"
        val effectiveCookie = if (cookie.isNotBlank()) cookie else SettingsRepository.userProfile.value.cookie

        // 双重保障：不仅在请求头携带 Cookie，亦向 Query 追加 cookie 参数，防止被反向代理或网关裁剪
        val fullPath = if (effectiveCookie.isNotBlank() && !path.contains("cookie=")) {
            val sep = if (path.contains("?")) "&" else "?"
            "$path${sep}cookie=${URLEncoder.encode(effectiveCookie, "UTF-8")}"
        } else {
            path
        }
        val fullUrl = "$baseUrl$separator$fullPath"

        val reqBuilder = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Nocturn/1.0.0")
            .header("Accept", "application/json, text/plain, */*")

        if (effectiveCookie.isNotBlank()) {
            reqBuilder.header("Cookie", effectiveCookie)
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful && !body.trim().startsWith("{")) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            body
        }
    }

    suspend fun ping(targetUrl: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val clean = targetUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$clean/banner?type=2")
                .header("User-Agent", "Nocturn/1.0.0")
                .build()
            client.newCall(req).execute().use { res ->
                val latency = System.currentTimeMillis() - start
                Pair(res.isSuccessful, latency)
            }
        } catch (e: Exception) {
            Pair(false, -1L)
        }
    }

    // 1. 轮播图 /banner?type=2 (移动端标准接口)
    suspend fun getBanners(): List<BannerItem> {
        return try {
            val jsonStr = executeGet("/banner?type=2")
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("banners") ?: JSONArray()
            val list = mutableListOf<BannerItem>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val picUrl = item.optString("pic", item.optString("imageUrl", ""))
                val titleColor = item.optString("titleColor", "red")
                val typeTitle = item.optString("typeTitle", "推荐")
                val targetId = item.optLong("targetId", 0L)
                val url = item.optString("url", "")
                if (picUrl.isNotBlank()) {
                    list.add(BannerItem(picUrl, titleColor, typeTitle, targetId, url))
                }
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 2. 推荐歌单 /personalized?limit=20
    suspend fun getPersonalizedPlaylists(limit: Int = 18): List<Playlist> {
        return try {
            val jsonStr = executeGet("/personalized?limit=$limit")
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    Playlist(
                        id = item.optLong("id"),
                        name = item.optString("name"),
                        coverUrl = item.optString("picUrl"),
                        playCount = item.optLong("playCount"),
                        trackCount = item.optInt("trackCount"),
                        description = item.optString("copywriter")
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 3. 官方榜单 /toplist
    suspend fun getToplists(): List<Playlist> {
        return try {
            val jsonStr = executeGet("/toplist")
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    Playlist(
                        id = item.optLong("id"),
                        name = item.optString("name"),
                        coverUrl = item.optString("coverImgUrl"),
                        playCount = item.optLong("playCount"),
                        trackCount = item.optInt("trackCount"),
                        description = item.optString("description")
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 4. 歌单详情 /playlist/detail?id=...
    suspend fun getPlaylistDetail(id: Long): Playlist? {
        return try {
            val jsonStr = executeGet("/playlist/detail?id=$id&timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            val p = json.optJSONObject("playlist") ?: return null

            val name = p.optString("name")
            val coverUrl = p.optString("coverImgUrl")
            val playCount = p.optLong("playCount")
            val trackCount = p.optInt("trackCount")
            val description = p.optString("description")

            val creator = p.optJSONObject("creator")
            val creatorName = creator?.optString("nickname") ?: ""
            val creatorAvatarUrl = creator?.optString("avatarUrl") ?: ""

            val tagsArr = p.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    tags.add(tagsArr.optString(i))
                }
            }

            // tracks 优先解析内嵌列表，后续结合 track/all 补齐
            val tracksArr = p.optJSONArray("tracks") ?: JSONArray()
            val tracks = mutableListOf<Song>()
            for (i in 0 until tracksArr.length()) {
                val t = tracksArr.getJSONObject(i)
                tracks.add(parseSongFromObject(t))
            }

            // 如果 tracks 数量较少，拉取完整曲目
            if (tracks.size < trackCount && trackCount > 0) {
                val moreTracks = getPlaylistAllTracks(id, limit = 100, offset = 0)
                if (moreTracks.isNotEmpty()) {
                    tracks.clear()
                    tracks.addAll(moreTracks)
                }
            }

            Playlist(
                id = id,
                name = name,
                coverUrl = coverUrl,
                playCount = playCount,
                trackCount = trackCount,
                description = description,
                creatorName = creatorName,
                creatorAvatarUrl = creatorAvatarUrl,
                tags = tags,
                tracks = tracks
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 5. 获取歌单所有歌曲 /playlist/track/all?id=... (网易云全量曲目接口)
    suspend fun getPlaylistAllTracks(id: Long, limit: Int = 100, offset: Int = 0): List<Song> {
        return try {
            val jsonStr = executeGet("/playlist/track/all?id=$id&limit=$limit&offset=$offset&timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("songs") ?: JSONArray()
            val list = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                list.add(parseSongFromObject(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 6. 热搜榜 /search/hot/detail (热搜榜单接口)
    suspend fun getHotSearchKeywords(): List<String> {
        return try {
            val jsonStr = executeGet("/search/hot/detail")
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val word = arr.getJSONObject(i).optString("searchWord")
                if (word.isNotBlank()) list.add(word)
            }
            list
        } catch (e: Exception) {
            listOf("周杰伦", "陈奕迅", "林俊杰", "晴天", "华语经典", "热门流行", "动漫", "轻音乐")
        }
    }

    // 7. 搜索 /search?keywords=...&type=...
    suspend fun searchSongs(query: String, page: Int = 0, limit: Int = 30): List<Song> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val jsonStr = executeGet("/search?keywords=$encoded&type=1&offset=${page * limit}&limit=$limit")
            val json = JSONObject(jsonStr)
            val result = json.optJSONObject("result") ?: return emptyList()
            val arr = result.optJSONArray("songs") ?: return emptyList()

            val list = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val id = s.optLong("id")
                val name = s.optString("name")
                val duration = s.optLong("dt", s.optLong("duration", 0L))
                val fee = s.optInt("fee", 0)

                val arArr = s.optJSONArray("artists") ?: s.optJSONArray("ar")
                val artists = mutableListOf<String>()
                if (arArr != null) {
                    for (j in 0 until arArr.length()) {
                        artists.add(arArr.getJSONObject(j).optString("name"))
                    }
                }
                val artistStr = if (artists.isEmpty()) "未知歌手" else artists.joinToString(" / ")

                val albumObj = s.optJSONObject("album") ?: s.optJSONObject("al")
                val albumName = albumObj?.optString("name") ?: ""
                val coverUrl = albumObj?.optString("picUrl", "")
                    ?: albumObj?.optJSONObject("artist")?.optString("img1v1Url", "") ?: ""

                list.add(
                    Song(
                        id = id,
                        title = name,
                        artist = artistStr,
                        album = albumName,
                        coverUrl = coverUrl,
                        durationMs = duration,
                        isVip = fee == 1 || fee == 4
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun searchPlaylists(query: String, page: Int = 0, limit: Int = 20): List<Playlist> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val jsonStr = executeGet("/search?keywords=$encoded&type=1000&offset=${page * limit}&limit=$limit")
            val json = JSONObject(jsonStr)
            val result = json.optJSONObject("result") ?: return emptyList()
            val arr = result.optJSONArray("playlists") ?: return emptyList()

            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                list.add(
                    Playlist(
                        id = p.optLong("id"),
                        name = p.optString("name"),
                        coverUrl = p.optString("coverImgUrl"),
                        playCount = p.optLong("playCount"),
                        trackCount = p.optInt("trackCount"),
                        description = p.optString("description"),
                        creatorName = p.optJSONObject("creator")?.optString("nickname") ?: ""
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchArtists(query: String, page: Int = 0, limit: Int = 20): List<Artist> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val jsonStr = executeGet("/search?keywords=$encoded&type=100&offset=${page * limit}&limit=$limit")
            val json = JSONObject(jsonStr)
            val result = json.optJSONObject("result") ?: return emptyList()
            val arr = result.optJSONArray("artists") ?: return emptyList()

            val list = mutableListOf<Artist>()
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                list.add(
                    Artist(
                        id = a.optLong("id"),
                        name = a.optString("name"),
                        avatarUrl = a.optString("picUrl", a.optString("img1v1Url", "")),
                        musicSize = a.optInt("musicSize"),
                        albumSize = a.optInt("albumSize")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchAlbums(query: String, page: Int = 0, limit: Int = 20): List<Album> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val jsonStr = executeGet("/search?keywords=$encoded&type=10&offset=${page * limit}&limit=$limit")
            val json = JSONObject(jsonStr)
            val result = json.optJSONObject("result") ?: return emptyList()
            val arr = result.optJSONArray("albums") ?: return emptyList()

            val list = mutableListOf<Album>()
            for (i in 0 until arr.length()) {
                val al = arr.getJSONObject(i)
                list.add(
                    Album(
                        id = al.optLong("id"),
                        name = al.optString("name"),
                        coverUrl = al.optString("picUrl"),
                        artistName = al.optJSONObject("artist")?.optString("name") ?: "",
                        publishTimeMs = al.optLong("publishTime"),
                        size = al.optInt("size")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 7.1 获取专辑详情与全部歌曲 /album?id=...
    suspend fun getAlbumDetail(id: Long): Playlist? {
        return try {
            val jsonStr = executeGet("/album?id=$id&timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            val albumObj = json.optJSONObject("album") ?: return null
            val songsArr = json.optJSONArray("songs") ?: JSONArray()
            val songs = mutableListOf<Song>()
            for (i in 0 until songsArr.length()) {
                songs.add(parseSongFromObject(songsArr.getJSONObject(i)))
            }
            val artistObj = albumObj.optJSONObject("artist")
            val artistName = artistObj?.optString("name") ?: ""
            val publishTime = albumObj.optLong("publishTime")
            val dateStr = if (publishTime > 0) {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(publishTime))
            } else ""
            val desc = albumObj.optString("description")
            val descFull = if (dateStr.isNotBlank()) "发行时间: $dateStr\n\n$desc" else desc
            Playlist(
                id = id,
                name = albumObj.optString("name"),
                coverUrl = albumObj.optString("picUrl"),
                playCount = 0L,
                trackCount = songs.size,
                description = descFull,
                creatorName = artistName,
                creatorAvatarUrl = artistObj?.optString("picUrl", "") ?: "",
                tags = listOf("专辑", artistName).filter { it.isNotBlank() },
                tracks = songs
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 7.2 获取歌手热门单曲 /artists?id=...
    suspend fun getArtistDetail(id: Long): Playlist? {
        return try {
            val jsonStr = executeGet("/artists?id=$id&timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            val artistObj = json.optJSONObject("artist") ?: return null
            val songsArr = json.optJSONArray("hotSongs") ?: JSONArray()
            val songs = mutableListOf<Song>()
            for (i in 0 until songsArr.length()) {
                songs.add(parseSongFromObject(songsArr.getJSONObject(i)))
            }
            val artistName = artistObj.optString("name")
            val avatarUrl = artistObj.optString("picUrl", artistObj.optString("img1v1Url", ""))
            val briefDesc = artistObj.optString("briefDesc")
            Playlist(
                id = id,
                name = "$artistName - 热门单曲",
                coverUrl = avatarUrl,
                playCount = 0L,
                trackCount = songs.size,
                description = briefDesc,
                creatorName = artistName,
                creatorAvatarUrl = avatarUrl,
                tags = listOf("歌手", "热门单曲"),
                tracks = songs
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 8. 获取歌词 /lyric/new?id=... (YRC 逐字歌词 + 翻译 + 罗马音)
    suspend fun getSongLyric(songId: Long): SongLyric {
        return try {
            val jsonStr = executeGet("/lyric/new?id=$songId")
            val json = JSONObject(jsonStr)

            val rawLrc = json.optJSONObject("lrc")?.optString("lyric")
            val rawTrans = json.optJSONObject("ytlrc")?.optString("lyric")
                ?: json.optJSONObject("tlyric")?.optString("lyric")
            val rawRoma = json.optJSONObject("yromalrc")?.optString("lyric")
                ?: json.optJSONObject("romalrc")?.optString("lyric")
            val rawYrc = json.optJSONObject("yrc")?.optString("lyric")

            LyricParser.parse(
                rawLrc = rawLrc,
                rawTranslationLrc = rawTrans,
                rawRomaLrc = rawRoma,
                rawYrc = rawYrc
            )
        } catch (e: Exception) {
            // 降级使用旧版 lyric 接口
            try {
                val jsonStr = executeGet("/lyric?id=$songId")
                val json = JSONObject(jsonStr)
                val rawLrc = json.optJSONObject("lrc")?.optString("lyric")
                val rawTrans = json.optJSONObject("tlyric")?.optString("lyric")
                LyricParser.parse(rawLrc, rawTrans)
            } catch (e2: Exception) {
                SongLyric(emptyList(), "")
            }
        }
    }

    // 9. 获取歌曲真实播放 URL /song/url/v1 (音质分级与回退逻辑，已强化 VIP 鉴权防试听)
    suspend fun getSongPlayUrl(songId: Long, level: String = "exhigh"): String {
        val userCookie = SettingsRepository.userProfile.value.cookie
        try {
            // 首先尝试请求 /song/url/v1
            val jsonStr = executeGet("/song/url/v1?id=$songId&level=$level&timestamp=${System.currentTimeMillis()}", userCookie)
            val json = JSONObject(jsonStr)
            val dataArr = json.optJSONArray("data")
            if (dataArr != null && dataArr.length() > 0) {
                val item = dataArr.getJSONObject(0)
                val url = item.optString("url")
                val freeTrialInfo = item.optJSONObject("freeTrialInfo")
                if (url.isNotBlank() && url != "null" && !url.contains("outer/url")) {
                    if (freeTrialInfo == null) {
                        return url.replace("^http:".toRegex(), "https:")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 备选请求 /song/url
        try {
            val jsonStr = executeGet("/song/url?id=$songId&timestamp=${System.currentTimeMillis()}", userCookie)
            val json = JSONObject(jsonStr)
            val dataArr = json.optJSONArray("data")
            if (dataArr != null && dataArr.length() > 0) {
                val item = dataArr.getJSONObject(0)
                val url = item.optString("url")
                if (url.isNotBlank() && url != "null" && !url.contains("outer/url")) {
                    return url.replace("^http:".toRegex(), "https:")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 降级尝试内置引擎 (EmbeddedNcmEngine 内置 EAPI / WEAPI 带 MUSIC_U)
        try {
            val embeddedUrl = EmbeddedNcmEngine.getSongPlayUrl(songId, level)
            if (embeddedUrl.isNotBlank() && !embeddedUrl.contains("outer/url")) {
                return embeddedUrl
            }
        } catch (e: Exception) {
            // ignore
        }

        // 终极备选：网易云官方 302 重定向外链（无需鉴权自动播放可用音源）
        return "https://music.163.com/song/media/outer/url?id=$songId.mp3"
    }

    // 10. 登录流程：/login/qr/key -> /login/qr/create -> /login/qr/check
    suspend fun getQrKey(): String? {
        return try {
            val jsonStr = executeGet("/login/qr/key?noCookie=true&timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            json.optJSONObject("data")?.optString("unikey")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getQrCreate(key: String): Pair<String, String>? {
        return try {
            val jsonStr = executeGet("/login/qr/create?key=$key&qrimg=true&timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            val data = json.optJSONObject("data") ?: return null
            val qrurl = data.optString("qrurl")
            val qrimg = data.optString("qrimg")
            Pair(qrurl, qrimg)
        } catch (e: Exception) {
            null
        }
    }

    // 检查扫码状态：返回 Pair(状态码, Cookie)
    // 800: 过期, 801: 等待扫码, 802: 待确认, 803: 授权登录成功
    suspend fun checkQrStatus(key: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val url = "$baseUrl/login/qr/check?key=$key&timestamp=${System.currentTimeMillis()}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Nocturn/1.0.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(req).execute().use { response ->
                val bodyStr = response.body.string()
                val json = try { JSONObject(bodyStr) } catch (e: Exception) { JSONObject() }
                val code = json.optInt("code", -1)
                var cookie = json.optString("cookie", "")
                if (cookie.isBlank() || !cookie.contains("MUSIC_U")) {
                    val setCookies = response.headers("Set-Cookie")
                    if (setCookies.isNotEmpty()) {
                        val headerCookie = setCookies.joinToString("; ") { it.substringBefore(";") }
                        if (headerCookie.contains("MUSIC_U")) {
                            cookie = if (cookie.isNotBlank()) "$cookie; $headerCookie" else headerCookie
                        } else if (cookie.isBlank()) {
                            cookie = headerCookie
                        }
                    }
                }
                Pair(code, cookie)
            }
        } catch (e: Exception) {
            Pair(-1, "")
        }
    }

    // 11. 获取用户账号与详情 /user/account & /user/detail
    suspend fun getUserAccount(cookie: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val encodedCookie = URLEncoder.encode(cookie, "UTF-8")
            val url = "$baseUrl/user/account?cookie=$encodedCookie&timestamp=${System.currentTimeMillis()}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Nocturn/1.0.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("Cookie", cookie)
                .build()

            client.newCall(req).execute().use { response ->
                val bodyStr = response.body.string()
                val json = JSONObject(bodyStr)
                val profile = json.optJSONObject("profile")
                val account = json.optJSONObject("account")
                if (profile == null && account == null) return@withContext null
                val uid = profile?.optLong("userId") ?: account?.optLong("id") ?: 0L
                val vipType = profile?.optInt("vipType") ?: account?.optInt("vipType") ?: 0
                val nickname = profile?.optString("nickname") ?: account?.optString("userName") ?: "云村村民"
                val avatarUrl = profile?.optString("avatarUrl") ?: ""
                val signature = profile?.optString("signature") ?: ""
                UserProfile(
                    userId = uid,
                    nickname = nickname,
                    avatarUrl = avatarUrl,
                    vipType = vipType,
                    signature = signature,
                    isLogin = true,
                    cookie = cookie
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    // 12. 获取用户歌单 /user/playlist?uid=...
    suspend fun getUserPlaylists(uid: Long, cookie: String = ""): List<Playlist> {
        return try {
            val jsonStr = executeGet("/user/playlist?uid=$uid&limit=50&timestamp=${System.currentTimeMillis()}", cookie)
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("playlist") ?: JSONArray()
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                list.add(
                    Playlist(
                        id = p.optLong("id"),
                        name = p.optString("name"),
                        coverUrl = p.optString("coverImgUrl"),
                        playCount = p.optLong("playCount"),
                        trackCount = p.optInt("trackCount"),
                        description = p.optString("description"),
                        creatorName = p.optJSONObject("creator")?.optString("nickname") ?: ""
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 13. 红心喜欢音乐 /like?id=...&like=true|false
    suspend fun likeSong(songId: Long, like: Boolean): Boolean {
        return try {
            val cookie = SettingsRepository.userProfile.value.cookie
            if (cookie.isBlank()) return false
            val jsonStr = executeGet("/like?id=$songId&like=$like&timestamp=${System.currentTimeMillis()}", cookie)
            val json = JSONObject(jsonStr)
            json.optInt("code") == 200
        } catch (e: Exception) {
            false
        }
    }

    // 14. 发送短信验证码 /captcha/sent?phone=...&ctcode=86
    suspend fun sendCaptcha(phone: String, ctcode: String = "86"): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val url = "$baseUrl/captcha/sent?phone=$phone&ctcode=$ctcode&timestamp=${System.currentTimeMillis()}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Nocturn/1.0.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(req).execute().use { response ->
                val bodyStr = response.body.string()
                val json = try { JSONObject(bodyStr) } catch (e: Exception) { JSONObject() }
                val code = json.optInt("code", response.code)
                if (code == 200 || response.isSuccessful) {
                    Pair(true, "验证码已发送")
                } else {
                    val msg = json.optString("message", json.optString("msg", "发送失败 ($code)"))
                    Pair(false, msg)
                }
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "网络请求失败")
        }
    }

    // 15. 手机号 + 短信验证码登录 /login/cellphone?phone=...&captcha=...
    suspend fun loginWithCaptcha(phone: String, captcha: String, ctcode: String = "86"): Pair<Boolean, Pair<UserProfile?, String>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val url = "$baseUrl/login/cellphone?phone=$phone&captcha=$captcha&countrycode=$ctcode&timestamp=${System.currentTimeMillis()}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Nocturn/1.0.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(req).execute().use { response ->
                val bodyStr = response.body.string()
                val json = try { JSONObject(bodyStr) } catch (e: Exception) { JSONObject() }
                val code = json.optInt("code", response.code)
                if (code == 200) {
                    var cookie = json.optString("cookie", "")
                    if (cookie.isBlank()) {
                        val setCookies = response.headers("Set-Cookie")
                        if (setCookies.isNotEmpty()) {
                            cookie = setCookies.joinToString("; ") { it.substringBefore(";") }
                        }
                    }
                    val profile = json.optJSONObject("profile")
                    val uid = profile?.optLong("userId") ?: 0L
                    val userProfile = UserProfile(
                        userId = uid,
                        nickname = profile?.optString("nickname") ?: "云村村民",
                        avatarUrl = profile?.optString("avatarUrl") ?: "",
                        vipType = profile?.optInt("vipType") ?: 0,
                        signature = profile?.optString("signature") ?: "",
                        isLogin = true,
                        cookie = cookie
                    )
                    Pair(true, Pair(userProfile, "登录成功"))
                } else {
                    val msg = json.optString("message", json.optString("msg", "登录失败 ($code)"))
                    Pair(false, Pair(null, msg))
                }
            }
        } catch (e: Exception) {
            Pair(false, Pair(null, e.message ?: "登录异常"))
        }
    }

    private fun parseSongFromObject(t: JSONObject): Song {
        val songId = t.optLong("id")
        val songTitle = t.optString("name")
        val duration = t.optLong("dt", t.optLong("duration", 0L))
        val fee = t.optInt("fee", 0)

        val arArray = t.optJSONArray("ar") ?: t.optJSONArray("artists")
        val artistNames = mutableListOf<String>()
        if (arArray != null) {
            for (j in 0 until arArray.length()) {
                artistNames.add(arArray.getJSONObject(j).optString("name"))
            }
        }
        val artistStr = if (artistNames.isEmpty()) "未知歌手" else artistNames.joinToString(" / ")

        val alObj = t.optJSONObject("al") ?: t.optJSONObject("album")
        val albumName = alObj?.optString("name") ?: ""
        val albumPicUrl = alObj?.optString("picUrl") ?: ""

        return Song(
            id = songId,
            title = songTitle,
            artist = artistStr,
            album = albumName,
            coverUrl = albumPicUrl,
            durationMs = duration,
            isVip = fee == 1 || fee == 4
        )
    }
}
