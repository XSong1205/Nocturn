package dev.nocturn.localapi

/** Process-local credentials. Do not persist or log [accessToken]. */
class LocalApiConnection internal constructor(val port: Int, val accessToken: String) {
    val baseUrl: String get() = "http://127.0.0.1:$port"
    override fun toString(): String = "LocalApiConnection(port=$port, accessToken=<redacted>)"
}

sealed interface LocalApiRuntimeState {
    data object Idle : LocalApiRuntimeState
    data object Preparing : LocalApiRuntimeState
    data object Starting : LocalApiRuntimeState
    data class Ready(val port: Int) : LocalApiRuntimeState
    data class Failed(val message: String, val processRestartRequired: Boolean) : LocalApiRuntimeState
}
