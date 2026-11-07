package dev.nocturn.app.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Per-destination UI state for one kind of detail route (playlist, album or artist), keyed by the
 * destination id.
 *
 * Every entry on the back stack keeps its own state, so popping back to an earlier page (or
 * previewing it under a predictive back gesture) shows that page instead of whichever page of the
 * same kind loaded last. [update] ignores ids without an entry, which drops late network responses
 * for pages that have already been pruned from the stack.
 */
class DetailStateStore<S> {
    private val mutable = MutableStateFlow<Map<Long, S>>(emptyMap())
    val states: StateFlow<Map<Long, S>> = mutable.asStateFlow()

    operator fun get(id: Long): S? = mutable.value[id]

    /** Creates or replaces the entry for [id]. */
    fun put(id: Long, state: S) {
        mutable.update { it + (id to state) }
    }

    /** Transforms the entry for [id]; does nothing when the entry has been pruned. */
    fun update(id: Long, transform: (S) -> S) {
        mutable.update { map ->
            val current = map[id] ?: return@update map
            map + (id to transform(current))
        }
    }

    /** Applies [transform] to every entry, e.g. to refresh liked or now-playing markers. */
    fun updateAll(transform: (S) -> S) {
        mutable.update { map -> map.mapValues { (_, state) -> transform(state) } }
    }

    /** Drops every entry whose id is not in [ids]. */
    fun retain(ids: Set<Long>) {
        mutable.update { map -> map.filterKeys { it in ids } }
    }
}
