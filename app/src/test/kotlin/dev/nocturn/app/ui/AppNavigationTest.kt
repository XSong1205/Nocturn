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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp")
class AppNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    // Shared predictive-back harness state. JUnit creates a fresh test instance per test method.
    private var navState by mutableStateOf(NavState())
    private var popCount = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher

    /** Compositions of the routed album page content; finger progress must not change it. */
    private var albumPageCompositions = 0

    @Test
    fun repeatedDestinationsKeepIndependentStateAndRestoreThePreviousEntry() {
        var navigation by mutableStateOf(NavState(stack = listOf(Screen.Album(1))))
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(navigation, true, {
                    navigation = navigation.copy(stack = navigation.stack.dropLast(1))
                }) {
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Button(onClick = { count++ }) { Text("Visits: $count") }
                }
            }
        }
        compose.onNodeWithText("Visits: 0").performClick()
        compose.runOnIdle {
            navigation = navigation.copy(stack = navigation.stack + Screen.Album(1))
        }
        compose.onNodeWithText("Visits: 0").assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Visits: 1").assertIsDisplayed()
    }

    @Test
    fun predictiveBackShrinksThePageAndCancellationRestoresItBeforeACommittedPop() {
        var navigation by mutableStateOf(NavState())
        var pops = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(navigation, true, {
                    pops++
                    navigation = navigation.copy(stack = navigation.stack.dropLast(1))
                }) { screen ->
                    if (screen == Screen.Home) {
                        LazyColumn(Modifier.testTag("rows")) {
                            items((0..100).toList()) { index -> Text("Row $index") }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().testTag("album-page")) {
                            Text("Album")
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("rows").performScrollToIndex(35)
        compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
        compose.onNodeWithText("Album").assertIsDisplayed()
        val fullBounds = compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        compose.waitForIdle()
        val gestureBounds = compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot
        assertTrue("Predictive back shrinks the page horizontally", gestureBounds.width < fullBounds.width)
        assertTrue("Predictive back shrinks the page vertically", gestureBounds.height < fullBounds.height)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithText("Album").assertIsDisplayed()
        val restoredBounds = compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot
        assertEquals(fullBounds.width, restoredBounds.width, 0.5f)
        assertEquals(fullBounds.height, restoredBounds.height, 0.5f)
        compose.runOnIdle {
            assertEquals(0, pops)
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.8f))
        }
        compose.waitForIdle()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Row 35").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, pops) }
    }

    @Test
    fun backClosesPlayerThenPopsThePageThenYieldsToTheTabHandler() {
        var navigation by mutableStateOf(NavState(stack = listOf(Screen.Album(1))))
        var playerVisible by mutableStateOf(false)
        var rootBacks = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { rootBacks++ }
            NocturnTheme {
                PlayerNavigationHost(
                    visible = playerVisible,
                    onCollapse = { playerVisible = false },
                    shell = {
                        ScreenNavigationHost(navigation, !playerVisible, {
                            navigation = navigation.copy(stack = navigation.stack.dropLast(1))
                        }) { screen ->
                            var count by rememberSaveable { mutableIntStateOf(0) }
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
    fun rapidNavigationAndBackSettleOnTheSelectedTab() {
        var navigation by mutableStateOf(NavState())
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(navigation, true, {
                    navigation = navigation.copy(stack = navigation.stack.dropLast(1))
                }) { screen -> Text(screen.toString()) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            navigation = navigation.copy(stack = navigation.stack + Screen.Artist(2))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(emptyList<Screen>(), navigation.stack)
            navigation = NavState(tab = AppTab.Search)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Search").assertIsDisplayed()
        compose.onNodeWithText("Album(id=1)").assertDoesNotExist()
        compose.onNodeWithText("Artist(id=2)").assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------------------------
    // Predictive-back recede engine: frame-precise regression tests.
    // ---------------------------------------------------------------------------------------------

    /**
     * The page slides in on the theme's spatial spring. If that spring overshoots, the page would
     * pass its resting place and open a gap at the right edge, so sample every frame of the push.
     */
    @Test
    fun pushedPageNeverOpensAGapAtTheEdgeWhileItSettles() {
        startTwoPageNavigationHost()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { navState = navState.copy(stack = navState.stack + Screen.Album(1)) }
            var leftMost = Float.MAX_VALUE
            repeat(90) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { }
                val node = runCatching {
                    compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot
                }.getOrNull()
                if (node != null) leftMost = minOf(leftMost, node.left)
            }
            assertTrue("The page reached its resting place: $leftMost", leftMost < 1f)
            assertTrue("The page did not overshoot past the screen edge: $leftMost", leftMost >= -0.5f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /**
     * Material's predictive back preview: the page shrinks toward 90% on a decelerated curve, so
     * the start of the gesture is the most visible part and the shrink never passes 90%.
     */
    @Test
    fun fingerProgressShrinksThePageOnADeceleratedCurveToNinetyPercent() {
        startTwoPageNavigationHost()
        compose.onNodeWithTag("rows").performScrollToIndex(35)
        pushAlbumPage()
        compose.onNodeWithText("Album").assertIsDisplayed()
        val fullWidth = pageWidth()

        try {
            compose.mainClock.autoAdvance = false
            beginGesture()
            dragTo(0.1f)
            val widthAt10 = widthAwaitingMotion(fullWidth)
            assertTrue("progress 0.1 shrinks the page: $widthAt10 of $fullWidth", widthAt10 < fullWidth)

            dragTo(0.3f)
            val widthAt30 = widthAwaitingMotion(widthAt10)
            assertTrue("progress 0.3 shrinks the page further: $widthAt30 of $widthAt10", widthAt30 < widthAt10)

            assertTrue(
                "The curve decelerates: the first 0.1 of progress shrinks more than half of 0.3's",
                fullWidth - widthAt10 > 0.5f * (fullWidth - widthAt30),
            )

            dragTo(1.0f)
            val widthAt60 = widthAwaitingMotion(widthAt30)
            assertEquals("The preview settles at 90% of the page", fullWidth * 0.9f, widthAt60, 2f)
        } finally {
            endPredictiveBackTest()
        }
    }

    /** The preview follows the finger vertically and never comes closer than 8dp to the screen edge. */
    @Test
    fun previewFollowsTheFingerVerticallyWithinTheEdgeMargin() {
        startTwoPageNavigationHost()
        pushAlbumPage()
        compose.onNodeWithText("Album").assertIsDisplayed()
        val density = compose.density.density
        val windowHeight = compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot.height

        try {
            compose.mainClock.autoAdvance = false
            compose.runOnIdle {
                backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
                backDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            }
            compose.mainClock.advanceTimeByFrame()
            var progress = 0f
            while (progress < 0.6f - 1e-4f) {
                progress = minOf(progress + 0.02f, 0.6f)
                // The finger travels down the screen while it swipes in.
                val touchY = 100f + progress * 1200f
                compose.runOnIdle {
                    backDispatcher.dispatchOnBackProgressed(
                        BackEventCompat(0f, touchY, progress, BackEventCompat.EDGE_LEFT),
                    )
                }
                advanceFrames(1)
            }
            advanceFrames(4)
            val bounds = compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot
            val centredTop = (windowHeight - bounds.height) / 2f
            assertTrue("A downward finger moves the page down: ${bounds.top} vs $centredTop", bounds.top > centredTop + 1f)
            val bottomMargin = windowHeight - bounds.bottom
            assertTrue("The page keeps 8dp from the bottom edge: $bottomMargin", bottomMargin >= 8f * density - 1f)

            compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
            settleTransition()
            assertEquals("Cancellation re-centres the page vertically", 0f, pageTop(), 0.5f)
        } finally {
            endPredictiveBackTest()
        }
    }

    /** The preview moves toward the side the finger travels to, mirrored per swipe edge. */
    @Test
    fun previewShiftsAwayFromTheSwipeEdge() {
        startTwoPageNavigationHost()
        pushAlbumPage()
        compose.onNodeWithText("Album").assertIsDisplayed()
        val restingCenter = pageCenterX()

        try {
            compose.mainClock.autoAdvance = false
            beginGesture(BackEventCompat.EDGE_LEFT)
            dragTo(0.6f, BackEventCompat.EDGE_LEFT)
            advanceFrames(3)
            val fromLeft = pageCenterX()
            compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
            settleTransition()
            compose.waitForIdle()
            assertEquals("Cancellation re-centres the page", restingCenter, pageCenterX(), 0.5f)

            beginGesture(BackEventCompat.EDGE_RIGHT)
            dragTo(0.6f, BackEventCompat.EDGE_RIGHT)
            advanceFrames(3)
            val fromRight = pageCenterX()

            assertTrue("A left-edge swipe moves the page right: $fromLeft vs $restingCenter", fromLeft > restingCenter + 1f)
            assertTrue("A right-edge swipe moves the page left: $fromRight vs $restingCenter", fromRight < restingCenter - 1f)
        } finally {
            endPredictiveBackTest()
        }
    }

    /**
     * Cancelling a gesture must hand the page back to a settle animation: one frame after the
     * cancellation the page is still shrunk, and it is only full size again once the settle ended.
     */
    @Test
    fun cancellingTheGestureAnimatesBackInsteadOfSnapping() {
        startTwoPageNavigationHost()
        pushAlbumPage()
        compose.onNodeWithText("Album").assertIsDisplayed()
        val fullWidth = pageWidth()
        val stackBeforeGesture = navState.stack

        try {
            compose.mainClock.autoAdvance = false
            beginGesture()
            dragTo(0.6f)
            val widthAt60 = widthAwaitingMotion(fullWidth)
            assertTrue("progress 0.6 shrinks the page: $widthAt60 of $fullWidth", widthAt60 < fullWidth - 1f)

            compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
            compose.mainClock.advanceTimeByFrame()
            val widthOneFrameAfterCancel = pageWidth()
            assertTrue(
                "One frame after cancellation the page is still mid-settle, not snapped back " +
                    "($widthOneFrameAfterCancel of $fullWidth)",
                widthOneFrameAfterCancel < fullWidth - 1f,
            )

            settleTransition()
            compose.waitForIdle()
            val settledWidth = pageWidth()
            assertEquals("Cancellation restores the page to full size", fullWidth, settledWidth, 0.5f)
            compose.onNodeWithText("Album").assertIsDisplayed()
            assertEquals("Cancellation does not pop the page", stackBeforeGesture, navState.stack)
        } finally {
            endPredictiveBackTest()
        }
    }

    /**
     * The gesture path must be draw-phase only: the routed page content is composed at most once
     * while the page is settled, and eight progress events must not recompose it.
     */
    @Test
    fun fingerProgressDoesNotRecomposeTheRoutedPageContent() {
        startTwoPageNavigationHost()
        pushAlbumPage()
        compose.onNodeWithText("Album").assertIsDisplayed()
        compose.waitForIdle()
        val baseline = albumPageCompositions
        assertTrue("Baseline captured after the album page settled: $baseline", baseline > 0)
        val fullWidth = pageWidth()

        try {
            compose.mainClock.autoAdvance = false
            beginGesture()
            compose.runOnIdle {
                for (step in 1..8) {
                    backDispatcher.dispatchOnBackProgressed(backEvent(step / 10f))
                }
            }
            val gesturedWidth = widthAwaitingMotion(fullWidth, maxFrames = 3)
            assertTrue("Progress events move the page while the finger moves", gesturedWidth < fullWidth)
            assertEquals(
                "Progress is draw-phase only, so the routed page must not recompose",
                baseline,
                albumPageCompositions,
            )
        } finally {
            endPredictiveBackTest()
        }
    }

    /**
     * A committed programmatic pop slides the outgoing page off toward the end edge: it stays
     * composed and moves before the previous page is revealed.
     */
    @Test
    fun programmaticBackSlidesThePageOffBeforeRevealingThePreviousPage() {
        startTwoPageNavigationHost()
        compose.onNodeWithTag("rows").performScrollToIndex(35)
        pushAlbumPage()
        compose.onNodeWithText("Album").assertIsDisplayed()
        val restingLeft = pageLeft()
        assertEquals(0, popCount)

        try {
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { backDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("album-page")
                .assertExists("The outgoing page must stay composed while the pop slides off")
            var left = pageLeft()
            var framesWaited = 0
            while (framesWaited < 3 && abs(left - restingLeft) < 0.5f) {
                compose.mainClock.advanceTimeByFrame()
                framesWaited++
                left = pageLeft()
            }
            assertTrue(
                "Programmatic back slides the page toward the end edge: $left from $restingLeft",
                left > restingLeft + 0.5f,
            )

            settleTransition()
            compose.waitForIdle()
            compose.onNodeWithText("Row 35").assertIsDisplayed()
            compose.onNodeWithTag("album-page").assertDoesNotExist()
            assertEquals("The programmatic pop is committed exactly once", 1, popCount)
        } finally {
            endPredictiveBackTest()
        }
    }

    /**
     * A gesture restarted in the middle of the cancel settle must adopt the new progress directly:
     * the page lands on the geometry of the new progress instead of jumping or finishing the old
     * settle first.
     */
    @Test
    fun restartingTheGestureMidSettleFollowsTheNewProgress() {
        startTwoPageNavigationHost()
        pushAlbumPage()
        compose.onNodeWithText("Album").assertIsDisplayed()
        val fullWidth = pageWidth()

        try {
            compose.mainClock.autoAdvance = false
            // Reference geometry of an uninterrupted gesture at 0.3.
            beginGesture()
            dragTo(0.3f)
            val expectedWidthAt30 = widthAwaitingMotion(fullWidth)
            compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
            settleTransition()
            compose.waitForIdle()
            assertEquals(fullWidth, pageWidth(), 0.5f)

            beginGesture()
            dragTo(0.5f)
            val widthAt50 = widthAwaitingMotion(fullWidth)
            assertTrue("progress 0.5 shrinks the page: $widthAt50 of $fullWidth", widthAt50 < fullWidth - 1f)

            compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
            advanceFrames(2)

            beginGesture()
            dragTo(0.3f)
            var widthAt30 = pageWidth()
            var framesWaited = 0
            while (framesWaited < 3 && abs(widthAt30 - expectedWidthAt30) > 2f) {
                compose.mainClock.advanceTimeByFrame()
                framesWaited++
                widthAt30 = pageWidth()
            }
            assertEquals(
                "A restarted gesture tracks its own progress without jumping",
                expectedWidthAt30,
                widthAt30,
                2f,
            )
            assertTrue("A restarted gesture still shrinks the page: $widthAt30 of $fullWidth", widthAt30 < fullWidth - 1f)
        } finally {
            endPredictiveBackTest()
        }
    }

    private fun startTwoPageNavigationHost() {
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(
                    navigation = navState,
                    backEnabled = true,
                    onBack = {
                        popCount++
                        navState = navState.copy(stack = navState.stack.dropLast(1))
                    },
                ) { screen ->
                    if (screen == Screen.Home) {
                        LazyColumn(Modifier.testTag("rows")) {
                            items((0..100).toList()) { index -> Text("Row $index") }
                        }
                    } else {
                        AlbumPage()
                    }
                }
            }
        }
    }

    /** Routed album page whose composition count is exposed through [albumPageCompositions]. */
    @Composable
    private fun AlbumPage() {
        var compositions by remember { mutableIntStateOf(0) }
        SideEffect {
            compositions++
            albumPageCompositions = compositions
        }
        Box(Modifier.fillMaxSize().testTag("album-page")) {
            Text("Album")
        }
    }

    private fun pushAlbumPage() {
        compose.runOnIdle { navState = navState.copy(stack = navState.stack + Screen.Album(1)) }
        compose.waitForIdle()
    }

    private fun pageWidth(): Float =
        compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot.width

    private fun pageLeft(): Float =
        compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot.left

    private fun pageTop(): Float =
        compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot.top

    private fun pageCenterX(): Float =
        compose.onNodeWithTag("album-page").fetchSemanticsNode().boundsInRoot.center.x

    private fun advanceFrames(frames: Int) {
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            // Resumed coroutines (the settle after cancel or commit) run on the main looper, which
            // only drains when the test syncs with it.
            compose.runOnIdle { }
        }
    }

    /**
     * Reads the outgoing page width, giving the engine up to [maxFrames] frames to register a new
     * transition target while the width has not moved at all yet.
     */
    private fun widthAwaitingMotion(unchangedFrom: Float, maxFrames: Int = 6): Float {
        var width = pageWidth()
        var framesWaited = 0
        while (framesWaited < maxFrames && abs(width - unchangedFrom) < 0.01f) {
            compose.mainClock.advanceTimeByFrame()
            framesWaited++
            width = pageWidth()
        }
        return width
    }

    /** Progress the simulated finger has reached since [beginGesture]. */
    private var gestureProgress = 0f

    /**
     * Moves the finger to [target] one frame at a time, as a device streams events. A single jump
     * right after the start is absorbed by the frame that composes the revealed page.
     */
    private fun dragTo(target: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        while (gestureProgress < target - 1e-4f) {
            gestureProgress = minOf(gestureProgress + 0.02f, target)
            compose.runOnIdle {
                backDispatcher.dispatchOnBackProgressed(backEvent(gestureProgress, edge))
            }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    /**
     * Runs a started transition to its end. NavDisplay launches the settle from an effect that
     * only starts on a frame, and a single large clock jump would skip past that start.
     */
    private fun settleTransition() {
        advanceFrames(60)
    }

    /**
     * Starts a gesture the way a device does: the start and a first progress event land on their
     * own frame before the finger moves. NavDisplay composes the revealed page on that first seek.
     */
    private fun beginGesture(edge: Int = BackEventCompat.EDGE_LEFT) {
        gestureProgress = 0f
        compose.runOnIdle {
            backDispatcher.dispatchOnBackStarted(backEvent(0f, edge))
            backDispatcher.dispatchOnBackProgressed(backEvent(0f, edge))
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Restores the automatic clock and drops any gesture still in flight. */
    private fun endPredictiveBackTest() {
        compose.mainClock.autoAdvance = true
        runCatching {
            compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
        }
    }

    private fun backEvent(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) =
        BackEventCompat(0f, 100f, progress, edge)
}
