package com.nocturn.music.data.api

import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.Album
import com.nocturn.music.model.Artist
import com.nocturn.music.model.BannerItem
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.model.SongLyric
import com.nocturn.music.model.SongWiki
import com.nocturn.music.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object EmbeddedNcmEngine {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class NetEaseResult(
        val body: String,
        val cookies: Map<String, String>,
        val rawSetCookie: String
    )

    fun extractCookieValue(cookieHeader: String, key: String): String {
        for (part in cookieHeader.split(";")) {
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val k = trimmed.substring(0, eq).trim()
                if (k.equals(key, ignoreCase = true)) {
                    return trimmed.substring(eq + 1).trim().trim('"', '\'')
                }
            }
        }
        return ""
    }

    private fun extractCookies(setCookieHeaders: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (header in setCookieHeaders) {
            val parts = header.split(";")
            if (parts.isNotEmpty()) {
                val pair = parts[0].trim()
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val k = pair.substring(0, eq).trim()
                    val v = pair.substring(eq + 1).trim()
                    if (k.isNotBlank()) {
                        map[k] = v
                    }
                }
            }
        }
        return map
    }

    suspend fun executeWeapiWithResult(path: String, data: JSONObject, customCookie: String = ""): NetEaseResult =
        withContext(Dispatchers.IO) {
            val url = "https://music.163.com/weapi/$path"
            val text = data.toString()
            val (params, encSecKey) = NcmCrypto.weapi(text)

            val form = FormBody.Builder()
                .add("params", params)
                .add("encSecKey", encSecKey)
                .build()

            val defaultCookie =
                "os=pc; appver=2.9.7; osver=Microsoft-Windows-10; NMTID=00OyqdGCxx--QXdj0rIpbAwMVOn3hYAAAGgdKAnMQ; __remember_me=true"
            val userCookie = if (customCookie.isNotBlank()) customCookie else SettingsRepository.userProfile.value.cookie
            val mergedCookie = if (userCookie.isNotBlank()) "$defaultCookie; $userCookie" else defaultCookie

            val request = Request.Builder()
                .url(url)
                .post(form)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("Cookie", mergedCookie)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                val setCookies = response.headers("Set-Cookie")
                val cookieMap = extractCookies(setCookies)
                val rawCookie = setCookies.joinToString("; ") { it.substringBefore(";").trim() }
                NetEaseResult(body, cookieMap, rawCookie)
            }
        }

    private suspend fun requestWeapi(path: String, data: JSONObject, customCookie: String = ""): String {
        return executeWeapiWithResult(path, data, customCookie).body
    }

    private suspend fun requestEapi(urlPath: String, data: JSONObject, customCookie: String = ""): String =
        withContext(Dispatchers.IO) {
            val fullUrl = "https://interface.music.163.com/eapi/$urlPath"
            val defaultCookie =
                "os=android; appver=8.9.70; channel=netease; osver=14; NMTID=00OyqdGCxx--QXdj0rIpbAwMVOn3hYAAAGgdKAnMQ"
            val userCookie = if (customCookie.isNotBlank()) customCookie else SettingsRepository.userProfile.value.cookie
            val mergedCookie = if (userCookie.isNotBlank()) "$defaultCookie; $userCookie" else defaultCookie

            // 规范化组装官方 Android 客户端 EAPI 所需的 data.header，携带 MUSIC_U 进行 VIP 鉴权
            val headerObj = JSONObject().apply {
                put("osver", "14")
                put("deviceId", "nocturn_${System.currentTimeMillis()}")
                put("appver", "8.9.70")
                put("versioncode", "140")
                put("mobilename", "Pixel")
                put("buildver", (System.currentTimeMillis() / 1000).toString())
                put("resolution", "1080x2400")
                put("__csrf", extractCookieValue(mergedCookie, "__csrf"))
                put("os", "android")
                put("channel", "netease")
                put("requestId", "${System.currentTimeMillis()}_${(1000..9999).random()}")
                val musicU = extractCookieValue(mergedCookie, "MUSIC_U")
                if (musicU.isNotBlank()) {
                    put("MUSIC_U", musicU)
                }
                val musicA = extractCookieValue(mergedCookie, "MUSIC_A")
                if (musicA.isNotBlank()) {
                    put("MUSIC_A", musicA)
                }
            }
            data.put("header", headerObj)

            val text = data.toString()
            val params = NcmCrypto.eapi("/api/$urlPath", text)

            val form = FormBody.Builder()
                .add("params", params)
                .build()

            val request = Request.Builder()
                .url(fullUrl)
                .post(form)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) NeteaseMusic/8.9.70")
                .header("Referer", "https://interface.music.163.com/")
                .header("Cookie", mergedCookie)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyBytes = response.body.bytes()
                val decrypted = NcmCrypto.eapiDecrypt(bodyBytes)
                if (decrypted.isNotBlank()) {
                    decrypted
                } else {
                    String(bodyBytes, Charsets.UTF_8)
                }
            }
        }

    // 1. 发现页轮播图
    suspend fun getBanners(): List<BannerItem> {
        return try {
            val data = JSONObject().apply {
                put("clientType", "pc")
            }
            val jsonStr = requestWeapi("v2/banner/get", data)
            val json = JSONObject(jsonStr)
            val bannersArray = json.optJSONArray("banners") ?: JSONArray()
            val list = mutableListOf<BannerItem>()
            for (i in 0 until bannersArray.length()) {
                val item = bannersArray.getJSONObject(i)
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

    // 2. 热门推荐歌单
    suspend fun getPersonalizedPlaylists(limit: Int = 20): List<Playlist> {
        return try {
            val data = JSONObject().apply {
                put("limit", limit)
                put("total", true)
                put("n", limit)
            }
            val jsonStr = requestWeapi("personalized/playlist", data)
            val json = JSONObject(jsonStr)
            val listArray = json.optJSONArray("result") ?: JSONArray()
            val result = mutableListOf<Playlist>()
            for (i in 0 until listArray.length()) {
                val item = listArray.getJSONObject(i)
                result.add(
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
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 3. 官方榜单
    suspend fun getToplists(): List<Playlist> {
        return try {
            val data = JSONObject()
            val jsonStr = requestWeapi("toplist", data)
            val json = JSONObject(jsonStr)
            val listArray = json.optJSONArray("list") ?: JSONArray()
            val result = mutableListOf<Playlist>()
            for (i in 0 until listArray.length()) {
                val item = listArray.getJSONObject(i)
                result.add(
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
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 4. 歌单详情
    suspend fun getPlaylistDetail(id: Long): Playlist? {
        return try {
            val data = JSONObject().apply {
                put("id", id)
                put("n", 100)
                put("s", 0)
            }
            val jsonStr = requestWeapi("v6/playlist/detail", data)
            val json = JSONObject(jsonStr)
            val plObj = json.optJSONObject("playlist") ?: return null

            val name = plObj.optString("name")
            val coverImgUrl = plObj.optString("coverImgUrl")
            val playCount = plObj.optLong("playCount")
            val trackCount = plObj.optInt("trackCount")
            val description = plObj.optString("description")

            val creatorObj = plObj.optJSONObject("creator")
            val creatorName = creatorObj?.optString("nickname") ?: ""
            val creatorAvatarUrl = creatorObj?.optString("avatarUrl") ?: ""

            val tagsArray = plObj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    tags.add(tagsArray.optString(i))
                }
            }

            val tracksArray = plObj.optJSONArray("tracks") ?: JSONArray()
            val tracks = mutableListOf<Song>()
            for (i in 0 until tracksArray.length()) {
                tracks.add(parseSongFromObject(tracksArray.getJSONObject(i)))
            }

            // 若返回的 tracks 不完整，解析 trackIds 并分批获取完整信息
            val trackIdsArr = plObj.optJSONArray("trackIds")
            if (trackIdsArr != null && tracks.size < trackIdsArr.length() && trackIdsArr.length() > 0) {
                val allIds = mutableListOf<Long>()
                for (i in 0 until trackIdsArr.length()) {
                    val tid = trackIdsArr.getJSONObject(i).optLong("id")
                    if (tid > 0L) allIds.add(tid)
                }
                val moreSongs = getSongDetails(allIds)
                if (moreSongs.isNotEmpty()) {
                    tracks.clear()
                    tracks.addAll(moreSongs)
                }
            }

            Playlist(
                id = id,
                name = name,
                coverUrl = coverImgUrl,
                playCount = playCount,
                trackCount = if (trackCount > 0) trackCount else tracks.size,
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

    suspend fun getSongDetails(songIds: List<Long>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        return try {
            val list = mutableListOf<Song>()
            for (chunk in songIds.chunked(200)) {
                val cArray = JSONArray()
                chunk.forEach { id ->
                    cArray.put(JSONObject().apply { put("id", id) })
                }
                val data = JSONObject().apply {
                    put("c", cArray.toString())
                }
                val jsonStr = requestWeapi("v3/song/detail", data)
                val json = JSONObject(jsonStr)
                val songsArr = json.optJSONArray("songs") ?: JSONArray()
                for (i in 0 until songsArr.length()) {
                    list.add(parseSongFromObject(songsArr.getJSONObject(i)))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 5. 歌单全部曲目
    suspend fun getPlaylistAllTracks(id: Long, limit: Int = 100, offset: Int = 0): List<Song> {
        val detail = getPlaylistDetail(id) ?: return emptyList()
        if (offset >= detail.tracks.size) return emptyList()
        return detail.tracks.drop(offset).take(limit)
    }

    // 6. 热搜词列表
    suspend fun getHotSearchKeywords(): List<String> {
        return try {
            val data = JSONObject()
            val jsonStr = requestWeapi("search/hot/detail", data)
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val word = arr.getJSONObject(i).optString("searchWord")
                if (word.isNotBlank()) list.add(word)
            }
            if (list.isNotEmpty()) list else getDefaultHotWords()
        } catch (e: Exception) {
            getDefaultHotWords()
        }
    }

    private fun getDefaultHotWords(): List<String> =
        listOf("周杰伦", "陈奕迅", "林俊杰", "晴天", "华语经典", "热门流行", "动漫", "轻音乐")

    // 7. 搜索
    suspend fun searchSongs(query: String, page: Int = 0, limit: Int = 30): List<Song> {
        return try {
            val data = JSONObject().apply {
                put("s", query)
                put("type", 1)
                put("limit", limit)
                put("offset", page * limit)
                put("total", true)
            }
            val jsonStr = requestWeapi("search/get/web", data)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val songsArray = resultObj.optJSONArray("songs") ?: return emptyList()

            val list = mutableListOf<Song>()
            for (i in 0 until songsArray.length()) {
                list.add(parseSongFromObject(songsArray.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchPlaylists(query: String, page: Int = 0, limit: Int = 20): List<Playlist> {
        return try {
            val data = JSONObject().apply {
                put("s", query)
                put("type", 1000)
                put("limit", limit)
                put("offset", page * limit)
                put("total", true)
            }
            val jsonStr = requestWeapi("search/get/web", data)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val arr = resultObj.optJSONArray("playlists") ?: return emptyList()

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
            val data = JSONObject().apply {
                put("s", query)
                put("type", 100)
                put("limit", limit)
                put("offset", page * limit)
            }
            val jsonStr = requestWeapi("search/get/web", data)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val arr = resultObj.optJSONArray("artists") ?: return emptyList()

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
            val data = JSONObject().apply {
                put("s", query)
                put("type", 10)
                put("limit", limit)
                put("offset", page * limit)
            }
            val jsonStr = requestWeapi("search/get/web", data)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val arr = resultObj.optJSONArray("albums") ?: return emptyList()

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

    // 7.1 获取专辑详情与全部歌曲
    suspend fun getAlbumDetail(id: Long): Playlist? {
        return try {
            val jsonStr = requestWeapi("v1/album/$id", JSONObject())
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

    // 7.2 获取歌手热门单曲
    suspend fun getArtistDetail(id: Long): Playlist? {
        return try {
            val jsonStr = requestWeapi("v1/artist/$id", JSONObject())
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

    // 8. 歌词解析 (带 YRC 逐字歌词)
    suspend fun getSongLyric(songId: Long): SongLyric {
        return try {
            val data = JSONObject().apply {
                put("id", songId.toString())
                put("cp", false)
                put("tv", 0)
                put("lv", 0)
                put("rv", 0)
                put("kv", 0)
                put("yv", 0)
                put("ytv", 0)
                put("yrv", 0)
            }
            val jsonStr = requestEapi("song/lyric/v1", data)
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
            // weapi 降级重试
            try {
                val data = JSONObject().apply {
                    put("id", songId)
                    put("lv", -1)
                    put("tv", -1)
                }
                val jsonStr = requestWeapi("song/lyric?lv=-1&kv=-1&tv=-1", data)
                val json = JSONObject(jsonStr)
                val rawLrc = json.optJSONObject("lrc")?.optString("lyric")
                val rawTrans = json.optJSONObject("tlyric")?.optString("lyric")
                LyricParser.parse(rawLrc, rawTrans)
            } catch (e2: Exception) {
                SongLyric(emptyList(), "")
            }
        }
    }

    // 9. 获取歌曲真实播放 URL (多层 VIP 鉴权与降级机制，彻底规避 30s 试听限制)
    suspend fun getSongPlayUrl(songId: Long, level: String = "exhigh"): String {
        // 1. 尝试 EAPI 接口 (原生支持音质档位及 VIP 高清流，已注入 MUSIC_U 移动端鉴权)
        try {
            val data = JSONObject().apply {
                put("ids", "[$songId]")
                put("level", level)
                put("encodeType", if (level == "lossless" || level == "hires") "flac" else "mp3")
            }
            val jsonStr = requestEapi("song/enhance/player/url/v1", data)
            val json = JSONObject(jsonStr)
            val dataArr = json.optJSONArray("data")
            if (dataArr != null && dataArr.length() > 0) {
                val item = dataArr.getJSONObject(0)
                val url = item.optString("url")
                val freeTrialInfo = item.optJSONObject("freeTrialInfo")
                // 若返回了非外链的真实流媒体地址
                if (url.isNotBlank() && url != "null" && !url.contains("outer/url")) {
                    if (freeTrialInfo == null) {
                        return url.replace("^http:".toRegex(), "https:")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. 尝试 WEAPI 接口 (Web 端核心鉴权接口 /weapi/song/enhance/player/url)
        try {
            val br = when (level) {
                "hires", "lossless" -> 999000
                "exhigh" -> 320000
                "standard" -> 128000
                else -> 320000
            }
            val dataWeapi = JSONObject().apply {
                put("ids", "[$songId]")
                put("br", br)
            }
            val jsonStr = requestWeapi("song/enhance/player/url", dataWeapi)
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

        // 3. 尝试 NcmApiClient 透传 Cookie 请求
        try {
            val remoteUrl = NcmApiClient.getSongPlayUrl(songId, level)
            if (remoteUrl.isNotBlank() && !remoteUrl.contains("outer/url")) {
                return remoteUrl
            }
        } catch (e: Exception) {
            // ignore
        }

        // 4. 外链 fallback (仅作为万一失败时的最终兜底)
        return "https://music.163.com/song/media/outer/url?id=$songId.mp3"
    }

    // 10. 登录流程
    suspend fun getQrKey(): String? {
        return try {
            val data = JSONObject().apply {
                put("type", 1)
            }
            val jsonStr = requestWeapi("login/qrcode/unikey", data)
            val json = JSONObject(jsonStr)
            json.optString("unikey")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun checkQrStatus(key: String): Pair<Int, String> {
        return try {
            val data = JSONObject().apply {
                put("key", key)
                put("type", 1)
            }
            val result = executeWeapiWithResult("login/qrcode/client/login", data)
            val json = JSONObject(result.body)
            val code = json.optInt("code")
            var cookie = json.optString("cookie", "")
            if (cookie.isBlank() || !cookie.contains("MUSIC_U")) {
                if (result.cookies.containsKey("MUSIC_U")) {
                    cookie = result.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                } else if (result.rawSetCookie.isNotBlank()) {
                    cookie = result.rawSetCookie
                }
            }
            Pair(code, cookie)
        } catch (e: Exception) {
            Pair(-1, "")
        }
    }

    suspend fun sendCaptcha(phone: String, ctcode: String = "86"): Pair<Boolean, String> {
        return try {
            val data = JSONObject().apply {
                put("cellphone", phone)
                put("ctcode", ctcode)
            }
            val jsonStr = requestWeapi("sms/captcha/sent", data)
            val json = JSONObject(jsonStr)
            val code = json.optInt("code")
            if (code == 200) {
                Pair(true, "验证码已发送")
            } else {
                val msg = json.optString("message", json.optString("msg", "发送失败 ($code)"))
                Pair(false, msg)
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "网络请求失败")
        }
    }

    suspend fun loginWithCaptcha(phone: String, captcha: String, ctcode: String = "86"): Pair<Boolean, Pair<UserProfile?, String>> {
        return try {
            val data = JSONObject().apply {
                put("phone", phone)
                put("captcha", captcha)
                put("countrycode", ctcode)
                put("rememberLogin", true)
            }
            val result = executeWeapiWithResult("login/cellphone", data)
            val json = JSONObject(result.body)
            val code = json.optInt("code")
            if (code == 200) {
                var cookie = json.optString("cookie", "")
                if (cookie.isBlank() || !cookie.contains("MUSIC_U")) {
                    if (result.cookies.containsKey("MUSIC_U")) {
                        cookie = result.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    } else if (result.rawSetCookie.isNotBlank()) {
                        cookie = result.rawSetCookie
                    }
                }
                val profile = json.optJSONObject("profile")
                val account = json.optJSONObject("account")
                val uid = profile?.optLong("userId") ?: account?.optLong("id") ?: 0L
                val vipType = profile?.optInt("vipType") ?: account?.optInt("vipType") ?: 0
                val userProfile = UserProfile(
                    userId = uid,
                    nickname = profile?.optString("nickname") ?: account?.optString("userName") ?: "云村村民",
                    avatarUrl = profile?.optString("avatarUrl") ?: "",
                    vipType = vipType,
                    signature = profile?.optString("signature") ?: "",
                    isLogin = true,
                    cookie = cookie
                )
                Pair(true, Pair(userProfile, "登录成功"))
            } else {
                val msg = json.optString("message", json.optString("msg", "登录失败 ($code)"))
                Pair(false, Pair(null, msg))
            }
        } catch (e: Exception) {
            Pair(false, Pair(null, e.message ?: "登录异常"))
        }
    }

    suspend fun getUserAccount(cookie: String): UserProfile? {
        return try {
            val data = JSONObject()
            var jsonStr = ""
            try {
                jsonStr = requestWeapi("nuser/account/get", data, cookie)
            } catch (e: Exception) {
                jsonStr = requestWeapi("w/nuser/account/get", data, cookie)
            }
            var json = JSONObject(jsonStr)
            var profile = json.optJSONObject("profile")
            var account = json.optJSONObject("account")
            if (profile == null && account == null) {
                try {
                    jsonStr = requestWeapi("w/nuser/account/get", data, cookie)
                    json = JSONObject(jsonStr)
                    profile = json.optJSONObject("profile")
                    account = json.optJSONObject("account")
                } catch (e: Exception) {
                    // ignore
                }
            }

            if (profile == null && account == null) return null

            val uid = profile?.optLong("userId") ?: account?.optLong("id") ?: 0L
            val nickname = profile?.optString("nickname") ?: account?.optString("userName") ?: "云村村民"
            val avatarUrl = profile?.optString("avatarUrl") ?: ""
            val vipType = profile?.optInt("vipType") ?: account?.optInt("vipType") ?: 0
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
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserPlaylists(uid: Long, cookie: String = ""): List<Playlist> {
        return try {
            val data = JSONObject().apply {
                put("uid", uid)
                put("limit", 50)
                put("offset", 0)
                put("includeVideo", true)
            }
            val jsonStr = requestWeapi("user/playlist", data, cookie)
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

    suspend fun likeSong(songId: Long, like: Boolean, cookie: String): Boolean {
        return try {
            val data = JSONObject().apply {
                put("trackId", songId)
                put("like", like)
            }
            val jsonStr = requestWeapi("radio/like", data, cookie)
            val json = JSONObject(jsonStr)
            json.optInt("code") == 200
        } catch (e: Exception) {
            false
        }
    }

    // 20. 添加歌曲到歌单
    suspend fun addToPlaylist(playlistId: Long, songId: Long, customCookie: String = ""): Boolean {
        return try {
            val data = JSONObject().apply {
                put("op", "add")
                put("pid", playlistId)
                put("trackIds", "[$songId]")
                put("tracks", "$songId")
            }
            val jsonStr = requestWeapi("playlist/manipulate/tracks", data, customCookie)
            val json = JSONObject(jsonStr)
            val code = json.optInt("code", 0)
            code == 200 || code == 502
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 21. 获取歌曲音乐百科
    suspend fun getSongWiki(songId: Long, customCookie: String = ""): SongWiki? {
        return try {
            val data = JSONObject().apply {
                put("songId", songId)
            }
            val jsonStr = requestEapi("song/play/about/block/page", data, customCookie)
            val json = JSONObject(jsonStr)
            val dataObj = json.optJSONObject("data") ?: return null
            val blocks = dataObj.optJSONArray("blocks") ?: JSONArray()

            val styles = mutableListOf<String>()
            var desc = ""
            val credits = mutableListOf<Pair<String, String>>()
            var language = ""

            for (i in 0 until blocks.length()) {
                val block = blocks.getJSONObject(i)
                val code = block.optString("code")
                when (code) {
                    "SONG_PLAY_ABOUT_CREATIVE_TEAM" -> {
                        val creatives = block.optJSONArray("creatives") ?: JSONArray()
                        for (c in 0 until creatives.length()) {
                            val cr = creatives.getJSONObject(c)
                            val userType = cr.optString("userType")
                            val userArr = cr.optJSONArray("users") ?: JSONArray()
                            val names = mutableListOf<String>()
                            for (u in 0 until userArr.length()) {
                                names.add(userArr.getJSONObject(u).optString("name"))
                            }
                            if (userType.isNotBlank() && names.isNotEmpty()) {
                                credits.add(userType to names.joinToString(" / "))
                            }
                        }
                    }
                    "SONG_PLAY_ABOUT_SONG_TAG" -> {
                        val tags = block.optJSONArray("tags") ?: JSONArray()
                        for (t in 0 until tags.length()) {
                            val tag = tags.getJSONObject(t).optString("name")
                            if (tag.isNotBlank()) styles.add(tag)
                        }
                    }
                    "SONG_PLAY_ABOUT_INTRODUCTION" -> {
                        desc = block.optString("content", block.optString("text", ""))
                    }
                    "SONG_PLAY_ABOUT_BASIC_INFO" -> {
                        language = block.optString("language", "")
                    }
                }
            }

            SongWiki(
                songId = songId,
                styles = styles,
                description = desc,
                credits = credits,
                language = language
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseSongFromObject(s: JSONObject): Song {
        val songId = s.optLong("id")
        val title = s.optString("name")
        val duration = s.optLong("dt", s.optLong("duration", 0L))
        val fee = s.optInt("fee", 0)

        val arArray = s.optJSONArray("ar") ?: s.optJSONArray("artists")
        val artistNames = mutableListOf<String>()
        if (arArray != null) {
            for (j in 0 until arArray.length()) {
                artistNames.add(arArray.getJSONObject(j).optString("name"))
            }
        }
        val artistStr = if (artistNames.isEmpty()) "未知歌手" else artistNames.joinToString(" / ")

        val alObj = s.optJSONObject("al") ?: s.optJSONObject("album")
        val albumName = alObj?.optString("name") ?: ""
        val albumPicUrl = alObj?.optString("picUrl")
            ?: alObj?.optJSONObject("artist")?.optString("img1v1Url", "") ?: ""

        return Song(
            id = songId,
            title = title,
            artist = artistStr,
            album = albumName,
            coverUrl = albumPicUrl,
            durationMs = duration,
            isVip = fee == 1 || fee == 4
        )
    }
}
