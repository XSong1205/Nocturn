package dev.nocturn.app.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
class PlayerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var visible by mutableStateOf(false)
    private var expands = 0
    private var collapses = 0
    private var playerCompositions = 0
    private lateinit var sheet: PlayerSheetState
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private fun start() {
        compose.setContent {
            sheet = rememberPlayerSheetState()
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                PlayerNavigationHost(
                    visible = visible,
                    onCollapse = {
                        collapses++
                        visible = false
                    },
                    onExpand = {
                        expands++
                        visible = true
                    },
                    state = sheet,
                    shell = {
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .testTag("mini")
                                    .onGloballyPositioned { sheet.miniBounds = it.boundsInRoot() },
                            )
                        }
                    },
                    player = {
                        SideEffect { playerCompositions++ }
                        Box(Modifier.fillMaxSize().testTag("player"))
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun playerBounds() = compose.onNodeWithTag("player").fetchSemanticsNode().boundsInRoot

    private fun advanceFrames(frames: Int) {
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { }
        }
    }

    @Test
    fun aCollapsedSheetComposesNoPlayer() {
        start()
        compose.onNodeWithTag("player").assertDoesNotExist()
    }

    @Test
    fun expandingMorphsFromTheMiniPlayerOutlineToFullScreen() {
        start()
        val mini = compose.onNodeWithTag("mini").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { visible = true }
            advanceFrames(2)
            val early = playerBounds()
            assertTrue("The morph starts at the capsule: ${early.width} vs ${mini.width}", early.width < 411f * 0.97f)
            assertTrue("It grows from the capsule: ${early.width} >= ${mini.width}", early.width >= mini.width - 1f)
            advanceFrames(8)
            val mid = playerBounds()
            assertTrue("It keeps growing: ${mid.width} > ${early.width}", mid.width > early.width)
            advanceFrames(90)
            val full = playerBounds()
            assertEquals(411f, full.width, 0.5f)
            assertEquals(914f, full.height, 0.5f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun draggingTheMiniPlayerUpFollowsTheFingerAndOpensPastHalf() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle {
                sheet.onDragStart()
                sheet.onDrag(-300f)
            }
            advanceFrames(2)
            val partial = playerBounds()
            assertTrue("A partial drag is a partial morph: ${partial.height}", partial.height in 80f..900f)
            val first = partial.top
            compose.runOnIdle { sheet.onDrag(-200f) }
            advanceFrames(2)
            assertTrue("More drag lifts the sheet further", playerBounds().top < first)
            compose.runOnIdle { sheet.onDragEnd(0f) }
            advanceFrames(100)
            assertEquals("A release past half asks to expand once", 1, expands)
            assertEquals(914f, playerBounds().height, 0.5f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun aShortDragReturnsToTheMiniPlayerWithoutExpanding() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle {
                sheet.onDragStart()
                sheet.onDrag(-80f)
                sheet.onDragEnd(0f)
            }
            advanceFrames(120)
            assertEquals(0, expands)
            compose.onNodeWithTag("player").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun aFlingUpOpensEvenFromAShortDrag() {
        start()
        compose.runOnIdle {
            sheet.onDragStart()
            sheet.onDrag(-60f)
            sheet.onDragEnd(-6000f)
        }
        compose.waitForIdle()
        assertEquals(1, expands)
    }

    @Test
    fun grabbingTheSheetAgainBeforeReleaseSettlesKeepsTheDraggedPosition() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle {
                sheet.onDragStart()
                sheet.onDrag(-300f)
                val dragged = sheet.progress
                sheet.onDragEnd(0f)
                // A second pointer can arrive before the release coroutine has run.
                sheet.onDragStart()
                assertEquals("Re-grabbing must not jump to the old animated position", dragged, sheet.progress, 0.001f)
                sheet.onDragEnd(0f)
            }
            advanceFrames(120)
            compose.onNodeWithTag("player").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun reversingAnExpansionKeepsItsMomentumAndEventuallyRemovesThePlayer() {
        start()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { visible = true }
            advanceFrames(6)
            var before = 0f
            compose.runOnIdle {
                before = sheet.progress
                assertTrue("Reverse while still expanding", before in 0.01f..0.7f)
                visible = false
                sheet.settle(0f)
                assertEquals("Changing direction must preserve position", before, sheet.progress, 0.001f)
            }
            advanceFrames(2)
            compose.runOnIdle {
                assertTrue(
                    "The spring carries its incoming velocity through the reversal: $before -> ${sheet.progress}",
                    sheet.progress > before,
                )
            }
            advanceFrames(120)
            compose.onNodeWithTag("player").assertDoesNotExist()
            compose.runOnIdle { assertEquals("The scrim is also cleared", 0f, sheet.scrimProgress, 0.001f) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun draggingTheOpenPlayerDownCollapsesItOnRelease() {
        start()
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        compose.runOnIdle {
            sheet.onDragStart()
            sheet.onDrag(700f)
            sheet.onDragEnd(0f)
        }
        compose.waitForIdle()
        assertEquals(1, collapses)
        compose.onNodeWithTag("player").assertDoesNotExist()
    }

    @Test
    fun backCollapsesTheOpenPlayer() {
        start()
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, collapses)
    }

    @Test
    fun cancellingABackGestureSpringsTheOpenPlayerBackToFullScreen() {
        start()
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle {
                backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
                backDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            }
            advanceFrames(1)
            for (step in 1..25) {
                compose.runOnIdle {
                    backDispatcher.dispatchOnBackProgressed(
                        BackEventCompat(0f, 100f, step * 0.02f, BackEventCompat.EDGE_LEFT),
                    )
                }
                advanceFrames(1)
            }
            assertTrue("The preview pulls the player down", playerBounds().top > 1f)
            compose.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
            advanceFrames(120)
            assertEquals(0, collapses)
            assertEquals(914f, playerBounds().height, 0.5f)
            assertEquals(0f, playerBounds().top, 0.5f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun movingTheSheetDoesNotRecomposeThePlayerContent() {
        start()
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        val baseline = playerCompositions
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { sheet.onDragStart() }
            for (step in 1..10) {
                compose.runOnIdle { sheet.onDrag(30f) }
                advanceFrames(1)
            }
            compose.runOnIdle { sheet.onDragEnd(0f) }
            advanceFrames(5)
            assertEquals("Sheet progress is read in the draw phase only", baseline, playerCompositions)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
