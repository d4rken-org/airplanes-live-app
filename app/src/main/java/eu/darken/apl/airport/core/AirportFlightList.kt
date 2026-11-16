package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AircraftObservation
import eu.darken.apl.server.api.AirportFlightEntryDto
import eu.darken.apl.server.api.AirportFlightsResponse
import eu.darken.apl.server.api.AirportFlightsSourceDto
import java.time.Instant

/**
 * An answered flight list with the pages loaded so far. [directions] holds every wire direction, including ones
 * this build does not know, in the order the server grouped them; directions that only appear on entries follow.
 * Entries keep the server's order and are unique by key within their direction.
 */
data class AirportFlightList(
    val evaluationId: String,
    val evaluatedAt: Instant,
    val expiresAt: Instant?,
    val evaluationExpiresAt: Instant?,
    val airport: AirportInfo?,
    val sources: List<AirportFlightsSourceDto>,
    val directions: List<Direction>,
    val aircraft: Map<String, Aircraft>,
) {
    data class AirportInfo(
        val id: String,
        val timeZone: String?,
        val elevationFt: Int?,
    )

    /** [total] and [next] are null for a direction the server sent entries for but no group. */
    data class Direction(
        val direction: String,
        val total: Int?,
        val next: String?,
        val entries: List<AirportFlightEntryDto>,
    )

    /** [id] is the aircraft's hex, [type] its ICAO type designator, e.g. "A20N". */
    data class Aircraft(
        val id: String,
        val registration: String?,
        val type: String?,
    )

    fun direction(direction: String): Direction? = directions.firstOrNull { it.direction == direction }

    /**
     * Adds a page answer for [direction] to the list, null when it belongs to another evaluation and has to be
     * discarded. Entries already listed under their direction are skipped, the page's groups replace their
     * direction's [Direction.total] and [Direction.next]. Without a group for [direction] that direction has no
     * further page.
     */
    fun appendPage(response: AirportFlightsResponse, direction: String): AirportFlightList? {
        if (response.outcome != AirportFlightsResponse.OUTCOME_ANSWERED) return null
        if (response.evaluationId != evaluationId) return null

        val merged = directions.associateByTo(LinkedHashMap()) { it.direction }
        response.groups.forEach { group ->
            val current = merged[group.direction]
            merged[group.direction] = current?.copy(total = group.total, next = group.next)
                ?: Direction(group.direction, group.total, group.next, emptyList())
        }
        if (response.groups.none { it.direction == direction }) {
            merged[direction]?.let { merged[direction] = it.copy(next = null) }
        }
        response.entries.groupBy { it.direction }.forEach { (direction, entries) ->
            val current = merged[direction] ?: Direction(direction, total = null, next = null, entries = emptyList())
            merged[direction] = current.copy(entries = (current.entries + entries).distinctBy { it.key })
        }

        return copy(
            directions = merged.values.toList(),
            aircraft = aircraft + response.aircraft.toAircraft(),
        )
    }

    companion object {
        /** Null when [response] is not an answered list. */
        fun from(response: AirportFlightsResponse): AirportFlightList? {
            if (response.outcome != AirportFlightsResponse.OUTCOME_ANSWERED) return null
            val evaluationId = response.evaluationId ?: return null
            val evaluatedAt = response.evaluatedAt ?: return null

            val byDirection = response.entries.groupBy { it.direction }
            val grouped = response.groups
                .distinctBy { it.direction }
                .map { group ->
                    Direction(
                        direction = group.direction,
                        total = group.total,
                        next = group.next,
                        entries = byDirection[group.direction].orEmpty().distinctBy { it.key },
                    )
                }
            val groupedNames = grouped.map { it.direction }.toSet()
            val ungrouped = byDirection
                .filterKeys { it !in groupedNames }
                .map { (direction, entries) ->
                    Direction(direction, total = null, next = null, entries = entries.distinctBy { it.key })
                }

            return AirportFlightList(
                evaluationId = evaluationId,
                evaluatedAt = Instant.ofEpochMilli(evaluatedAt),
                expiresAt = response.expiresAt?.let { Instant.ofEpochMilli(it) },
                evaluationExpiresAt = response.evaluationExpiresAt?.let { Instant.ofEpochMilli(it) },
                airport = response.airport?.let { AirportInfo(it.id, it.timeZone, it.elevationFt) },
                sources = response.sources,
                directions = grouped + ungrouped,
                aircraft = response.aircraft.toAircraft(),
            )
        }

        private fun List<AircraftObservation>.toAircraft(): Map<String, Aircraft> =
            associate {
                it.id to Aircraft(
                    id = it.id,
                    registration = it.registration,
                    type = it.aircraftType?.trim()?.takeIf { type -> type.isNotEmpty() },
                )
            }
    }
}
