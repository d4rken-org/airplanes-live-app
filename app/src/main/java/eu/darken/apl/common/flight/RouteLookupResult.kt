package eu.darken.apl.common.flight

sealed interface RouteLookupResult {
    data class Found(val route: FlightRoute) : RouteLookupResult

    /** No route exists or none can be asked for, asking again soon gives the same answer. */
    data object None : RouteLookupResult

    /** Unknown for now, worth asking again from [retryAtElapsed] on (`MonotonicClock` milliseconds). */
    data class Unavailable(val retryAtElapsed: Long) : RouteLookupResult
}
