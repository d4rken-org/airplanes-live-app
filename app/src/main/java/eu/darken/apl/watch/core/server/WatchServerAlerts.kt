package eu.darken.apl.watch.core.server

import eu.darken.apl.watch.core.types.Watch

/** Posts what the server watch list reports; a post repeated for the same cause replaces the earlier one. */
interface WatchServerAlerts {

    /** [aircraftIds] can be fewer than [totalMatching]. */
    suspend fun postAlert(watch: Watch, aircraftIds: List<String>, totalMatching: Int)

    suspend fun postSummary(sightings: Int, watches: Int)
}
