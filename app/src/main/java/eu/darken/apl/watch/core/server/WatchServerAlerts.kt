package eu.darken.apl.watch.core.server

import eu.darken.apl.watch.core.types.Watch
import java.time.Instant

/** Posts what the server watch list reports; a post repeated for the same cause replaces the earlier one. */
interface WatchServerAlerts {

    /**
     * [aircraftIds] can be fewer than [totalMatching]. [observedAt] is when the server saw the match, if it said.
     * [firstCallsign] belongs to the first of [aircraftIds], if the server knew it.
     */
    suspend fun postAlert(
        watch: Watch,
        aircraftIds: List<String>,
        totalMatching: Int,
        observedAt: Instant?,
        firstCallsign: String?,
    )

    suspend fun postSummary(sightings: Int, watches: Int)
}
