package dev.nocturn.localapi.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class MusicArtist(val id: Long, val name: String)
data class MusicAlbum(val id: Long, val name: String, val coverUrl: String?)
data class Song(
    val id: Long,
    val name: String,
    val artists: List<MusicArtist>,
    val album: MusicAlbum?,
    val durationMs: Long,
    val fee: Int?,
) {
    companion object {
        /** Handles both legacy search and cloudsearch/detail song shapes. */
        fun fromJson(value: JsonObject): Song? {
            val id = value.number("id")?.takeIf { it > 0 } ?: return null
            val name = value.text("name") ?: return null
            val artists = (value["ar"] as? JsonArray) ?: (value["artists"] as? JsonArray)
            val album = (value["al"] as? JsonObject) ?: (value["album"] as? JsonObject)
            return Song(
                id, name,
                artists.orEmpty().mapNotNull { element ->
                    val artist = element as? JsonObject ?: return@mapNotNull null
                    MusicArtist(artist.number("id") ?: 0, artist.text("name") ?: return@mapNotNull null)
                },
                album?.let { MusicAlbum(it.number("id") ?: 0, it.text("name").orEmpty(), it.text("picUrl")) },
                (value.number("dt") ?: value.number("duration") ?: 0).coerceAtLeast(0),
                (value["fee"] as? JsonPrimitive)?.intOrNull,
            )
        }
    }
}

data class SongSearchPage(val songs: List<Song>, val total: Int?, val hasMore: Boolean?)
data class PlaylistSongPage(val songs: List<Song>, val offset: Int, val total: Int, val nextOffset: Int?)

/** Upstream business denial, deliberately without raw response text or account data. */
class MusicServiceException(val businessCode: Int) : Exception("Music service returned business code $businessCode.")

internal fun JsonObject.requireSuccess(): JsonObject {
    val code = (this["code"] as? JsonPrimitive)?.intOrNull ?: throw invalidMusicResponse()
    if (code != 200) throw MusicServiceException(code)
    return this
}

internal fun invalidMusicResponse() = LocalApiException("INVALID_RESPONSE", message = "The music service returned an invalid response.")
internal fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

suspend fun LocalApiClient.searchSongPage(
    keywords: String,
    page: ApiPage = ApiPage(30),
    cookie: String? = null,
): SongSearchPage {
    val result = search(keywords, page = page, cookie = cookie).requireSuccess()["result"] as? JsonObject
        ?: throw invalidMusicResponse()
    val elements = result["songs"] as? JsonArray
    val total = (result["songCount"] as? JsonPrimitive)?.intOrNull
    if (elements == null && total != 0) throw invalidMusicResponse()
    val songs = elements.orEmpty().mapNotNull { (it as? JsonObject)?.let(Song::fromJson) }
    val more = ((result["hasMore"] ?: result["more"]) as? JsonPrimitive)?.booleanOrNull
        ?: total?.let { page.offset.toLong() + (elements?.size ?: 0) < it }
    return SongSearchPage(songs, total, more)
}

/**
 * Uses the complete trackIds list, then fetches only the requested page of details.
 * Never mistakes the truncated playlist.tracks preview for a complete playlist.
 */
suspend fun LocalApiClient.playlistSongPage(
    id: Long,
    page: ApiPage = ApiPage(),
    cookie: String? = null,
): PlaylistSongPage {
    require(id > 0) { "A positive playlist ID is required." }
    val detail = request(LocalApiEndpoint.PlaylistDetail, buildJsonObject {
        put("id", id.toString())
        cookie?.let { put("cookie", it) }
    }).requireSuccess()
    val playlist = detail["playlist"] as? JsonObject ?: throw invalidMusicResponse()
    val entries = playlist["trackIds"] as? JsonArray ?: throw invalidMusicResponse()
    val ids = entries.map { entry ->
        (entry as? JsonObject)?.number("id")?.takeIf { it > 0 } ?: throw invalidMusicResponse()
    }
    val selected = ids.drop(page.offset).take(page.limit)
    if (selected.isEmpty()) return PlaylistSongPage(emptyList(), page.offset, ids.size, null)
    val songResponse = songDetail(selected, cookie).requireSuccess()
    val songs = (songResponse["songs"] as? JsonArray ?: throw invalidMusicResponse())
        .mapNotNull { (it as? JsonObject)?.let(Song::fromJson) }.associateBy { it.id }
    val next = (page.offset + selected.size).takeIf { it < ids.size }
    return PlaylistSongPage(selected.mapNotNull { songs[it] }, page.offset, ids.size, next)
}

enum class QrLoginState { Expired, WaitingForScan, WaitingForConfirmation, Authorized, Rejected }

class QrLoginResult(val state: QrLoginState, val code: Int, val cookie: String?) {
    override fun toString(): String = "QrLoginResult(state=$state, code=$code, cookie=<redacted>)"
}

suspend fun LocalApiClient.pollQrLogin(key: String): QrLoginResult {
    val body = loginQrCheck(key)
    val code = (body["code"] as? JsonPrimitive)?.intOrNull ?: throw invalidMusicResponse()
    val state = when (code) {
        800 -> QrLoginState.Expired
        801 -> QrLoginState.WaitingForScan
        802 -> QrLoginState.WaitingForConfirmation
        803 -> QrLoginState.Authorized
        else -> QrLoginState.Rejected
    }
    val cookie = body.text("cookie")?.takeIf { it.isNotBlank() && state == QrLoginState.Authorized }
    if (state == QrLoginState.Authorized && cookie == null) throw invalidMusicResponse()
    return QrLoginResult(state, code, cookie)
}

data class AccountProfile(val id: Long, val nickname: String, val avatarUrl: String?)
data class SessionStatus(val profile: AccountProfile?) {
    val isLoggedIn: Boolean get() = profile != null
}

/** Validates a stored cookie without re-authenticating or silently refreshing QR credentials. */
suspend fun LocalApiClient.restoreSession(cookie: String): SessionStatus {
    val response = loginStatus(cookie)
    val data = (response["data"] as? JsonObject) ?: response
    val code = (data["code"] as? JsonPrimitive)?.intOrNull ?: throw invalidMusicResponse()
    if (code == 301) return SessionStatus(null)
    data.requireSuccess()
    val profile = data["profile"] as? JsonObject ?: return SessionStatus(null)
    val id = profile.number("userId")?.takeIf { it > 0 } ?: throw invalidMusicResponse()
    return SessionStatus(AccountProfile(id, profile.text("nickname").orEmpty(), profile.text("avatarUrl")))
}
