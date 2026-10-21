package eu.darken.apl.common.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * What another screen asked a tab to show, held until that tab's ViewModel takes it, even while the tab was
 * never opened. Requests published before the tab takes one replace each other: only the newest is applied.
 */
open class TabRequest<T : Any> {

    // Compared by identity, so asking for the same thing again is a new request
    private class Pending<T : Any>(val value: T)

    private val pending = MutableStateFlow<Pending<T>?>(null)

    fun publish(value: T) {
        pending.value = Pending(value)
    }

    /** The request waiting right now, taking it so [consumeEach] won't see it. */
    fun take(): T? = pending.value?.takeIf { pending.compareAndSet(it, null) }?.value

    suspend fun consumeEach(block: suspend (T) -> Unit) {
        pending.filterNotNull().collect { request ->
            if (pending.compareAndSet(request, null)) block(request.value)
        }
    }
}
