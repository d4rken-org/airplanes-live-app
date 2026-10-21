package eu.darken.apl.common.navigation

import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.search.ui.DestinationSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

/** What another screen asked a tab to show, held until that tab's ViewModel takes it. */
@Singleton
class TabRequests @Inject constructor() {
    val map = TabRequest<MapOptions>()
    val search = TabRequest<DestinationSearch>()
}

/**
 * Holds the newest request until the tab takes it, even while the tab was never opened.
 * Requests published before the tab takes one replace each other: only the newest is applied.
 */
class TabRequest<T : Any> {

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
