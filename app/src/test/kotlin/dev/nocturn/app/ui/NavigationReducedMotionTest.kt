package dev.nocturn.app.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.nocturn.app.ui.state.NavState
import dev.nocturn.app.ui.state.Screen
import dev.nocturn.app.ui.theme.NocturnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationReducedMotionTest {
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun disabledAnimationsStillAllowPushPopAndPredictiveCancellationAndCommit() {
        var navigation by mutableStateOf(NavState())
        var pops = 0
        var acceptedClicks = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var action: (Unit) -> Unit
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            NocturnTheme {
                ScreenNavigationHost(navigation, true, {
                    pops++
                    navigation = navigation.copy(stack = navigation.stack.dropLast(1))
                }) { screen ->
                    val navigate = rememberResumedNavigation<Unit> { acceptedClicks++ }
                    SideEffect { action = navigate }
                    Text(screen.toString())
                }
            }
        }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
        compose.onNodeWithText("Album(id=1)").assertIsDisplayed()
        compose.runOnIdle { action(Unit) }
        assertEquals("The destination reaches RESUMED without animation", 1, acceptedClicks)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.runOnIdle { navigation = navigation.copy(stack = listOf(Screen.Album(1))) }
        compose.onNodeWithText("Album(id=1)").assertIsDisplayed()
        startGesture(dispatcher)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithText("Album(id=1)").assertIsDisplayed()
        assertEquals(1, pops)
        startGesture(dispatcher)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("Home").assertIsDisplayed()
        assertEquals(2, pops)
    }

    private fun startGesture(dispatcher: OnBackPressedDispatcher) {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }
}
