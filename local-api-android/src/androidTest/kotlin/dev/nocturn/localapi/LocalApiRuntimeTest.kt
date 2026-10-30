package dev.nocturn.localapi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalApiRuntimeTest {
    @Test
    fun concurrentStartupHealthAndAuthorization() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = LocalApiRuntime.get(context)
        val connections = coroutineScope {
            List(4) { async { runtime.start() } }.map { it.await() }
        }
        val connection = connections.first()
        assertTrue(connections.all { it === connection })
        assertEquals(LocalApiRuntimeState.Ready(connection.port), runtime.state.value)
        assertFalse(connection.toString().contains(connection.accessToken))
        val health = request(connection, "/health")
        assertEquals(200, health.first)
        assertEquals("Nocturn LocalAPI", JSONObject(health.second).getString("app"))
        assertFalse(health.second.contains(connection.accessToken))
        assertEquals(401, request(connection, "/api/search", post = true).first)
        assertEquals(404, request(connection, "/api/not-an-endpoint", post = true, authenticate = true).first)
        assertEquals(200, request(connection, "/health").first)
    }

    private fun request(
        api: LocalApiConnection,
        path: String,
        post: Boolean = false,
        authenticate: Boolean = false,
    ): Pair<Int, String> {
        val connection = URI(api.baseUrl + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            if (authenticate) connection.setRequestProperty("Authorization", "Bearer ${api.accessToken}")
            if (post) {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write("{}".toByteArray()) }
            }
            val code = connection.responseCode
            val body = (if (code < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            connection.disconnect()
        }
    }
}
