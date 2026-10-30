package dev.nocturn.localapi.client

import android.content.Context
import dev.nocturn.localapi.LocalApiRuntime
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/** Starts or joins the process-wide embedded runtime, then creates an owned HTTP client. */
suspend fun createLocalApi(context: Context): LocalApiClient {
    val connection = LocalApiRuntime.get(context.applicationContext).start()
    val client = HttpClient(OkHttp) {
        followRedirects = false
        expectSuccess = false
        engine {
            config {
                // Login, SMS and like requests must not be replayed by the HTTP engine.
                retryOnConnectionFailure(false)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 35_000
            socketTimeoutMillis = 35_000
        }
    }
    return LocalApiClient(client, connection.baseUrl, connection.accessToken)
}
