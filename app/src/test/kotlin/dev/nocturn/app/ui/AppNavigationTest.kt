package dev.nocturn.app.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.LayoutDirection
import dev.nocturn.app.ui.state.AppTab
import dev.nocturn.app.ui.state.NavState
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.theme.NocturnTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp")
class AppNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private var navigation by mutableStateOf(NavState())
    private var pageBackEnabled by mutableStateOf(true)
    private var popCount = 0
    private var rootBacks = 0
    private var albumPageCompositions = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Test
    fun repeatedDestinationsKeepIndependentStateAndRestoreThePreviousEntry() {
        navigation = NavState(stack = listOf(Screen.Album(1)))
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(navigation, true, ::pop) {
                    var count by rememberSaveable { mutableStateOf(0) }
                    Button(onClick = { count++ }) { Text("Visits: $count") }
                }
            }
        }
        compose.onNodeWithText("Visits: 0").performClick()
        compose.runOnIdle { navigation = navigation.copy(stack = navigation.stack + Screen.Album(1)) }
        compose.onNodeWithText("Visits: 0").assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Visits: 1").assertIsDisplayed()
    }

    @Test
    fun revisitingAPoppedDestinationStartsWithFreshState() {
        navigation = NavState(stack = listOf(Screen.Album(1)))
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(navigation, true, ::pop) { screen ->
                    var count by rememberSaveable { mutableStateOf(0) }
                    Button(onClick = { count++ }) { Text("$screen visits: $count") }
                }
            }
        }
        compose.onNodeWithText("Album(id=1) visits: 0").performClick()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Home visits: 0").assertIsDisplayed()
        compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
        compose.onNodeWithText("Album(id=1) visits: 0").assertIsDisplayed()
    }

    @Test
    fun backClosesPlayerThenPopsThePageThenYieldsToTheTabHandler() {
        navigation = NavState(stack = listOf(Screen.Album(1)))
        var playerVisible by mutableStateOf(false)
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { rootBacks++ }
            NocturnTheme {
                PlayerNavigationHost(
                    visible = playerVisible,
                    onCollapse = { playerVisible = false },
                    shell = {
                        ScreenNavigationHost(navigation, !playerVisible, ::pop) { screen ->
                            var count by rememberSaveable { mutableStateOf(0) }
                            Column {
                                Text(screen.toString())
                                Button(onClick = { count++ }) { Text("Page count: $count") }
                            }
                        }
                    },
                    player = { Text("Player") },
                )
            }
        }
        compose.onNodeWithText("Page count: 0").performClick()
        compose.runOnIdle { playerVisible = true }
        compose.onNodeWithText("Player").assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Page count: 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(Screen.Album(1)), navigation.stack) }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, rootBacks) }
    }

    @Test
    fun disabledPageBackYieldsWithoutChangingTheStack() {
        startHost()
        pushAlbum()
        compose.runOnIdle {
            pageBackEnabled = false
        }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(0, popCount)
            assertEquals(1, rootBacks)
            assertEquals(listOf(Screen.Album(1)), navigation.stack)
        }
    }

    @Test
    fun rapidNavigationAndBackSettleOnTheSelectedTab() {
        startHost()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
            advanceFrames(1)
            compose.runOnIdle { navigation = navigation.copy(stack = navigation.stack + Screen.Artist(2)) }
            advanceFrames(1)
            compose.runOnIdle { dispatcher.onBackPressed() }
            advanceFrames(1)
            compose.runOnIdle { dispatcher.onBackPressed() }
            advanceFrames(1)
            compose.runOnIdle {
                assertEquals(emptyList<Screen>(), navigation.stack)
                navigation = NavState(tab = AppTab.Search)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithText("Search").assertIsDisplayed()
        compose.onNodeWithTag("album-page").assertDoesNotExist()
        compose.onNodeWithText("Artist(id=2)").assertDoesNotExist()
    }

    @Test
    fun predictiveBackCancellationRestoresPageAndCommitPopsImmediatelyOnce() {
        startHost()
        compose.onNodeWithTag("rows").performScrollToIndex(35)
        pushAlbum()
        val resting = bounds()
        compose.mainClock.autoAdvance = false
        try {
            beginGesture()
            dragTo(0.5f)
            assertTrue("The native transition follows the finger", bounds().left > resting.left + 1f)
            compose.runOnIdle { assertEquals(0, popCount) }
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            advanceFrames(30)
            assertEquals(resting.left, bounds().left, 0.5f)
            assertEquals(resting.width, bounds().width, 0.5f)
            compose.runOnIdle { assertEquals(listOf(Screen.Album(1)), navigation.stack) }

            beginGesture()
            dragTo(0.6f)
            compose.runOnIdle {
                dispatcher.onBackPressed()
                assertEquals("Commit updates the stack without a second animation delay", 1, popCount)
                assertTrue(navigation.stack.isEmpty())
            }
            advanceFrames(30)
            compose.onNodeWithTag("album-page").assertDoesNotExist()
            compose.onNodeWithText("Row 35").assertIsDisplayed()
        } finally {
            finishGestureTest()
        }
    }

    @Test
    fun predictiveBackFromEitherEdgeUsesTheSamePopTransformThroughRelease() {
        startHost()
        pushAlbum()
        compose.mainClock.autoAdvance = false
        try {
            beginGesture(BackEventCompat.EDGE_LEFT)
            dragTo(0.5f, BackEventCompat.EDGE_LEFT)
            assertTrue("Left-edge back slides right", bounds().left > 1f)
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            advanceFrames(30)
            assertEquals(0f, bounds().left, 0.5f)

            beginGesture(BackEventCompat.EDGE_RIGHT)
            dragTo(0.5f, BackEventCompat.EDGE_RIGHT)
            val beforeCommit = bounds().left
            assertTrue("Either edge seeks the normal pop transform", beforeCommit > 1f)
            compose.runOnIdle { dispatcher.onBackPressed() }
            advanceFrames(2)
            assertTrue("Release continues in the same direction", bounds().left >= beforeCommit - 1f)
            advanceFrames(30)
            compose.onNodeWithTag("album-page").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, popCount) }
        } finally {
            finishGestureTest()
        }
    }

    @Test
    fun gestureProgressDoesNotRecomposeTheRoutedPage() {
        startHost()
        pushAlbum()
        compose.mainClock.autoAdvance = false
        try {
            beginGesture()
            dragTo(0.1f)
            val baseline = albumPageCompositions
            val left = bounds().left
            dragTo(0.6f)
            assertTrue(bounds().left > left + 1f)
            assertEquals("Only the native transition moves; content is skipped", baseline, albumPageCompositions)
        } finally {
            finishGestureTest()
        }
    }

    @Test
    fun restartingGestureDuringCancellationCanStillCommit() {
        startHost()
        pushAlbum()
        compose.mainClock.autoAdvance = false
        try {
            beginGesture()
            dragTo(0.6f)
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            advanceFrames(2)
            beginGesture()
            dragTo(0.3f)
            assertTrue(bounds().left > 1f)
            compose.runOnIdle { dispatcher.onBackPressed() }
            advanceFrames(30)
            compose.runOnIdle {
                assertEquals(1, popCount)
                assertTrue(navigation.stack.isEmpty())
            }
            compose.onNodeWithTag("rows").assertIsDisplayed()
            compose.onNodeWithTag("album-page").assertDoesNotExist()
        } finally {
            finishGestureTest()
        }
    }

    @Test
    fun programmaticBackSlidesOutAndRetainsThePreviousScrollPosition() {
        startHost()
        compose.onNodeWithTag("rows").performScrollToIndex(35)
        pushAlbum()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { dispatcher.onBackPressed() }
            advanceFrames(4)
            assertTrue("Outgoing page remains composed and slides off", bounds().left > 1f)
            compose.runOnIdle { assertEquals(1, popCount) }
            advanceFrames(30)
            compose.onNodeWithText("Row 35").assertIsDisplayed()
            compose.onNodeWithTag("album-page").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun forwardAndBackSlidesMirrorInRtl() {
        startHost(LayoutDirection.Rtl)
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
            advanceFrames(4)
            assertTrue("RTL push arrives from the left", bounds().left < -1f)
            advanceFrames(30)
            assertEquals(0f, bounds().left, 0.5f)
            compose.runOnIdle { dispatcher.onBackPressed() }
            advanceFrames(4)
            assertTrue("RTL pop exits to the left", bounds().left < -1f)
            advanceFrames(30)
            compose.onNodeWithTag("rows").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun navigationActionsIgnorePagesThatAreEnteringOrLeaving() {
        val actions = mutableMapOf<Screen, (Long) -> Unit>()
        val clicks = mutableListOf<Long>()
        compose.setContent {
            NocturnTheme {
                ScreenNavigationHost(navigation, true, ::pop) { screen ->
                    val navigate = rememberResumedNavigation<Long> { clicks += it }
                    SideEffect { actions[screen] = navigate }
                    Text(screen.toString())
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { actions.getValue(Screen.Home)(1) }
        assertEquals(listOf(1L), clicks)
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
            advanceFrames(4)
            compose.runOnIdle {
                actions.getValue(Screen.Home)(2)
                actions.getValue(Screen.Album(1))(3)
            }
            assertEquals("Neither transitioning page accepts navigation", listOf(1L), clicks)
            advanceFrames(30)
            compose.runOnIdle {
                actions.getValue(Screen.Home)(4)
                actions.getValue(Screen.Album(1))(5)
            }
            assertEquals("Only the resumed destination accepts navigation", listOf(1L, 5L), clicks)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun switchingTabsDuringPredictiveBackDoesNotLeaveAnOutgoingPage() {
        startHost()
        pushAlbum()
        compose.mainClock.autoAdvance = false
        try {
            beginGesture()
            dragTo(0.4f)
            compose.runOnIdle { navigation = NavState(tab = AppTab.Search) }
            advanceFrames(1)
            compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            advanceFrames(30)
            compose.onNodeWithText("Search").assertIsDisplayed()
            compose.onNodeWithTag("album-page").assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, popCount) }
        } finally {
            finishGestureTest()
        }
    }

    private fun pop() {
        popCount++
        navigation = navigation.copy(stack = navigation.stack.dropLast(1))
    }

    private fun startHost(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { rootBacks++ }
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NocturnTheme {
                    ScreenNavigationHost(navigation, pageBackEnabled, ::pop) { screen ->
                        when (screen) {
                            Screen.Home -> LazyColumn(Modifier.testTag("rows")) {
                                items((0..100).toList()) { Text("Row $it") }
                            }
                            is Screen.Album -> AlbumPage()
                            else -> Text(screen.toString())
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun AlbumPage() {
        SideEffect { albumPageCompositions++ }
        Box(Modifier.fillMaxSize().testTag("album-page")) { Text("Album") }
    }

    private fun pushAlbum() {
        compose.runOnIdle { navigation = navigation.copy(stack = navigation.stack + Screen.Album(1)) }
        compose.onNodeWithText("Album").assertIsDisplayed()
    }

    private fun bounds() = compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }
        }
    }

    private fun beginGesture(edge: Int = BackEventCompat.EDGE_LEFT) {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(backEvent(0f, edge))
            dispatcher.dispatchOnBackProgressed(backEvent(0f, edge))
        }
        advanceFrames(1)
    }

    private fun dragTo(target: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        var progress = 0f
        while (progress < target - 1e-4f) {
            progress = minOf(progress + 0.02f, target)
            compose.runOnIdle { dispatcher.dispatchOnBackProgressed(backEvent(progress, edge)) }
            advanceFrames(1)
        }
        advanceFrames(3)
    }

    private fun finishGestureTest() {
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
    }

    private fun backEvent(progress: Float, edge: Int) = BackEventCompat(0f, 100f, progress, edge)
}
