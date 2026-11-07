package dev.nocturn.app.data

import dev.nocturn.localapi.client.LocalApiException
import dev.nocturn.localapi.client.MusicServiceException
import java.io.IOException

private typealias Kind = MusicRepositoryException.Kind

/**
 * The music service reports business failures through the JSON `code` field instead of HTTP
 * errors. Only the numeric code is retained: upstream messages can embed account data and must
 * never reach a log, an exception message or the UI.
 */
internal class NeteaseBusinessException(val businessCode: Int) :
    Exception("The music service returned business code $businessCode.")

/** The local service answered HTTP 200 with JSON that this app cannot interpret. */
internal class MalformedResponseException(detail: String) : Exception(detail)

/**
 * Maps transport, protocol and business failures onto one user-presentable exception type.
 *
 * Messages are short Chinese sentences because they are rendered directly in retry states. No
 * upstream response body, cookie or request URL is included in either the message or the cause.
 */
internal fun Throwable.toMusicRepositoryException(): MusicRepositoryException {
    if (this is MusicRepositoryException) return this
    return when (this) {
        is NeteaseBusinessException -> businessException(businessCode, this)
        // Thrown by the shared typed helpers (for example LyricParser) for upstream business codes.
        is MusicServiceException -> businessException(businessCode, this)
        is MalformedResponseException ->
            MusicRepositoryException(Kind.Service, "音乐服务返回了无法识别的数据，请稍后重试。", this)
        is LocalApiException -> localServiceException(this)
        is IOException ->
            MusicRepositoryException(Kind.Offline, "网络连接不可用，请检查网络后重试。", this)
        is IllegalArgumentException ->
            MusicRepositoryException(Kind.Unknown, "请求参数有误，请稍后重试。", this)
        else ->
            MusicRepositoryException(Kind.Unknown, "出现未知错误，请稍后重试。", this)
    }
}

private fun businessException(
    code: Int,
    cause: Throwable,
): MusicRepositoryException = when (code) {
    // Not logged in / session expired. The stored cookie is no longer usable.
    301, 302 ->
        MusicRepositoryException(Kind.Service, "登录状态已失效，请重新登录。", cause)
    // Rate limiting reported by the provider.
    -447, 405 ->
        MusicRepositoryException(Kind.Service, "操作太频繁了，请稍后再试。", cause)
    // Content removed, wrong id or no search result.
    400, 404 ->
        MusicRepositoryException(Kind.Empty, "没有找到相关内容。", cause)
    else ->
        MusicRepositoryException(Kind.Service, "音乐服务暂时不可用，请稍后重试。", cause)
}

private fun localServiceException(error: LocalApiException): MusicRepositoryException =
    when (error.code) {
        // The loopback socket could not be reached: the embedded runtime is not ready.
        "TRANSPORT_ERROR" ->
            MusicRepositoryException(Kind.Unavailable, "本机音乐服务连接失败，请重新打开应用后再试。", error)
        "UPSTREAM_TIMEOUT" ->
            MusicRepositoryException(Kind.Offline, "音乐服务响应超时，请检查网络后重试。", error)
        "UPSTREAM_ERROR", "UPSTREAM_FAILURE", "INVALID_UPSTREAM_RESPONSE" ->
            MusicRepositoryException(Kind.Offline, "无法连接音乐服务，请检查网络后重试。", error)
        "BUSY" ->
            MusicRepositoryException(Kind.Service, "音乐服务繁忙，请稍后重试。", error)
        "UNAUTHORIZED", "FORBIDDEN", "SERVICE_UNAVAILABLE", "INTERNAL_ERROR" ->
            MusicRepositoryException(Kind.Unavailable, "本机音乐服务未就绪，请重新打开应用后再试。", error)
        "INVALID_RESPONSE" ->
            MusicRepositoryException(Kind.Service, "音乐服务返回了无法识别的数据，请稍后重试。", error)
        "INVALID_JSON", "INVALID_BODY", "INVALID_PARAMETERS", "BODY_TOO_LARGE",
        "UNSUPPORTED_ENCODING", "UNSUPPORTED_MEDIA_TYPE", "METHOD_NOT_ALLOWED", "UNKNOWN_ENDPOINT",
        ->
            MusicRepositoryException(Kind.Unknown, "请求参数有误，请稍后重试。", error)
        else ->
            MusicRepositoryException(Kind.Service, "音乐服务暂时不可用，请稍后重试。", error)
    }
