package dev.nocturn.app.data

import dev.nocturn.app.data.model.QrLoginState
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end parsing checks against JSON fixtures on the test classpath
 * (`app/src/test/resources/fixtures`). No network, no Android, no Node runtime.
 */
class NeteaseJsonFixtureTest {
    private fun fixture(name: String): JsonObject {
        val resource = javaClass.classLoader?.getResource("fixtures/$name")
            ?: error("Missing fixture: $name")
        return json(resource.readText())
    }

    @Test
    fun cloudSearchFixtureFeedsEverySearchTab() {
        val body = fixture("search_cloudsearch.json")

        val songs = NeteaseJson.parseSearchSongsPage(body, offset = 0)
        assertEquals(2, songs.songs.size)
        assertEquals(523, songs.total)
        assertTrue(songs.hasMore)
        assertEquals("海阔天空", songs.songs.first().name)
        assertEquals("电影《BEYOND日记》主题曲", songs.songs.first().alias)
        assertEquals("Beyond", songs.songs.first().artistText)

        val playlists = NeteaseJson.parseSearchPlaylistsPage(body, offset = 0)
        assertEquals(1, playlists.playlists.size)
        assertEquals(87, playlists.total)
        assertEquals("歌单作者", playlists.playlists.first().creatorName)

        val albums = NeteaseJson.parseSearchAlbumsPage(body, offset = 0)
        assertEquals(1, albums.albums.size)
        assertEquals("周杰伦", albums.albums.first().artistText)

        val artists = NeteaseJson.parseSearchArtistsPage(body, offset = 0)
        assertEquals(1, artists.artists.size)
        assertEquals(1_234_567L, artists.artists.first().fansCount)
    }

    @Test
    fun playlistDetailFixturePagesTheCompleteTrackIdList() {
        val body = fixture("playlist_detail.json")
        val detail = NeteaseJson.parsePlaylistDetail(body, selfUserId = 999)
        assertEquals("深夜安静的歌", detail?.name)
        assertEquals(listOf("华语", "安静"), detail?.tags)
        assertEquals(4, detail?.trackCount)
        assertTrue(detail?.canSubscribe == true)

        val trackIds = NeteaseJson.parsePlaylistTrackIds(body)
        assertEquals(listOf(347230L, 186016L, 1330348068L, 28815250L), trackIds)

        val page = NeteaseJson.playlistSongsPage(
            trackIds = trackIds,
            songs = emptyList(),
            offset = 0,
            limit = 2,
        )
        assertTrue(page.songs.isEmpty())
        assertEquals(4, page.total)
        assertEquals(2, page.nextOffset)
    }

    @Test
    fun lyricNewFixturePrefersWordSyncedYrcAndTranslation() {
        val lyrics = NeteaseJson.parseLyrics(fixture("lyric_new.json"))
        assertEquals(2, lyrics.lines.size)
        assertEquals(16210L, lyrics.lines[0].startMs)
        assertEquals("还没有", lyrics.lines[0].text)
        assertEquals("Not yet", lyrics.lines[0].translation)
        assertEquals("结束", lyrics.lines[1].text)
        assertEquals("Finished", lyrics.lines[1].translation)
    }

    @Test
    fun personalizedNewSongFixtureUnwrapsNestedSongsWithWrapperCovers() {
        val songs = NeteaseJson.parsePersonalizedSongs(fixture("personalized_newsong.json"))
        // The duplicate wrapper and the entry without a usable song are dropped; order is kept.
        assertEquals(listOf(3451531276L, 1330348068L), songs.map { it.id })

        val first = songs[0]
        assertEquals("一张相片", first.name)
        assertEquals("连麻Swimming", first.artistText)
        assertEquals("斩鸭线", first.album?.name)
        assertEquals(224_842L, first.durationMs)
        assertEquals(
            "http://p1.music.126.net/Eafh2qRUgw2IXXOVD4B9LQ==/109951174139932904.jpg",
            first.coverUrl,
        )

        val second = songs[1]
        assertEquals("买辣椒也用券 / 合作歌手", second.artistText)
        assertEquals("电视剧《加油，你是最棒的》插曲", second.alias)
        assertTrue(second.isVipOnly)
        assertEquals(325_868L, second.durationMs)
        assertEquals("http://p1.music.126.net/wrapper-cover.jpg", second.cover)
    }

    @Test
    fun personalizedNewSongsWithoutAResultListAreEmptyNotInvented() {
        assertTrue(NeteaseJson.parsePersonalizedSongs(json("""{"code":200}""")).isEmpty())
        assertTrue(NeteaseJson.parsePersonalizedSongs(json("""{"code":200,"result":[]}""")).isEmpty())
    }

    @Test
    fun loginStatusFixtureValidatesTheStoredSession() {
        val profile = NeteaseJson.parseLoginStatus(fixture("login_status.json"))
        assertEquals(32953014L, profile?.id)
        assertEquals("夜行者", profile?.nickname)
        assertEquals(11, profile?.vipLevel)
    }

    @Test
    fun qrFixturesAreNotConfusedWithLoginStatus() {
        val poll = NeteaseJson.parseQrPoll(json("""{"code":801}"""))
        assertEquals(QrLoginState.WaitingForScan, poll.state)
        val authorized = NeteaseJson.parseQrPoll(json("""{"code":803,"cookie":"MUSIC_U=x"}"""))
        assertEquals(QrLoginState.Authorized, authorized.state)
    }
}
