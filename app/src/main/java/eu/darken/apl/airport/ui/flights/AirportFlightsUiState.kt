package eu.darken.apl.airport.ui.flights

import java.time.Instant

/** What the flights section of an airport's details shows. */
sealed interface AirportFlightsUiState {

    /** The airport is still being looked up. */
    data object Resolving : AirportFlightsUiState

    /** The lookup found no airport id to ask for. */
    data object NoList : AirportFlightsUiState

    /** Nothing loaded yet, loading is up to the user. */
    data object Ready : AirportFlightsUiState

    data object Loading : AirportFlightsUiState

    /**
     * [refreshAvailableAt] keeps refresh disabled until that moment, on top of [refreshEnabled].
     * [pagingDirections] are the wire directions whose next page is loading. [notice] is about the last action,
     * the list stays.
     */
    data class Loaded(
        val display: AirportFlightsDisplay,
        val refreshing: Boolean = false,
        val refreshEnabled: Boolean = true,
        val refreshAvailableAt: Instant? = null,
        val pagingDirections: Set<String> = emptySet(),
        val notice: Notice? = null,
    ) : AirportFlightsUiState

    data object NotOffered : AirportFlightsUiState

    data object UnknownAirport : AirportFlightsUiState

    data object AllowanceExhausted : AirportFlightsUiState

    /** Only a new load can answer. */
    data object ReloadOffered : AirportFlightsUiState

    /** Flight data is unavailable, another try follows at [until]. */
    data class Waiting(val until: ClockTime) : AirportFlightsUiState

    data class Failed(val error: Throwable, val canRetry: Boolean) : AirportFlightsUiState

    data object UpdateRequired : AirportFlightsUiState

    data object Restricted : AirportFlightsUiState

    data object Revoked : AirportFlightsUiState

    sealed interface Notice {
        data class Failed(val error: Throwable, val canRetry: Boolean) : Notice
        data object ReloadOffered : Notice
        data object AllowanceExhausted : Notice
        data class Waiting(val until: ClockTime) : Notice
    }
}
