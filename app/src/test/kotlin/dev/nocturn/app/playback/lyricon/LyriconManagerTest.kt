package dev.nocturn.app.playback.lyricon

import android.content.Context
import android.media.session.PlaybackState
import android.os.SystemClock
import dev.nocturn.app.data.MusicRepository
import dev.nocturn.app.data.SettingsStore
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AccountStats
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.HomeFeed
import dev.nocturn.app.data.model.LyricLine as NocturnLyricLine
import dev.nocturn.app.data.model.LyricWord as NocturnLyricWord
import dev.nocturn.app.data.model.Lyrics as NocturnLyrics
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.PlaylistSongsPage
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.QrChallenge
import dev.nocturn.app.data.model.QrPollResult
import dev.nocturn.app.data.model.SearchAlbumsPage
import dev.nocturn.app.data.model.SearchArtistsPage
import dev.nocturn.app.data.model.SearchPlaylistsPage
import dev.nocturn.app.data.model.SearchSongsPage
import dev.nocturn.app.data.model.Song as NocturnSong
import dev.nocturn.app.playback.PlayableSong
import dev.nocturn.app.playback.PlaybackEngine
import dev.nocturn.app.playback.PlaybackSnapshot
import dev.nocturn.app.playback.RepeatMode
import dev.nocturn.localapi.client.AudioQuality
import io.github.proify.lyricon.lyric.model.Song as LyriconSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration

/**
 * Deterministic unit tests for [LyriconManager].
 *
 * The manager is driven through [LyriconProviderBridge] / [LyriconPlayerClient] fakes, so no real
 * Lyricon IPC connection is created. A Robolectric [Context] is only needed for [SettingsStore]
 * and for the `SharedPreferences` change listener that mirrors the settings toggle. Coroutines run
 * on [Dispatchers.Unconfined] so every snapshot emission and settings write is observed
 * synchronously and assertions stay deterministic without a virtual-time scheduler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyriconManagerTest {

    private lateinit var context: Context
    private lateinit var settings: SettingsStore
    private lateinit var scope: CoroutineScope
    private lateinit var playback: FakePlaybackEngine
    private lateinit var repository: FakeMusicRepository
    private val managers = mutableListOf<LyriconManager>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settings = SettingsStore(context)
        settings.lyriconEnabled = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        playback = FakePlaybackEngine()
        repository = FakeMusicRepository()
    }

    @After
    fun tearDown() {
        managers.forEach { manager -> runCatching { manager.destroy() } }
        managers.clear()
        scope.cancel()
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle: start / stop / destroy
    // ---------------------------------------------------------------------------------------

    @Test
    fun startRegistersProviderAndConfiguresRemotePlayer() {
        val (manager, bridge) = createManager()

        manager.start()

        assertEquals(1, bridge.registerCount)
        assertTrue("bridge must enable autoSync", bridge.autoSync)
        assertTrue("translation display must be enabled up front", bridge.player.displayTranslationRequests.contains(true))
        assertTrue(manager.isEnabled())
    }

    @Test
    fun startDoesNotRegisterProviderWhenSettingsDisabled() {
        val (manager, bridge) = createManager(initialEnabled = false)

        manager.start()

        assertEquals(0, bridge.registerCount)
        assertFalse(manager.isEnabled())

        // No data may be pushed while the provider is disabled.
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Disabled", playing = true))
        assertTrue(bridge.player.songs.isEmpty())
        assertTrue(bridge.player.playbackStates.isEmpty())
    }

    @Test
    fun startIsIdempotent() {
        val (manager, bridge) = createManager()
        repository.stubLyrics(1L, simpleLyrics())

        manager.start()
        manager.start()

        assertEquals(1, bridge.registerCount)

        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Once", playing = true))
        assertEquals("only a placeholder and the resolved lyrics should be published", 2, bridge.player.songs.size)
    }

    @Test
    fun stopUnregistersProviderAndStopsObserving() {
        val (manager, bridge) = createManager()
        repository.stubLyrics(1L, simpleLyrics())
        manager.start()

        manager.stop()

        assertEquals(1, bridge.unregisterCount)
        assertFalse(bridge.isRegistered)

        // Snapshot emissions after stop must not reach the provider.
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "AfterStop", playing = true))
        assertEquals(listOf<LyriconSong?>(null), bridge.player.songs)

        // The settings listener is also detached by stop().
        settings.lyriconEnabled = false
        assertEquals(1, bridge.unregisterCount)
        assertFalse(manager.isEnabled())
    }

    @Test
    fun destroyReleasesProviderExactlyOnce() {
        val (manager, bridge) = createManager()
        manager.start()

        manager.destroy()
        manager.destroy()

        assertEquals(1, bridge.unregisterCount)
        assertEquals(1, bridge.destroyCount)
    }

    // ---------------------------------------------------------------------------------------
    // Enabled toggle
    // ---------------------------------------------------------------------------------------

    @Test
    fun setEnabledFalseUnregistersAndIgnoresSnapshots() {
        val (manager, bridge) = createManager()
        repository.stubLyrics(1L, simpleLyrics())
        manager.start()

        manager.setEnabled(false)

        assertFalse(manager.isEnabled())
        assertFalse("settings must be persisted", SettingsStore(context).lyriconEnabled)
        assertEquals(1, bridge.unregisterCount)

        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Ignored", playing = true))
        assertEquals(listOf<LyriconSong?>(null), bridge.player.songs)
    }

    @Test
    fun setEnabledTrueRegistersAndResynchronizesCurrentSnapshot() {
        val (manager, bridge) = createManager(initialEnabled = false)
        repository.stubLyrics(7L, simpleLyrics())
        manager.start()

        playback.emit(
            PlaybackSnapshot(
                currentSongId = 7L,
                title = "Resync",
                artist = "Artist",
                durationMs = 200_000L,
                playing = true,
                positionMs = 4_000L,
            ),
        )
        assertTrue(bridge.player.songs.isEmpty())

        manager.setEnabled(true)

        assertTrue(manager.isEnabled())
        assertEquals(1, bridge.registerCount)
        assertEquals("7", bridge.player.songs.lastOrNull()?.id)
        assertTrue(bridge.player.playbackStates.contains(true))
        assertTrue(bridge.player.positions.contains(4_000L))
    }

    @Test
    fun settingsStoreChangeTogglesProviderDynamically() {
        val (manager, bridge) = createManager()
        repository.stubLyrics(11L, simpleLyrics())
        manager.start()
        assertEquals(1, bridge.registerCount)

        // Toggle exactly like `NocturnViewModel.setLyriconEnabled` does: write to SettingsStore.
        settings.lyriconEnabled = false
        assertEquals(1, bridge.unregisterCount)
        assertFalse(manager.isEnabled())

        playback.emit(PlaybackSnapshot(currentSongId = 11L, title = "Muted", playing = true))
        assertEquals(listOf<LyriconSong?>(null), bridge.player.songs)

        settings.lyriconEnabled = true
        assertEquals(2, bridge.registerCount)
        assertTrue(manager.isEnabled())
        assertEquals("the latest snapshot is re-synced after re-enabling", "11", bridge.player.songs.lastOrNull()?.id)
    }

    // ---------------------------------------------------------------------------------------
    // Song metadata and lyric push
    // ---------------------------------------------------------------------------------------

    @Test
    fun songChangePushesMetadataAndMappedLyrics() {
        val lyrics = NocturnLyrics(
            lines = listOf(
                NocturnLyricLine(
                    startMs = 1_000L,
                    text = "天空一片蔚蓝",
                    translation = "The sky is blue",
                    words = listOf(
                        NocturnLyricWord(startMs = 1_000L, durationMs = 500L, text = "天空"),
                        NocturnLyricWord(startMs = 1_500L, durationMs = 1_500L, text = "蔚蓝"),
                    ),
                ),
            ),
        )
        repository.stubLyrics(42L, lyrics)
        val (manager, bridge) = createManager()
        manager.start()
        bridge.player.displayTranslationRequests.clear()

        playback.emit(
            PlaybackSnapshot(
                currentSongId = 42L,
                title = "夜曲",
                artist = "周杰伦",
                durationMs = 240_000L,
                playing = true,
                positionMs = 1_000L,
            ),
        )

        assertEquals(2, bridge.player.songs.size)
        val pushed = bridge.player.songs.last()
        assertNotNull(pushed)
        assertEquals("42", pushed?.id)
        assertEquals("夜曲", pushed?.name)
        assertEquals("周杰伦", pushed?.artist)
        assertEquals(240_000L, pushed?.duration)

        val lines = pushed?.lyrics
        assertNotNull(lines)
        assertEquals(1, lines?.size)
        val line = lines?.first()
        assertEquals(1_000L, line?.begin)
        assertEquals("天空一片蔚蓝", line?.text)
        assertEquals("The sky is blue", line?.translation)
        assertEquals(2, line?.words?.size)

        // Initial playback state/position are synced with the song.
        assertTrue(bridge.player.playbackStates.contains(true))
        assertTrue(bridge.player.positions.contains(1_000L))
        // Translation display is (re)enabled for translated lyrics.
        assertEquals(listOf(true, true), bridge.player.displayTranslationRequests)
    }

    @Test
    fun songChangeCancelsStaleLyricsFetch() {
        val firstLyrics = CompletableDeferred<NocturnLyrics>()
        val secondLyrics = CompletableDeferred<NocturnLyrics>()
        repository.stubDeferredLyrics(1L, firstLyrics)
        repository.stubDeferredLyrics(2L, secondLyrics)
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "First", playing = true))
        // Metadata clears the previous lyrics while the lookup is pending.
        assertEquals("1", bridge.player.songs.single()?.id)
        assertTrue(bridge.player.songs.single()?.lyrics.orEmpty().isEmpty())

        // Switching songs must cancel the stale fetch.
        playback.emit(PlaybackSnapshot(currentSongId = 2L, title = "Second", playing = true))
        assertEquals("2", bridge.player.songs.last()?.id)
        assertTrue(bridge.player.songs.last()?.lyrics.orEmpty().isEmpty())

        secondLyrics.complete(simpleLyrics(text = "Second line"))
        assertEquals(3, bridge.player.songs.size)
        assertEquals("2", bridge.player.songs.last()?.id)

        // Completing the cancelled fetch must not push anything anymore.
        firstLyrics.complete(simpleLyrics(text = "Stale line"))
        assertEquals("stale lyrics must never be applied", 3, bridge.player.songs.size)
        assertEquals(listOf(1L, 2L), repository.fetchedSongIds)
    }

    @Test
    fun songClearedWhenCurrentSongBecomesNull() {
        repository.stubLyrics(10L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 10L, title = "A", playing = true))
        assertEquals(2, bridge.player.songs.size)

        playback.emit(PlaybackSnapshot(currentSongId = null))
        assertEquals(3, bridge.player.songs.size)
        assertNull(bridge.player.songs.last())
        assertEquals(false, bridge.player.playbackStates.lastOrNull())

        // Repeated idle snapshots must not spam the provider.
        playback.emit(PlaybackSnapshot(currentSongId = null))
        assertEquals(3, bridge.player.songs.size)
    }

    @Test
    fun metadataOnlyChangeRepublishesSongWithoutRefetchingLyrics() {
        repository.stubLyrics(55L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 55L, title = "Old", artist = "A", positionMs = 0L))
        assertEquals(2, bridge.player.songs.size)

        playback.emit(PlaybackSnapshot(currentSongId = 55L, title = "New", artist = "B", positionMs = 0L))
        assertEquals(3, bridge.player.songs.size)
        assertEquals("New", bridge.player.songs.last()?.name)
        assertEquals("B", bridge.player.songs.last()?.artist)
        assertEquals("测试歌词", bridge.player.songs.last()?.lyrics?.single()?.text)
        assertEquals(listOf(55L), repository.fetchedSongIds)
    }

    // ---------------------------------------------------------------------------------------
    // Playback state, progress and seek synchronisation
    // ---------------------------------------------------------------------------------------

    @Test
    fun playingStateTransitionsArePushed() {
        repository.stubLyrics(1L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = true, positionMs = 0L))
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = false, positionMs = 500L))
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = true, positionMs = 500L))

        assertEquals(listOf(true, true, false, true), bridge.player.playbackStates)
    }

    @Test
    fun regularProgressTicksPushSetPosition() {
        repository.stubLyrics(1L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", positionMs = 0L, positionDiscontinuity = 0L))
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", positionMs = 500L, positionDiscontinuity = 0L))
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", positionMs = 1_000L, positionDiscontinuity = 0L))

        assertEquals(listOf(0L, 0L, 500L, 1_000L), bridge.player.positions)
        assertTrue("normal progress must not be reported as a seek", bridge.player.seeks.isEmpty())
    }

    @Test
    fun positionDiscontinuityPushesSeekTo() {
        repository.stubLyrics(1L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(
            PlaybackSnapshot(currentSongId = 1L, title = "Song", positionMs = 1_000L, positionDiscontinuity = 0L),
        )
        // User scrubs the timeline: discontinuity changes but the song stays the same.
        playback.emit(
            PlaybackSnapshot(currentSongId = 1L, title = "Song", positionMs = 8_000L, positionDiscontinuity = 1L),
        )

        assertEquals(listOf(8_000L), bridge.player.seeks)
        assertFalse("a seek must not be duplicated as setPosition", bridge.player.positions.contains(8_000L))
    }

    @Test
    fun progressUsesContinuousRemoteClockWithoutReanchoringForPollingJitter() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        repository.stubLyrics(1L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()
        val initial = PlaybackSnapshot(
            currentSongId = 1L, title = "Song", playing = true,
            positionSampledAtNanos = SystemClock.elapsedRealtimeNanos(),
        )
        playback.emit(initial)
        val anchor = bridge.player.timedPlaybackStates.last()
        val anchorCount = bridge.player.timedPlaybackStates.size
        for (position in listOf(500L, 995L, 1_504L)) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(500))
            playback.emit(initial.copy(positionMs = position, positionSampledAtNanos = SystemClock.elapsedRealtimeNanos()))
        }

        assertEquals("normal 500 ms polls must not restart the remote clock", anchorCount, bridge.player.timedPlaybackStates.size)
        assertEquals("normal polls must not rebuild the remote lyric view", 2, bridge.player.songs.size)
        assertEquals(PlaybackState.STATE_PLAYING, anchor.state)
        assertEquals(1f, anchor.playbackSpeed)
        assertEquals(1_500L, anchor.position + SystemClock.elapsedRealtime() - anchor.lastPositionUpdateTime)
        assertTrue(bridge.player.seeks.isEmpty())
    }

    @Test
    fun remoteClockTracksSpeedBufferingAndSmallBackwardSeeks() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        repository.stubLyrics(1L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()
        val initial = PlaybackSnapshot(
            currentSongId = 1L, title = "Song", playing = true, positionMs = 5_000L,
            positionSampledAtNanos = SystemClock.elapsedRealtimeNanos(), playbackSpeed = 1.5f,
        )
        playback.emit(initial)
        assertEquals(1.5f, bridge.player.timedPlaybackStates.last().playbackSpeed)

        playback.emit(initial.copy(positionMs = 4_900L, positionDiscontinuity = 1L))
        assertEquals(4_900L, bridge.player.timedPlaybackStates.last().position)
        assertEquals(listOf(4_900L), bridge.player.seeks)

        playback.emit(initial.copy(positionMs = 4_900L, positionDiscontinuity = 1L, buffering = true))
        assertEquals(PlaybackState.STATE_PAUSED, bridge.player.timedPlaybackStates.last().state)
        val pausedCount = bridge.player.timedPlaybackStates.size
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        playback.emit(initial.copy(
            positionMs = 4_900L, positionDiscontinuity = 1L, buffering = true,
            positionSampledAtNanos = SystemClock.elapsedRealtimeNanos(),
        ))
        assertEquals(pausedCount, bridge.player.timedPlaybackStates.size)

        playback.emit(initial.copy(positionMs = 4_900L, positionDiscontinuity = 1L, playbackSpeed = 1f))
        assertEquals(PlaybackState.STATE_PLAYING, bridge.player.timedPlaybackStates.last().state)
        assertEquals(1f, bridge.player.timedPlaybackStates.last().playbackSpeed)
    }

    // ---------------------------------------------------------------------------------------
    // Pure music / missing lyrics placeholders
    // ---------------------------------------------------------------------------------------

    @Test
    fun pureMusicTitlePushesTextInsteadOfSong() {
        repository.stubLyrics(3L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 3L, title = "晴天 (纯音乐)", playing = true))

        assertEquals(listOf(LyriconModelMapper.PURE_MUSIC_TEXT), bridge.player.texts)
        assertEquals(1, bridge.player.songs.size)
        assertTrue(bridge.player.songs.single()?.lyrics.orEmpty().isEmpty())
    }

    @Test
    fun pureMusicMarkerLyricsPushTextInsteadOfSong() {
        repository.stubLyrics(
            4L,
            NocturnLyrics(lines = listOf(NocturnLyricLine(startMs = 0L, text = "纯音乐，请欣赏"))),
        )
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 4L, title = "晴天", playing = true))

        assertEquals(listOf(LyriconModelMapper.PURE_MUSIC_TEXT), bridge.player.texts)
        assertEquals(1, bridge.player.songs.size)
    }

    @Test
    fun missingLyricsPushPlaceholderTextInsteadOfSong() {
        repository.stubLyrics(5L, NocturnLyrics.EMPTY)
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 5L, title = "普通歌曲", playing = true))

        assertEquals(listOf(LyriconModelMapper.NO_LYRICS_TEXT), bridge.player.texts)
        assertEquals(1, bridge.player.songs.size)
    }

    @Test
    fun lyricsFetchFailureFallsBackToPlaceholderText() {
        repository.stubFailure(6L, IOException("网络超时"))
        val (manager, bridge) = createManager()
        manager.start()

        playback.emit(PlaybackSnapshot(currentSongId = 6L, title = "无网歌曲", playing = true))

        assertEquals(listOf(LyriconModelMapper.NO_LYRICS_TEXT), bridge.player.texts)
        assertEquals(1, bridge.player.songs.size)
        assertTrue("provider must stay registered after a fetch failure", bridge.isRegistered)
    }

    @Test
    fun delayedLyricsUseLatestPositionAndPauseStateInDocumentedOrder() {
        val pending = CompletableDeferred<NocturnLyrics>()
        repository.stubDeferredLyrics(1L, pending)
        val (manager, bridge) = createManager()
        manager.start()
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Loading", playing = true))
        playback.emit(
            PlaybackSnapshot(
                currentSongId = 1L, title = "Updated", artist = "Artist", durationMs = 90_000L,
                playing = false, positionMs = 30_000L, positionDiscontinuity = 1L,
            ),
        )
        bridge.player.events.clear()

        pending.complete(simpleLyrics())

        assertEquals("Updated", bridge.player.songs.last()?.name)
        assertEquals(90_000L, bridge.player.songs.last()?.duration)
        assertEquals(listOf("song:1", "position:30000", "playing:false", "translation:true"), bridge.player.events)
    }

    @Test
    fun reconnectedPlayerReceivesCurrentLyricsPositionAndStateWithoutRefetch() {
        repository.stubLyrics(1L, simpleLyrics())
        val (manager, bridge) = createManager()
        manager.start()
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = true))
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = false, positionMs = 8_000L))
        bridge.player.events.clear()

        bridge.connectedListener?.invoke()

        assertEquals(listOf("song:1", "position:8000", "playing:false", "translation:true"), bridge.player.events)
        assertEquals("测试歌词", bridge.player.songs.last()?.lyrics?.single()?.text)
        assertEquals(listOf(1L), repository.fetchedSongIds)
    }

    @Test
    fun disableClearsDisplayAndIgnoresLateLyricsAndConnectionCallbacks() {
        val pending = CompletableDeferred<NocturnLyrics>()
        repository.stubDeferredLyrics(1L, pending)
        val (manager, bridge) = createManager()
        manager.start()
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = true))
        bridge.player.events.clear()

        manager.setEnabled(false)
        assertEquals(listOf("song:null", "playing:false"), bridge.player.events)
        pending.complete(simpleLyrics())
        bridge.connectedListener?.invoke()

        assertEquals(listOf("song:null", "playing:false"), bridge.player.events)
        assertEquals(1, bridge.unregisterCount)
    }

    @Test
    fun destroyBeforeStartDoesNotCreateProviderAndCannotRestart() {
        var created = 0
        val manager = LyriconManager(context, playback, repository, settings, scope) {
            created++
            FakeLyriconProviderBridge()
        }
        managers += manager

        manager.destroy()
        manager.start()

        assertEquals(0, created)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun rejectedRegistrationRetriesAndSynchronizesLatestSong() = runTest {
        val bridge = FakeLyriconProviderBridge().apply { registrationSucceeds = false }
        val manager = LyriconManager(context, playback, repository, settings, backgroundScope) { bridge }
        managers += manager
        repository.stubLyrics(1L, simpleLyrics())
        manager.start()
        runCurrent()
        playback.emit(PlaybackSnapshot(currentSongId = 1L, title = "Song", playing = true, positionMs = 5_000L))
        runCurrent()
        assertEquals(1, bridge.registerCount)
        assertTrue(bridge.player.songs.isEmpty())
        assertTrue(repository.fetchedSongIds.isEmpty())

        bridge.registrationSucceeds = true
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(2, bridge.registerCount)
        assertEquals("1", bridge.player.songs.last()?.id)
        assertEquals(5_000L, bridge.player.positions.last())
        manager.destroy()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(2, bridge.registerCount)
    }

    // ---------------------------------------------------------------------------------------
    // Helpers and test doubles
    // ---------------------------------------------------------------------------------------

    private fun createManager(initialEnabled: Boolean = true): Pair<LyriconManager, FakeLyriconProviderBridge> {
        settings.lyriconEnabled = initialEnabled
        val bridge = FakeLyriconProviderBridge()
        val manager = LyriconManager(
            context = context,
            playback = playback,
            repository = repository,
            settings = settings,
            coroutineScope = scope,
            bridgeFactory = { bridge },
        )
        managers += manager
        return manager to bridge
    }

    private fun simpleLyrics(text: String = "测试歌词"): NocturnLyrics =
        NocturnLyrics(lines = listOf(NocturnLyricLine(startMs = 0L, text = text)))

    class FakeLyriconPlayerClient : LyriconPlayerClient {
        val events = mutableListOf<String>()
        val songs = mutableListOf<LyriconSong?>()
        val texts = mutableListOf<String?>()
        val playbackStates = mutableListOf<Boolean>()
        val timedPlaybackStates = mutableListOf<PlaybackState>()
        val positions = mutableListOf<Long>()
        val seeks = mutableListOf<Long>()
        val displayTranslationRequests = mutableListOf<Boolean>()

        override fun setSong(song: LyriconSong?) {
            events += "song:${song?.id}"
            songs += song
        }

        override fun sendText(text: String?) {
            events += "text:$text"
            texts += text
        }

        override fun setPlaybackState(playing: Boolean) {
            events += "playing:$playing"
            playbackStates += playing
        }

        override fun setPlaybackState(state: PlaybackState) {
            timedPlaybackStates += state
            setPlaybackState(state.state == PlaybackState.STATE_PLAYING)
        }

        override fun setPosition(positionMs: Long) {
            events += "position:$positionMs"
            positions += positionMs
        }

        override fun seekTo(positionMs: Long) {
            events += "seek:$positionMs"
            seeks += positionMs
        }

        override fun setDisplayTranslation(display: Boolean) {
            events += "translation:$display"
            displayTranslationRequests += display
        }
    }

    class FakeLyriconProviderBridge : LyriconProviderBridge {
        override val player = FakeLyriconPlayerClient()

        override var autoSync: Boolean = false

        var connectedListener: (() -> Unit)? = null
        var registrationSucceeds = true

        override fun setOnConnectedListener(listener: (() -> Unit)?) {
            connectedListener = listener
        }

        var isRegistered: Boolean = false
        var registerCount: Int = 0
        var unregisterCount: Int = 0
        var destroyCount: Int = 0

        override fun register(): Boolean {
            registerCount++
            isRegistered = registrationSucceeds
            return registrationSucceeds
        }

        override fun unregister() {
            unregisterCount++
            isRegistered = false
        }

        override fun destroy() {
            destroyCount++
            isRegistered = false
        }
    }

    class FakePlaybackEngine : PlaybackEngine {
        private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())

        override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot

        fun emit(snapshot: PlaybackSnapshot) {
            mutableSnapshot.value = snapshot
        }

        override suspend fun connect() = Unit

        override fun setQueue(songs: List<PlayableSong>, startIndex: Int) = Unit

        override fun play() {
            mutableSnapshot.value = mutableSnapshot.value.copy(playing = true)
        }

        override fun pause() {
            mutableSnapshot.value = mutableSnapshot.value.copy(playing = false)
        }

        override fun seekTo(positionMs: Long) = Unit

        override fun next() = Unit

        override fun previous() = Unit

        override fun setRepeatMode(mode: RepeatMode) = Unit

        override fun setShuffle(enabled: Boolean) = Unit

        override fun clearError() = Unit

        override fun release() = Unit
    }

    class FakeMusicRepository : MusicRepository {
        private val lyricsStubs = mutableMapOf<Long, NocturnLyrics>()
        private val deferredLyricsStubs = mutableMapOf<Long, CompletableDeferred<NocturnLyrics>>()
        private val failures = mutableMapOf<Long, Throwable>()

        val fetchedSongIds = mutableListOf<Long>()

        fun stubLyrics(songId: Long, lyrics: NocturnLyrics) {
            lyricsStubs[songId] = lyrics
        }

        fun stubDeferredLyrics(songId: Long, lyrics: CompletableDeferred<NocturnLyrics>) {
            deferredLyricsStubs[songId] = lyrics
        }

        fun stubFailure(songId: Long, error: Throwable) {
            failures[songId] = error
        }

        override suspend fun lyrics(songId: Long): NocturnLyrics {
            fetchedSongIds += songId
            failures[songId]?.let { throw it }
            deferredLyricsStubs[songId]?.let { return it.await() }
            return lyricsStubs[songId] ?: NocturnLyrics.EMPTY
        }

        override val session: StateFlow<AccountProfile?> = MutableStateFlow(null)

        override suspend fun homeFeed(): HomeFeed = HomeFeed()

        override suspend fun playlistDetail(id: Long): PlaylistDetail = error("not needed")

        override suspend fun playlistSongs(id: Long, offset: Int, limit: Int): PlaylistSongsPage = error("not needed")

        override suspend fun searchSongs(keyword: String, offset: Int, limit: Int): SearchSongsPage = error("not needed")

        override suspend fun searchPlaylists(keyword: String, offset: Int, limit: Int): SearchPlaylistsPage =
            error("not needed")

        override suspend fun searchAlbums(keyword: String, offset: Int, limit: Int): SearchAlbumsPage = error("not needed")

        override suspend fun searchArtists(keyword: String, offset: Int, limit: Int): SearchArtistsPage =
            error("not needed")

        override suspend fun searchSuggestions(keyword: String): List<String> = emptyList()

        override suspend fun hotSearches(): List<String> = emptyList()

        override suspend fun defaultKeyword(): String? = null

        override suspend fun albumDetail(id: Long): AlbumDetail = error("not needed")

        override suspend fun artistDetail(id: Long): ArtistDetail = error("not needed")

        override suspend fun streamUrls(ids: List<Long>, quality: AudioQuality): Map<Long, String?> = emptyMap()

        override suspend fun createQrChallenge(): QrChallenge = error("not needed")

        override suspend fun pollQrLogin(key: String): QrPollResult = error("not needed")

        override suspend fun sendLoginCode(phone: String, countryCode: String) = Unit

        override suspend fun loginWithCode(phone: String, captcha: String, countryCode: String): AccountProfile =
            error("not needed")

        override suspend fun loginWithPassword(phone: String, password: String, countryCode: String): AccountProfile =
            error("not needed")

        override suspend fun restoreSession(): AccountProfile? = null

        override suspend fun logout() = Unit

        override suspend fun accountStats(): AccountStats = error("not needed")

        override suspend fun userPlaylists(): List<PlaylistSummary> = emptyList()

        override suspend fun likedSongIds(): Set<Long> = emptySet()

        override suspend fun setLiked(songId: Long, liked: Boolean): Boolean = false

        override suspend fun likedSongs(): List<NocturnSong> = emptyList()

        override suspend fun dailySongs(): List<NocturnSong> = emptyList()

        override suspend fun personalizedSongs(): List<NocturnSong> = emptyList()

        override suspend fun recentSongs(): List<NocturnSong> = emptyList()

        override suspend fun setPlaylistSubscribed(playlistId: Long, subscribed: Boolean): Boolean = false

        override fun close() = Unit
    }
}
