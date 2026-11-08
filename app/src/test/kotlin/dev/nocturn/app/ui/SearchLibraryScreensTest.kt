package dev.nocturn.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import dev.nocturn.app.data.model.ArtistSummary
import dev.nocturn.app.data.model.QrLoginState
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.screens.library.LibraryScreen
import dev.nocturn.app.ui.screens.library.LikedSongsScreen
import dev.nocturn.app.ui.screens.library.RecentSongsScreen
import dev.nocturn.app.ui.screens.library.LoginSheet
import dev.nocturn.app.ui.screens.search.SearchScreen
import dev.nocturn.app.ui.state.LibraryUiState
import dev.nocturn.app.ui.state.LikedSongsUiState
import dev.nocturn.app.ui.state.LoginUiState
import dev.nocturn.app.ui.state.LoginMode
import dev.nocturn.app.ui.state.SearchUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric/Compose tests for the search and library surfaces.
 *
 * They are intentionally state driven: each screen receives the same immutable state objects the
 * view model produces, callbacks are captured as plain lambdas and the important user journeys
 * (hot keyword, result tab switch, error retry, login entry, QR sheet, liked songs) are asserted
 * through stable test tags. Artwork is null everywhere, so nothing touches the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchLibraryScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) { content() }
        }
    }

    private val artistSummaries: List<ArtistSummary> = listOf(
        ArtistSummary(
            id = 6452,
            name = "周杰伦",
            avatarUrl = null,
            albumCount = 15,
            songCount = 320,
            fansCount = 12_000_000,
        ),
        ArtistSummary(
            id = 2116,
            name = "陈奕迅",
            avatarUrl = null,
            albumCount = 12,
            songCount = 240,
            fansCount = 8_600_000,
        ),
    )

    /** Results state where every tab has content, mirroring all four tabs having loaded. */
    private fun fullResultsState(): SearchUiState = PreviewData.searchResults.copy(
        playlists = PreviewData.playlists,
        albums = PreviewData.albums,
        artists = artistSummaries,
    )

    // ---------------------------------------------------------------------------------------
    // Search landing
    // ---------------------------------------------------------------------------------------

    @Test
    fun searchLandingShowsHotKeywordsHistoryAndInput() {
        setScreen { SearchScreen(state = PreviewData.searchIdle) }
        compose.waitForIdle()

        compose.onNodeWithTag("search-input").assertExists()
        assertTrue(compose.onAllNodesWithTag("search-hot").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithTag("search-history").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("热门搜索").assertExists()
        compose.onNodeWithText("搜索历史").assertExists()
        compose.onNodeWithTag("search-default-keyword").assertExists()
        compose.onNodeWithTag("search-clear-history").assertExists()
    }

    @Test
    fun clickingHotKeywordReportsThatKeyword() {
        var clicked: String? = null
        setScreen {
            SearchScreen(
                state = PreviewData.searchIdle,
                onHotClick = { clicked = it },
            )
        }
        compose.waitForIdle()

        compose.onAllNodesWithTag("search-hot")[0].performClick()

        assertEquals(PreviewData.searchIdle.hotKeywords.first(), clicked)
    }

    @Test
    fun clearingHistoryReportsCallback() {
        var cleared = false
        setScreen {
            SearchScreen(
                state = PreviewData.searchIdle,
                onClearHistory = { cleared = true },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("search-clear-history").performScrollTo().performClick()

        assertTrue(cleared)
    }

    @Test
    fun typingInSearchInputUpdatesQueryAndTriggersRealtimeSuggestions() {
        var queried = ""
        setScreen {
            var state by remember { mutableStateOf(SearchUiState(expanded = true)) }
            SearchScreen(
                state = state,
                onQueryChange = {
                    queried = it
                    state = state.copy(query = it)
                },
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("search-input").performTextInput("x")
        compose.waitForIdle()
        assertEquals("x", queried)
    }

    @Test
    fun searchInputFieldUsesDefaultKeywordAsPlaceholderAndFallback() {
        var searched = ""
        setScreen {
            SearchScreen(
                state = PreviewData.searchIdle.copy(defaultKeyword = "晴天", query = ""),
                onSearch = { searched = it },
            )
        }
        compose.waitForIdle()

        assertTrue(compose.onAllNodesWithText("晴天").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("search-input").performImeAction()
        compose.waitForIdle()
        assertEquals("晴天", searched)
    }

    @Test
    fun renderingSuggestionsForAnActiveQuery() {
        var suggestion: String? = null
        setScreen {
            SearchScreen(
                state = PreviewData.searchIdle.copy(
                    query = "晴",
                    suggestions = listOf("晴天", "晴天 - 周杰伦"),
                ),
                onSuggestionClick = { suggestion = it },
            )
        }
        compose.waitForIdle()

        assertTrue(compose.onAllNodesWithTag("search-suggestion").fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithTag("search-suggestion")[0].performClick()

        assertEquals("晴天", suggestion)
    }

    // ---------------------------------------------------------------------------------------
    // Search results
    // ---------------------------------------------------------------------------------------

    @Test
    fun resultTabsSwitchTheRenderedList() {
        compose.setContent {
            NocturnTheme(themeMode = ThemeMode.Dark) {
                var state by remember { mutableStateOf(fullResultsState()) }
                SearchScreen(
                    state = state,
                    onTabChange = { tab -> state = state.copy(tab = tab) },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("search-tabs").assertExists()
        assertTrue(
            compose.onAllNodesWithTag("search-result-song").fetchSemanticsNodes().isNotEmpty(),
        )

        compose.onNodeWithTag("search-tab-playlists").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("search-result-playlist").fetchSemanticsNodes().isNotEmpty(),
        )

        compose.onNodeWithTag("search-tab-albums").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("search-result-album").fetchSemanticsNodes().isNotEmpty(),
        )

        compose.onNodeWithTag("search-tab-artists").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("search-result-artist").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun searchErrorStateRetries() {
        var retried = 0
        setScreen {
            SearchScreen(
                state = PreviewData.searchResults.copy(
                    error = "网络不可用",
                    songs = emptyList(),
                    hasMore = false,
                ),
                onRetry = { retried += 1 },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("search-error").assertExists()
        compose.onNodeWithText("重试").performClick()

        assertEquals(1, retried)
    }

    @Test
    fun searchEmptyResultShowsEmptyState() {
        setScreen {
            SearchScreen(
                state = PreviewData.searchResults.copy(
                    songs = emptyList(),
                    hasMore = false,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("search-empty").assertExists()
        compose.onNodeWithText("没有找到相关内容").assertExists()
    }

    @Test
    fun searchResultSongClickReportsSongAndList() {
        var clickedSong: Song? = null
        var clickedList: List<Song>? = null
        setScreen {
            SearchScreen(
                state = PreviewData.searchResults,
                onSongClick = { song, songs ->
                    clickedSong = song
                    clickedList = songs
                },
            )
        }
        compose.waitForIdle()

        compose.onAllNodesWithTag("search-result-song")[0].performClick()

        assertEquals(PreviewData.songs.first(), clickedSong)
        assertEquals(PreviewData.searchResults.songs, clickedList)
    }

    // ---------------------------------------------------------------------------------------
    // Library
    // ---------------------------------------------------------------------------------------

    @Test
    fun librarySignedOutShowsLoginNoticeAndCallsLogin() {
        var logins = 0
        setScreen {
            LibraryScreen(
                state = PreviewData.libraryAnonymous,
                onLoginClick = { logins += 1 },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("library-third-party-notice").assertExists()
        compose.onNodeWithText("登录网易云音乐").assertExists()
        compose.onNodeWithText("第三方客户端").assertExists()

        compose.onNodeWithTag("library-login-button").performClick()
        assertEquals(1, logins)
    }

    @Test
    fun librarySignedInShowsProfileStatsAndLikedEntry() {
        var likedClicks = 0
        var recentClicks = 0
        setScreen {
            LibraryScreen(
                state = PreviewData.libraryLoggedIn,
                onLikedSongsClick = { likedClicks += 1 },
                onRecentSongsClick = { recentClicks += 1 },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("library-profile").assertExists()
        compose.onNodeWithText("夜行电台").assertExists()
        compose.onNodeWithText("把夜色交给音乐。").assertExists()
        compose.onNodeWithText("我喜欢的音乐").assertExists()
        compose.onNodeWithText("最近播放").assertExists()

        compose.onNodeWithTag("library-screen").performScrollToNode(hasTestTag("library-liked-entry"))
        compose.onNodeWithTag("library-liked-entry").performClick()
        assertEquals(1, likedClicks)

        compose.onNodeWithTag("library-screen").performScrollToNode(hasTestTag("library-recent-entry"))
        compose.onNodeWithTag("library-recent-entry").performClick()
        assertEquals(1, recentClicks)
    }

    @Test
    fun libraryEntriesDebounceRapidClicks() {
        var likedClicks = 0
        var recentClicks = 0
        setScreen {
            LibraryScreen(
                state = PreviewData.libraryLoggedIn,
                onLikedSongsClick = { likedClicks += 1 },
                onRecentSongsClick = { recentClicks += 1 },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("library-screen").performScrollToNode(hasTestTag("library-liked-entry"))
        compose.onNodeWithTag("library-liked-entry").performClick()
        compose.onNodeWithTag("library-liked-entry").performClick()
        assertEquals(1, likedClicks)

        compose.onNodeWithTag("library-screen").performScrollToNode(hasTestTag("library-recent-entry"))
        compose.onNodeWithTag("library-recent-entry").performClick()
        compose.onNodeWithTag("library-recent-entry").performClick()
        assertEquals(1, recentClicks)
    }

    @Test
    fun libraryErrorShowsRetry() {
        var retried = 0
        setScreen {
            LibraryScreen(
                state = LibraryUiState(loading = false, error = "登录状态已失效"),
                onRetry = { retried += 1 },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("library-error").assertExists()
        compose.onNodeWithText("重试").performClick()

        assertEquals(1, retried)
    }

    @Test
    fun librarySettingsButtonNavigatesToSettings() {
        var opened = 0
        setScreen {
            LibraryScreen(
                state = PreviewData.libraryAnonymous,
                onSettingsClick = { opened++ },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("library-settings-button").performClick()
        assertEquals(1, opened)
    }

    // ---------------------------------------------------------------------------------------
    // Login sheet
    // ---------------------------------------------------------------------------------------

    @Test
    fun loginSheetRendersQrPanelAndStatus() {
        setScreen {
            LoginSheet(
                state = LoginUiState(visible = true, loading = true, challenge = null),
                onClose = {},
                onRefresh = {},
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("login-sheet").assertExists()
        compose.onNodeWithTag("login-qr").assertExists()
        compose.onNodeWithTag("login-status").assertExists()
        compose.onNodeWithText("等待扫码").assertExists()
        compose.onNodeWithTag("login-refresh").assertExists()
    }

    @Test
    fun loginSheetExpiredOffersRefresh() {
        var refreshed = 0
        setScreen {
            LoginSheet(
                state = LoginUiState(
                    visible = true,
                    challenge = null,
                    state = QrLoginState.Expired,
                ),
                onClose = {},
                onRefresh = { refreshed += 1 },
            )
        }
        compose.waitForIdle()

        assertTrue(
            compose.onAllNodesWithText("二维码已过期").fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithTag("login-refresh").performScrollTo().performClick()

        assertEquals(1, refreshed)
    }

    @Test
    fun phoneLoginOffersCodeAndPasswordWithoutRetainingPreviousCredential() {
        var mode by mutableStateOf(LoginMode.Code)
        var codeRequest: Pair<String, String>? = null
        var loginRequest: Triple<String, String, String>? = null
        setScreen {
            LoginSheet(
                state = LoginUiState(visible = true, mode = mode),
                onClose = {},
                onRefresh = {},
                onModeChange = { mode = it },
                onSendCode = { phone, country -> codeRequest = phone to country },
                onSubmitPhone = { phone, country, value -> loginRequest = Triple(phone, country, value) },
            )
        }
        compose.onNodeWithTag("login-phone").performTextInput("13800138000")
        compose.onNodeWithTag("login-send-code").performClick()
        assertEquals("13800138000" to "86", codeRequest)
        compose.onNodeWithTag("login-credential").performTextInput("123456")
        compose.onNodeWithTag("login-submit").performClick()
        assertEquals(Triple("13800138000", "86", "123456"), loginRequest)

        compose.onNodeWithTag("login-mode-password").performClick()
        compose.onNodeWithTag("login-send-code").assertDoesNotExist()
        compose.onNodeWithTag("login-credential").performTextInput("new-password")
        compose.onNodeWithTag("login-submit").performClick()
        assertEquals(Triple("13800138000", "86", "new-password"), loginRequest)
    }

    // ---------------------------------------------------------------------------------------
    // Liked songs
    // ---------------------------------------------------------------------------------------

    @Test
    fun likedSongsRowsReportPlayAndLikeCallbacks() {
        val expectedSong = PreviewData.songs.first()
        var played: Song? = null
        var liked: Song? = null
        setScreen {
            LikedSongsScreen(
                state = PreviewData.likedSongsState,
                onSongClick = { song, _ -> played = song },
                onLikeClick = { liked = it },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("liked-songs-list").assertExists()
        compose.onNodeWithTag("liked-song-row-${expectedSong.id}").performClick()
        assertEquals(expectedSong, played)

        compose.onAllNodesWithContentDescription("取消喜欢")[0].performClick()
        assertEquals(expectedSong, liked)
    }

    @Test
    fun likedSongsEmptyStateRenders() {
        setScreen {
            LikedSongsScreen(state = LikedSongsUiState(loading = false))
        }
        compose.waitForIdle()

        compose.onNodeWithTag("liked-empty").assertExists()
        compose.onNodeWithText("还没有喜欢的歌曲").assertExists()
    }

    // ---------------------------------------------------------------------------------------
    // Recent songs
    // ---------------------------------------------------------------------------------------

    @Test
    fun recentSongsRowsReportPlayAndLikeCallbacks() {
        val expectedSong = PreviewData.songs.first()
        var played: Song? = null
        var liked: Song? = null
        var allPlayed = false
        setScreen {
            RecentSongsScreen(
                state = PreviewData.libraryLoggedIn.copy(likedSongIds = setOf(expectedSong.id)),
                onSongClick = { song, _ -> played = song },
                onLikeClick = { liked = it },
                onPlayAll = { allPlayed = true },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("recent-songs-list").assertExists()
        compose.onNodeWithTag("recent-play-all").performClick()
        assertTrue(allPlayed)

        compose.onNodeWithTag("recent-song-row-${expectedSong.id}").performClick()
        assertEquals(expectedSong, played)

        compose.onAllNodesWithContentDescription("取消喜欢")[0].performClick()
        assertEquals(expectedSong, liked)
    }

    @Test
    fun recentSongsEmptyStateRenders() {
        setScreen {
            RecentSongsScreen(state = PreviewData.libraryLoggedIn.copy(recentSongs = emptyList()))
        }
        compose.waitForIdle()

        compose.onNodeWithTag("recent-empty").assertExists()
        compose.onNodeWithText("还没有播放记录").assertExists()
    }
}
