package dev.nocturn.localapi

internal object NodeNative {
    init {
        System.loadLibrary("nocturn_node")
    }

    fun ensureLoaded() = Unit

    @JvmStatic
    external fun start(entryPath: String, dataDirectory: String, port: Int, token: String): Int
}
