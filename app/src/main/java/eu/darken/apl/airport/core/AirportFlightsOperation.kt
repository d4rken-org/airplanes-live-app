package eu.darken.apl.airport.core

import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.api.AirportFlightsRequest

/**
 * One deliberate load or page. Sending it again resends the identical [request] under the same id, which the
 * server answers from its replay store; that only works until [deadline], so after it a recovery is a new
 * operation. Made by [AirportFlightsRepo.newLoad] and [AirportFlightsRepo.newPage].
 */
class AirportFlightsOperation internal constructor(
    val kind: Kind,
    val request: AirportFlightsRequest,
) {

    sealed interface Kind {
        data class Load(val airportId: String) : Kind

        /** [direction] is the wire value, e.g. [eu.darken.apl.server.api.AirportFlightEntryDto.DIRECTION_ARRIVAL]. */
        data class Page(val evaluationId: String, val direction: String, val cursor: String) : Kind
    }

    val operationId: String get() = request.operationId

    /** Monotonic, set at the first transmission and never again. */
    @Volatile
    var firstSentAt: Long? = null
        private set

    /** Monotonic, the server's Retry-After for this operation: it must not go out again before then. */
    @Volatile
    var notBeforeElapsed: Long? = null
        internal set

    val deadline: Long?
        get() = firstSentAt?.plus(OperationStore.REPLAY_WINDOW.toMillis())

    /** Called right before a transmission; false means the replay window has passed and it must not go out. */
    @Synchronized
    internal fun claimSend(now: Long): Boolean {
        if (firstSentAt == null) firstSentAt = now
        return !isExpired(now)
    }

    fun isExpired(now: Long): Boolean = deadline?.let { now >= it } ?: false

    override fun toString(): String = "AirportFlightsOperation($operationId, $kind, firstSentAt=$firstSentAt)"
}
