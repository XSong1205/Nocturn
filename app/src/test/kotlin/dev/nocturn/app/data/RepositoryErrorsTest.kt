package dev.nocturn.app.data

import dev.nocturn.localapi.client.LocalApiException
import dev.nocturn.localapi.client.MusicServiceException
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryErrorsTest {
    private val chinese = Regex("[\\u4e00-\\u9fa5]")

    @Test
    fun embeddedRuntimeFailuresBecomeChineseUnavailableErrors() {
        val mapped = LocalApiException("TRANSPORT_ERROR", message = "sanitized").toMusicRepositoryException()
        assertEquals(MusicRepositoryException.Kind.Unavailable, mapped.kind)
        assertTrue(chinese.containsMatchIn(mapped.message.orEmpty()))
    }

    @Test
    fun upstreamNetworkFailuresBecomeOfflineErrors() {
        val kinds = listOf("UPSTREAM_ERROR", "UPSTREAM_FAILURE", "INVALID_UPSTREAM_RESPONSE", "UPSTREAM_TIMEOUT")
            .map { LocalApiException(it, message = "sanitized").toMusicRepositoryException().kind }
        assertEquals(
            listOf(
                MusicRepositoryException.Kind.Offline,
                MusicRepositoryException.Kind.Offline,
                MusicRepositoryException.Kind.Offline,
                MusicRepositoryException.Kind.Offline,
            ),
            kinds,
        )
        assertEquals(
            MusicRepositoryException.Kind.Offline,
            SocketTimeoutException("timeout").toMusicRepositoryException().kind,
        )
    }

    @Test
    fun localProtocolFailuresAreTyped() {
        assertEquals(
            MusicRepositoryException.Kind.Service,
            LocalApiException("BUSY", message = "busy").toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Unavailable,
            LocalApiException("SERVICE_UNAVAILABLE", message = "down").toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Service,
            LocalApiException("INVALID_RESPONSE", message = "bad").toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Unknown,
            LocalApiException("INVALID_PARAMETERS", message = "bad").toMusicRepositoryException().kind,
        )
    }

    @Test
    fun businessCodesMapToUserFacingStates() {
        assertEquals(
            MusicRepositoryException.Kind.Service,
            NeteaseBusinessException(301).toMusicRepositoryException().kind,
        )
        assertTrue(
            NeteaseBusinessException(301).toMusicRepositoryException().message.orEmpty()
                .contains("登录"),
        )
        assertEquals(
            MusicRepositoryException.Kind.Empty,
            NeteaseBusinessException(404).toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Service,
            NeteaseBusinessException(-447).toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Service,
            NeteaseBusinessException(500_123).toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Service,
            MusicServiceException(301).toMusicRepositoryException().kind,
        )
    }

    @Test
    fun malformedPayloadsAndBadArgumentsDoNotLookLikeNetworkErrors() {
        assertEquals(
            MusicRepositoryException.Kind.Service,
            MalformedResponseException("bad").toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Unknown,
            IllegalArgumentException("bad").toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Unknown,
            IllegalStateException("bad").toMusicRepositoryException().kind,
        )
        assertEquals(
            MusicRepositoryException.Kind.Offline,
            IOException("bad").toMusicRepositoryException().kind,
        )
    }

    @Test
    fun repositoryExceptionsPassThroughUnchanged() {
        val original = MusicRepositoryException(MusicRepositoryException.Kind.Empty, "没有找到该歌单。")
        assertSame(original, original.toMusicRepositoryException())
    }

    @Test
    fun mappedMessagesNeverContainRawUpstreamDetailsOrCredentials() {
        val upstream = LocalApiException("UPSTREAM_FAILURE", message = "GET https://music.163.com failed")
        val messages = listOf(
            upstream.toMusicRepositoryException().message.orEmpty(),
            NeteaseBusinessException(301).toMusicRepositoryException().message.orEmpty(),
            MalformedResponseException("body").toMusicRepositoryException().message.orEmpty(),
        )
        for (message in messages) {
            assertTrue(chinese.containsMatchIn(message))
            assertFalse(message.contains("MUSIC_U"))
            assertFalse(message.contains("cookie", ignoreCase = true))
            assertFalse(message.contains("http", ignoreCase = true))
            assertFalse(message.contains("music.163.com"))
            assertFalse(message.contains("code", ignoreCase = true))
        }
    }
}
