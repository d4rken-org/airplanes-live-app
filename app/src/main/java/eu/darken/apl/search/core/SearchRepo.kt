package eu.darken.apl.search.core

import android.location.Location
import androidx.core.text.isDigitsOnly
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.main.core.api.AirplanesLiveEndpoint
import eu.darken.apl.main.core.api.getByLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepo @Inject constructor(
    private val endpoint: AirplanesLiveEndpoint,
    private val aircraftRepo: AircraftRepo,
) {

    private data class FlowResult(
        val aircraft: Collection<Aircraft>?,
        val error: Throwable? = null
    )

    private fun safeFlow(block: suspend () -> Collection<Aircraft>): Flow<FlowResult> = flow<FlowResult> {
        emit(FlowResult(aircraft = block()))
    }.onStart { emit(FlowResult(aircraft = null)) }.catch { e ->
        log(TAG, ERROR) { "Search flow failed: ${e.asLog()}" }
        emit(FlowResult(aircraft = emptySet(), error = e))
    }

    data class Result(
        val aircraft: Collection<Aircraft>,
        val searching: Boolean,
        val errors: List<Throwable> = emptyList(),
    )

    suspend fun liveSearch(query: SearchQuery): Flow<Result> {
        log(TAG) { "liveSearch($query)" }

        val squawks = mutableSetOf<SquawkCode>()
        val hexes = mutableSetOf<AircraftHex>()
        val airframes = mutableSetOf<Airframe>()
        val callsigns = mutableSetOf<Callsign>()
        val registrations = mutableSetOf<Registration>()
        var searchMilitary = false
        var searchPia = false
        var searchLadd = false
        var location: Location? = null
        var locationRange: Long = 0

        when (query) {
            is SearchQuery.All -> {
                query.terms.forEach { term ->
                    when {
                        term.length == 4 && term.isDigitsOnly() -> squawks.add(term)
                        term.length == 6 && term.all { it.isLetterOrDigit() } -> hexes.add(term)
                        term.length <= 4 -> airframes.add(term)
                        term.length in 5..8 && term.any { it.isDigit() } -> registrations.add(term)
                        term.length in 5..8 -> callsigns.add(term)
                    }
                }
            }

            is SearchQuery.Hex -> {
                hexes.addAll(query.hexes)
            }

            is SearchQuery.Callsign -> {
                callsigns.addAll(query.signs)
            }

            is SearchQuery.Registration -> {
                registrations.addAll(query.regs)
            }

            is SearchQuery.Squawk -> {
                squawks.addAll(query.codes)
            }

            is SearchQuery.Airframe -> {
                airframes.addAll(query.types)
            }

            is SearchQuery.Interesting -> {
                searchMilitary = query.military
                searchLadd = query.ladd
                searchPia = query.pia
            }

            is SearchQuery.Position -> {
                location = query.location
                locationRange = query.rangeInMeter
            }
        }

        return combine(
            safeFlow { endpoint.getBySquawk(squawks) },
            safeFlow { endpoint.getByHex(hexes) },
            safeFlow { endpoint.getByAirframe(airframes) },
            safeFlow { endpoint.getByCallsign(callsigns) },
            safeFlow { endpoint.getByRegistration(registrations) },
            safeFlow { if (searchMilitary) endpoint.getMilitary() else emptySet() },
            safeFlow { if (searchLadd) endpoint.getLADD() else emptySet() },
            safeFlow { if (searchPia) endpoint.getPIA() else emptySet() },
            safeFlow { if (location != null) endpoint.getByLocation(location, locationRange) else emptySet() },
        ) { squawkRes, hexRes, airframeRes, callsignRes, registrationRes, militaryRes, laddRes, piaRes, locationRes ->
            val ac = mutableSetOf<Aircraft>()
            val errors = mutableListOf<Throwable>()

            listOf(squawkRes, hexRes, airframeRes, callsignRes, registrationRes, militaryRes, laddRes, piaRes, locationRes).forEach { res ->
                res.aircraft?.let { ac.addAll(it) }
                res.error?.let { errors.add(it) }
            }

            Result(
                aircraft = ac,
                searching = listOf(squawkRes, hexRes, airframeRes, callsignRes, registrationRes, militaryRes, laddRes, piaRes, locationRes)
                    .any { it.aircraft == null },
                errors = errors
            )
        }
            .onEach { result ->
                aircraftRepo.update(result.aircraft)
            }
            .catch {
                log(TAG, ERROR) { "liveSearch($query) failed:\n${it.asLog()}" }
                emit(Result(aircraft = emptySet(), searching = false, errors = listOf(it)))
            }
    }

    suspend fun search(query: SearchQuery): Result = liveSearch(query).last()

    companion object {
        private val TAG = logTag("Search", "Repo")
    }
}