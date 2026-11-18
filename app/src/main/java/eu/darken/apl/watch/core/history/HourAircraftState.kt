package eu.darken.apl.watch.core.history

import eu.darken.apl.main.core.aircraft.AircraftHex
import java.time.Instant

/** An aircraft that passed a watch within one hour, as the server answered it. */
data class HourAircraft(
    val hex: AircraftHex,
    val lastSeenAt: Instant,
    val callsign: String? = null,
    val registration: String? = null,
    val aircraftType: String? = null,
    val airlineName: String? = null,
)

/** How much of the hour the server could evaluate, worst first. */
enum class HourCoverage {
    NONE,
    PARTIAL,
    FULL,
    ;

    companion object {
        // A value this app does not know claims neither a complete hour nor an empty one
        fun of(value: String): HourCoverage = when (value) {
            "full" -> FULL
            "none" -> NONE
            else -> PARTIAL
        }
    }
}

sealed interface HourAircraftState {

    /** The pages received so far, another one follows. */
    data class Loading(
        val aircraft: List<HourAircraft>,
        val provisional: Boolean,
        val coverage: HourCoverage,
    ) : HourAircraftState

    /** [provisional]: the server may still add to the hour. [coverage] is the worst any page reported. */
    data class Complete(
        val aircraft: List<HourAircraft>,
        val provisional: Boolean,
        val coverage: HourCoverage,
    ) : HourAircraftState

    /** The watch was deleted or edited on the server, or the installation changed; the identity is outdated. */
    data object Changed : HourAircraftState

    data class NotAvailable(val reason: Reason) : HourAircraftState {
        enum class Reason {
            /** The hour is outside the range the server keeps. */
            OUT_OF_RANGE,
            TIER_RESTRICTED,
            INSTALLATION_RESTRICTED,
            INSTALLATION_REVOKED,
            LIST_DELETED,
        }
    }

    data class Failed(val error: Throwable) : HourAircraftState
}
