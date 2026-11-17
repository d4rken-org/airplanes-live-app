package eu.darken.apl.search.ui.actions

import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchLimitReachedException

sealed interface SearchActionEvents {
    data class WatchAdded(val watchId: WatchId, val label: String) : SearchActionEvents
    data class WatchLimitReached(val error: WatchLimitReachedException) : SearchActionEvents
}
