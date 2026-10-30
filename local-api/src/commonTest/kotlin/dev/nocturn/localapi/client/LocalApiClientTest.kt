package dev.nocturn.localapi.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
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

class LocalApiClientTest {
    @Test
    fun healthDoesNotTransmitTheAccessToken() = runTest {
        withClient({ request ->
            assertEquals("http://127.0.0.1:1145/health", request.url.toString())
            assertEquals(HttpMethod.Get, request.method)
            assertNull(request.headers[HttpHeaders.Authorization])
            respondJson("""{"app":"Nocturn LocalAPI","version":"0.1.0","status":"ready"}""")
        }) { client ->
            assertEquals(LocalApiHealth("Nocturn LocalAPI", "0.1.0", "ready"), client.health())
        }
    }

    @Test
    fun searchUsesAnAuthenticatedJsonPostWithAnAccountCookie() = runTest {
        withClient({ request ->
            assertEquals("/api/search", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
            assertTrue(request.body.contentType.toString().startsWith("application/json"))
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()) as JsonObject
            assertEquals("夜曲", body["keywords"]?.jsonPrimitive?.content)
            assertEquals("30", body["limit"]?.jsonPrimitive?.content)
            assertEquals("account-session", body["cookie"]?.jsonPrimitive?.content)
            assertFalse(request.url.toString().contains("account-session"))
            respondJson("""{"code":200,"result":{"songs":[]}}""")
        }) { client ->
            assertEquals("200", client.searchSongs("夜曲", cookie = "account-session")["code"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun songUrlUsesTheSingleIdContract() = runTest {
        withClient({ request ->
            assertEquals("/api/song/url/v1", request.url.encodedPath)
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()) as JsonObject
            assertEquals("123456", body["id"]?.jsonPrimitive?.content)
            assertEquals("lossless", body["level"]?.jsonPrimitive?.content)
            respondJson("""{"code":200,"data":[]}""")
        }) { client -> client.songUrl(123456, AudioQuality.Lossless) }
    }

    @Test
    fun qrPendingStatusIsPreservedForTheAccountLayer() = runTest {
        withClient({ request ->
            assertEquals("/api/login/qr/check", request.url.encodedPath)
            respondJson("""{"code":801,"message":"Waiting for scan"}""")
        }) { client ->
            assertEquals("801", client.loginQrCheck("valid-key")["code"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun failuresAreTypedAndDoNotCopySensitiveServerText() = runTest {
        withClient({
            respondJson(
                """{"error":{"code":"UNAUTHORIZED","message":"$TOKEN cookie=secret"}}""",
                HttpStatusCode.Unauthorized,
            )
        }) { client ->
            val error = assertFailsWith<LocalApiException> { client.loginQrKey() }
            assertEquals(401, error.status)
            assertEquals("UNAUTHORIZED", error.code)
            assertFalse(error.toString().contains(TOKEN))
            assertFalse(error.toString().contains("cookie=secret"))
        }
    }

    @Test
    fun malformedResponsesDoNotLeakTheirContents() = runTest {
        withClient({ respondJson("not-json cookie=secret $TOKEN") }) { client ->
            val error = assertFailsWith<LocalApiException> { client.loginQrKey() }
            assertEquals("INVALID_RESPONSE", error.code)
            assertFalse(error.toString().contains(TOKEN))
            assertNull(error.cause)
        }
    }

    @Test
    fun unexpectedHealthServicesAreRejected() = runTest {
        withClient({ respondJson("""{"app":"Other service","version":"0.1.0","status":"ready"}""") }) { client ->
            assertEquals("INVALID_RESPONSE", assertFailsWith<LocalApiException> { client.health() }.code)
        }
    }

    @Test
    fun redirectsAreNotFollowedEvenWhenTheSuppliedClientEnablesThem() = runTest {
        var calls = 0
        withClient({
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.org/collect"))
        }) { client ->
            assertEquals(302, assertFailsWith<LocalApiException> { client.loginQrKey() }.status)
            assertEquals(1, calls)
        }
    }

    @Test
    fun invalidUrlsAndHeaderInjectionTokensAreRejected() {
        val source = HttpClient(MockEngine { respondJson("{}") })
        try {
            listOf(
                "https://127.0.0.1:1145", "http://example.org:1145", "http://localhost:1145",
                "http://127.0.0.1:0", "http://127.0.0.1:65536", "http://127.0.0.1:1145/api",
                "http://127.0.0.1:1145?redirect=https://example.org", "http://user:pass@127.0.0.1:1145",
                "http://[::1]:1145",
            ).forEach { url ->
                assertFailsWith<IllegalArgumentException> { LocalApiClient(source, url, TOKEN) }
            }
            assertFailsWith<IllegalArgumentException> { LocalApiClient(source, BASE_URL, "$TOKEN\r\nOther: value") }
            assertFailsWith<IllegalArgumentException> { LocalApiClient(source, BASE_URL, "too-short") }
            assertFailsWith<IllegalArgumentException> { LocalApiClient(source, BASE_URL, TOKEN + ".") }
            assertFailsWith<IllegalArgumentException> { LocalApiClient(source, BASE_URL, "a".repeat(257)) }
        } finally {
            source.close()
        }
    }

    @Test
    fun invalidConvenienceArgumentsDoNotReachTheServer() = runTest {
        withClient({ error("No request should have been sent.") }) { client ->
            assertFailsWith<IllegalArgumentException> { client.searchSongs(" ") }
            assertFailsWith<IllegalArgumentException> { client.searchSongs("valid", offset = -1) }
            assertFailsWith<IllegalArgumentException> { client.searchSongs("valid", limit = 201) }
            assertFailsWith<IllegalArgumentException> { client.songUrl(0) }
            assertFailsWith<IllegalArgumentException> { client.songDetail(emptyList()) }
            assertFailsWith<IllegalArgumentException> { client.lyrics(-1) }
            assertFailsWith<IllegalArgumentException> { client.loginQrCheck("") }
        }
    }

    @Test
    fun cancellationIsNotWrappedAsATransportError() = runTest {
        withClient({ throw CancellationException("Cancelled by caller") }) { client ->
            assertFailsWith<CancellationException> { client.loginQrKey() }
        }
    }

    private suspend fun withClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        block: suspend (LocalApiClient) -> Unit,
    ) {
        val source = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            followRedirects = true
        }
        val client = LocalApiClient(source, BASE_URL, TOKEN)
        try {
            block(client)
        } finally {
            client.close()
        }
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        const val BASE_URL = "http://127.0.0.1:1145"
        const val TOKEN = "nocturn-test-token-0123456789abcdef"
    }
}
