package eu.darken.apl.server.api

import kotlinx.serialization.Serializable

/**
 * A load sets [airportId] and optionally [window]; a page sets only [page]. Unset fields are left out of the
 * body, never sent as null.
 */
@Serializable
data class AirportFlightsRequest(
    val operationId: String,
    val airportId: String? = null,
    val window: AirportFlightsWindowDto? = null,
    val page: AirportFlightsPageDto? = null,
)

/** Epoch milliseconds. */
@Serializable
data class AirportFlightsWindowDto(
    val from: Long,
    val to: Long,
)

/** [cursor] is a [AirportFlightsGroupDto.next] of the evaluation [evaluationId]. */
@Serializable
data class AirportFlightsPageDto(
    val evaluationId: String,
    val direction: String,
    val cursor: String,
)

/**
 * Only [OUTCOME_ANSWERED] carries the evaluation fields and the lists. [refreshAfter] is only set for a load.
 * A page answer holds only the paged direction in [groups] and [entries]; a load answer holds every direction
 * that has entries, each with its first page.
 *
 * [outcome] and every other open-set value stay plain strings, so a value this build does not know decodes
 * instead of failing the whole answer.
 */
@Serializable
data class AirportFlightsResponse(
    val operationId: String,
    val outcome: String,
    val replayed: Boolean = false,
    val charged: Boolean = false,
    val pass: AirportFlightsPassDto,
    val evaluationId: String? = null,
    val evaluatedAt: Long? = null,
    val expiresAt: Long? = null,
    val evaluationExpiresAt: Long? = null,
    val operationExpiresAt: Long? = null,
    val refreshAfter: Long? = null,
    val airport: AirportFlightsAirportDto? = null,
    val window: AirportFlightsWindowDto? = null,
    val sources: List<AirportFlightsSourceDto> = emptyList(),
    val groups: List<AirportFlightsGroupDto> = emptyList(),
    val entries: List<AirportFlightEntryDto> = emptyList(),
    val aircraft: List<AircraftObservation> = emptyList(),
    val usage: UsageUpdate,
) {
    companion object {
        const val OUTCOME_ANSWERED = "answered"
        const val OUTCOME_ALLOWANCE_EXHAUSTED = "allowance_exhausted"
        const val OUTCOME_RESULT_EXPIRED = "result_expired"
        const val OUTCOME_EVALUATION_EXPIRED = "evaluation_expired"
        const val OUTCOME_PASS_REQUIRED = "pass_required"
        const val OUTCOME_ACCESS_CHANGED = "access_changed"
    }
}

/** [airportId] and [validUntil] are only set while a pass exists. */
@Serializable
data class AirportFlightsPassDto(
    val scope: String,
    val revision: Long,
    val airportId: String? = null,
    val validUntil: Long? = null,
)

/** [timeZone] is an IANA zone name. */
@Serializable
data class AirportFlightsAirportDto(
    val id: String,
    val timeZone: String? = null,
    val elevationFt: Int? = null,
)

@Serializable
data class AirportFlightsSourceDto(
    val kind: String,
    val state: String,
) {
    companion object {
        const val KIND_KNOWN_ROUTES = "known_routes"
        const val KIND_CACHED_ROUTES = "cached_routes"
        const val KIND_NEARBY_TRAFFIC = "nearby_traffic"
        const val KIND_HISTORY = "history"
        const val KIND_SCHEDULES = "schedules"

        const val STATE_OK = "ok"
        const val STATE_PARTIAL = "partial"
        const val STATE_UNAVAILABLE = "unavailable"
        const val STATE_STALE = "stale"
    }
}

/** [next] is the cursor for the direction's next page. */
@Serializable
data class AirportFlightsGroupDto(
    val direction: String,
    val total: Int,
    val next: String? = null,
)

/** [aircraftId] refers to an [AirportFlightsResponse.aircraft] id, which may be missing from that list. */
@Serializable
data class AirportFlightEntryDto(
    val key: String,
    val direction: String,
    val status: String? = null,
    val estimated: Boolean? = null,
    val flight: AirportFlightDto? = null,
    val otherAirport: AirportFlightOtherAirportDto? = null,
    val times: AirportFlightTimesDto? = null,
    val aircraftId: String? = null,
    val evidence: List<String> = emptyList(),
) {
    companion object {
        const val DIRECTION_ARRIVAL = "arrival"
        const val DIRECTION_DEPARTURE = "departure"
        const val DIRECTION_UNKNOWN = "unknown"

        const val STATUS_ON_GROUND = "on_ground"
        const val STATUS_APPROACHING = "approaching"
        const val STATUS_DEPARTED = "departed"
        const val STATUS_EN_ROUTE = "en_route"
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_DELAYED = "delayed"
        const val STATUS_BOARDING = "boarding"
        const val STATUS_LANDED = "landed"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_DIVERTED = "diverted"

        const val EVIDENCE_KNOWN_ROUTE = "known_route"
        const val EVIDENCE_CACHED_ROUTE = "cached_route"
        const val EVIDENCE_FITTED_LEG = "fitted_leg"
        const val EVIDENCE_NEARBY_LOW = "nearby_low"
        const val EVIDENCE_ON_GROUND = "on_ground"
        const val EVIDENCE_DESCENDING = "descending"
        const val EVIDENCE_CLIMBING = "climbing"
        const val EVIDENCE_HISTORY = "history"
        const val EVIDENCE_SCHEDULE = "schedule"
    }
}

@Serializable
data class AirportFlightDto(
    val callsign: String? = null,
    val number: String? = null,
)

/** [id] is an [AirportSummaryDto.id]. */
@Serializable
data class AirportFlightOtherAirportDto(
    val id: String? = null,
    val ident: String? = null,
    val icao: String? = null,
    val iata: String? = null,
    val name: String? = null,
)

@Serializable
data class AirportFlightTimesDto(
    val scheduled: AirportFlightTimeDto? = null,
    val estimated: AirportFlightTimeDto? = null,
    val actual: AirportFlightTimeDto? = null,
)

/** [at] in epoch milliseconds. */
@Serializable
data class AirportFlightTimeDto(
    val at: Long,
    val basis: String? = null,
) {
    companion object {
        const val BASIS_GATE = "gate"
        const val BASIS_RUNWAY = "runway"
    }
}
