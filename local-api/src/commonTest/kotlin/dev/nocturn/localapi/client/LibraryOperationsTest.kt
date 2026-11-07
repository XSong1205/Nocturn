package dev.nocturn.localapi.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryOperationsTest {
    @Test
    fun categoryCatalogsUseSeparateRoutesWithoutAccountCredentials() = runTest {
        val routes = mutableListOf<String>()
        withApi({ route, body ->
            routes += route
            assertTrue(body.isEmpty())
            SUCCESS
        }) { api ->
            api.playlistCategories()
            api.hotPlaylistCategories()
            api.highQualityPlaylistTags()
        }
        assertEquals(listOf("playlist/catlist", "playlist/hot", "playlist/highquality/tags"), routes)
    }

    @Test
    fun playlistBrowsingSeparatesOffsetsFromTimestampCursors() = runTest {
        var calls = 0
        withApi({ route, body ->
            when (calls++) {
                0 -> {
                    assertEquals("top/playlist", route)
                    assertEquals("华语", body.text("cat"))
                    assertEquals("new", body.text("order"))
                    assertEquals("30", body.text("limit"))
                    assertEquals("60", body.text("offset"))
                    assertEquals(COOKIE, body.text("cookie"))
                    assertFalse("before" in body)
                }
                1 -> {
                    assertEquals("top/playlist/highquality", route)
                    assertEquals("古典", body.text("cat"))
                    assertEquals("25", body.text("limit"))
                    assertEquals("9007199254740991", body.text("before"))
                    assertTrue(body["before"]!!.jsonPrimitive.isString)
                    assertEquals(COOKIE, body.text("cookie"))
                    assertFalse("offset" in body)
                    assertFalse("order" in body)
                }
                2 -> {
                    assertEquals("top/playlist/highquality", route)
                    assertEquals("0", body.text("before"))
                }
                3 -> {
                    assertEquals("top/playlist/highquality", route)
                    assertFalse("before" in body)
                    assertFalse("cookie" in body)
                    assertEquals("全部", body.text("cat"))
                }
                else -> error("Unexpected request")
            }
            SUCCESS
        }) { api ->
            api.topPlaylists("华语", PlaylistOrder.New, ApiPage(30, 60), COOKIE)
            api.highQualityPlaylists("古典", 25, 9_007_199_254_740_991L, COOKIE)
            api.highQualityPlaylists(before = 0)
            api.highQualityPlaylists()
        }
        assertEquals(4, calls)
    }

    @Test
    fun playlistCreationPreservesTextAndMapsPrivacyAndPlaylistTypes() = runTest {
        var calls = 0
        withApi({ route, body ->
            assertEquals("playlist/create", route)
            assertEquals("夜晚的 \"歌单\"\n第二行", body.text("name"))
            assertEquals(if (calls == 0) "0" else "10", body.text("privacy"))
            assertFalse(body["privacy"]!!.jsonPrimitive.isString)
            assertEquals(PlaylistType.entries[calls++].value, body.text("type"))
            assertEquals(COOKIE, body.text("cookie"))
            """{"code":200,"id":123}"""
        }) { api ->
            for ((index, type) in PlaylistType.entries.withIndex()) {
                assertEquals("123", api.createPlaylist("夜晚的 \"歌单\"\n第二行", index > 0, type, COOKIE).text("id"))
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun trackActionsPreserveLongIdsAndBusinessDenialsWithoutRetrying() = runTest {
        var calls = 0
        withApi({ route, body ->
            assertEquals("playlist/tracks", route)
            assertEquals(Long.MAX_VALUE.toString(), body.text("pid"))
            assertEquals("9223372036854775807,347230", body.text("tracks"))
            assertTrue(body["pid"]!!.jsonPrimitive.isString)
            assertTrue(body["tracks"]!!.jsonPrimitive.isString)
            assertEquals(if (calls++ == 0) "add" else "del", body.text("op"))
            assertEquals(COOKIE, body.text("cookie"))
            """{"code":512,"message":"Already present"}"""
        }) { api ->
            for (operation in PlaylistTrackOperation.entries) {
                val result = api.updatePlaylistTracks(Long.MAX_VALUE, listOf(Long.MAX_VALUE, 347230L), operation, COOKIE)
                assertEquals("512", result.text("code"))
                assertEquals("Already present", result.text("message"))
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun playlistDeletionSendsABoundedCsvListWithoutNumericRounding() = runTest {
        val ids = List(200) { Long.MAX_VALUE - it }
        withApi({ route, body ->
            assertEquals("playlist/delete", route)
            assertEquals(ids.joinToString(","), body.text("id"))
            assertTrue(body["id"]!!.jsonPrimitive.isString)
            assertEquals(COOKIE, body.text("cookie"))
            SUCCESS
        }) { api -> api.deletePlaylists(ids, COOKIE) }
    }

    @Test
    fun metadataUpdatesPreserveQuotesAndNewlinesAndSendExplicitEmptyValues() = runTest {
        val requests = mutableListOf<Pair<String, JsonObject>>()
        val text = "听见 \"夜\"\n保留换行"
        withApi({ route, body ->
            assertEquals(Long.MAX_VALUE.toString(), body.text("id"))
            assertEquals(COOKIE, body.text("cookie"))
            requests += route to body
            SUCCESS
        }) { api ->
            api.renamePlaylist(Long.MAX_VALUE, text, COOKIE)
            api.updatePlaylistDescription(Long.MAX_VALUE, text, COOKIE)
            api.updatePlaylistDescription(Long.MAX_VALUE, "", COOKIE)
            api.updatePlaylistTags(Long.MAX_VALUE, listOf("华语", "古典", "夜晚"), COOKIE)
            api.updatePlaylistTags(Long.MAX_VALUE, emptyList(), COOKIE)
        }
        assertEquals(listOf("playlist/name/update", "playlist/desc/update", "playlist/desc/update", "playlist/tags/update", "playlist/tags/update"), requests.map { it.first })
        assertEquals(text, requests[0].second.text("name"))
        assertEquals(text, requests[1].second.text("desc"))
        assertEquals("", requests[2].second.text("desc"))
        assertEquals("华语;古典;夜晚", requests[3].second.text("tags"))
        assertEquals("", requests[4].second.text("tags"))
    }

    @Test
    fun subscriptionActionsUseExplicitSubscribeAndUnsubscribeCodes() = runTest {
        val routes = mutableListOf<String>()
        withApi({ route, body ->
            routes += route
            assertEquals(Long.MAX_VALUE.toString(), body.text("id"))
            assertTrue(body["id"]!!.jsonPrimitive.isString)
            assertEquals(if (routes.size % 2 == 1) "1" else "2", body.text("t"))
            assertFalse(body["t"]!!.jsonPrimitive.isString)
            assertEquals(COOKIE, body.text("cookie"))
            SUCCESS
        }) { api ->
            api.subscribePlaylist(Long.MAX_VALUE, cookie = COOKIE)
            api.subscribePlaylist(Long.MAX_VALUE, false, COOKIE)
            api.subscribeAlbum(Long.MAX_VALUE, cookie = COOKIE)
            api.subscribeAlbum(Long.MAX_VALUE, false, COOKIE)
            api.subscribeArtist(Long.MAX_VALUE, cookie = COOKIE)
            api.subscribeArtist(Long.MAX_VALUE, false, COOKIE)
        }
        assertEquals(listOf("playlist/subscribe", "playlist/subscribe", "album/sub", "album/sub", "artist/sub", "artist/sub"), routes)
    }

    @Test
    fun collectionPagesAndArtistSongsUseExplicitPaginationAndArtistOrdering() = runTest {
        val routes = mutableListOf<String>()
        withApi({ route, body ->
            routes += route
            assertEquals("25", body.text("limit"))
            assertEquals("50", body.text("offset"))
            assertEquals(COOKIE, body.text("cookie"))
            if (route == "artist/songs") {
                assertEquals(Long.MAX_VALUE.toString(), body.text("id"))
                assertEquals("time", body.text("order"))
            } else {
                assertFalse("id" in body)
                assertFalse("order" in body)
            }
            SUCCESS
        }) { api ->
            api.subscribedAlbums(COOKIE, ApiPage(25, 50))
            api.subscribedArtists(COOKIE, ApiPage(25, 50))
            api.artistSongs(Long.MAX_VALUE, ArtistSongOrder.Time, ApiPage(25, 50), COOKIE)
        }
        assertEquals(listOf("album/sublist", "artist/sublist", "artist/songs"), routes)
    }

    @Test
    fun invalidLibraryArgumentsNeverDispatchMutationsOrQueries() = runTest {
        withApi({ _, _ -> error("No request may be sent") }) { api ->
            assertFailsWith<IllegalArgumentException> { api.topPlaylists(" ") }
            assertFailsWith<IllegalArgumentException> { api.highQualityPlaylists("a".repeat(41)) }
            assertFailsWith<IllegalArgumentException> { api.highQualityPlaylists(limit = 0) }
            assertFailsWith<IllegalArgumentException> { api.highQualityPlaylists(limit = 201) }
            assertFailsWith<IllegalArgumentException> { api.highQualityPlaylists(before = -1) }
            assertFailsWith<IllegalArgumentException> { api.highQualityPlaylists(before = 9_007_199_254_740_992L) }
            assertFailsWith<IllegalArgumentException> { api.createPlaylist(" ", cookie = COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.createPlaylist("a".repeat(41), cookie = COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.createPlaylist("Valid", cookie = " ") }
            for (ids in listOf(emptyList(), listOf(0L), listOf(-1L), List(201) { 1L })) {
                assertFailsWith<IllegalArgumentException> { api.deletePlaylists(ids, COOKIE) }
                assertFailsWith<IllegalArgumentException> { api.updatePlaylistTracks(1, ids, PlaylistTrackOperation.Add, COOKIE) }
            }
            assertFailsWith<IllegalArgumentException> { api.deletePlaylists(listOf(1), "") }
            assertFailsWith<IllegalArgumentException> { api.updatePlaylistTracks(0, listOf(1), PlaylistTrackOperation.Add, COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.updatePlaylistTracks(1, listOf(1), PlaylistTrackOperation.Add, "") }
            assertFailsWith<IllegalArgumentException> { api.renamePlaylist(0, "Valid", COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.renamePlaylist(1, "", COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.renamePlaylist(1, "a".repeat(41), COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.renamePlaylist(1, "Valid", "") }
            assertFailsWith<IllegalArgumentException> { api.updatePlaylistDescription(0, "", COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.updatePlaylistDescription(1, "a".repeat(1001), COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.updatePlaylistDescription(1, "", "") }
            for (tags in listOf(listOf(""), listOf(" "), listOf("a;b"), listOf("a".repeat(41)), List(4) { "tag" })) {
                assertFailsWith<IllegalArgumentException> { api.updatePlaylistTags(1, tags, COOKIE) }
            }
            assertFailsWith<IllegalArgumentException> { api.updatePlaylistTags(0, emptyList(), COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.updatePlaylistTags(1, emptyList(), "") }
            assertFailsWith<IllegalArgumentException> { api.subscribePlaylist(0, cookie = COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.subscribePlaylist(1, cookie = "") }
            assertFailsWith<IllegalArgumentException> { api.subscribeAlbum(0, cookie = COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.subscribeAlbum(1, cookie = "") }
            assertFailsWith<IllegalArgumentException> { api.subscribeArtist(0, cookie = COOKIE) }
            assertFailsWith<IllegalArgumentException> { api.subscribeArtist(1, cookie = "") }
            assertFailsWith<IllegalArgumentException> { api.subscribedAlbums("") }
            assertFailsWith<IllegalArgumentException> { api.subscribedArtists(" ") }
            assertFailsWith<IllegalArgumentException> { api.artistSongs(0) }
        }
    }

    private suspend fun withApi(
        handler: (String, JsonObject) -> String,
        block: suspend (LocalApiClient) -> Unit,
    ) {
        val client = LocalApiClient(HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.parameters.isEmpty())
            assertFalse(request.url.toString().contains(COOKIE))
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()) as JsonObject
            val response = handler(request.url.encodedPath.removePrefix("/api/"), body)
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }), "http://127.0.0.1:1145", TOKEN)
        try { block(client) } finally { client.close() }
    }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content

    private companion object {
        const val TOKEN = "nocturn-test-token-0123456789abcdef"
        const val COOKIE = "MUSIC_U=library-session"
        const val SUCCESS = "{\"code\":200}"
    }
}
