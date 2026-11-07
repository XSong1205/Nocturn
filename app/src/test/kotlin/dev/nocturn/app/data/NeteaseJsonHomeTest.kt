package dev.nocturn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseJsonHomeTest {
    @Test
    fun personalizedPlaylistsUsePicUrlAndTrackCounts() {
        val playlists = NeteaseJson.parsePersonalizedPlaylists(
            json(
                """
                {
                  "code": 200,
                  "result": [
                    {"id": 1, "name": "推荐一", "picUrl": "https://example.test/p1.jpg", "playCount": 500},
                    {"id": 2, "name": "推荐二", "picUrl": "https://example.test/p2.jpg", "playCount": 0, "trackCount": 20},
                    {"id": 0, "name": "invalid"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(1L, 2L), playlists.map { it.id })
        assertEquals("https://example.test/p1.jpg", playlists[0].coverUrl)
        assertEquals(500L, playlists[0].playCount)
        assertEquals(20, playlists[1].trackCount)
    }

    @Test
    fun accountRecommendedPlaylistsUseTheRecommendArray() {
        // `/recommend/resource` (the account-aware "每日推荐歌单") wraps its shelf in `recommend`.
        val playlists = NeteaseJson.parsePersonalizedPlaylists(
            json(
                """
                {
                  "code": 200,
                  "featureFirst": false,
                  "haveRcmdSongs": true,
                  "recommend": [
                    {"id": 11, "name": "账号推荐一", "picUrl": "https://example.test/a1.jpg", "playCount": 1200, "trackCount": 32},
                    {"id": 12, "name": "账号推荐二", "coverImgUrl": "https://example.test/a2.jpg"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(11L, 12L), playlists.map { it.id })
        assertEquals("https://example.test/a1.jpg", playlists[0].coverUrl)
        assertEquals(32, playlists[0].trackCount)
        assertEquals("https://example.test/a2.jpg", playlists[1].coverUrl)
    }

    @Test
    fun albumDetailIncludesArtistsMetadataAndTracks() {
        val detail = NeteaseJson.parseAlbumDetail(
            json(
                """
                {
                  "code": 200,
                  "songs": [{"id": 99, "name": "Top-level song"}],
                  "album": {
                    "id": 32311,
                    "name": "叶惠美",
                    "picUrl": "https://example.test/album.jpg",
                    "publishTime": 1600000000000,
                    "company": "杰威尔音乐",
                    "description": "第四张专辑",
                    "artists": [{"id": 6452, "name": "周杰伦"}],
                    "songs": [
                      {"id": 1, "name": "以父之名", "ar": [{"id": 6452, "name": "周杰伦"}], "al": {"id": 32311, "name": "叶惠美"}, "dt": 200000},
                      {"id": 2, "name": "晴天", "ar": [{"id": 6452, "name": "周杰伦"}], "al": {"id": 32311, "name": "叶惠美"}, "dt": 269000}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(32311L, detail?.id)
        assertEquals("叶惠美", detail?.name)
        assertEquals("周杰伦", detail?.artistText)
        assertEquals("杰威尔音乐", detail?.company)
        assertEquals(1600000000000L, detail?.publishTime)
        assertEquals(listOf(1L, 2L), detail?.songs?.map { it.id })
    }

    @Test
    fun albumDetailWithoutAlbumObjectReturnsNull() {
        assertNull(NeteaseJson.parseAlbumDetail(json("""{"code":200,"songs":[]}""")))
    }

    @Test
    fun artistDetailMergesHotSongsEnrichmentAndAlbums() {
        val artistBody = json(
            """
            {
              "code": 200,
              "artist": {"id": 6452, "name": "周杰伦", "picUrl": "https://example.test/artist.jpg", "briefDesc": "华语歌手", "albumSize": 30, "musicSize": 300},
              "hotSongs": [
                {"id": 1, "name": "晴天", "ar": [{"id": 6452, "name": "周杰伦"}], "dt": 269000},
                {"id": 2, "name": "夜曲", "ar": [{"id": 6452, "name": "周杰伦"}], "dt": 226000}
              ]
            }
            """.trimIndent(),
        )
        val detailBody = json("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦","fansCount":5000,"briefDesc":"更详细的简介"}}}""")
        val albumBody = json(
            """
            {
              "code": 200,
              "hotAlbums": [
                {"id": 32311, "name": "叶惠美", "picUrl": "https://example.test/a1.jpg", "artist":{"id":6452,"name":"周杰伦"}, "size": 11, "publishTime": 1600000000000}
              ]
            }
            """.trimIndent(),
        )
        val detail = NeteaseJson.parseArtistDetail(artistBody, detailBody, albumBody)
        assertEquals(6452L, detail?.id)
        assertEquals("周杰伦", detail?.name)
        assertEquals("https://example.test/artist.jpg", detail?.avatarUrl)
        assertEquals("更详细的简介", detail?.briefDescription)
        assertEquals(5000L, detail?.fansCount)
        assertEquals(30, detail?.albumCount)
        assertEquals(2, detail?.hotSongs?.size)
        assertEquals(1, detail?.albums?.size)
        assertEquals("周杰伦", detail?.albums?.first()?.artistText)
    }

    @Test
    fun artistDetailFallsBackToTheEnrichmentResponseWhenIdentityFailed() {
        val detailOnly = json("""{"code":200,"data":{"artist":{"id":5,"name":"独立歌手","fansCount":12}}}""")
        val detail = NeteaseJson.parseArtistDetail(null, detailOnly, null)
        assertEquals(5L, detail?.id)
        assertEquals("独立歌手", detail?.name)
        assertTrue(detail?.hotSongs?.isEmpty() == true)
        assertTrue(detail?.albums?.isEmpty() == true)
        assertNull(NeteaseJson.parseArtistDetail(null, null, null))
    }

    @Test
    fun albumAndArtistSummaryListsSkipMalformedEntries() {
        val albums = NeteaseJson.parseAlbumSummaries(
            jsonArray(
                """
                [
                  {"id": 1, "name": "专辑一", "picUrl": "https://example.test/1.jpg", "artists": [{"id": 2, "name": "甲"}, {"id": 3, "name": "乙"}], "size": 5, "publishTime": 100},
                  {"id": 4, "name": "专辑二", "artist": {"id": 6, "name": "丙"}, "size": 2},
                  {"id": 7, "name": ""}
                ]
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(1L, 4L), albums.map { it.id })
        assertEquals("甲 / 乙", albums[0].artistText)
        assertEquals("丙", albums[1].artistText)
        assertEquals(5, albums[0].trackCount)

        val artists = NeteaseJson.parseArtistSummaries(
            jsonArray("""[{"id": 9, "name": "歌手", "img1v1Url": "https://example.test/i.jpg"}]"""),
        )
        assertEquals(1, artists.size)
        assertEquals("https://example.test/i.jpg", artists.first().avatarUrl)
        assertFalse(artists.first().albumCount > 0)
    }
}
