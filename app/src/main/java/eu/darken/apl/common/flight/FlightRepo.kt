package eu.darken.apl.common.flight

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flight.db.FlightDatabase
import eu.darken.apl.common.flight.db.FlightRouteEntity
import eu.darken.apl.common.geo.Spherical
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.RouteAirportDto
import eu.darken.apl.server.api.RouteLookupRequest
import eu.darken.apl.server.api.RouteLookupResponse
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightRepo @Inject constructor(
    private val flightDatabase: FlightDatabase,
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val accessRepo: AccessRepo,
    private val gate: RouteLookupGate,
    @param:AppScope private val appScope: CoroutineScope,
) {

    private val inFlight = mutableMapOf<RouteLookupGate.Key, Deferred<RouteLookupResult>>()

    fun prefetch(hex: AircraftHex, callsign: Callsign?) {
        appScope.launch { lookup(hex, callsign) }
    }

    suspend fun cleanup() {
        log(TAG) { "cleanup()" }
        val cutoff = Instant.now().minus(Duration.ofDays(30))
        flightDatabase.deleteOlderThan(cutoff)
    }

    suspend fun lookup(hex: AircraftHex, callsign: Callsign?): FlightRoute? =
        (lookupResult(hex, callsign) as? RouteLookupResult.Found)?.route

    suspend fun lookupResult(hex: AircraftHex, callsign: Callsign?): RouteLookupResult {
        val normalized = callsign?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return RouteLookupResult.None
        val key = RouteLookupGate.Key(hex.lowercase(), normalized)
        return try {
            // Shared in the app scope, so one caller leaving does not cancel the request for the others
            val deferred = synchronized(inFlight) {
                inFlight[key] ?: appScope.async(start = CoroutineStart.LAZY) { resolveLookup(hex, key) }.also { d ->
                    inFlight[key] = d
                    d.invokeOnCompletion { synchronized(inFlight) { if (inFlight[key] === d) inFlight.remove(key) } }
                }
            }
            deferred.start()
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "lookup(hex=$hex, callsign=$normalized) -> error: ${e.asLog()}" }
            RouteLookupResult.Unavailable(gate.now() + ERROR_RETRY_MS)
        }
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

    private suspend fun resolveLookup(hex: AircraftHex, key: RouteLookupGate.Key): RouteLookupResult = try {
        fetchAndPersist(hex, key)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ServerApiException) {
        log(TAG, WARN) { "${key.label()} -> error ${e.code} (HTTP ${e.status})" }
        RouteLookupResult.Unavailable(gate.now() + ERROR_RETRY_MS)
    } catch (e: Exception) {
        log(TAG, WARN) { "${key.label()} -> error ${e.javaClass.simpleName}: ${e.asLog()}" }
        RouteLookupResult.Unavailable(gate.now() + ERROR_RETRY_MS)
    }

    private suspend fun fetchAndPersist(hex: AircraftHex, key: RouteLookupGate.Key): RouteLookupResult {
        val cached = flightDatabase.getLatestByHex(hex)
        if (cached != null) {
            val age = Duration.between(cached.fetchedAt, Instant.now())
            if (age < CACHE_MAX_AGE && cached.callsign == key.callsign) {
                log(TAG) { "${key.label()} -> cache hit" }
                return RouteLookupResult.Found(resolve(cached))
            }
            log(TAG, VERBOSE) { "${key.label()} -> stale (age=$age, callsign changed=${cached.callsign != key.callsign})" }
        }

        if (!HEX_PATTERN.matches(key.hex) || !CALLSIGN_PATTERN.matches(key.callsign)) {
            log(TAG, DEBUG) { "${key.label()} -> skipped, not a valid hex and callsign" }
            return RouteLookupResult.None
        }
        if (gate.isRejected(key)) {
            log(TAG, DEBUG) { "${key.label()} -> skipped, the server rejected it before" }
            return RouteLookupResult.None
        }
        if (gate.isRememberedNone(key)) {
            log(TAG, DEBUG) { "${key.label()} -> skipped, the server had no route recently" }
            return RouteLookupResult.None
        }

        accessRepo.awaitLoaded()
        val access = accessRepo.state.value
        if (access != null && !access.offersRouteLookup) {
            log(TAG, DEBUG) { "${key.label()} -> skipped, not offered by the access policy" }
            return RouteLookupResult.None
        }

        awaitHolds(key)?.let { until ->
            log(TAG) { "${key.label()} -> held until $until" }
            return RouteLookupResult.Unavailable(until)
        }

        val attempt = sessionManager.authed<Attempt> { token ->
            // Acquiring the session can take a while, another lookup may have set a hold meanwhile
            val until = awaitHolds(key)
            if (until != null) return@authed Attempt.Held(until)
            try {
                Attempt.Answered(endpoint.route(token, RouteLookupRequest(key.hex, key.callsign)))
            } catch (e: ServerApiException) {
                // Left to authed, which refreshes the token or ends the session
                if (e.code in SESSION_CODES) throw e
                Attempt.Rejected(e)
            }
        }

        return when (attempt) {
            is Attempt.Held -> {
                log(TAG) { "${key.label()} -> held until ${attempt.until}" }
                RouteLookupResult.Unavailable(attempt.until)
            }

            is Attempt.Rejected -> onRejected(key, attempt.error)
            is Attempt.Answered -> onAnswer(hex, key, attempt.response)
        }
    }

    /**
     * Null when the request may be sent now. A short app-wide hold is waited out, after which all
     * holds are checked again.
     */
    private suspend fun awaitHolds(key: RouteLookupGate.Key): Long? {
        while (true) {
            gate.keyHold(key)?.let { return it }
            val until = gate.appHold() ?: return null
            val remaining = until - gate.now()
            if (remaining > MAX_HOLD_WAIT_MS) return until
            log(TAG, VERBOSE) { "${key.label()} -> waiting ${remaining}ms for the hold to pass" }
            delay(remaining)
        }
    }

    private fun onRejected(key: RouteLookupGate.Key, e: ServerApiException): RouteLookupResult {
        log(TAG, INFO) { "${key.label()} -> error ${e.code} (HTTP ${e.status}, retryAfter=${e.retryAfterSeconds})" }
        return when {
            e.code in APP_HOLD_CODES -> RouteLookupResult.Unavailable(gate.holdApp(e.retryAfterSeconds))
            e.code == ServerCodes.ROUTE_LOOKUP_UNAVAILABLE -> {
                RouteLookupResult.Unavailable(gate.holdKey(key, e.retryAfterSeconds))
            }

            e.code == ServerCodes.INVALID_REQUEST -> {
                gate.reject(key)
                RouteLookupResult.None
            }

            e.status in 400..499 && e.status != 408 && e.status != 429 -> RouteLookupResult.None
            else -> RouteLookupResult.Unavailable(gate.now() + ERROR_RETRY_MS)
        }
    }

    private suspend fun onAnswer(
        hex: AircraftHex,
        key: RouteLookupGate.Key,
        response: RouteLookupResponse,
    ): RouteLookupResult {
        val route = response.route
        if (response.status != RouteLookupResponse.STATUS_FOUND || route == null) {
            log(TAG) { "${key.label()} -> none (status=${response.status})" }
            gate.rememberNone(key)
            return RouteLookupResult.None
        }

        upsertAirport(route.origin)
        upsertAirport(route.destination)

        val now = Instant.now()
        val entity = FlightRouteEntity(
            aircraftHex = hex,
            callsign = key.callsign,
            originIcao = route.origin.icao,
            destinationIcao = route.destination.icao,
            seenAt = now,
            fetchedAt = now,
        )
        flightDatabase.insertRoute(entity)
        log(TAG) { "${key.label()} -> found via ${route.source}: ${route.origin.icao}-${route.destination.icao}" }
        return RouteLookupResult.Found(resolve(entity))
    }

    private suspend fun upsertAirport(airport: RouteAirportDto) {
        // Stored as a valid pair or not at all, a bad position would place the airport somewhere else
        val valid = Spherical.isValid(airport.latitude, airport.longitude)
        flightDatabase.upsertAirport(
            icao = airport.icao,
            iata = airport.iata,
            name = airport.name,
            country = airport.country,
            latitude = airport.latitude.takeIf { valid },
            longitude = airport.longitude.takeIf { valid },
        )
    }

    private fun RouteLookupGate.Key.label() = "lookup(hex=$hex, callsign=$callsign)"

    private sealed interface Attempt {
        data class Answered(val response: RouteLookupResponse) : Attempt
        data class Rejected(val error: ServerApiException) : Attempt
        data class Held(val until: Long) : Attempt
    }

    companion object {
        private val TAG = logTag("Flight", "Repo")
        private val CACHE_MAX_AGE = Duration.ofHours(1)
        private const val MAX_HOLD_WAIT_MS = 30_000L
        private const val ERROR_RETRY_MS = 5_000L
        private val HEX_PATTERN = Regex("~?[0-9a-f]{6}")
        private val CALLSIGN_PATTERN = Regex("[A-Z0-9]{1,8}")

        private val SESSION_CODES = setOf(
            ServerCodes.EXPIRED,
            ServerCodes.INVALID_TOKEN,
            ServerCodes.INSTALLATION_REVOKED,
        )

        /** Installation- or address-wide, every route lookup would get the same answer until the hint passes. */
        private val APP_HOLD_CODES = setOf(
            ServerCodes.INSTALLATION_RATE_EXCEEDED,
            ServerCodes.QUOTA_EXCEEDED,
            ServerCodes.REQUEST_TIMEOUT,
            ServerCodes.UPSTREAM_UNAVAILABLE,
            ServerCodes.DATABASE_UNAVAILABLE,
            ServerCodes.UPSTREAM_ERROR,
        )
    }
}
