package dev.nocturn.app.data

import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCipherTest {
    private val key: SecretKey = newKey()

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun sealedCookieRoundTripsUnicodeAndPunctuation() {
        val cookie = "MUSIC_U=abc123; __csrf=def456; NMTID=中文"
        val sealed = SessionCipher.seal(key, cookie.toByteArray(Charsets.UTF_8))
        val opened = SessionCipher.open(key, sealed)
        assertArrayEquals(cookie.toByteArray(Charsets.UTF_8), opened)
    }

    @Test
    fun everySealUsesAFreshRandomIv() {
        val plaintext = "MUSIC_U=abc".toByteArray(Charsets.UTF_8)
        val first = SessionCipher.seal(key, plaintext)
        val second = SessionCipher.seal(key, plaintext)
        assertNotEquals(first, second)
        assertArrayEquals(plaintext, SessionCipher.open(key, first))
        assertArrayEquals(plaintext, SessionCipher.open(key, second))
    }

    @Test
    fun wrongKeyFailsClosedInsteadOfReturningGarbage() {
        val sealed = SessionCipher.seal(key, "MUSIC_U=abc".toByteArray(Charsets.UTF_8))
        assertNull(SessionCipher.open(newKey(), sealed))
    }

    @Test
    fun tamperedEnvelopeIsRejected() {
        val sealed = SessionCipher.seal(key, "MUSIC_U=abc".toByteArray(Charsets.UTF_8))
        val chars = sealed.toCharArray()
        // Flip one character in the ciphertext section; the GCM tag must reject it.
        val index = (chars.size / 2).coerceAtLeast(4)
        chars[index] = if (chars[index] == 'A') 'B' else 'A'
        assertNull(SessionCipher.open(key, String(chars)))
        assertNull(SessionCipher.open(key, "not-base64!"))
        assertNull(SessionCipher.open(key, "AAAA"))
    }

    @Test
    fun envelopeShapeValidationIsCheapAndDoesNotThrow() {
        val sealed = SessionCipher.seal(key, "MUSIC_U=abc".toByteArray(Charsets.UTF_8))
        assertTrue(SessionCipher.looksValid(sealed))
        assertFalse(SessionCipher.looksValid(""))
        assertFalse(SessionCipher.looksValid("AAAA"))
        assertFalse(SessionCipher.looksValid("@@@"))
    }

    @Test
    fun randomBytesAreNotMistakenForAWrappedCookie() {
        val random = ByteArray(SecureRandom().nextInt(64) + 1).also { SecureRandom().nextBytes(it) }
        val plaintext = "MUSIC_U=random".toByteArray(Charsets.UTF_8)
        assertNull(SessionCipher.open(key, java.util.Base64.getEncoder().encodeToString(random + plaintext)))
    }
}
