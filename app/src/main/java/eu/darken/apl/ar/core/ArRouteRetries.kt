package eu.darken.apl.ar.core

import eu.darken.apl.common.flight.RouteLookupResult
import eu.darken.apl.main.core.aircraft.AircraftHex

class ArRouteRetries(private val maxAttempts: Int = 3) {

    private val entries = mutableMapOf<AircraftHex, Entry>()

    /** Records a lookup start for [hex] and returns true if one may start at [nowMs]. */
    @Synchronized
    fun tryStart(hex: AircraftHex, nowMs: Long): Boolean {
        val entry = entries[hex]
        if (entry != null) {
            val status = entry.status
            if (status !is Status.RetryAt) return false
            if (nowMs < status.atMs || entry.attempts >= maxAttempts) return false
        }
        entries[hex] = Entry(attempts = (entry?.attempts ?: 0) + 1, status = Status.InFlight)
        return true
    }

    @Synchronized
    fun onResult(hex: AircraftHex, result: RouteLookupResult) {
        val entry = entries[hex] ?: return
        val status = when (result) {
            is RouteLookupResult.Unavailable -> Status.RetryAt(result.retryAtElapsed)
            is RouteLookupResult.Found, RouteLookupResult.None -> Status.Final
        }
        entries[hex] = entry.copy(status = status)
    }

    private data class Entry(val attempts: Int, val status: Status)

    private sealed interface Status {
        data object InFlight : Status
        data object Final : Status
        data class RetryAt(val atMs: Long) : Status
    }
}
