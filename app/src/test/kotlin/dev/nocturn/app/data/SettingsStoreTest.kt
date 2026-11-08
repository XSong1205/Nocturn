package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.localapi.client.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric unit test suite for [SettingsStore] persistence, verifying default values,
 * cross-instance persistence, preference mutations, and isolation for all user preferences
 * including status bar Lyricon provider synchronization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // -----------------------------------------------------------------------------------
    // Lyricon Provider Toggle Verification (Requirement R4)
    // -----------------------------------------------------------------------------------

    @Test
    fun lyriconEnabledDefaultsToTrue() {
        val store = SettingsStore(context)
        assertTrue("lyriconEnabled must default to true when unconfigured", store.lyriconEnabled)
    }

    @Test
    fun lyriconEnabledPersistsFalseAcrossInstances() {
        val store1 = SettingsStore(context)
        store1.lyriconEnabled = false

        // Verify across independent SettingsStore instances backed by the same context
        val store2 = SettingsStore(context)
        assertFalse("lyriconEnabled=false must persist across separate SettingsStore instances", store2.lyriconEnabled)

        // Verify directly in the underlying SharedPreferences
        val rawPrefs = context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
        assertFalse(
            "Underlying SharedPreferences must hold false for key 'lyricon-enabled'",
            rawPrefs.getBoolean(SettingsStore.KEY_LYRICON_ENABLED, true)
        )
    }

    @Test
    fun lyriconEnabledRevertsToTrueCorrectly() {
        val store = SettingsStore(context)
        store.lyriconEnabled = false
        assertFalse(SettingsStore(context).lyriconEnabled)

        // Revert back to true
        store.lyriconEnabled = true

        val freshStore = SettingsStore(context)
        assertTrue("lyriconEnabled must revert to true across separate SettingsStore instances", freshStore.lyriconEnabled)

        val rawPrefs = context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
        assertTrue(
            "Underlying SharedPreferences must hold true for key 'lyricon-enabled'",
            rawPrefs.getBoolean(SettingsStore.KEY_LYRICON_ENABLED, false)
        )
    }

    @Test
    fun clearingPreferencesRestoresLyriconEnabledDefault() {
        val store = SettingsStore(context)
        store.lyriconEnabled = false
        assertFalse(store.lyriconEnabled)

        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val freshStore = SettingsStore(context)
        assertTrue("After clearing preferences, lyriconEnabled should fall back to default true", freshStore.lyriconEnabled)
    }

    @Test
    fun mutatingLyriconEnabledPreservesOtherPreferences() {
        val store = SettingsStore(context)
        store.dynamicColor = false
        store.hapticsEnabled = false
        store.themeMode = ThemeMode.Dark

        // Mutate lyriconEnabled
        store.lyriconEnabled = false

        val freshStore = SettingsStore(context)
        assertFalse(freshStore.lyriconEnabled)
        assertFalse(freshStore.dynamicColor)
        assertFalse(freshStore.hapticsEnabled)
        assertEquals(ThemeMode.Dark, freshStore.themeMode)

        freshStore.lyriconEnabled = true
        val freshStore2 = SettingsStore(context)
        assertTrue(freshStore2.lyriconEnabled)
        assertFalse(freshStore2.dynamicColor)
        assertFalse(freshStore2.hapticsEnabled)
        assertEquals(ThemeMode.Dark, freshStore2.themeMode)
    }

    // -----------------------------------------------------------------------------------
    // General SettingsStore Contract Sanity
    // -----------------------------------------------------------------------------------

    @Test
    fun defaultValuesForOtherSettingsMatchSpecifications() {
        val store = SettingsStore(context)
        assertEquals(ThemeMode.System, store.themeMode)
        assertTrue(store.dynamicColor)
        assertEquals(AudioQuality.Exhigh, store.quality)
        assertTrue(store.hapticsEnabled)
        assertTrue(store.searchHistory().isEmpty())
    }

    @Test
    fun searchHistoryRecordsAndClearsCorrectly() {
        val store = SettingsStore(context)
        store.recordSearch("First")
        store.recordSearch("Second")
        assertEquals(listOf("Second", "First"), store.searchHistory())

        store.clearSearchHistory()
        assertTrue(store.searchHistory().isEmpty())
    }
}
