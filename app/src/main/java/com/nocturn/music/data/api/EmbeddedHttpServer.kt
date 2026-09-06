package com.nocturn.music.data.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

object EmbeddedHttpServer {
    const val PORT = 1145
    const val BASE_URL = "http://127.0.0.1:$PORT/api/netease"

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        serverJob = scope.launch {
            try {
                // 绑定到 127.0.0.1，安全隔离
                val addr = InetAddress.getByName("127.0.0.1")
                serverSocket = ServerSocket(PORT, 50, addr)
                isRunning = true
                println("EmbeddedHttpServer started on $BASE_URL")

                while (isActive && !serverSocket!!.isClosed) {
                    val socket = serverSocket!!.accept()
                    launch {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(s.getOutputStream(), false, Charsets.UTF_8)

                val reqLine = reader.readLine() ?: return@withContext
                val parts = reqLine.split(" ")
                if (parts.size < 2) return@withContext

                val fullPath = parts[1]
                val pathAndQuery = fullPath.split("?", limit = 2)
                val path = pathAndQuery[0]
                val queryParams = if (pathAndQuery.size > 1) parseQuery(pathAndQuery[1]) else emptyMap()

                // 读取 Headers 提取 Cookie
                var cookie = ""
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank()) break
                    val lower = line!!.lowercase()
                    if (lower.startsWith("cookie:")) {
                        cookie = line!!.substring(7).trim()
                    }
                }

                val responseJson = routeRequest(path, queryParams, cookie)
                val bodyBytes = responseJson.toByteArray(Charsets.UTF_8)

                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: application/json; charset=utf-8\r\n")
                writer.print("Content-Length: ${bodyBytes.size}\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("Connection: close\r\n")
                writer.print("\r\n")
                writer.flush()

                s.getOutputStream().write(bodyBytes)
                s.getOutputStream().flush()
            }
        } catch (e: Exception) {
            // ignore connection abort
        }
    }

    private suspend fun routeRequest(
        path: String,
        params: Map<String, String>,
        cookie: String
    ): String {
        val effectiveCookie = if (cookie.contains("MUSIC_U")) {
            cookie
        } else {
            val saved = com.nocturn.music.data.repository.SettingsRepository.userProfile.value.cookie
            if (saved.isNotBlank()) saved else cookie
        }
        val norm = path.removePrefix("/api/netease")
        return when {
            norm == "/inner/version" -> {
                JSONObject().apply {
                    put("code", 200)
                    put("data", JSONObject().apply {
                        put("version", "splayer-embedded-1.0")
                    })
                }.toString()
            }
            norm.startsWith("/banner") -> {
                val banners = EmbeddedNcmEngine.getBanners()
                val arr = JSONArray()
                banners.forEach { b ->
                    arr.put(JSONObject().apply {
                        put("pic", b.picUrl)
                        put("titleColor", b.titleColor)
                        put("typeTitle", b.typeTitle)
                        put("targetId", b.targetId)
                        put("url", b.url)
                    })
                }
                JSONObject().apply {
                    put("code", 200)
                    put("banners", arr)
                }.toString()
            }
            norm.startsWith("/personalized") -> {
                val limit = params["limit"]?.toIntOrNull() ?: 20
                val playlists = EmbeddedNcmEngine.getPersonalizedPlaylists(limit)
                val arr = JSONArray()
                playlists.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("picUrl", p.coverUrl)
                        put("playCount", p.playCount)
                        put("trackCount", p.trackCount)
                        put("copywriter", p.description)
                    })
                }
                JSONObject().apply {
                    put("code", 200)
                    put("result", arr)
                }.toString()
            }
            norm.startsWith("/toplist") -> {
                val toplists = EmbeddedNcmEngine.getToplists()
                val arr = JSONArray()
                toplists.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("coverImgUrl", p.coverUrl)
                        put("playCount", p.playCount)
                        put("trackCount", p.trackCount)
                        put("description", p.description)
                    })
                }
                JSONObject().apply {
                    put("code", 200)
                    put("list", arr)
                }.toString()
            }
            norm.startsWith("/playlist/detail") -> {
                val id = params["id"]?.toLongOrNull() ?: 0L
                val detail = EmbeddedNcmEngine.getPlaylistDetail(id)
                if (detail != null) {
                    val pObj = JSONObject().apply {
                        put("id", detail.id)
                        put("name", detail.name)
                        put("coverImgUrl", detail.coverUrl)
                        put("playCount", detail.playCount)
                        put("trackCount", detail.trackCount)
                        put("description", detail.description)
                        put("creator", JSONObject().apply {
                            put("nickname", detail.creatorName)
                            put("avatarUrl", detail.creatorAvatarUrl)
                        })
                        val tracksArr = JSONArray()
                        detail.tracks.forEach { t ->
                            tracksArr.put(JSONObject().apply {
                                put("id", t.id)
                                put("name", t.title)
                                put("dt", t.durationMs)
                                put("fee", if (t.isVip) 1 else 0)
                                put("ar", JSONArray().apply {
                                    put(JSONObject().apply { put("name", t.artist) })
                                })
                                put("al", JSONObject().apply {
                                    put("name", t.album)
                                    put("picUrl", t.coverUrl)
                                })
                            })
                        }
                        put("tracks", tracksArr)
                    }
                    JSONObject().apply {
                        put("code", 200)
                        put("playlist", pObj)
                    }.toString()
                } else {
                    """{"code":404,"message":"Playlist not found"}"""
                }
            }
            norm.startsWith("/song/url") -> {
                val id = params["id"]?.toLongOrNull() ?: 0L
                val level = params["level"] ?: "exhigh"
                val playUrl = com.nocturn.music.data.repository.MusicRepository.getSongPlayUrl(id, level)
                JSONObject().apply {
                    put("code", 200)
                    put("data", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", id)
                            put("url", playUrl)
                            put("level", level)
                            put("code", 200)
                        })
                    })
                }.toString()
            }
            norm.startsWith("/lyric") -> {
                val id = params["id"]?.toLongOrNull() ?: 0L
                val lyric = EmbeddedNcmEngine.getSongLyric(id)
                JSONObject().apply {
                    put("code", 200)
                    put("lrc", JSONObject().apply { put("lyric", lyric.rawLrc) })
                }.toString()
            }
            norm.startsWith("/search/hot") -> {
                val hotWords = EmbeddedNcmEngine.getHotSearchKeywords()
                val arr = JSONArray()
                hotWords.forEach { w ->
                    arr.put(JSONObject().apply { put("searchWord", w) })
                }
                JSONObject().apply {
                    put("code", 200)
                    put("data", arr)
                }.toString()
            }
            norm.startsWith("/search") -> {
                val q = params["keywords"] ?: params["s"] ?: ""
                val type = params["type"]?.toIntOrNull() ?: 1
                val page = params["offset"]?.toIntOrNull()?.div(30) ?: 0
                val songs = EmbeddedNcmEngine.searchSongs(q, page)
                val arr = JSONArray()
                songs.forEach { s ->
                    arr.put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.title)
                        put("dt", s.durationMs)
                        put("fee", if (s.isVip) 1 else 0)
                        put("ar", JSONArray().apply {
                            put(JSONObject().apply { put("name", s.artist) })
                        })
                        put("al", JSONObject().apply {
                            put("name", s.album)
                            put("picUrl", s.coverUrl)
                        })
                    })
                }
                JSONObject().apply {
                    put("code", 200)
                    put("result", JSONObject().apply { put("songs", arr) })
                }.toString()
            }
            norm.startsWith("/login/qr/key") -> {
                val key = EmbeddedNcmEngine.getQrKey() ?: System.currentTimeMillis().toString()
                JSONObject().apply {
                    put("code", 200)
                    put("data", JSONObject().apply { put("unikey", key) })
                }.toString()
            }
            norm.startsWith("/login/qr/create") -> {
                val key = params["key"] ?: ""
                val qrurl = "https://music.163.com/login?codekey=$key"
                JSONObject().apply {
                    put("code", 200)
                    put("data", JSONObject().apply {
                        put("qrurl", qrurl)
                        put("qrimg", "")
                    })
                }.toString()
            }
            norm.startsWith("/login/qr/check") -> {
                val key = params["key"] ?: ""
                val (code, newCookie) = EmbeddedNcmEngine.checkQrStatus(key)
                JSONObject().apply {
                    put("code", code)
                    put("cookie", newCookie)
                }.toString()
            }
            norm.startsWith("/captcha/sent") -> {
                val phone = params["phone"] ?: ""
                val ctcode = params["ctcode"] ?: "86"
                val (success, msg) = EmbeddedNcmEngine.sendCaptcha(phone, ctcode)
                JSONObject().apply {
                    put("code", if (success) 200 else 400)
                    put("data", success)
                    put("message", msg)
                }.toString()
            }
            norm.startsWith("/login/cellphone") -> {
                val phone = params["phone"] ?: ""
                val captcha = params["captcha"] ?: ""
                val ctcode = params["countrycode"] ?: params["ctcode"] ?: "86"
                val (success, result) = EmbeddedNcmEngine.loginWithCaptcha(phone, captcha, ctcode)
                val (profile, msg) = result
                if (success && profile != null) {
                    JSONObject().apply {
                        put("code", 200)
                        put("cookie", profile.cookie)
                        put("profile", JSONObject().apply {
                            put("userId", profile.userId)
                            put("nickname", profile.nickname)
                            put("avatarUrl", profile.avatarUrl)
                            put("vipType", profile.vipType)
                            put("signature", profile.signature)
                        })
                    }.toString()
                } else {
                    JSONObject().apply {
                        put("code", 400)
                        put("message", msg)
                    }.toString()
                }
            }
            norm.startsWith("/user/account") -> {
                val user = EmbeddedNcmEngine.getUserAccount(effectiveCookie)
                if (user != null) {
                    JSONObject().apply {
                        put("code", 200)
                        put("profile", JSONObject().apply {
                            put("userId", user.userId)
                            put("nickname", user.nickname)
                            put("avatarUrl", user.avatarUrl)
                            put("vipType", user.vipType)
                            put("signature", user.signature)
                        })
                    }.toString()
                } else {
                    """{"code":400,"message":"Not logged in"}"""
                }
            }
            norm.startsWith("/user/playlist") -> {
                val uid = params["uid"]?.toLongOrNull() ?: 0L
                val playlists = EmbeddedNcmEngine.getUserPlaylists(uid, effectiveCookie)
                val arr = JSONArray()
                playlists.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("coverImgUrl", p.coverUrl)
                        put("playCount", p.playCount)
                        put("trackCount", p.trackCount)
                        put("description", p.description)
                        put("creator", JSONObject().apply {
                            put("nickname", p.creatorName)
                        })
                    })
                }
                JSONObject().apply {
                    put("code", 200)
                    put("playlist", arr)
                }.toString()
            }
            norm.startsWith("/like") -> {
                val id = params["id"]?.toLongOrNull() ?: 0L
                val like = params["like"]?.toBooleanStrictOrNull() ?: true
                val success = EmbeddedNcmEngine.likeSong(id, like, effectiveCookie)
                JSONObject().apply {
                    put("code", if (success) 200 else 400)
                }.toString()
            }
            else -> {
                """{"code":200,"message":"Embedded API OK"}"""
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }
}
