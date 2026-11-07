package dev.nocturn.app.data

import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.localapi.client.LocalApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountAuthenticatorTest {
    @Test
    fun passwordLoginValidatesCookieBeforeSavingSession() = runBlocking {
        val requests = mutableListOf<Pair<String, JsonObject>>()
        val client = mockClient { route, body ->
            requests += route to body
            when (route) {
                "login/cellphone" -> """{"code":200,"cookie":"MUSIC_U=test-session"}"""
                "login/status" -> """{"data":{"code":200,"profile":{"userId":42,"nickname":"测试用户"}}}"""
                else -> error("Unexpected route: $route")
            }
        }
        try {
            var saved: Pair<String, AccountProfile>? = null
            val auth = AccountAuthenticator({ client }) { cookie, profile -> saved = cookie to profile }
            val profile = auth.loginWithPassword("13800138000", "a-password", "86")
            assertEquals(42L, profile.id)
            assertEquals("MUSIC_U=test-session", saved?.first)
            assertEquals("a-password", requests[0].second["password"]?.toString()?.trim('"'))
            assertFalse(requests[0].second.containsKey("captcha"))
            assertEquals("MUSIC_U=test-session", requests[1].second["cookie"]?.toString()?.trim('"'))
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectedLoginNeverSavesSession() = runBlocking {
        val client = mockClient { _, _ -> """{"code":502,"msg":"unsafe upstream details"}""" }
        try {
            var saved = false
            val auth = AccountAuthenticator({ client }) { _, _ -> saved = true }
            val error = assertThrows(MusicRepositoryException::class.java) {
                runBlocking { auth.loginWithCode("13800138000", "123456", "86") }
            }
            assertFalse(saved)
            assertFalse(error.message.orEmpty().contains("unsafe"))
        } finally {
            client.close()
        }
    }

    private fun mockClient(handler: (String, JsonObject) -> String): LocalApiClient =
        LocalApiClient(HttpClient(MockEngine { request ->
            val route = request.url.encodedPath.removePrefix("/api/")
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()) as JsonObject
            respond(
                handler(route, body),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }), "http://127.0.0.1:1145", "nocturn-test-token-0123456789abcdef")
}
