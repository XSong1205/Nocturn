package dev.nocturn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseJsonSongsTest {
    @Test
    fun legacyAndCloudSearchSongShapesNormalizeToTheSameDomainSong() {
        val legacy = json(
            """
            {
              "id": 347230,
              "name": "海阔天空",
              "artists": [{"id": 2, "name": "Beyond"}],
              "album": {"id": 3, "name": "乐与怒", "picUrl": "https://example.test/cover.jpg"},
              "duration": 326000,
              "fee": 8
            }
            """.trimIndent(),
        )
        val cloud = json(
            """
            {
              "id": 347230,
              "name": "海阔天空",
              "ar": [{"id": 2, "name": "Beyond"}],
              "al": {"id": 3, "name": "乐与怒", "picUrl": "https://example.test/cover.jpg"},
              "dt": 326000,
              "fee": 8
            }
            """.trimIndent(),
        )
        val legacySong = NeteaseJson.parseSong(legacy)
        val cloudSong = NeteaseJson.parseSong(cloud)
        assertEquals(legacySong, cloudSong)
        assertEquals(326000L, cloudSong?.durationMs)
        assertEquals("Beyond", cloudSong?.artistText)
        assertEquals("https://example.test/cover.jpg", cloudSong?.cover)
        assertEquals(8, cloudSong?.fee)
    }

    @Test
    fun aliasesArtistsAndUnknownTracksAreNormalized() {
        val song = NeteaseJson.parseSong(
            json(
                """
                {
                  "id": 1,
                  "name": "夜曲",
                  "ar": [{"id": 10, "name": "周杰伦"}],
                  "al": {"id": 20, "name": "十一月的萧邦", "picUrl": ""},
                  "dt": 0,
                  "alia": ["电影主题曲", "  "]
                }
                """.trimIndent(),
            ),
        )
        assertEquals("周杰伦", song?.artistText)
        assertEquals("电影主题曲", song?.alias)
        assertNull(song?.coverUrl)
        assertEquals("十一月的萧邦", song?.album?.name)
        assertEquals(0L, song?.durationMs)
    }

    @Test
    fun invalidSongEntriesAreSkippedAndDuplicateIdsKeepTheFirstOccurrence() {
        val songs = NeteaseJson.parseSongs(
            jsonArray(
                """
                [
                  {"id": 0, "name": "invalid id"},
                  {"id": 5, "name": ""},
                  {"id": 5, "name": "第一首"},
                  {"id": 5, "name": "重复"},
                  {"id": 8, "name": "第二首", "artists": [], "duration": -20}
                ]
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(5L, 8L), songs.map { it.id })
        assertEquals("第一首", songs.first().name)
        assertEquals("未知歌手", songs.first().artistText)
        assertEquals(0L, songs.last().durationMs)
    }

    @Test
    fun batchStreamUrlsKeepEveryRequestedIdAndTreatUnavailableSourcesAsNull() {
        val urls = NeteaseJson.parseStreamUrls(
            json(
                """
                {
                  "code": 200,
                  "data": [
                    {"id": 1, "url": "https://example.test/1.mp3"},
                    {"id": 2, "url": null},
                    {"id": 3, "url": ""},
                    {"id": 4, "url": "http://example.test/4.mp3"},
                    {"id": 5, "url": "javascript:alert(1)"},
                    {"id": 999, "url": "https://example.test/other.mp3"}
                  ]
                }
                """.trimIndent(),
            ),
            requestedIds = listOf(1L, 2L, 3L, 4L, 5L),
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), urls.keys.toList())
        assertEquals("https://example.test/1.mp3", urls[1L])
        assertNull(urls[2L])
        assertNull(urls[3L])
        assertEquals("http://example.test/4.mp3", urls[4L])
        assertNull(urls[5L])
        assertEquals(5, urls.size)
    }

    @Test
    fun streamUrlsWithoutDataStillReportEveryIdAsUnavailable() {
        val urls = NeteaseJson.parseStreamUrls(json("""{"code":200}"""), listOf(7L, 8L))
        assertEquals(setOf(7L, 8L), urls.keys)
        assertTrue(urls.values.all { it == null })
    }
}
