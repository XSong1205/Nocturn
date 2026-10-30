package dev.nocturn.localapi.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * An authenticated client for the Nocturn service running inside this device.
 *
 * Ownership of [httpClient] transfers to this object. [close] closes both the configured
 * client and its source, including their shared engine. Redirects are disabled to keep the
 * per-process credential at the configured loopback address.
 *
 * No Cookie is retained here: the account layer supplies it only for requests that need it.
 */
class LocalApiClient(
    httpClient: HttpClient,
    baseUrl: String,
    private val token: String,
) {
    private val baseUrl = validateBaseUrl(baseUrl)
    private val sourceClient = httpClient
    private val client: HttpClient

    init {
        require(TOKEN_PATTERN.matches(token)) { "A valid LocalAPI access token is required." }
        client = httpClient.config {
            followRedirects = false
            expectSuccess = false
        }
    }

    suspend fun health(): LocalApiHealth {
        val body = execute { client.get("$baseUrl/health") }
        val health = LocalApiHealth(
            app = body.string("app"),
            version = body.string("version"),
            status = body.string("status"),
        )
        if (health.app != "Nocturn LocalAPI" || health.status != "ready" || health.version.isBlank()) {
            throw invalidResponse()
        }
        return health
    }

    suspend fun request(
        endpoint: LocalApiEndpoint,
        parameters: JsonObject = JsonObject(emptyMap()),
    ): JsonObject = execute {
        client.post("$baseUrl/api/${endpoint.path}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(parameters.toString())
        }
    }

    suspend fun searchSongs(
        keywords: String,
        limit: Int = 30,
        offset: Int = 0,
        cookie: String? = null,
    ): JsonObject {
        require(keywords.isNotBlank() && keywords.length <= 200) { "Supply 1 to 200 search characters." }
        require(limit in 1..200) { "Search limit must be between 1 and 200." }
        require(offset in 0..100_000) { "Search offset must be between 0 and 100000." }
        return request(LocalApiEndpoint.Search, buildJsonObject {
            put("keywords", keywords)
            put("type", 1)
            put("limit", limit)
            put("offset", offset)
            cookie?.let { put("cookie", it) }
        })
    }

    suspend fun songDetail(ids: List<Long>, cookie: String? = null): JsonObject {
        require(ids.isNotEmpty() && ids.size <= 200 && ids.all { it > 0 }) {
            "Supply between 1 and 200 positive song IDs."
        }
        return request(LocalApiEndpoint.SongDetail, buildJsonObject {
            put("ids", ids.joinToString(","))
            cookie?.let { put("cookie", it) }
        })
    }

    suspend fun songUrl(
        id: Long,
        level: AudioQuality = AudioQuality.Standard,
        cookie: String? = null,
    ): JsonObject {
        require(id > 0) { "A positive song ID is required." }
        return request(LocalApiEndpoint.SongUrl, buildJsonObject {
            put("id", id.toString())
            put("level", level.value)
            cookie?.let { put("cookie", it) }
        })
    }

    suspend fun lyrics(id: Long, cookie: String? = null): JsonObject {
        require(id > 0) { "A positive song ID is required." }
        return request(LocalApiEndpoint.Lyric, buildJsonObject {
            put("id", id.toString())
            cookie?.let { put("cookie", it) }
        })
    }

    suspend fun loginQrKey(): JsonObject = request(LocalApiEndpoint.LoginQrKey)

    suspend fun loginQrCreate(key: String, qrImage: Boolean = true): JsonObject {
        require(key.isNotBlank()) { "A QR login key is required." }
        return request(LocalApiEndpoint.LoginQrCreate, buildJsonObject {
            put("key", key)
            put("qrimg", qrImage)
        })
    }

    suspend fun loginQrCheck(key: String): JsonObject {
        require(key.isNotBlank()) { "A QR login key is required." }
        return request(LocalApiEndpoint.LoginQrCheck, buildJsonObject { put("key", key) })
    }

    suspend fun loginStatus(cookie: String): JsonObject {
        require(cookie.isNotBlank()) { "An account cookie is required." }
        return request(LocalApiEndpoint.LoginStatus, buildJsonObject { put("cookie", cookie) })
    }

    fun close() {
        client.close()
        sourceClient.close()
    }

    private suspend fun execute(call: suspend () -> HttpResponse): JsonObject {
        val response = try {
            call()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw LocalApiException("TRANSPORT_ERROR", message = "The local service could not be reached.")
        }
        val body = try {
            JSON.parseToJsonElement(response.bodyAsText()) as? JsonObject
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (response.status.value !in 200..299) {
            val error = body?.get("error") as? JsonObject
            val proposedCode = (error?.get("code") as? JsonPrimitive)?.contentOrNull
            val code = proposedCode?.takeIf { it in KNOWN_ERROR_CODES } ?: "HTTP_ERROR"
            // Server text is intentionally not copied: upstream messages can contain account data.
            throw LocalApiException(code, response.status.value, errorMessage(code, response.status.value))
        }
        return body ?: throw invalidResponse()
    }

    private fun JsonObject.string(name: String): String =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: throw invalidResponse()

    private fun invalidResponse() = LocalApiException(
        "INVALID_RESPONSE", message = "The local service returned an invalid response.",
    )

    private companion object {
        val JSON = Json
        val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{32,256}")
        val BASE_URL_PATTERN = Regex("^http://127\\.0\\.0\\.1:([0-9]{1,5})/?$")
        val KNOWN_ERROR_CODES = setOf(
            "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "METHOD_NOT_ALLOWED", "UNSUPPORTED_MEDIA_TYPE",
            "INVALID_JSON", "INVALID_BODY", "BODY_TOO_LARGE", "INVALID_PARAMETERS", "UNKNOWN_ENDPOINT",
            "UPSTREAM_ERROR", "UPSTREAM_FAILURE", "UPSTREAM_TIMEOUT", "INTERNAL_ERROR", "SERVICE_UNAVAILABLE",
            "UNSUPPORTED_ENCODING", "ORIGIN_FORBIDDEN", "BUSY", "INVALID_UPSTREAM_RESPONSE",
        )

        fun validateBaseUrl(value: String): String {
            val match = BASE_URL_PATTERN.matchEntire(value)
            require(match != null && match.groupValues[1].toIntOrNull() in 1..65535) {
                "LocalAPI must use an explicit HTTP loopback address and port."
            }
            return value.removeSuffix("/")
        }

        fun errorMessage(code: String, status: Int): String = when (code) {
            "UNAUTHORIZED", "FORBIDDEN" -> "The local service rejected the access credential."
            "UPSTREAM_TIMEOUT" -> "The music service did not respond in time."
            "UPSTREAM_ERROR", "UPSTREAM_FAILURE", "INVALID_UPSTREAM_RESPONSE" ->
                "The music service could not complete this request."
            "BUSY" -> "The local service is busy. Try again shortly."
            "INVALID_JSON", "INVALID_BODY", "INVALID_PARAMETERS", "BODY_TOO_LARGE" ->
                "The local service rejected the request parameters."
            else -> "The local service returned HTTP $status."
        }
    }
}

enum class AudioQuality(val value: String) {
    Standard("standard"),
    Higher("higher"),
    Exhigh("exhigh"),
    Lossless("lossless"),
    Hires("hires"),
    Jyeffect("jyeffect"),
    Sky("sky"),
    Dolby("dolby"),
    Jymaster("jymaster"),
}
