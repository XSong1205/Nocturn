package dev.nocturn.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.localapi.client.LocalApiClient
import dev.nocturn.localapi.client.LocalApiEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Owns the account cookie for the whole process.
 *
 * The cookie is passed explicitly to the loopback API per request and never exposed to the UI. At
 * rest it is sealed with an AES-256-GCM key held by the AndroidKeyStore and stored as Base64 in a
 * private SharedPreferences file; [SessionCipher] fails closed, so a corrupted or foreign envelope
 * is deleted instead of being used. The cookie is never written to plaintext preferences and never
 * logged. [session] mirrors the validated profile so screens can react to sign-in/out.
 */
internal class SessionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val mutableSession = MutableStateFlow<AccountProfile?>(null)

    val session: StateFlow<AccountProfile?> = mutableSession.asStateFlow()

    /** In-memory cached cookie; decrypted from storage at most once per process. */
    private var cachedCookie: String? = null

    /** The current account cookie, or null when anonymous. Never logged or returned to the UI. */
    suspend fun cookie(): String? = lock.withLock {
        cachedCookie ?: readStoredCookieLocked()?.also { cachedCookie = it }
    }

    /** Persists an authorized cookie and publishes the validated profile only after it is durable. */
    suspend fun save(cookie: String, profile: AccountProfile?) = lock.withLock {
        require(cookie.isNotBlank()) { "An account cookie is required." }
        writeStoredCookieLocked(cookie)
        cachedCookie = cookie
        if (profile != null) mutableSession.value = profile
    }

    /**
     * Validates the stored cookie with `login/status` without re-authenticating.
     *
     * A business code of 301 (anonymous / expired) clears the stored session and returns null.
     * Transport and protocol failures propagate so the caller can show a retry state; the stored
     * cookie is kept for the next attempt. No stored cookie means the account is simply anonymous.
     */
    suspend fun restore(api: LocalApiClient): AccountProfile? {
        val saved = cookie() ?: return null
        val profile = try {
            NeteaseJson.parseLoginStatus(
                api.request(
                    LocalApiEndpoint.LoginStatus,
                    buildJsonObject { put("cookie", saved) },
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: NeteaseBusinessException) {
            if (error.businessCode == 301) {
                clearIfCookieMatches(saved)
                return null
            }
            throw error
        }
        if (profile == null) {
            clearIfCookieMatches(saved)
            return null
        }
        return lock.withLock {
            if (cachedCookie == saved) {
                mutableSession.value = profile
                profile
            } else {
                mutableSession.value
            }
        }
    }

    /** Forgets the account cookie and the published profile (logout / invalid session). */
    suspend fun clear() = lock.withLock {
        check(preferences.edit().remove(KEY_COOKIE).commit()) {
            "Unable to clear the stored account session."
        }
        cachedCookie = null
        mutableSession.value = null
        Unit
    }

    private suspend fun clearIfCookieMatches(cookie: String) = lock.withLock {
        if (cachedCookie != cookie) return@withLock
        check(preferences.edit().remove(KEY_COOKIE).commit()) {
            "Unable to clear the stored account session."
        }
        cachedCookie = null
        mutableSession.value = null
    }

    private fun readStoredCookieLocked(): String? {
        val sealed = preferences.getString(KEY_COOKIE, null) ?: return null
        val key = runCatching { loadOrCreateKey() }.getOrNull()
            ?: return null
        val plaintext = SessionCipher.open(key, sealed)
        if (plaintext == null) {
            // Key rotated away or value tampered with: drop it instead of retrying every launch.
            runCatching { preferences.edit().remove(KEY_COOKIE).commit() }
            return null
        }
        return plaintext.toString(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    private fun writeStoredCookieLocked(cookie: String) {
        val sealed = SessionCipher.seal(loadOrCreateKey(), cookie.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(KEY_COOKIE, sealed).commit()) {
            "Unable to persist the account session."
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // The session must be restorable on launch without a biometric prompt.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nocturn-account-session"
        const val KEY_SIZE_BITS = 256
        const val PREFERENCES = "nocturn-session"
        const val KEY_COOKIE = "sealed-cookie"
    }
}
