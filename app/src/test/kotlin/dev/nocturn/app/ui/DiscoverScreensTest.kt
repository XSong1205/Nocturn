package dev.nocturn.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.home.DailySongsScreen
import dev.nocturn.app.ui.screens.home.HomeScreen
import dev.nocturn.app.ui.screens.playlist.AlbumScreen
import dev.nocturn.app.ui.screens.playlist.ArtistScreen
import dev.nocturn.app.ui.screens.playlist.PlaylistScreen
import dev.nocturn.app.ui.state.HomeUiState
import dev.nocturn.app.ui.state.PlaylistUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric 渲染测试：只使用 [PreviewData] 的离线状态，不发网络请求。
 * 覆盖发现页/歌单详情/专辑/歌手的关键渲染与回调接线。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp")
class DiscoverScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                content()
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Home
    // -----------------------------------------------------------------------------------------

    @Test
    fun homeRendersGreetingHeroCardsAndRecommendations() {
        setScreen { HomeScreen(state = PreviewData.homeState) }

        compose.onNodeWithTag("home-greeting").assertExists()
        compose.onNodeWithText("晚上好").assertExists()
        // Daily recommendations are a single hero card, followed by one card per chart.
        compose.onNodeWithTag("home-hero-daily").assertExists()
        compose.onNodeWithTag("home-hero-chart-${PreviewData.charts.first().id}").assertExists()

        // The editorial banner row was removed: the feed ends with the recommended playlists.
        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("home-playlists"))
        compose.onNodeWithTag("home-banner").assertDoesNotExist()
        compose.onNodeWithText("编辑精选").assertDoesNotExist()
    }

    @Test
    fun homeDoesNotListDailySongsInline() {
        setScreen { HomeScreen(state = PreviewData.homeState) }

        // The full daily list lives on its own page; the feed only links to it.
        compose.onNodeWithTag("home-daily-song-0").assertDoesNotExist()
        compose.onNodeWithTag("daily-song-0").assertDoesNotExist()
        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("home-song-grid"))
        compose.onNodeWithText("根据你喜爱的歌曲推荐").assertExists()
    }

    @Test
    fun homeDailyCardOpensDailyPageAndPlayGlyphPlaysIt() {
        var opened = 0
        var played = 0
        setScreen {
            HomeScreen(
                state = PreviewData.homeState,
                onOpenDaily = { opened += 1 },
                onPlayAllDaily = { played += 1 },
            )
        }

        compose.onNodeWithTag("home-hero-daily").performClick()
        compose.onNodeWithContentDescription("播放每日推荐").performClick()

        compose.runOnIdle {
            assertEquals(1, opened)
            assertEquals(1, played)
        }
    }

    @Test
    fun homeChartCardOpensThatChart() {
        var clicked: Long? = null
        val chart = PreviewData.charts.first()
        setScreen {
            HomeScreen(
                state = PreviewData.homeState,
                onChartClick = { clicked = it.id },
            )
        }

        compose.onNodeWithTag("home-hero-chart-${chart.id}").performClick()

        compose.runOnIdle { assertEquals(chart.id, clicked) }
    }

    @Test
    fun homePlaylistCardClickInvokesCallback() {
        var clickedPlaylistId: Long? = null
        val playlist = PreviewData.playlists.first()
        setScreen {
            HomeScreen(
                state = PreviewData.homeState,
                onPlaylistClick = { clickedPlaylistId = it },
            )
        }

        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("home-playlists"))
        compose.onNodeWithText(playlist.name).performClick()

        compose.runOnIdle {
            assertEquals(playlist.id, clickedPlaylistId)
        }
    }

    @Test
    fun homeSongGridPlaysPersonalizedSongsAsTheQueue() {
        var clickedSong: Song? = null
        var queue: List<Song> = emptyList()
        val recommended = PreviewData.personalizedSongs
        setScreen {
            HomeScreen(
                state = PreviewData.homeState,
                onSongClick = { song, songs ->
                    clickedSong = song
                    queue = songs
                },
            )
        }

        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("home-song-grid"))
        compose.onNodeWithText("根据你喜爱的歌曲推荐").assertExists()
        compose.onNodeWithText("热歌速递").assertDoesNotExist()
        compose.onNodeWithTag("song-tile-${recommended.first().id}").performClick()

        compose.runOnIdle {
            assertEquals(recommended.first().id, clickedSong?.id)
            assertEquals(recommended.map { it.id }, queue.map { it.id })
        }
    }

    @Test
    fun homeSongGridFallsBackToChartTracksWithoutPersonalizedSongs() {
        var queue: List<Song> = emptyList()
        val hot = PreviewData.charts.flatMap { it.tracks }.distinctBy { it.id }
        setScreen {
            HomeScreen(
                state = PreviewData.homeStateAnonymous,
                onSongClick = { _, songs -> queue = songs },
            )
        }

        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("home-song-grid"))
        compose.onNodeWithText("热歌速递").assertExists()
        compose.onNodeWithText("根据你喜爱的歌曲推荐").assertDoesNotExist()
        compose.onNodeWithTag("song-tile-${hot.first().id}").performClick()

        compose.runOnIdle { assertEquals(hot.map { it.id }, queue.map { it.id }) }
    }

    // -----------------------------------------------------------------------------------------
    // Daily recommendations page
    // -----------------------------------------------------------------------------------------

    @Test
    fun dailyPageListsSongsAndPlaysAll() {
        var played = 0
        var clicked: Song? = null
        setScreen {
            DailySongsScreen(
                state = PreviewData.homeState,
                onPlayAll = { played += 1 },
                onSongClick = { song, _ -> clicked = song },
            )
        }

        compose.onNodeWithTag("daily-play-all").performClick()
        compose.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("daily-song-0"))
        compose.onNodeWithTag("daily-song-0").performClick()

        compose.runOnIdle {
            assertEquals(1, played)
            assertEquals(PreviewData.homeState.dailySongs.first().id, clicked?.id)
        }
    }

    @Test
    fun dailyPageLikeButtonInvokesCallback() {
        var likedSong: Song? = null
        setScreen {
            DailySongsScreen(
                state = PreviewData.homeState,
                onLikeClick = { likedSong = it },
            )
        }

        compose.onNodeWithTag("daily-list").performScrollToNode(hasTestTag("daily-song-0"))
        // PreviewData 中第一首每日推荐已被收藏，爱心为实心「取消喜欢」。
        compose.onAllNodesWithContentDescription("取消喜欢").onFirst().performClick()

        compose.runOnIdle {
            assertEquals(PreviewData.homeState.dailySongs.first().id, likedSong?.id)
        }
    }

    @Test
    fun dailyPageSignedOutExplainsLogin() {
        setScreen { DailySongsScreen(state = PreviewData.homeStateAnonymous) }

        compose.onNodeWithTag("daily-empty").assertExists()
        compose.onNodeWithTag("daily-back").assertExists()
    }

    @Test
    fun homeErrorStateShowsRetryAndInvokesRefresh() {
        var refreshCount = 0
        setScreen {
            HomeScreen(
                state = HomeUiState(loading = false, error = "网络似乎开小差了"),
                onRefresh = { refreshCount += 1 },
            )
        }

        compose.onNodeWithText("连接不上音乐服务").assertExists()
        compose.onNodeWithText("网络似乎开小差了").assertExists()
        compose.onNodeWithText("重试").performClick()

        compose.runOnIdle {
            assertEquals(1, refreshCount)
        }
    }

    @Test
    fun homeAnonymousStateShowsLoginPrompt() {
        setScreen { HomeScreen(state = PreviewData.homeStateAnonymous) }

        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("home-login-prompt"))
        compose.onNodeWithTag("home-login-prompt").assertExists()
        compose.onNodeWithText("登录网易云音乐").assertExists()
    }

    // -----------------------------------------------------------------------------------------
    // Playlist detail
    // -----------------------------------------------------------------------------------------

    @Test
    fun playlistRendersImmersiveHeaderAndTracks() {
        setScreen { PlaylistScreen(state = PreviewData.playlistState) }

        compose.onNodeWithTag("playlist-header").assertExists()
        compose.onNodeWithText(PreviewData.playlistsDetail.name).assertExists()
        compose.onNodeWithText("Nocturn 编辑部").assertExists()

        compose.onNodeWithTag("playlist-list").performScrollToNode(hasTestTag("playlist-song-0"))
        compose.onNodeWithTag("playlist-song-0").assertExists()
        compose.onNodeWithText(PreviewData.songs.first().name).assertExists()
    }

    @Test
    fun playlistPlayAllPassesLoadedSongs() {
        var playedSongs: List<Song>? = null
        setScreen {
            PlaylistScreen(
                state = PreviewData.playlistState,
                onPlayAll = { playedSongs = it },
            )
        }

        compose.onNodeWithTag("playlist-list").performScrollToNode(hasTestTag("playlist-play-all"))
        compose.onNodeWithTag("playlist-play-all").performClick()

        compose.runOnIdle {
            assertEquals(PreviewData.playlistState.songs.map { it.id }, playedSongs?.map { it.id })
        }
    }

    @Test
    fun playlistSubscribeToggleInvokesCallback() {
        var toggleCount = 0
        setScreen {
            PlaylistScreen(
                state = PreviewData.playlistState,
                onSubscribeToggle = { toggleCount += 1 },
            )
        }

        compose.onNodeWithTag("playlist-list").performScrollToNode(hasTestTag("playlist-subscribe"))
        compose.onNodeWithTag("playlist-subscribe").performClick()

        compose.runOnIdle {
            assertEquals(1, toggleCount)
        }
    }

    @Test
    fun playlistErrorStateShowsRetry() {
        setScreen {
            PlaylistScreen(
                state = PlaylistUiState(
                    loading = false,
                    error = "歌单加载失败",
                ),
            )
        }

        compose.onNodeWithText("重试").assertExists()
        compose.onNodeWithTag("playlist-back").assertExists()
    }

    // -----------------------------------------------------------------------------------------
    // Album / artist detail
    // -----------------------------------------------------------------------------------------

    @Test
    fun albumRendersHeaderAndTrackList() {
        setScreen { AlbumScreen(state = PreviewData.albumState) }

        compose.onAllNodesWithText(PreviewData.albumDetail.name).onFirst().assertExists()
        compose.onNodeWithTag("album-list").performScrollToNode(hasTestTag("album-song-0"))
        compose.onNodeWithTag("album-song-0").assertExists()
    }

    @Test
    fun artistRendersHotSongsAndAlbums() {
        setScreen { ArtistScreen(state = PreviewData.artistState) }

        compose.onAllNodesWithText(PreviewData.artistDetail.name).onFirst().assertExists()
        compose.onNodeWithTag("artist-list").performScrollToNode(hasTestTag("artist-song-0"))
        compose.onNodeWithTag("artist-song-0").assertExists()

        compose.onNodeWithTag("artist-list").performScrollToNode(hasTestTag("artist-albums"))
        compose.onNodeWithTag("artist-albums").assertExists()
    }

    @Test
    fun albumSongClickInvokesCallback() {
        var clicked: Song? = null
        setScreen {
            AlbumScreen(
                state = PreviewData.albumState,
                onSongClick = { song, _ -> clicked = song },
            )
        }

        compose.onNodeWithTag("album-list").performScrollToNode(hasTestTag("album-song-0"))
        compose.onNodeWithTag("album-song-0").performClick()

        compose.runOnIdle {
            assertEquals(PreviewData.albumDetail.songs.first().id, clicked?.id)
        }
    }

    @Test
    fun previewStatesAreRenderable() {
        // 浅色主题 + 匿名首页，确认主题切换路径不会崩溃。
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Light) {
                HomeScreen(state = PreviewData.homeStateAnonymous)
            }
        }
        compose.onNodeWithTag("home-feed").assertExists()
        assertTrue(PreviewData.homeState.hasContent)
    }
}
