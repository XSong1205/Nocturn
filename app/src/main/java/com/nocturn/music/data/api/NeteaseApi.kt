package com.nocturn.music.data.api

import com.nocturn.music.model.Album
import com.nocturn.music.model.Artist
import com.nocturn.music.model.BannerItem
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.model.SongLyric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object NeteaseApi {
    private const val BASE_URL = "https://music.163.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private suspend fun executeGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .header("Accept", "application/json, text/plain, */*")
            .header("Cookie", "os=pc; appver=2.9.7; osver=Microsoft-Windows-10; NMTID=00OyqdGCxx--QXdj0rIpbAwMVOn3hYAAAGgdKAnMQ")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            response.body.string()
        }
    }

    suspend fun getBanners(): List<BannerItem> {
        return try {
            val jsonStr = executeGet("$BASE_URL/api/v2/banner/get?clientType=pc")
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

    suspend fun getToplists(): List<Playlist> {
        return try {
            val jsonStr = executeGet("$BASE_URL/api/toplist")
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

    suspend fun getPersonalizedPlaylists(limit: Int = 10): List<Playlist> {
        return try {
            val jsonStr = executeGet("$BASE_URL/api/personalized?limit=$limit")
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

    suspend fun getPlaylistDetail(id: Long): Playlist? {
        return try {
            val jsonStr = executeGet("$BASE_URL/api/v6/playlist/detail?id=$id")
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
                val t = tracksArray.getJSONObject(i)
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

                tracks.add(
                    Song(
                        id = songId,
                        title = songTitle,
                        artist = artistStr,
                        album = albumName,
                        coverUrl = albumPicUrl,
                        durationMs = duration,
                        isVip = fee == 1 || fee == 4
                    )
                )
            }

            Playlist(
                id = id,
                name = name,
                coverUrl = coverImgUrl,
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

    suspend fun searchSongs(query: String, page: Int = 0, limit: Int = 30): List<Song> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/api/search/get/web?s=$encodedQuery&type=1&offset=${page * limit}&limit=$limit&total=true"
            val jsonStr = executeGet(url)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val songsArray = resultObj.optJSONArray("songs") ?: return emptyList()

            val list = mutableListOf<Song>()
            for (i in 0 until songsArray.length()) {
                val s = songsArray.getJSONObject(i)
                val id = s.optLong("id")
                val title = s.optString("name")
                val duration = s.optLong("duration", s.optLong("dt", 0L))
                val fee = s.optInt("fee", 0)

                val artistsArr = s.optJSONArray("artists")
                val artists = mutableListOf<String>()
                if (artistsArr != null) {
                    for (j in 0 until artistsArr.length()) {
                        artists.add(artistsArr.getJSONObject(j).optString("name"))
                    }
                }
                val artistStr = if (artists.isEmpty()) "未知歌手" else artists.joinToString(" / ")

                val albumObj = s.optJSONObject("album")
                val albumName = albumObj?.optString("name") ?: ""
                val coverUrl = albumObj?.optJSONObject("artist")?.optString("img1v1Url", "")
                    ?: albumObj?.optString("picUrl", "") ?: ""

                list.add(
                    Song(
                        id = id,
                        title = title,
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
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/api/search/get/web?s=$encodedQuery&type=1000&offset=${page * limit}&limit=$limit&total=true"
            val jsonStr = executeGet(url)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val playlistsArray = resultObj.optJSONArray("playlists") ?: return emptyList()

            val list = mutableListOf<Playlist>()
            for (i in 0 until playlistsArray.length()) {
                val p = playlistsArray.getJSONObject(i)
                val id = p.optLong("id")
                val name = p.optString("name")
                val coverUrl = p.optString("coverImgUrl")
                val playCount = p.optLong("playCount")
                val trackCount = p.optInt("trackCount")
                val description = p.optString("description")
                val creatorName = p.optJSONObject("creator")?.optString("nickname") ?: ""

                list.add(
                    Playlist(
                        id = id,
                        name = name,
                        coverUrl = coverUrl,
                        playCount = playCount,
                        trackCount = trackCount,
                        description = description,
                        creatorName = creatorName
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun searchArtists(query: String, page: Int = 0, limit: Int = 20): List<Artist> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/api/search/get/web?s=$encodedQuery&type=100&offset=${page * limit}&limit=$limit&total=true"
            val jsonStr = executeGet(url)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val artistsArray = resultObj.optJSONArray("artists") ?: return emptyList()

            val list = mutableListOf<Artist>()
            for (i in 0 until artistsArray.length()) {
                val a = artistsArray.getJSONObject(i)
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
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun searchAlbums(query: String, page: Int = 0, limit: Int = 20): List<Album> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/api/search/get/web?s=$encodedQuery&type=10&offset=${page * limit}&limit=$limit&total=true"
            val jsonStr = executeGet(url)
            val json = JSONObject(jsonStr)
            val resultObj = json.optJSONObject("result") ?: return emptyList()
            val albumsArray = resultObj.optJSONArray("albums") ?: return emptyList()

            val list = mutableListOf<Album>()
            for (i in 0 until albumsArray.length()) {
                val al = albumsArray.getJSONObject(i)
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
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSongLyric(songId: Long): SongLyric {
        return try {
            val url = "$BASE_URL/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
            val jsonStr = executeGet(url)
            val json = JSONObject(jsonStr)
            val lrcObj = json.optJSONObject("lrc")
            val tlyricObj = json.optJSONObject("tlyric")

            val rawLrc = lrcObj?.optString("lyric") ?: ""
            val rawTransLrc = tlyricObj?.optString("lyric")

            LyricParser.parse(rawLrc, rawTransLrc)
        } catch (e: Exception) {
            e.printStackTrace()
            SongLyric(emptyList(), "")
        }
    }
}
