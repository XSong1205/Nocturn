package dev.nocturn.app.data

import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.localapi.client.LocalApiClient
import dev.nocturn.localapi.client.loginWithCode
import dev.nocturn.localapi.client.loginWithPassword
import dev.nocturn.localapi.client.sendVerificationCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** All login methods validate the profile and persist only the returned session cookie. */
internal class AccountAuthenticator(
    private val api: suspend () -> LocalApiClient,
    private val saveSession: suspend (String, AccountProfile) -> Unit,
) {
    suspend fun sendCode(phone: String, countryCode: String) {
        requireLoginSuccess(api().sendVerificationCode(phone, countryCode), sendingCode = true)
    }

    suspend fun loginWithCode(phone: String, captcha: String, countryCode: String): AccountProfile =
        completeLogin(api().loginWithCode(phone, captcha, countryCode))

    suspend fun loginWithPassword(phone: String, password: String, countryCode: String): AccountProfile =
        completeLogin(api().loginWithPassword(phone, password, countryCode))

    private suspend fun completeLogin(body: JsonObject): AccountProfile {
        requireLoginSuccess(body)
        val value = body["cookie"] as? JsonPrimitive
        val cookie = value?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            ?: throw MalformedResponseException("登录响应缺少会话凭据。")
        return saveValidatedCookie(cookie)
    }

    suspend fun saveValidatedCookie(cookie: String): AccountProfile {
        val profile = NeteaseJson.parseLoginStatus(api().loginStatus(cookie))
            ?: throw MusicRepositoryException(
                MusicRepositoryException.Kind.Service,
                "登录未生效，请重新登录。",
            )
        saveSession(cookie, profile)
        return profile
    }

    private fun requireLoginSuccess(body: JsonObject, sendingCode: Boolean = false) {
        // Never display the upstream message: it can include credentials or account details.
        val message = when (body.int("code")) {
            400, 502 -> if (sendingCode) {
                "验证码发送失败，请检查手机号和区号。"
            } else {
                "手机号、验证码或密码有误，请检查后重试。"
            }
            501 -> "未找到该手机号对应的账号，请检查手机号和区号。"
            503 -> "验证码错误或已过期，请重新获取。"
            509, -447, 405 -> "操作太频繁了，请稍后再试。"
            415, 416 -> "账号需要额外验证，请使用网易云音乐 App 扫码登录。"
            else -> null
        }
        if (message != null) throw MusicRepositoryException(MusicRepositoryException.Kind.Service, message)
        NeteaseJson.ensureSuccess(body)
    }
}
