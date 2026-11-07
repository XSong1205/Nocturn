package dev.nocturn.app.data

import dev.nocturn.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NeteaseJsonPlaylistTest {
    private fun song(id: Long) = Song(
        id = id,
        name = "Song $id",
        artists = emptyList(),
        album = null,
        durationMs = 1000L,
    )

    @Test
    fun playlistPageUsesTrackIdsOrderAndReportsTheNextOffset() {
        val page = NeteaseJson.playlistSongsPage(
            trackIds = listOf(5L, 3L, 9L, 1L, 7L),
            songs = listOf(song(9L), song(1L), song(3L)),
            offset = 1,
            limit = 3,
        )
        assertEquals(listOf(3L, 9L, 1L), page.songs.map { it.id })
        assertEquals(5, page.total)
        assertEquals(4, page.nextOffset)
    }

    @Test
    fun playlistPageAtTheEndHasNoNextOffset() {
        val page = NeteaseJson.playlistSongsPage(
            trackIds = listOf(1L, 2L, 3L),
            songs = listOf(song(3L)),
            offset = 2,
            limit = 50,
        )
        assertEquals(listOf(3L), page.songs.map { it.id })
        assertNull(page.nextOffset)
    }

    @Test
    fun playlistPageBeyondTheEndIsEmptyAndDoesNotInventSongs() {
        val page = NeteaseJson.playlistSongsPage(
            trackIds = listOf(1L, 2L),
            songs = listOf(song(1L), song(2L)),
            offset = 10,
            limit = 50,
        )
        assertTrue(page.songs.isEmpty())
        assertEquals(2, page.total)
        assertNull(page.nextOffset)
    }

    @Test
    fun playlistPageAdvancesPastSongsTheProviderCouldNotReturn() {
        // Track 6, 7 are missing from the detail response; the window still advances by 3 so a
        // single missing song can never make "load more" repeat the same page forever.
        val page = NeteaseJson.playlistSongsPage(
            trackIds = listOf(4L, 5L, 6L, 7L, 8L),
            songs = listOf(song(4L), song(5L), song(8L)),
            offset = 0,
            limit = 3,
        )
        assertEquals(listOf(4L, 5L), page.songs.map { it.id })
        assertEquals(3, page.nextOffset)
        assertEquals(5, page.total)
    }

    @Test
    fun emptyPlaylistPageIsValidAndHasNoNextOffset() {
        val page = NeteaseJson.playlistSongsPage(emptyList(), emptyList(), 0, 50)
        assertTrue(page.songs.isEmpty())
        assertEquals(0, page.total)
        assertNull(page.nextOffset)
    }

    @Test
    fun completeTrackIdsAreReadFromPlaylistDetailNotFromTheTruncatedPreview() {
        val body = json(
            """
            {
              "code": 200,
              "playlist": {
                "trackIds": [{"id": 11}, {"id": 12}, {"id": 13}],
                "tracks": [{"id": 11, "name": "only a preview"}]
              }
            }
            """.trimIndent(),
        )
        assertEquals(listOf(11L, 12L, 13L), NeteaseJson.parsePlaylistTrackIds(body))
        assertThrows(MalformedResponseException::class.java) {
            NeteaseJson.parsePlaylistTrackIds(json("""{"code":200,"playlist":{}}"""))
        }
    }

    @Test
    fun playlistDetailNormalizesCreatorTagsAndSubscriptionState() {
        val body = json(
            """
            {
              "code": 200,
              "playlist": {
                "id": 24381616,
                "name": "深夜歌单",
                "coverImgUrl": "https://example.test/cover.jpg",
                "description": "适合夜晚",
                "creator": {"userId": 500, "nickname": "夜行者", "avatarUrl": "https://example.test/avatar.jpg"},
                "playCount": 1234,
                "trackCount": 42,
                "subscribed": true,
                "tags": ["华语", "夜晚", ""],
                "updateFrequency": "每周一更新",
                "createTime": 1600000000
              }
            }
            """.trimIndent(),
        )
        val detail = NeteaseJson.parsePlaylistDetail(body, selfUserId = 900)
        assertEquals(24381616L, detail?.id)
        assertEquals("夜行者", detail?.creatorName)
        assertEquals(1234L, detail?.playCount)
        assertEquals(42, detail?.trackCount)
        assertTrue(detail?.subscribed == true)
        assertTrue(detail?.canSubscribe == true)
        assertEquals(listOf("华语", "夜晚"), detail?.tags)
        assertEquals("每周一更新", detail?.updateFrequency)
    }

    @Test
    fun ownPlaylistCannotBeSubscribedAndMissingPayloadReturnsNull() {
        val mine = json(
            """
            {"code":200,"playlist":{"id":1,"name":"我的歌单","creator":{"userId":900,"nickname":"我"}}}
            """.trimIndent(),
        )
        assertFalse(NeteaseJson.parsePlaylistDetail(mine, selfUserId = 900)?.canSubscribe == true)
        assertNull(NeteaseJson.parsePlaylistDetail(json("""{"code":200}""")))
    }

    @Test
    fun userPlaylistPageCarriesMoreFlagAndSummaryFields() {
        val body = json(
            """
            {
              "code": 200,
              "more": true,
              "playlist": [
                {
                  "id": 7,
                  "name": "我的收藏",
                  "coverImgUrl": "https://example.test/p.jpg",
                  "playCount": 10,
                  "trackCount": 3,
                  "creator": {"nickname": "我"},
                  "updateFrequency": null
                },
                {"id": 0, "name": "invalid"},
                {"id": 8, "name": ""}
              ]
            }
            """.trimIndent(),
        )
        val page = NeteaseJson.parseUserPlaylists(body)
        assertTrue(page.hasMore)
        assertEquals(listOf(7L), page.playlists.map { it.id })
        assertEquals("我", page.playlists.first().creatorName)
        assertEquals(10L, page.playlists.first().playCount)
    }

    @Test
    fun toplistsExposePreviewTracksAndCounts() {
        val body = json(
            """
            {
              "code": 200,
              "list": [
                {
                  "id": 19723756,
                  "name": "飙升榜",
                  "coverImgUrl": "https://example.test/top.jpg",
                  "updateFrequency": "每天更新",
                  "description": "每日飙升",
                  "trackCount": 100,
                  "tracks": [
                    {"id": 1, "name": "A", "ar": [{"id": 2, "name": "歌手"}], "dt": 180000},
                    {"id": 3, "name": "B", "artists": [{"id": 4, "name": "歌手2"}], "duration": 200000}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val charts = NeteaseJson.parseToplists(body)
        assertEquals(1, charts.size)
        val chart = charts.first()
        assertEquals(19723756L, chart.id)
        assertEquals("飙升榜", chart.name)
        assertEquals(100, chart.trackCount)
        assertEquals(2, chart.tracks.size)
        assertEquals(listOf(1L, 3L), chart.tracks.map { it.id })
        assertEquals("每天更新", chart.updateFrequency)
    }
}
