package dev.nocturn.app.data

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM envelope used to seal the account cookie before it is written to SharedPreferences.
 *
 * Storage format is `Base64(iv[12] || ciphertext || tag[16])`. The GCM tag makes tampering and
 * wrong-key decryption fail closed; [open] returns null instead of handing corrupted bytes to the
 * JSON/HTTP layer. The actual key lives in the AndroidKeyStore (see [SessionStore]); this object is
 * deliberately free of Android APIs so the cryptographic envelope can be unit-tested on the JVM.
 */
internal object SessionCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Encrypts [plaintext] with a fresh random IV and returns the Base64 storage string. */
    fun seal(key: SecretKey, plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // AndroidKeyStore requires a provider-generated IV for randomized encryption keys.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected GCM IV size." }
        val ciphertext = cipher.doFinal(plaintext)
        val packed = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(packed, destinationOffset = 0)
        ciphertext.copyInto(packed, destinationOffset = iv.size)
        return Base64.getEncoder().encodeToString(packed)
    }

    /**
     * Decrypts a value produced by [seal]. Returns null when the value is malformed, was tampered
     * with, or was encrypted with a different key (for example after a device restore).
     */
    fun open(key: SecretKey, value: String): ByteArray? = try {
        val packed = Base64.getDecoder().decode(value)
        if (packed.size <= IV_BYTES) {
            null
        } else {
            val iv = packed.copyOfRange(0, IV_BYTES)
            val ciphertext = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        }
    } catch (_: Exception) {
        // Do not propagate provider/key details: callers only need "cannot be decrypted".
        null
    }

    /** True when [value] has the shape of an envelope produced by [seal]. */
    fun looksValid(value: String): Boolean = try {
        Base64.getDecoder().decode(value).size > IV_BYTES
    } catch (_: IllegalArgumentException) {
        false
    }
}
