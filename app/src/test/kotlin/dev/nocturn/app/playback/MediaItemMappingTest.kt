package dev.nocturn.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit coverage for the pure Media3 mapping layer. Media items are built with Robolectric so
 * [android.net.Uri] parsing behaves like a real device; no service or controller is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaItemMappingTest {
    @Test
    fun buildsPlayableMediaItemAndUpgradesUrlsToHttps() {
        val item = requireNotNull(
            MediaItemMapping.buildMediaItem(
                playableSong(
                    id = 42L,
                    url = "http://m701.music.126.net/2026/stream-42.mp3?token=abc",
                    coverUrl = "http://p1.music.126.net/cover-42.jpg",
                ),
            ),
        )

        assertEquals("42", item.mediaId)
        val uri = requireNotNull(item.localConfiguration).uri
        assertEquals("https://m701.music.126.net/2026/stream-42.mp3?token=abc", uri.toString())
        val metadata = item.mediaMetadata
        assertEquals("夜航星", metadata.title?.toString())
        assertEquals("测试歌手", metadata.artist?.toString())
        assertEquals("夜色", metadata.albumTitle?.toString())
        assertEquals(245_000L, metadata.durationMs)
        assertEquals("https://p1.music.126.net/cover-42.jpg", metadata.artworkUri?.toString())
        assertFalse(metadata.isBrowsable ?: true)
        assertTrue(metadata.isPlayable ?: false)
    }

    @Test
    fun normalizeUrlUpgradesCleartextAndRejectsUnplayableValues() {
        assertEquals(
            "https://m701.music.126.net/a.mp3",
            MediaItemMapping.normalizeUrl("http://m701.music.126.net/a.mp3"),
        )
        assertEquals(
            "https://m701.music.126.net/a.mp3",
            MediaItemMapping.normalizeUrl("https://m701.music.126.net/a.mp3"),
        )
        assertNull(MediaItemMapping.normalizeUrl(null))
        assertNull(MediaItemMapping.normalizeUrl("   "))
        assertNull(MediaItemMapping.normalizeUrl("ftp://example.com/a.mp3"))
        assertNull(MediaItemMapping.normalizeUrl("file:///data/a.mp3"))
        assertNull(MediaItemMapping.normalizeUrl("not a url"))
    }

    @Test
    fun buildMediaItemRejectsEntriesWithoutPlayableStreamUrl() {
        assertNull(MediaItemMapping.buildMediaItem(playableSong(url = "")))
        assertNull(MediaItemMapping.buildMediaItem(playableSong(url = "   ")))
        assertNull(MediaItemMapping.buildMediaItem(playableSong(url = "ftp://example.com/a.mp3")))
    }

    @Test
    fun mediaIdRoundTripsToDomainSongId() {
        assertEquals(42L, MediaItemMapping.songIdOf(MediaItemMapping.mediaIdFor(42L)))
        assertEquals(42L, MediaItemMapping.songIdOf(" 42 "))
        assertNull(MediaItemMapping.songIdOf(null))
        assertNull(MediaItemMapping.songIdOf(""))
        assertNull(MediaItemMapping.songIdOf("not-a-number"))
        assertNull(MediaItemMapping.songIdOf("0"))
        assertNull(MediaItemMapping.songIdOf("-3"))
    }

    @Test
    fun resolveQueueDropsUnplayableEntriesAndRemapsStartIndex() {
        val songs = listOf(
            playableSong(id = 1L, url = "https://example.com/1.mp3"),
            playableSong(id = 2L, url = ""),
            playableSong(id = 3L, url = "https://example.com/3.mp3"),
            playableSong(id = 4L, url = "https://example.com/4.mp3"),
        )

        val resolved = MediaItemMapping.resolveQueue(songs, startIndex = 2)

        assertEquals(listOf("1", "3", "4"), resolved.items.map { it.mediaId })
        // Song 3 was requested and is the second playable entry in the resolved queue.
        assertEquals(1, resolved.startIndex)
        assertEquals(1, resolved.skipped)
    }

    @Test
    fun resolveQueueFallsBackToNextPlayableEntryWhenStartIsDropped() {
        val songs = listOf(
            playableSong(id = 1L, url = "https://example.com/1.mp3"),
            playableSong(id = 2L, url = ""),
            playableSong(id = 3L, url = "https://example.com/3.mp3"),
        )

        val resolved = MediaItemMapping.resolveQueue(songs, startIndex = 1)

        assertEquals(listOf("1", "3"), resolved.items.map { it.mediaId })
        assertEquals(1, resolved.startIndex)
    }

    @Test
    fun resolveQueueClampsOutOfRangeStartIndex() {
        val songs = listOf(
            playableSong(id = 1L, url = "https://example.com/1.mp3"),
            playableSong(id = 2L, url = "https://example.com/2.mp3"),
        )

        assertEquals(0, MediaItemMapping.resolveQueue(songs, startIndex = -5).startIndex)
        assertEquals(1, MediaItemMapping.resolveQueue(songs, startIndex = 99).startIndex)
    }

    @Test
    fun resolveQueueHandlesEmptyAndFullyUnplayableInput() {
        val empty = MediaItemMapping.resolveQueue(emptyList(), startIndex = 0)
        assertTrue(empty.items.isEmpty())
        assertEquals(-1, empty.startIndex)
        assertEquals(0, empty.skipped)

        val unplayable = listOf(
            playableSong(id = 1L, url = ""),
            playableSong(id = 2L, url = "ftp://example.com/2.mp3"),
        )
        val resolved = MediaItemMapping.resolveQueue(unplayable, startIndex = 1)
        assertTrue(resolved.items.isEmpty())
        assertEquals(-1, resolved.startIndex)
        assertEquals(2, resolved.skipped)
    }

    private fun playableSong(
        id: Long = 42L,
        url: String = "https://m701.music.126.net/2026/stream-42.mp3",
        coverUrl: String? = "https://p1.music.126.net/cover-42.jpg",
    ) = PlayableSong(
        id = id,
        title = "夜航星",
        artist = "测试歌手",
        album = "夜色",
        coverUrl = coverUrl,
        durationMs = 245_000L,
        url = url,
    )
}
