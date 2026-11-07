package dev.nocturn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NeteaseJsonSearchTest {
    @Test
    fun cloudSearchSongPageInfersHasMoreFromTheTypeTotal() {
        val page = NeteaseJson.parseSearchSongsPage(
            json(
                """
                {
                  "code": 200,
                  "result": {
                    "songs": [
                      {"id": 1, "name": "A", "ar": [{"id": 2, "name": "X"}], "al": {"id": 3, "name": "Y"}, "dt": 1000},
                      {"id": 4, "name": "B", "ar": [], "al": null, "dt": 2000}
                    ],
                    "songCount": 100
                  }
                }
                """.trimIndent(),
            ),
            offset = 0,
        )
        assertEquals(listOf(1L, 4L), page.songs.map { it.id })
        assertEquals(100, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun lastSearchPageHasNoMoreResults() {
        val page = NeteaseJson.parseSearchSongsPage(
            json(
                """
                {"code":200,"result":{"songs":[{"id":9,"name":"Z","artists":[{"id":1,"name":"Y"}],"duration":3}],"songCount":31}}
                """.trimIndent(),
            ),
            offset = 30,
        )
        assertEquals(1, page.songs.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun legacySearchPayloadIsAcceptedForTheSameSongPage() {
        val page = NeteaseJson.parseSearchSongsPage(
            json(
                """
                {"code":200,"result":{"songs":[{"id":5,"name":"Legacy","artists":[{"id":6,"name":"Singer"}],"album":{"id":7,"name":"Album","picUrl":"https://example.test/a.jpg"},"duration":123}],"songCount":1}}
                """.trimIndent(),
            ),
            offset = 0,
        )
        assertEquals("Singer", page.songs.first().artistText)
        assertEquals("Album", page.songs.first().album?.name)
        assertFalse(page.hasMore)
    }

    @Test
    fun explicitHasMoreFlagWinsOverTheTotalHeuristic() {
        val page = NeteaseJson.parseSearchPlaylistsPage(
            json(
                """
                {"code":200,"result":{"playlists":[{"id":1,"name":"P","coverImgUrl":"https://example.test/p.jpg","playCount":3,"trackCount":2}],"playlistCount":999,"hasMore":false}}
                """.trimIndent(),
            ),
            offset = 0,
        )
        assertFalse(page.hasMore)
        assertEquals(999, page.total)
        assertEquals(3L, page.playlists.first().playCount)
    }

    @Test
    fun albumAndArtistSearchPagesAreNormalized() {
        val albums = NeteaseJson.parseSearchAlbumsPage(
            json(
                """
                {"code":200,"result":{"albums":[{"id":10,"name":"专辑","picUrl":"https://example.test/album.jpg","artist":{"id":11,"name":"歌手"},"size":12,"publishTime":1600000000000}],"albumCount":1}}
                """.trimIndent(),
            ),
            offset = 0,
        )
        assertEquals(1, albums.albums.size)
        assertEquals("歌手", albums.albums.first().artistText)
        assertEquals(12, albums.albums.first().trackCount)
        assertFalse(albums.hasMore)

        val artists = NeteaseJson.parseSearchArtistsPage(
            json(
                """
                {"code":200,"result":{"artists":[{"id":20,"name":"歌手甲","picUrl":"https://example.test/a.jpg","albumSize":8,"musicSize":50,"fansCount":123}],"artistCount":1}}
                """.trimIndent(),
            ),
            offset = 0,
        )
        assertEquals(1, artists.artists.size)
        assertEquals("歌手甲", artists.artists.first().name)
        assertEquals(8, artists.artists.first().albumCount)
        assertEquals(123L, artists.artists.first().fansCount)
    }

    @Test
    fun searchResultWithoutSongsIsAnEmptyPageButMalformedEnvelopeThrows() {
        val empty = NeteaseJson.parseSearchSongsPage(
            json("""{"code":200,"result":{"songCount":0}}"""),
            offset = 0,
        )
        assertTrue(empty.songs.isEmpty())
        assertEquals(0, empty.total)
        assertFalse(empty.hasMore)
        assertThrows(MalformedResponseException::class.java) {
            NeteaseJson.parseSearchSongsPage(json("""{"code":200}"""), offset = 0)
        }
    }

    @Test
    fun mobileSuggestionsAndOrderFallbackAreDeduplicatedAndBounded() {
        val suggestions = NeteaseJson.parseSearchSuggestions(
            json(
                """
                {
                  "code": 200,
                  "result": {
                    "allMatch": [
                      {"keyword": "夜曲"},
                      {"keyword": "夜曲 周杰伦"},
                      {"keyword": "  "},
                      {"name": "无 keyword 的建议"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("夜曲", "夜曲 周杰伦", "无 keyword 的建议"), suggestions)

        val ordered = NeteaseJson.parseSearchSuggestions(
            json("""{"code":200,"result":{"order":["海阔天空","Beyond"]}}"""),
        )
        assertEquals(listOf("海阔天空", "Beyond"), ordered)
    }

    @Test
    fun hotSearchesSupportNewAndLegacyShapes() {
        val modern = NeteaseJson.parseHotSearches(
            json(
                """
                {"code":200,"data":[{"searchWord":"晴天"},{"searchWord":"夜曲"},{"searchWord":"晴天"},{"searchWord":""}]}
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("晴天", "夜曲"), modern)

        val legacy = NeteaseJson.parseHotSearches(
            json("""{"code":200,"result":{"hots":[{"first":"稻香"},{"first":"七里香"}]}}"""),
        )
        assertEquals(listOf("稻香", "七里香"), legacy)
    }

    @Test
    fun defaultKeywordReadsShowKeywordAndHandlesMissingData() {
        assertEquals(
            "夜曲",
            NeteaseJson.parseDefaultKeyword(json("""{"code":200,"data":{"showKeyword":"夜曲"}}""")),
        )
        assertEquals(
            "晴天",
            NeteaseJson.parseDefaultKeyword(json("""{"code":200,"data":{"realkeyword":"晴天"}}""")),
        )
        assertNull(NeteaseJson.parseDefaultKeyword(json("""{"code":200,"data":{}}""")))
        assertNull(NeteaseJson.parseDefaultKeyword(json("""{"code":200}""")))
    }
}
