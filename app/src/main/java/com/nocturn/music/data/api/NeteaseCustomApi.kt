package com.nocturn.music.data.api

import com.nocturn.music.model.Playlist
import com.nocturn.music.model.Song
import com.nocturn.music.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NeteaseCustomApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun get(baseUrl: String, path: String, cookie: String = ""): String = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trimEnd('/')
        val separator = if (path.startsWith("/")) "" else "/"
        val url = "$cleanBase$separator$path"
        val reqBuilder = Request.Builder().url(url)
        if (cookie.isNotBlank()) {
            reqBuilder.header("Cookie", cookie)
        }
        client.newCall(reqBuilder.build()).execute().use { res ->
            if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
            res.body.string()
        }
    }

    suspend fun getQrKey(baseUrl: String): String? {
        return try {
            val jsonStr = get(baseUrl, "/login/qr/key?timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            json.optJSONObject("data")?.optString("unkey")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun checkQrStatus(baseUrl: String, key: String): Pair<Int, String> {
        return try {
            val jsonStr = get(baseUrl, "/login/qr/check?key=$key&timestamp=${System.currentTimeMillis()}")
            val json = JSONObject(jsonStr)
            val code = json.optInt("code")
            val cookie = json.optString("cookie", "")
            Pair(code, cookie)
        } catch (e: Exception) {
            Pair(-1, "")
        }
    }

    suspend fun getUserAccount(baseUrl: String, cookie: String): UserProfile? {
        return try {
            val jsonStr = get(baseUrl, "/user/account?timestamp=${System.currentTimeMillis()}", cookie)
            val json = JSONObject(jsonStr)
            val profile = json.optJSONObject("profile") ?: return null
            UserProfile(
                userId = profile.optLong("userId"),
                nickname = profile.optString("nickname"),
                avatarUrl = profile.optString("avatarUrl"),
                vipType = profile.optInt("vipType"),
                signature = profile.optString("signature"),
                isLogin = true,
                cookie = cookie
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserPlaylists(baseUrl: String, uid: Long, cookie: String = ""): List<Playlist> {
        return try {
            val jsonStr = get(baseUrl, "/user/playlist?uid=$uid&timestamp=${System.currentTimeMillis()}", cookie)
            val json = JSONObject(jsonStr)
            val playlistArr = json.optJSONArray("playlist") ?: JSONArray()
            val result = mutableListOf<Playlist>()
            for (i in 0 until playlistArr.length()) {
                val p = playlistArr.getJSONObject(i)
                result.add(
                    Playlist(
                        id = p.optLong("id"),
                        name = p.optString("name"),
                        coverUrl = p.optString("coverImgUrl"),
                        playCount = p.optLong("playCount"),
                        trackCount = p.optInt("trackCount"),
                        description = p.optString("description")
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getDailyRecommendSongs(baseUrl: String, cookie: String): List<Song> {
        return try {
            val jsonStr = get(baseUrl, "/recommend/songs?timestamp=${System.currentTimeMillis()}", cookie)
            val json = JSONObject(jsonStr)
            val dataObj = json.optJSONObject("data") ?: return emptyList()
            val dailySongs = dataObj.optJSONArray("dailySongs") ?: JSONArray()
            val list = mutableListOf<Song>()
            for (i in 0 until dailySongs.length()) {
                val s = dailySongs.getJSONObject(i)
                val id = s.optLong("id")
                val name = s.optString("name")
                val dt = s.optLong("dt", 0L)
                val arArr = s.optJSONArray("ar")
                val arList = mutableListOf<String>()
                if (arArr != null) {
                    for (j in 0 until arArr.length()) {
                        arList.add(arArr.getJSONObject(j).optString("name"))
                    }
                }
                val alObj = s.optJSONObject("al")
                list.add(
                    Song(
                        id = id,
                        title = name,
                        artist = arList.joinToString(" / "),
                        album = alObj?.optString("name") ?: "",
                        coverUrl = alObj?.optString("picUrl") ?: "",
                        durationMs = dt
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
