package dev.nocturn.localapi.client

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class SearchType(val value: Int) {
    Song(1), Album(10), Artist(100), Playlist(1000), User(1002), Mv(1004),
    Lyric(1006), Radio(1009), Video(1014), Combined(1018), Voice(2000),
}

/** A bounded page. Offsets count items, not page numbers. */
data class ApiPage(val limit: Int = 50, val offset: Int = 0) {
    init {
        require(limit in 1..200) { "Page limit must be between 1 and 200." }
        require(offset in 0..100_000) { "Page offset must be between 0 and 100000." }
    }
}

suspend fun LocalApiClient.search(
    keywords: String,
    type: SearchType = SearchType.Song,
    page: ApiPage = ApiPage(30),
    enhanced: Boolean = true,
    cookie: String? = null,
): JsonObject {
    requireKeywords(keywords)
    return request(if (enhanced) LocalApiEndpoint.CloudSearch else LocalApiEndpoint.Search, buildJsonObject {
        put("keywords", keywords)
        put("type", type.value)
        put("limit", page.limit)
        put("offset", page.offset)
        cookie?.let { put("cookie", it) }
    })
}

suspend fun LocalApiClient.searchSuggestions(
    keywords: String,
    mobile: Boolean = true,
    cookie: String? = null,
): JsonObject {
    requireKeywords(keywords)
    return request(LocalApiEndpoint.SearchSuggest, buildJsonObject {
        put("keywords", keywords)
        put("type", if (mobile) "mobile" else "web")
        cookie?.let { put("cookie", it) }
    })
}

suspend fun LocalApiClient.defaultSearch(cookie: String? = null): JsonObject =
    request(LocalApiEndpoint.SearchDefault, buildJsonObject {
        cookie?.let { put("cookie", it) }
    })

suspend fun LocalApiClient.searchHotDetail(cookie: String? = null): JsonObject =
    request(LocalApiEndpoint.SearchHotDetail, buildJsonObject {
        cookie?.let { put("cookie", it) }
    })

suspend fun LocalApiClient.songUrls(
    ids: List<Long>,
    level: AudioQuality = AudioQuality.Standard,
    cookie: String? = null,
): JsonObject {
    require(ids.isNotEmpty() && ids.size <= 200 && ids.all { it > 0 }) {
        "Supply between 1 and 200 positive song IDs."
    }
    return request(LocalApiEndpoint.SongUrl, buildJsonObject {
        put("id", ids.joinToString(","))
        put("level", level.value)
        cookie?.let { put("cookie", it) }
    })
}

suspend fun LocalApiClient.checkMusic(id: Long, bitrate: Int = 999_000, cookie: String? = null): JsonObject {
    require(id > 0 && bitrate in 1..10_000_000) { "A positive song ID and valid bitrate are required." }
    return request(LocalApiEndpoint.CheckMusic, buildJsonObject {
        put("id", id.toString())
        put("br", bitrate)
        cookie?.let { put("cookie", it) }
    })
}

suspend fun LocalApiClient.songMusicDetail(id: Long, cookie: String? = null): JsonObject {
    require(id > 0) { "A positive song ID is required." }
    return request(LocalApiEndpoint.SongMusicDetail, buildJsonObject {
        put("id", id.toString())
        cookie?.let { put("cookie", it) }
    })
}

/** Creates an anonymous session only when called; startup never silently logs in. */
suspend fun LocalApiClient.guestLogin(): JsonObject = request(LocalApiEndpoint.GuestLogin)

/** Upstream does not support refreshing QR-login cookies. */
suspend fun LocalApiClient.refreshLogin(cookie: String): JsonObject =
    requestWithCookie(LocalApiEndpoint.LoginRefresh, cookie)

suspend fun LocalApiClient.userAccount(cookie: String): JsonObject =
    requestWithCookie(LocalApiEndpoint.UserAccount, cookie)

suspend fun LocalApiClient.userSubcount(cookie: String): JsonObject =
    requestWithCookie(LocalApiEndpoint.UserSubcount, cookie)

/** Call only in response to the user's explicit Send code action. No automatic retries. */
suspend fun LocalApiClient.sendVerificationCode(phone: String, countryCode: String = "86"): JsonObject {
    requirePhone(phone, countryCode)
    return request(LocalApiEndpoint.CaptchaSent, buildJsonObject {
        put("phone", phone)
        put("ctcode", countryCode)
    })
}

suspend fun LocalApiClient.verifyCode(phone: String, captcha: String, countryCode: String = "86"): JsonObject {
    requirePhone(phone, countryCode)
    requireCaptcha(captcha)
    return request(LocalApiEndpoint.CaptchaVerify, buildJsonObject {
        put("phone", phone)
        put("captcha", captcha)
        put("ctcode", countryCode)
    })
}

suspend fun LocalApiClient.loginWithCode(phone: String, captcha: String, countryCode: String = "86"): JsonObject {
    requirePhone(phone, countryCode)
    requireCaptcha(captcha)
    return request(LocalApiEndpoint.LoginCellphone, buildJsonObject {
        put("phone", phone)
        put("captcha", captcha)
        put("countrycode", countryCode)
    })
}

/** Passwords are sent only in the authenticated loopback POST body, never in a URL. */
suspend fun LocalApiClient.loginWithPassword(phone: String, password: String, countryCode: String = "86"): JsonObject {
    requirePhone(phone, countryCode)
    require(password.length in 1..128) { "A password of 1 to 128 characters is required." }
    return request(LocalApiEndpoint.LoginCellphone, buildJsonObject {
        put("phone", phone)
        put("password", password)
        put("countrycode", countryCode)
    })
}

suspend fun LocalApiClient.dailySongs(cookie: String, afresh: Boolean = false): JsonObject {
    require(cookie.isNotBlank()) { "An account cookie is required." }
    return request(LocalApiEndpoint.RecommendSongs, buildJsonObject {
        put("afresh", afresh)
        put("cookie", cookie)
    })
}

/**
 * `personalized/newsong` recommendations. Works anonymously; an account cookie personalises the
 * list. The upstream route has no offset, so only [limit] is sent.
 */
suspend fun LocalApiClient.personalizedNewSongs(limit: Int = 10, cookie: String? = null): JsonObject {
    require(limit in 1..200) { "Page limit must be between 1 and 200." }
    return request(LocalApiEndpoint.PersonalizedNewSongs, buildJsonObject {
        put("limit", limit)
        cookie?.let { put("cookie", it) }
    })
}

/**
 * `recommend/resource`: the playlists the account is recommended today. It is the account-aware
 * counterpart of the public `/personalized` shelf, so it always carries the login cookie.
 */
suspend fun LocalApiClient.recommendPlaylists(cookie: String): JsonObject {
    require(cookie.isNotBlank()) { "An account cookie is required." }
    return request(
        LocalApiEndpoint.RecommendResource,
        buildJsonObject { put("cookie", cookie) },
    )
}

/** Explicit pagination avoids the upstream module's implicit 1000-song default. */
suspend fun LocalApiClient.playlistSongs(id: Long, page: ApiPage = ApiPage(), cookie: String? = null): JsonObject {
    require(id > 0) { "A positive playlist ID is required." }
    return request(LocalApiEndpoint.PlaylistTrackAll, buildJsonObject {
        put("id", id.toString())
        put("limit", page.limit)
        put("offset", page.offset)
        cookie?.let { put("cookie", it) }
    })
}

suspend fun LocalApiClient.likeSong(id: Long, like: Boolean = true, cookie: String): JsonObject {
    require(id > 0 && cookie.isNotBlank()) { "A positive song ID and account cookie are required." }
    return request(LocalApiEndpoint.Like, buildJsonObject {
        put("id", id.toString())
        put("like", like)
        put("cookie", cookie)
    })
}

suspend fun LocalApiClient.likeSongForUser(id: Long, uid: Long, like: Boolean, cookie: String): JsonObject {
    require(id > 0 && uid > 0 && cookie.isNotBlank()) { "Positive IDs and an account cookie are required." }
    return request(LocalApiEndpoint.SongLike, buildJsonObject {
        put("id", id.toString())
        put("uid", uid.toString())
        put("like", like)
        put("cookie", cookie)
    })
}

suspend fun LocalApiClient.userPlaylists(
    uid: Long,
    kind: UserPlaylistKind = UserPlaylistKind.All,
    page: ApiPage = ApiPage(),
    cookie: String? = null,
): JsonObject {
    require(uid > 0) { "A positive user ID is required." }
    val endpoint = when (kind) {
        UserPlaylistKind.All -> LocalApiEndpoint.UserPlaylist
        UserPlaylistKind.Created -> LocalApiEndpoint.UserCreatedPlaylists
        UserPlaylistKind.Collected -> LocalApiEndpoint.UserCollectedPlaylists
    }
    return request(endpoint, buildJsonObject {
        put("uid", uid.toString())
        put("limit", page.limit)
        put("offset", page.offset)
        cookie?.let { put("cookie", it) }
    })
}

enum class UserPlaylistKind { All, Created, Collected }

suspend fun LocalApiClient.recentSongs(cookie: String, limit: Int = 100): JsonObject {
    require(cookie.isNotBlank() && limit in 1..200) { "An account cookie and valid limit are required." }
    return request(LocalApiEndpoint.RecentSongs, buildJsonObject {
        put("limit", limit)
        put("cookie", cookie)
    })
}

suspend fun LocalApiClient.lyricsNew(id: Long, cookie: String? = null): JsonObject {
    require(id > 0) { "A positive song ID is required." }
    return request(LocalApiEndpoint.LyricNew, buildJsonObject {
        put("id", id.toString())
        cookie?.let { put("cookie", it) }
    })
}

private suspend fun LocalApiClient.requestWithCookie(endpoint: LocalApiEndpoint, cookie: String): JsonObject {
    require(cookie.isNotBlank()) { "An account cookie is required." }
    return request(endpoint, buildJsonObject { put("cookie", cookie) })
}

private fun requireKeywords(value: String) {
    require(value.isNotBlank() && value.length <= 200) { "Supply 1 to 200 search characters." }
}

private fun requirePhone(phone: String, countryCode: String) {
    require(Regex("[0-9]{5,15}").matches(phone)) { "A valid phone number is required." }
    require(Regex("[1-9][0-9]{0,3}").matches(countryCode)) { "A valid country code is required." }
}

private fun requireCaptcha(captcha: String) {
    require(Regex("[0-9]{4,8}").matches(captcha)) { "A valid verification code is required." }
}
