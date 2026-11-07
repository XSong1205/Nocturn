package dev.nocturn.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for stacked detail pages: before per-id state, album 1 -> artist -> album 3 ->
 * back showed album 3's tracks on album 1's page, and a slow response for one page could overwrite
 * another page of the same kind.
 */
class DetailStateStoreTest {

    private val store = DetailStateStore<AlbumUiState>()

    @Test
    fun stackedPagesOfTheSameKindKeepTheirOwnState() {
        store.put(1, AlbumUiState(albumId = 1, loading = false))
        store.put(3, AlbumUiState(albumId = 3, loading = true))

        store.update(3) { it.copy(loading = false, error = "加载失败") }

        assertEquals(AlbumUiState(albumId = 1, loading = false), store[1])
        assertEquals("加载失败", store[3]?.error)
    }

    @Test
    fun lateResponseForAPrunedPageIsDropped() {
        store.put(1, AlbumUiState(albumId = 1, loading = true))
        store.retain(emptySet())

        store.update(1) { it.copy(loading = false) }

        assertNull(store[1])
        assertEquals(emptyMap<Long, AlbumUiState>(), store.states.value)
    }

    @Test
    fun retainKeepsOnlyIdsStillOnTheStack() {
        store.put(1, AlbumUiState(albumId = 1))
        store.put(2, AlbumUiState(albumId = 2))
        store.put(3, AlbumUiState(albumId = 3))

        store.retain(setOf(1, 3))

        assertEquals(setOf(1L, 3L), store.states.value.keys)
    }

    @Test
    fun updateAllRefreshesEveryEntry() {
        store.put(1, AlbumUiState(albumId = 1))
        store.put(2, AlbumUiState(albumId = 2))

        store.updateAll { it.copy(nowPlayingSongId = 42) }

        assertEquals(42L, store[1]?.nowPlayingSongId)
        assertEquals(42L, store[2]?.nowPlayingSongId)
    }

    @Test
    fun putReplacesAnExistingEntryForRetry() {
        store.put(7, AlbumUiState(albumId = 7, loading = false, error = "网络错误"))

        store.put(7, AlbumUiState(albumId = 7, loading = true))

        assertEquals(AlbumUiState(albumId = 7, loading = true), store[7])
    }
}
