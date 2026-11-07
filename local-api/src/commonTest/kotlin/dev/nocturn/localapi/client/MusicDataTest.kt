package dev.nocturn.localapi.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicDataTest {
    @Test
    fun batchUrlsPreserveLongIdsAndAllDocumentedQualities() = runTest {
        withApi({ route, body ->
            assertEquals("song/url/v1", route)
            assertEquals("9223372036854775807,347230", body["id"]?.jsonPrimitive?.content)
            assertTrue(body["id"]!!.jsonPrimitive.isString)
            assertEquals("MUSIC_U=session", body["cookie"]?.jsonPrimitive?.content)
            """{"code":200,"data":[]}"""
        }) { api ->
            for (quality in AudioQuality.entries) api.songUrls(listOf(Long.MAX_VALUE, 347230L), quality, "MUSIC_U=session")
        }
        assertEquals(setOf("standard", "higher", "exhigh", "lossless", "hires", "jyeffect", "sky", "dolby", "jymaster"), AudioQuality.entries.map { it.value }.toSet())
    }

    @Test
    fun enhancedSearchAndSuggestionsUseSeparateTypeContracts() = runTest {
        withApi({ route, body ->
            when (route) {
                "cloudsearch" -> {
                    assertEquals("1018", body["type"]?.jsonPrimitive?.content)
                    assertEquals("60", body["offset"]?.jsonPrimitive?.content)
                }
                "search/suggest" -> assertEquals("mobile", body["type"]?.jsonPrimitive?.content)
                else -> error("Unexpected request: $route")
            }
            """{"code":200}"""
        }) { api ->
            api.search("夜曲", SearchType.Combined, ApiPage(30, 60))
            api.searchSuggestions("夜曲")
        }
    }

    @Test
    fun playlistRequestsAlwaysSendExplicitPagination() = runTest {
        withApi({ route, body ->
            assertEquals("playlist/track/all", route)
            assertEquals("50", body["limit"]?.jsonPrimitive?.content)
            assertEquals("100", body["offset"]?.jsonPrimitive?.content)
            """{"code":200,"songs":[]}"""
        }) { it.playlistSongs(123, ApiPage(50, 100)) }
    }

    @Test
    fun playlistPagesUseTrackIdsAndRestoreOrderEvenWhenTracksAreTruncated() = runTest {
        var calls = 0
        withApi({ route, body ->
            calls++
            assertEquals("session", body["cookie"]?.jsonPrimitive?.content)
            when (route) {
                "playlist/detail" -> """{"code":200,"playlist":{"trackIds":[{"id":1},{"id":2},{"id":3},{"id":4}],"tracks":[{"id":1,"name":"Preview only"}]}}"""
                "song/detail" -> {
                    assertEquals("2,3", body["ids"]?.jsonPrimitive?.content)
                    """{"code":200,"songs":[{"id":3,"name":"Third"},{"id":2,"name":"Second"}]}"""
                }
                else -> error("Unexpected request")
            }
        }) { api ->
            val page = api.playlistSongPage(123, ApiPage(2, 1), "session")
            assertEquals(listOf(2L, 3L), page.songs.map { it.id })
            assertEquals(4, page.total)
            assertEquals(3, page.nextOffset)
            assertEquals(2, calls)
        }
    }

    @Test
    fun playlistOutOfRangeDoesNotFetchOrRepeatThePreview() = runTest {
        var calls = 0
        withApi({ route, _ ->
            calls++
            assertEquals("playlist/detail", route)
            """{"code":200,"playlist":{"trackIds":[],"tracks":[]}}"""
        }) { api ->
            val page = api.playlistSongPage(123, ApiPage(50, 50))
            assertTrue(page.songs.isEmpty())
            assertNull(page.nextOffset)
            assertEquals(1, calls)
        }
    }

    @Test
    fun businessLoginFailureDoesNotLookLikeAnEmptyPlaylist() = runTest {
        withApi({ _, _ -> """{"code":301}""" }) { api ->
            assertEquals(301, assertFailsWith<MusicServiceException> { api.playlistSongPage(123) }.businessCode)
        }
    }

    @Test
    fun legacyAndEnhancedSongShapesNormalizeToTheSameDomainSong() {
        val legacy = json("""{"id":1,"name":"Song","artists":[{"id":2,"name":"Artist"}],"album":{"id":3,"name":"Album","picUrl":"https://example.test/cover"},"duration":12345,"fee":8}""")
        val enhanced = json("""{"id":1,"name":"Song","ar":[{"id":2,"name":"Artist"}],"al":{"id":3,"name":"Album","picUrl":"https://example.test/cover"},"dt":12345,"fee":8}""")
        assertEquals(Song.fromJson(legacy), Song.fromJson(enhanced))
        assertEquals(12345, Song.fromJson(enhanced)?.durationMs)
    }

    @Test
    fun zeroResultSearchAndPaginationAreDecodedWithoutInventingSongs() = runTest {
        withApi({ _, _ -> """{"code":200,"result":{"songCount":0}}""" }) { api ->
            val page = api.searchSongPage("No matches")
            assertEquals(0, page.total)
            assertEquals(false, page.hasMore)
            assertTrue(page.songs.isEmpty())
        }
    }

    @Test
    fun qrPollingMapsEveryStateAndRedactsReturnedCredentials() = runTest {
        val codes = listOf(800, 801, 802, 803, 301)
        val states = listOf(QrLoginState.Expired, QrLoginState.WaitingForScan, QrLoginState.WaitingForConfirmation, QrLoginState.Authorized, QrLoginState.Rejected)
        var index = 0
        withApi({ route, _ ->
            assertEquals("login/qr/check", route)
            """{"code":${codes[index++]},"cookie":"secret-account-cookie"}"""
        }) { api ->
            for (state in states) {
                val result = api.pollQrLogin("key")
                assertEquals(state, result.state)
                assertFalse(result.toString().contains("secret-account-cookie"))
                assertEquals(if (state == QrLoginState.Authorized) "secret-account-cookie" else null, result.cookie)
            }
        }
    }

    @Test
    fun sessionRestoreUsesExistingCookieWithoutRefreshingOrLoggingIn() = runTest {
        var calls = 0
        withApi({ route, body ->
            assertEquals("login/status", route)
            assertEquals("saved-session", body["cookie"]?.jsonPrimitive?.content)
            if (calls++ == 0) """{"data":{"code":200,"profile":{"userId":123,"nickname":"Listener"}}}"""
            else """{"code":301}"""
        }) { api ->
            assertEquals(123, api.restoreSession("saved-session").profile?.id)
            assertFalse(api.restoreSession("saved-session").isLoggedIn)
            assertEquals(2, calls)
        }
    }

    @Test
    fun qrAuthorizationWithoutARealCookieIsRejected() = runTest {
        for (response in listOf("""{"code":803}""", """{"code":803,"cookie":123}""", """{"code":803,"cookie":""}""")) {
            withApi({ _, _ -> response }) { api ->
                assertEquals("INVALID_RESPONSE", assertFailsWith<LocalApiException> { api.pollQrLogin("key") }.code)
            }
        }
    }

    @Test
    fun personalizedNewSongsSendOnlyAnExplicitLimitAndOptionalCookie() = runTest {
        val bodies = mutableListOf<JsonObject>()
        withApi({ route, body ->
            assertEquals("personalized/newsong", route)
            bodies += body
            """{"code":200,"result":[]}"""
        }) { api ->
            api.personalizedNewSongs()
            api.personalizedNewSongs(30, "MUSIC_U=session")
            assertFailsWith<IllegalArgumentException> { api.personalizedNewSongs(0) }
            assertFailsWith<IllegalArgumentException> { api.personalizedNewSongs(201) }
        }
        assertEquals(2, bodies.size)
        assertEquals(setOf("limit"), bodies[0].keys)
        assertEquals("10", bodies[0]["limit"]!!.jsonPrimitive.content)
        assertFalse(bodies[0]["limit"]!!.jsonPrimitive.isString)
        assertEquals(setOf("limit", "cookie"), bodies[1].keys)
        assertEquals("30", bodies[1]["limit"]!!.jsonPrimitive.content)
        assertEquals("MUSIC_U=session", bodies[1]["cookie"]!!.jsonPrimitive.content)
    }

    @Test
    fun invalidArgumentsNeverSendSmsOrOtherRequests() = runTest {
        withApi({ _, _ -> error("No request may be sent") }) { api ->
            assertFailsWith<IllegalArgumentException> { api.songUrls(emptyList()) }
            assertFailsWith<IllegalArgumentException> { api.songUrls(List(201) { 1L }) }
            assertFailsWith<IllegalArgumentException> { api.sendVerificationCode("invalid") }
            assertFailsWith<IllegalArgumentException> { api.loginWithCode("13800138000", "bad") }
            assertFailsWith<IllegalArgumentException> { api.loginWithPassword("13800138000", "") }
            assertFailsWith<IllegalArgumentException> { api.dailySongs("") }
            assertFailsWith<IllegalArgumentException> { ApiPage(201) }
        }
    }

    @Test
    fun mutationsAndRefreshFlagsAreJsonBooleansNotTruthyStrings() = runTest {
        withApi({ route, body ->
            val flag = body[if (route == "recommend/songs") "afresh" else "like"]!!.jsonPrimitive
            assertFalse(flag.isString)
            assertEquals("false", flag.content)
            """{"code":200}"""
        }) { api ->
            api.likeSong(123, false, "session")
            api.likeSongForUser(123, 456, false, "session")
            api.dailySongs("session", afresh = false)
        }
    }

    private suspend fun withApi(
        handler: (String, JsonObject) -> String,
        block: suspend (LocalApiClient) -> Unit,
    ) {
        val client = LocalApiClient(HttpClient(MockEngine { request ->
            assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
            val body = json(request.body.toByteArray().decodeToString())
            val response = handler(request.url.encodedPath.removePrefix("/api/"), body)
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }), "http://127.0.0.1:1145", TOKEN)
        try { block(client) } finally { client.close() }
    }

    private fun json(source: String) = Json.parseToJsonElement(source) as JsonObject
    private companion object { const val TOKEN = "nocturn-test-token-0123456789abcdef" }
}
