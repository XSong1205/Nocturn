package dev.nocturn.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests navigation routes and predictive back destination resolution for the settings screens.
 */
class SettingsNavigationTest {

    @Test
    fun settingsDestinationsAreNotTabRoots() {
        assertFalse(Screen.Settings.isTabRoot)
        assertFalse(Screen.SettingsPlayback.isTabRoot)
        assertFalse(Screen.SettingsAppearance.isTabRoot)
        assertFalse(Screen.SettingsGeneral.isTabRoot)
        assertFalse(Screen.SettingsStorage.isTabRoot)
        assertFalse(Screen.SettingsAbout.isTabRoot)
    }

    @Test
    fun openingSettingsFromLibraryRevealsLibraryOnBack() {
        val nav = NavState(tab = AppTab.Library, stack = listOf(Screen.Settings))
        assertEquals(Screen.Library, nav.previous)
        assertEquals(Screen.Settings, nav.current)
    }

    @Test
    fun openingSubMenuFromSettingsRevealsSettingsOnBack() {
        val subMenus = listOf(
            Screen.SettingsPlayback,
            Screen.SettingsAppearance,
            Screen.SettingsGeneral,
            Screen.SettingsStorage,
            Screen.SettingsAbout,
        )

        for (subMenu in subMenus) {
            val nav = NavState(
                tab = AppTab.Library,
                stack = listOf(Screen.Settings, subMenu),
            )
            assertEquals(
                "Expected previous of $subMenu to be Settings",
                Screen.Settings,
                nav.previous,
            )
            assertEquals(subMenu, nav.current)
        }
    }

    @Test
    fun poppingSubMenuReturnsToSettingsThenToLibraryRoot() {
        var nav = NavState(
            tab = AppTab.Library,
            stack = listOf(Screen.Settings, Screen.SettingsPlayback),
        )
        assertEquals(Screen.SettingsPlayback, nav.current)

        // Pop sub-menu
        nav = nav.copy(stack = nav.stack.dropLast(1))
        assertEquals(Screen.Settings, nav.current)
        assertEquals(Screen.Library, nav.previous)

        // Pop settings root
        nav = nav.copy(stack = nav.stack.dropLast(1))
        assertEquals(Screen.Library, nav.current)
        assertNull(nav.previous)
    }
}
