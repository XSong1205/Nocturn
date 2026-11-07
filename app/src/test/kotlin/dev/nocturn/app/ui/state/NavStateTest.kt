package dev.nocturn.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The predictive back gesture previews [NavState.previous], so it must match what back() pops to. */
class NavStateTest {

    @Test
    fun tabRootHasNothingBehindIt() {
        assertNull(NavState(tab = AppTab.Home).previous)
    }

    @Test
    fun firstPushedScreenRevealsItsTabRoot() {
        val nav = NavState(tab = AppTab.Library, stack = listOf(Screen.LikedSongs))
        assertEquals(Screen.Library, nav.previous)
    }

    @Test
    fun deeperStackRevealsTheScreenBelowTheTop() {
        val nav = NavState(
            tab = AppTab.Search,
            stack = listOf(Screen.Album(1), Screen.Artist(2), Screen.Album(3)),
        )
        assertEquals(Screen.Artist(2), nav.previous)
    }

    @Test
    fun onlyTheThreeTabDestinationsAreRoots() {
        assertTrue(Screen.Home.isTabRoot)
        assertTrue(Screen.Search.isTabRoot)
        assertTrue(Screen.Library.isTabRoot)
        assertFalse(Screen.LikedSongs.isTabRoot)
        assertFalse(Screen.Playlist(7).isTabRoot)
    }
}
