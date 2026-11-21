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

    @Test
    fun multipleBackstacksPreservePerTabHistory() {
        var nav = NavState(tab = AppTab.Library)
        nav = nav.copy(stack = listOf(Screen.LikedSongs))
        assertEquals(Screen.LikedSongs, nav.current)

        // Switch to Search and push Album(42)
        nav = nav.copy(tab = AppTab.Search)
        assertTrue(nav.isTabRoot)
        assertEquals(Screen.Search, nav.current)

        nav = nav.copy(stack = listOf(Screen.Album(42)))
        assertEquals(Screen.Album(42), nav.current)

        // Switch back to Library - LikedSongs is preserved!
        nav = nav.copy(tab = AppTab.Library)
        assertFalse(nav.isTabRoot)
        assertEquals(Screen.LikedSongs, nav.current)
        assertEquals(listOf(Screen.LikedSongs), nav.stack)

        // Switch back to Search - Album(42) is preserved!
        nav = nav.copy(tab = AppTab.Search)
        assertFalse(nav.isTabRoot)
        assertEquals(Screen.Album(42), nav.current)
        assertEquals(listOf(Screen.Album(42)), nav.stack)

        // allScreens retains screens from both tabs
        assertEquals(setOf(Screen.LikedSongs, Screen.Album(42)), nav.allScreens.toSet())
    }
}
