package eu.darken.apl.search.ui

import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchLimitReachedException

sealed interface SearchEvents {
    data object RequestLocationPermission : SearchEvents
    data object LocationUnavailable : SearchEvents
    data object PlaceSearchUnavailable : SearchEvents
    data class PlaceNotFound(val place: String) : SearchEvents

    sealed interface WatchOutcome : SearchEvents

    /** [createdIds] are only the watches this add created, what an undo removes. */
    data class WatchesAdded(val createdIds: Set<WatchId>, val existingCount: Int) : WatchOutcome

    data class WatchLimitReached(val error: WatchLimitReachedException, val addedCount: Int) : WatchOutcome
}
