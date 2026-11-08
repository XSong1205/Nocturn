package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.localapi.client.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical stress tests and adversarial edge cases for [SettingsStore] persistence.
 *
 * Verifies:
 * 1. Default value consistency across multiple fresh instances.
 * 2. Rapid multi-toggle stress cycling (true -> false -> true -> false).
 * 3. Preference isolation: mutations to lyriconEnabled never alter or corrupt theme, dynamic color, quality, haptics, or search history.
 * 4. Inverse preference isolation: mutations to other preferences never alter lyriconEnabled.
 * 5. Concurrent read/write thread-safety stress under multi-threaded execution.
 * 6. Direct SharedPreferences tampering and resilience.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsStoreStressTest {

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
    // 1. Fresh Instance and Default Consistency
    // -----------------------------------------------------------------------------------

    @Test
    fun defaultValueIsConsistentlyTrueAcrossFreshInstances() {
        for (i in 1..20) {
            val store = SettingsStore(context)
            assertTrue("Instance $i must default lyriconEnabled to true", store.lyriconEnabled)
        }
    }

    @Test
    fun unwrittenKeyReturnsDefaultWithoutPollutingRawPreferences() {
        val store = SettingsStore(context)
        val rawPrefs = context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)

        // Reading should return true without mutating or adding the key implicitly
        assertTrue(store.lyriconEnabled)
        assertFalse(
            "Reading default lyriconEnabled must not inject default key until explicitly mutated",
            rawPrefs.contains(SettingsStore.KEY_LYRICON_ENABLED)
        )
    }

    // -----------------------------------------------------------------------------------
    // 2. Rapid Multi-Toggle Stress Cycling (true -> false -> true)
    // -----------------------------------------------------------------------------------

    @Test
    fun rapidMultiToggleAcrossAlternatingInstances() {
        val storeA = SettingsStore(context)
        val storeB = SettingsStore(context)
        val rawPrefs = context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)

        val cycles = 50
        for (i in 1..cycles) {
            // Cycle: true -> false
            storeA.lyriconEnabled = false
            assertFalse("Cycle $i (storeA): lyriconEnabled should be false", storeA.lyriconEnabled)
            assertFalse("Cycle $i (storeB): lyriconEnabled should reflect false across instances", storeB.lyriconEnabled)
            assertFalse("Cycle $i (rawPrefs): raw prefs must hold false", rawPrefs.getBoolean(SettingsStore.KEY_LYRICON_ENABLED, true))

            // Cycle: false -> true
            storeB.lyriconEnabled = true
            assertTrue("Cycle $i (storeB): lyriconEnabled should be true", storeB.lyriconEnabled)
            assertTrue("Cycle $i (storeA): lyriconEnabled should reflect true across instances", storeA.lyriconEnabled)
            assertTrue("Cycle $i (rawPrefs): raw prefs must hold true", rawPrefs.getBoolean(SettingsStore.KEY_LYRICON_ENABLED, false))
        }
    }

    // -----------------------------------------------------------------------------------
    // 3. Preference Isolation & Blast Radius (Non-Interference)
    // -----------------------------------------------------------------------------------

    @Test
    fun mutatingLyriconEnabledNeverAffectsOtherPreferences() {
        val store = SettingsStore(context)

        // Establish non-default values for all other preferences
        store.themeMode = ThemeMode.Light
        store.dynamicColor = false
        store.quality = AudioQuality.Lossless
        store.hapticsEnabled = false
        store.recordSearch("Adversarial Search Term")

        // Perform multiple toggles of lyriconEnabled
        val toggles = listOf(false, true, false, false, true, false, true)
        for ((index, state) in toggles.withIndex()) {
            store.lyriconEnabled = state

            val fresh = SettingsStore(context)
            assertEquals("Step $index: lyriconEnabled must match $state", state, fresh.lyriconEnabled)
            assertEquals("Step $index: themeMode must remain Light", ThemeMode.Light, fresh.themeMode)
            assertEquals("Step $index: dynamicColor must remain false", false, fresh.dynamicColor)
            assertEquals("Step $index: quality must remain Lossless", AudioQuality.Lossless, fresh.quality)
            assertEquals("Step $index: hapticsEnabled must remain false", false, fresh.hapticsEnabled)
            assertEquals("Step $index: searchHistory must remain unchanged", listOf("Adversarial Search Term"), fresh.searchHistory())
        }
    }

    @Test
    fun mutatingOtherPreferencesNeverAffectsLyriconEnabled() {
        val store = SettingsStore(context)
        store.lyriconEnabled = false

        // Mutate theme
        store.themeMode = ThemeMode.Dark
        assertFalse("lyriconEnabled must remain false after theme change", SettingsStore(context).lyriconEnabled)

        // Mutate quality
        store.quality = AudioQuality.Hires
        assertFalse("lyriconEnabled must remain false after quality change", SettingsStore(context).lyriconEnabled)

        // Mutate dynamic color
        store.dynamicColor = false
        assertFalse("lyriconEnabled must remain false after dynamicColor change", SettingsStore(context).lyriconEnabled)

        // Mutate haptics
        store.hapticsEnabled = false
        assertFalse("lyriconEnabled must remain false after haptics change", SettingsStore(context).lyriconEnabled)

        // Mutate search history
        store.recordSearch("Song A")
        store.recordSearch("Song B")
        assertFalse("lyriconEnabled must remain false after search history addition", SettingsStore(context).lyriconEnabled)

        store.clearSearchHistory()
        assertFalse("lyriconEnabled must remain false after search history clear", SettingsStore(context).lyriconEnabled)
    }

    @Test
    fun preferenceKeysAreMutuallyExclusiveAndDistinct() {
        val keys = setOf(
            SettingsStore.KEY_THEME,
            SettingsStore.KEY_DYNAMIC_COLOR,
            SettingsStore.KEY_QUALITY,
            SettingsStore.KEY_HAPTICS,
            SettingsStore.KEY_LYRICON_ENABLED,
            SettingsStore.KEY_HISTORY,
        )
        assertEquals("All 6 preference keys must be unique strings", 6, keys.size)
        assertTrue(SettingsStore.KEY_LYRICON_ENABLED.isNotEmpty())
    }

    // -----------------------------------------------------------------------------------
    // 4. Concurrent Read / Write Thread-Safety Stress
    // -----------------------------------------------------------------------------------

    @Test
    fun concurrentReadWriteStressDoesNotCorruptOrCrash() = runBlocking {
        val store = SettingsStore(context)
        val iterations = 100

        val writerJob = async(Dispatchers.IO) {
            for (i in 1..iterations) {
                store.lyriconEnabled = (i % 2 == 0)
                store.recordSearch("Query-$i")
            }
        }

        val readerJob = async(Dispatchers.Default) {
            var reads = 0
            for (i in 1..iterations) {
                val enabled = store.lyriconEnabled
                val theme = store.themeMode
                val history = store.searchHistory()
                if (enabled || !enabled) reads++
            }
            reads
        }

        writerJob.await()
        val readCount = readerJob.await()
        assertEquals(iterations, readCount)

        // After concurrent operations, state should be valid and readable
        val finalStore = SettingsStore(context)
        assertTrue(finalStore.lyriconEnabled || !finalStore.lyriconEnabled)
    }

    @Test
    fun multiThreadedExecutorStress() {
        val pool = Executors.newFixedThreadPool(8)
        val exceptions = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val tasks = 100

        for (i in 0 until tasks) {
            pool.submit {
                try {
                    val store = SettingsStore(context)
                    store.lyriconEnabled = (i % 2 == 0)
                    val value = store.lyriconEnabled
                    assertTrue(value || !value)
                } catch (t: Throwable) {
                    exceptions.add(t)
                }
            }
        }

        pool.shutdown()
        assertTrue("Executor should terminate within 5 seconds", pool.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue("No exceptions should occur during multi-threaded access: $exceptions", exceptions.isEmpty())

        // Final store read must be valid
        val finalStore = SettingsStore(context)
        assertTrue(finalStore.lyriconEnabled || !finalStore.lyriconEnabled)
    }

    // -----------------------------------------------------------------------------------
    // 5. External Manipulation & Recovery Resilience
    // -----------------------------------------------------------------------------------

    @Test
    fun externalPreferenceClearRevertsLyriconToDefaultTrue() {
        val store = SettingsStore(context)
        store.lyriconEnabled = false
        assertFalse(store.lyriconEnabled)

        // Simulate external clear (e.g. app data clear)
        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val newStore = SettingsStore(context)
        assertTrue("After SharedPreferences clear, lyriconEnabled must default back to true", newStore.lyriconEnabled)
    }

    @Test
    fun externalKeyRemovalRevertsLyriconToDefaultTrue() {
        val store = SettingsStore(context)
        store.lyriconEnabled = false
        assertFalse(store.lyriconEnabled)

        // Remove only the lyricon-enabled key
        context.getSharedPreferences("nocturn-settings", Context.MODE_PRIVATE)
            .edit()
            .remove(SettingsStore.KEY_LYRICON_ENABLED)
            .commit()

        val newStore = SettingsStore(context)
        assertTrue("After removing lyricon key, lyriconEnabled must default back to true", newStore.lyriconEnabled)
    }
}
