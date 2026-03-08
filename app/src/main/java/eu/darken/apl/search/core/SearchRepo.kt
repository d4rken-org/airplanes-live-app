package eu.darken.apl.search.core

import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.api.AirplanesLiveEndpoint
import eu.darken.apl.main.core.api.getByLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepo @Inject constructor(
    private val endpoint: AirplanesLiveEndpoint,
    private val aircraftRepo: AircraftRepo,
) {

    enum class CachePolicy {
        API_ONLY,
        CACHE_FIRST_UI,
    }

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
        val cacheOnlyCount: Int = 0,
    )

    private suspend fun searchCache(buckets: SearchBuckets): List<Aircraft> {
        if (!buckets.isCacheSupported) return emptyList()

        val hasAnyTerms = buckets.hexes.isNotEmpty() ||
                buckets.callsigns.isNotEmpty() ||
                buckets.registrations.isNotEmpty() ||
                buckets.squawks.isNotEmpty() ||
                buckets.airframes.isNotEmpty()
        if (!hasAnyTerms) return emptyList()

        val allAircraft = aircraftRepo.aircraft.first()

        return allAircraft.values.filter { ac ->
            val matchesHex = buckets.hexes.isEmpty() ||
                    buckets.hexes.any { it.equals(ac.hex, ignoreCase = true) }
            val matchesCallsign = buckets.callsigns.isEmpty() ||
                    buckets.callsigns.any { ac.callsign?.equals(it, ignoreCase = true) == true }
            val matchesRegistration = buckets.registrations.isEmpty() ||
                    buckets.registrations.any { ac.registration?.equals(it, ignoreCase = true) == true }
            val matchesSquawk = buckets.squawks.isEmpty() ||
                    buckets.squawks.any { it.equals(ac.squawk, ignoreCase = true) }
            val matchesAirframe = buckets.airframes.isEmpty() ||
                    buckets.airframes.any { ac.airframe?.equals(it, ignoreCase = true) == true }

            // OR across populated buckets
            val populatedChecks = mutableListOf<Boolean>()
            if (buckets.hexes.isNotEmpty()) populatedChecks.add(matchesHex)
            if (buckets.callsigns.isNotEmpty()) populatedChecks.add(matchesCallsign)
            if (buckets.registrations.isNotEmpty()) populatedChecks.add(matchesRegistration)
            if (buckets.squawks.isNotEmpty()) populatedChecks.add(matchesSquawk)
            if (buckets.airframes.isNotEmpty()) populatedChecks.add(matchesAirframe)

            populatedChecks.any { it }
        }.distinctBy { it.hex }
    }

    private fun buildApiFlow(buckets: SearchBuckets): Flow<Result> {
        return combine(
            safeFlow { endpoint.getBySquawk(buckets.squawks) },
            safeFlow { endpoint.getByHex(buckets.hexes) },
            safeFlow { endpoint.getByAirframe(buckets.airframes) },
            safeFlow { endpoint.getByCallsign(buckets.callsigns) },
            safeFlow { endpoint.getByRegistration(buckets.registrations) },
            safeFlow { if (buckets.military) endpoint.getMilitary() else emptySet() },
            safeFlow { if (buckets.ladd) endpoint.getLADD() else emptySet() },
            safeFlow { if (buckets.pia) endpoint.getPIA() else emptySet() },
            safeFlow { if (buckets.location != null) endpoint.getByLocation(buckets.location, buckets.locationRange) else emptySet() },
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
                log(TAG, ERROR) { "buildApiFlow failed:\n${it.asLog()}" }
                emit(Result(aircraft = emptySet(), searching = false, errors = listOf(it)))
            }
    }

    suspend fun liveSearch(query: SearchQuery, cachePolicy: CachePolicy = CachePolicy.API_ONLY): Flow<Result> {
        log(TAG) { "liveSearch($query, $cachePolicy)" }

        val buckets = query.toBuckets()

        val apiFlow = buildApiFlow(buckets)

        return when (cachePolicy) {
            CachePolicy.API_ONLY -> apiFlow

            CachePolicy.CACHE_FIRST_UI -> {
                val cached = searchCache(buckets)
                flow {
                    if (cached.isNotEmpty()) {
                        emit(Result(aircraft = cached, searching = true, cacheOnlyCount = cached.size))
                    }
                    apiFlow.collect { apiResult ->
                        val merged = if (apiResult.searching || apiResult.aircraft.isNotEmpty()) {
                            val apiByHex = apiResult.aircraft.associateBy { it.hex }
                            val cacheExtras = cached.filter { it.hex !in apiByHex }
                            apiResult.copy(
                                aircraft = apiResult.aircraft + cacheExtras,
                                cacheOnlyCount = cacheExtras.size,
                            )
                        } else if (apiResult.errors.isNotEmpty() && cached.isNotEmpty()) {
                            Result(
                                aircraft = cached,
                                searching = false,
                                errors = apiResult.errors,
                                cacheOnlyCount = cached.size,
                            )
                        } else {
                            apiResult
                        }
                        emit(merged)
                    }
                }
            }
        }
    }

    suspend fun search(query: SearchQuery): Result = liveSearch(query, CachePolicy.API_ONLY).last()

    companion object {
        private val TAG = logTag("Search", "Repo")
    }
}
