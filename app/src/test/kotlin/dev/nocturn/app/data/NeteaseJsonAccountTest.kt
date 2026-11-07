package dev.nocturn.app.data

import dev.nocturn.app.data.model.QrLoginState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseJsonAccountTest {
    @Test
    fun loginStatusParsesWrappedProfileAndKeepsVipBackground() {
        val profile = NeteaseJson.parseLoginStatus(
            json(
                """
                {
                  "data": {
                    "code": 200,
                    "account": {"id": 123},
                    "profile": {
                      "userId": 123,
                      "nickname": "夜行者",
                      "avatarUrl": "https://example.test/avatar.jpg",
                      "backgroundUrl": "https://example.test/bg.jpg",
                      "signature": "晚上好",
                      "vipType": 11
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(123L, profile?.id)
        assertEquals("夜行者", profile?.nickname)
        assertEquals("https://example.test/bg.jpg", profile?.backgroundUrl)
        assertEquals("晚上好", profile?.signature)
        assertEquals(11, profile?.vipLevel)
    }

    @Test
    fun loginStatus301MeansAnonymousAndOtherBusinessCodesFail() {
        assertNull(NeteaseJson.parseLoginStatus(json("""{"code":301}""")))
        assertNull(NeteaseJson.parseLoginStatus(json("""{"data":{"code":301}}""")))
        val failure = assertThrows(NeteaseBusinessException::class.java) {
            NeteaseJson.parseLoginStatus(json("""{"data":{"code":-100}}"""))
        }
        assertEquals(-100, failure.businessCode)
        assertThrows(MalformedResponseException::class.java) {
            NeteaseJson.parseLoginStatus(json("""{"data":{}}"""))
        }
    }

    @Test
    fun profileWithoutIdIsNotInvented() {
        assertNull(NeteaseJson.parseProfile(json("""{"nickname":"匿名"}""")))
        assertEquals("网易云用户", NeteaseJson.parseProfile(json("""{"userId":5}"""))?.nickname)
    }

    @Test
    fun accountStatsMergeSubcountPlaylistCountsWithProfileFollowCounts() {
        val subcount = json(
            """
            {"code":200,"createPlaylistCount":8,"collectPlaylistCount":20,"subPlaylistCount":5}
            """.trimIndent(),
        )
        val account = json(
            """
            {"code":200,"profile":{"userId":1,"followedUserCount":12,"followeds":345}}
            """.trimIndent(),
        )
        val stats = NeteaseJson.parseAccountStats(subcount, account)
        assertEquals(28, stats.playlistCount)
        assertEquals(12, stats.followingCount)
        assertEquals(345, stats.followerCount)
    }

    @Test
    fun accountStatsSupportLegacySingleFieldShapeAndMissingResponses() {
        val legacy = NeteaseJson.parseAccountStats(
            json("""{"code":200,"playlistCount":9,"followedUserCount":4,"followerCount":7}"""),
            null,
        )
        assertEquals(9, legacy.playlistCount)
        assertEquals(4, legacy.followingCount)
        assertEquals(7, legacy.followerCount)

        val empty = NeteaseJson.parseAccountStats(null, null)
        assertEquals(0, empty.playlistCount)
        assertEquals(0, empty.followingCount)
        assertEquals(0, empty.followerCount)
    }

    @Test
    fun likedIdsAreDeduplicatedAndInvalidValuesDropped() {
        val ids = NeteaseJson.parseLikedIds(
            json("""{"code":200,"ids":[1,2,2,0,-3,"5","bad"]}"""),
        )
        assertEquals(setOf(1L, 2L, 5L), ids)
        assertTrue(NeteaseJson.parseLikedIds(json("""{"code":200}""")).isEmpty())
    }

    @Test
    fun recentSongsUnwrapTheRecentPlayEnvelopeAndKeepOrder() {
        val songs = NeteaseJson.parseRecentSongs(
            json(
                """
                {
                  "code": 200,
                  "data": {
                    "list": [
                      {"resourceId": 1, "playTime": 123, "data": {"id": 1, "name": "最近一", "ar": [{"id": 2, "name": "甲"}], "dt": 1000}},
                      {"resourceId": 2, "playTime": 456, "data": {"id": 2, "name": "最近二", "artists": [{"id": 3, "name": "乙"}], "duration": 2000}}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(1L, 2L), songs.map { it.id })
        assertEquals("甲", songs.first().artistText)
    }

    @Test
    fun recentSongsAlsoAcceptAPlainArrayPayload() {
        val songs = NeteaseJson.parseRecentSongs(
            json("""{"code":200,"data":[{"id":4,"name":"直接数组","ar":[{"id":1,"name":"丙"}],"dt":50}]}"""),
        )
        assertEquals(listOf(4L), songs.map { it.id })
    }

    @Test
    fun recommendSongsReadDailySongsAndLegacyRecommendKeys() {
        val daily = NeteaseJson.parseRecommendSongs(
            json(
                """
                {"code":200,"data":{"dailySongs":[{"id":1,"name":"每日一","ar":[{"id":2,"name":"甲"}],"dt":1000}]}}
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(1L), daily.map { it.id })

        val legacy = NeteaseJson.parseRecommendSongs(
            json("""{"code":200,"data":{"recommend":[{"id":2,"name":"推荐二","artists":[{"id":3,"name":"乙"}],"duration":2000}]}}"""),
        )
        assertEquals(listOf(2L), legacy.map { it.id })
        assertTrue(NeteaseJson.parseRecommendSongs(json("""{"code":200}""")).isEmpty())
    }

    @Test
    fun qrKeyAndImageStripTheBase64DataUrlPrefix() {
        assertEquals(
            "abc-key",
            NeteaseJson.parseQrKey(json("""{"code":200,"data":{"unikey":"abc-key"}}""")),
        )
        assertNull(NeteaseJson.parseQrKey(json("""{"code":200,"data":{}}""")))

        val image = NeteaseJson.parseQrImage(
            json("""{"code":200,"data":{"qrimg":"data:image/png;base64,iVBORw0KGgo="}}"""),
        )
        assertEquals("iVBORw0KGgo=", image)
        assertNull(NeteaseJson.parseQrImage(json("""{"code":200,"data":{"qrimg":""}}""")))
        assertNull(NeteaseJson.parseQrImage(json("""{"code":200,"data":{}}""")))
    }

    @Test
    fun qrPollingMapsEveryStateAndOnlyExposesTheAuthorizedCookie() {
        val cases = mapOf(
            800 to QrLoginState.Expired,
            801 to QrLoginState.WaitingForScan,
            802 to QrLoginState.WaitingForConfirmation,
            803 to QrLoginState.Authorized,
            301 to QrLoginState.Rejected,
        )
        for ((code, expected) in cases) {
            val poll = NeteaseJson.parseQrPoll(
                json("""{"code":$code,"cookie":"MUSIC_U=secret"}"""),
            )
            assertEquals(expected, poll.state)
            assertEquals(code, poll.code)
            if (expected == QrLoginState.Authorized) {
                assertEquals("MUSIC_U=secret", poll.cookie)
            } else {
                assertNull(poll.cookie)
            }
        }
        assertNull(NeteaseJson.parseQrPoll(json("""{"code":803}""")).cookie)
        assertFalse(NeteaseJson.parseQrPoll(json("""{"code":801}""")).cookie != null)
        assertThrows(MalformedResponseException::class.java) {
            NeteaseJson.parseQrPoll(json("""{}"""))
        }
    }

    @Test
    fun ensureSuccessAcceptsOnlyBusinessCode200() {
        assertEquals(200, NeteaseJson.ensureSuccess(json("""{"code":200}""")).int("code"))
        assertThrows(NeteaseBusinessException::class.java) {
            NeteaseJson.ensureSuccess(json("""{"code":301}"""))
        }
        assertThrows(NeteaseBusinessException::class.java) {
            NeteaseJson.ensureSuccess(json("""{"code":-460}"""))
        }
        assertThrows(MalformedResponseException::class.java) {
            NeteaseJson.ensureSuccess(json("""{"msg":"no code"}"""))
        }
    }
}
