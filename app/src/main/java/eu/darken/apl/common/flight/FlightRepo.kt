package eu.darken.apl.common.flight

import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.api.AdsbdbEndpoint
import eu.darken.apl.common.flight.api.HexdbEndpoint
import eu.darken.apl.common.flight.db.FlightDatabase
import eu.darken.apl.common.flight.db.FlightRouteEntity
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightRepo @Inject constructor(
    private val flightDatabase: FlightDatabase,
    private val adsbdbEndpoint: AdsbdbEndpoint,
    private val hexdbEndpoint: HexdbEndpoint,
) {

    suspend fun cleanup() {
        log(TAG) { "cleanup()" }
        val cutoff = Instant.now().minus(Duration.ofDays(30))
        flightDatabase.deleteOlderThan(cutoff)
    }

    suspend fun lookup(hex: AircraftHex, callsign: Callsign?): FlightRoute? {
        val trimmed = callsign?.trim()?.takeIf { it.isNotBlank() } ?: return null

        log(TAG) { "lookup(hex=$hex, callsign=$trimmed)" }

        val cached = flightDatabase.getLatestByHex(hex)
        if (cached != null) {
            val age = Duration.between(cached.fetchedAt, Instant.now())
            if (age < CACHE_MAX_AGE && cached.callsign == trimmed) {
                log(TAG, VERBOSE) { "lookup($hex) -> cache hit" }
                return resolve(cached)
            }
            log(TAG, VERBOSE) { "lookup($hex) -> stale (age=$age, callsign changed=${cached.callsign != trimmed})" }
        }

        return fetchAndPersist(hex, trimmed)
    }

    fun getByHex(hex: AircraftHex): Flow<FlightRoute?> {
        return flightDatabase.byHex(hex).map { entity ->
            entity?.let { resolve(it) }
        }
    }

    fun getByAirport(icaoCode: String): Flow<List<FlightRoute>> {
        return flightDatabase.byAirport(icaoCode).map { entities ->
            entities.map { resolve(it) }
        }
    }

    fun getByOrigin(icaoCode: String): Flow<List<FlightRoute>> {
        return flightDatabase.byOrigin(icaoCode).map { entities ->
            entities.map { resolve(it) }
        }
    }

    fun getByDestination(icaoCode: String): Flow<List<FlightRoute>> {
        return flightDatabase.byDestination(icaoCode).map { entities ->
            entities.map { resolve(it) }
        }
    }

    private suspend fun resolve(entity: FlightRouteEntity): FlightRoute {
        val origin = entity.originIcao?.let { flightDatabase.airportByIcao(it).first() }
        val destination = entity.destinationIcao?.let { flightDatabase.airportByIcao(it).first() }
        return entity.toDomain(origin, destination)
    }

    private suspend fun fetchAndPersist(hex: AircraftHex, callsign: String): FlightRoute? {
        val now = Instant.now()

        try {
            val data = adsbdbEndpoint.getByCallsign(callsign)
            if (data != null) {
                log(TAG, VERBOSE) { "fetchAndPersist($hex) -> adsbdb success" }

                data.origin?.icaoCode?.let { icao ->
                    flightDatabase.upsertAirport(
                        icao = icao,
                        iata = data.origin.iataCode,
                        name = data.origin.name,
                        country = data.origin.countryName,
                    )
                }
                data.destination?.icaoCode?.let { icao ->
                    flightDatabase.upsertAirport(
                        icao = icao,
                        iata = data.destination.iataCode,
                        name = data.destination.name,
                        country = data.destination.countryName,
                    )
                }

                val entity = FlightRouteEntity(
                    aircraftHex = hex,
                    callsign = callsign,
                    originIcao = data.origin?.icaoCode,
                    destinationIcao = data.destination?.icaoCode,
                    seenAt = now,
                    fetchedAt = now,
                )
                flightDatabase.insertRoute(entity)
                return resolve(entity)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "fetchAndPersist($hex) -> adsbdb failed: $e" }
        }

        try {
            val data = hexdbEndpoint.getByCallsign(callsign)
            if (data?.route != null) {
                log(TAG, VERBOSE) { "fetchAndPersist($hex) -> hexdb success: ${data.route}" }

                val parts = data.route.split("-", limit = 2)
                val originIcao = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                val destinationIcao = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

                originIcao?.let { flightDatabase.upsertAirport(icao = it, iata = null, name = null, country = null) }
                destinationIcao?.let {
                    flightDatabase.upsertAirport(icao = it, iata = null, name = null, country = null)
                }

                val entity = FlightRouteEntity(
                    aircraftHex = hex,
                    callsign = callsign,
                    originIcao = originIcao,
                    destinationIcao = destinationIcao,
                    seenAt = now,
                    fetchedAt = now,
                )
                flightDatabase.insertRoute(entity)
                return resolve(entity)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "fetchAndPersist($hex) -> hexdb failed: $e" }
        }

        log(TAG) { "fetchAndPersist($hex) -> both APIs failed" }
        return null
    }

    companion object {
        private val TAG = logTag("Flight", "Repo")
        private val CACHE_MAX_AGE = Duration.ofHours(1)
    }
}
