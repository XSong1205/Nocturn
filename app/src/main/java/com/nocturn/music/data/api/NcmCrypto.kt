package com.nocturn.music.data.api

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NcmCrypto {
    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d81ec4e80479701e471db14801f7f509f6b0090bce5e2b1937cb514337da0c2223"
    private const val EXPONENT = "010001"

    private val PRESET_KEY = "0CoJUm6Qyw8W8jud".toByteArray(Charsets.UTF_8)
    private val FIXED_IV = "0102030405060708".toByteArray(Charsets.UTF_8)
    private val EAPI_KEY = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private val secureRandom = SecureRandom()

    fun weapi(text: String): Pair<String, String> {
        val secretKey = generateSecretKey(16)
        val intermediate = aesCbcEncrypt(text.toByteArray(Charsets.UTF_8), PRESET_KEY, FIXED_IV)
        val base64Intermediate = Base64.getEncoder().encode(intermediate)
        val finalParamsBytes = aesCbcEncrypt(base64Intermediate, secretKey.toByteArray(Charsets.UTF_8), FIXED_IV)
        val params = Base64.getEncoder().encodeToString(finalParamsBytes)

        val reversedKey = secretKey.reversed()
        val encSecKey = rsaEncrypt(reversedKey, MODULUS, EXPONENT)
        return Pair(params, encSecKey)
    }

    fun eapi(url: String, text: String): String {
        val message = "nobody${url}use${text}md5forencrypt"
        val digest = md5(message)
        val data = "$url-_-$text-_-$digest"
        val encrypted = aesEcbEncrypt(data.toByteArray(Charsets.UTF_8), EAPI_KEY)
        return bytesToHex(encrypted)
    }

    fun eapiDecrypt(cipherBytes: ByteArray): String {
        return try {
            val decrypted = aesEcbDecrypt(cipherBytes, EAPI_KEY)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun generateSecretKey(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(BASE62[secureRandom.nextInt(BASE62.length)])
        }
        return sb.toString()
    }

    private fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    private fun aesEcbEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(data)
    }

    private fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        return cipher.doFinal(data)
    }

    private fun rsaEncrypt(text: String, modulusHex: String, exponentHex: String): String {
        val modulus = BigInteger(modulusHex, 16)
        val exponent = BigInteger(exponentHex, 16)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val textInt = BigInteger(1, textBytes)
        val encrypted = textInt.modPow(exponent, modulus)
        var hex = encrypted.toString(16)
        if (hex.length < 256) {
            hex = "0".repeat(256 - hex.length) + hex
        }
        return hex
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(bytes).lowercase()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
