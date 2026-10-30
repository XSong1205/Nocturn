package dev.nocturn.localapi

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * One embedded Node runtime per application process. The native Node VM cannot be restarted after
 * it exits. Callers may cancel their own wait without cancelling application-owned startup.
 */
class LocalApiRuntime private constructor(private val context: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<LocalApiRuntimeState>(LocalApiRuntimeState.Idle)
    val state: StateFlow<LocalApiRuntimeState> = mutableState.asStateFlow()
    private var startup: Deferred<LocalApiConnection>? = null
    private val nativeExited = AtomicBoolean(false)
    private val nativeInvoked = AtomicBoolean(false)
    private val stateLock = Any()

    suspend fun start(): LocalApiConnection {
        val connection = startupTask().await()
        check(!nativeExited.get()) { "LocalAPI has exited; restart the application process." }
        return connection
    }

    @Synchronized
    private fun startupTask(): Deferred<LocalApiConnection> {
        startup?.let { previous ->
            if (!previous.isCancelled || nativeInvoked.get()) return previous
            // Asset preparation/library loading failed before Node was entered. Retrying is safe.
        }
        return scope.async {
            try {
                check(Application.getProcessName() == context.packageName) {
                    "LocalAPI must be started in the application's main process."
                }
                check(Os.sysconf(OsConstants._SC_PAGESIZE) == 4096L) {
                    "The pinned Node runtime supports 4 KB memory pages only; this device requires a rebuilt libnode.so."
                }
                mutableState.value = LocalApiRuntimeState.Preparing
                val entry = installBundle()
                val dataDirectory = File(context.noBackupFilesDir, "nocturn-local-api/data").apply { mkdirs() }
                val readyFile = File(dataDirectory, "runtime-ready.json")
                check(!readyFile.exists() || readyFile.delete()) { "Unable to clear LocalAPI readiness state." }
                val token = ByteArray(32).also(SecureRandom()::nextBytes)
                    .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }
                // Load libraries before starting the worker so a missing ABI is reported to the caller.
                NodeNative.ensureLoaded()
                mutableState.value = LocalApiRuntimeState.Starting
                Thread({
                    nativeInvoked.set(true)
                    val exitCode = runCatching {
                        NodeNative.start(entry.absolutePath, dataDirectory.absolutePath, 0, token)
                    }.getOrDefault(-1)
                    synchronized(stateLock) {
                        nativeExited.set(true)
                        mutableState.value = LocalApiRuntimeState.Failed(
                            "LocalAPI runtime exited (code $exitCode). Restart the application process.", true,
                        )
                    }
                }, "Nocturn-Node").apply { isDaemon = true; start() }

                val deadline = SystemClock.elapsedRealtime() + STARTUP_TIMEOUT_MS
                while (SystemClock.elapsedRealtime() < deadline) {
                    check(!nativeExited.get()) { "LocalAPI runtime exited during startup." }
                    val port = readReadyPort(readyFile)
                    if (port != null && isHealthy(port) && hasValidCredential(port, token)) {
                        synchronized(stateLock) {
                            check(!nativeExited.get()) { "LocalAPI runtime exited during startup." }
                            mutableState.value = LocalApiRuntimeState.Ready(port)
                        }
                        return@async LocalApiConnection(port, token)
                    }
                    delay(100)
                }
                error("LocalAPI did not become ready within ${STARTUP_TIMEOUT_MS / 1000} seconds.")
            } catch (error: Throwable) {
                mutableState.value = LocalApiRuntimeState.Failed(
                    error.message ?: "Unable to start LocalAPI.", nativeInvoked.get(),
                )
                throw error
            }
        }.also { startup = it }
    }

    private fun readReadyPort(file: File): Int? = runCatching {
        if (!file.isFile || file.length() > 4096) return null
        JSONObject(file.readText()).getInt("port").takeIf { it in 1..65535 }
    }.getOrNull()

    private fun isHealthy(port: Int): Boolean = runCatching {
        val connection = URI("http://127.0.0.1:$port/health").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 500
            connection.readTimeout = 500
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) return false
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            json.optString("app") == "Nocturn LocalAPI" && json.optString("status") == "ready"
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    // Missing search parameters are rejected before touching the network. This validates the
    // credential during startup without generating an upstream request or account side effect.
    private fun hasValidCredential(port: Int, token: String): Boolean = runCatching {
        val connection = URI("http://127.0.0.1:$port/api/search").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 500
            connection.readTimeout = 500
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray()) }
            connection.responseCode == 400 && connection.errorStream.bufferedReader().use {
                JSONObject(it.readText()).getJSONObject("error").getString("code") == "INVALID_PARAMETERS"
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private fun installBundle(): File {
        val manifest = context.assets.open("$ASSET_ROOT/manifest.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val version = manifest.getString("version")
        require(version.matches(Regex("[a-zA-Z0-9._-]{1,128}"))) { "Invalid LocalAPI bundle version." }
        val bundleRoot = File(context.noBackupFilesDir, "nocturn-local-api/bundles/$version")
        bundleRoot.mkdirs()
        val canonicalRoot = bundleRoot.canonicalPath + File.separator
        val files = manifest.getJSONArray("files")
        val checksums = manifest.getJSONObject("sha256")
        for (index in 0 until files.length()) {
            val relativePath = files.getString(index)
            val target = File(bundleRoot, relativePath)
            require(target.canonicalPath.startsWith(canonicalRoot)) { "Invalid LocalAPI asset path." }
            val expected = checksums.getString(relativePath)
            require(expected.matches(Regex("[a-f0-9]{64}"))) { "Invalid LocalAPI asset checksum." }
            if (target.isFile && sha256(target) == expected) continue
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, target.name + ".tmp")
            context.assets.open("$ASSET_ROOT/$relativePath").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(sha256(temporary) == expected) { "LocalAPI asset verification failed." }
            check(temporary.renameTo(target)) { "Unable to install LocalAPI assets." }
        }
        return File(bundleRoot, "main.cjs").also { check(it.isFile) { "LocalAPI entry is missing." } }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val ASSET_ROOT = "nocturn-local-api"
        private const val STARTUP_TIMEOUT_MS = 30_000L
        @Volatile private var instance: LocalApiRuntime? = null

        fun get(context: Context): LocalApiRuntime = instance ?: synchronized(this) {
            instance ?: LocalApiRuntime(context.applicationContext as Application).also { instance = it }
        }
    }
}
