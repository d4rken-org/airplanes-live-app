package eu.darken.apl.search.core

import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.query.QuerySnapshot
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerJson
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.SearchBatchRequest
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.UsageUpdate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.apl.server.api.SearchTerm as WireTerm

@Singleton
class SearchRepo @Inject constructor(
    private val aircraftRepo: AircraftRepo,
    private val accessRepo: AccessRepo,
    private val operationStore: OperationStore,
    private val serverClock: ServerClock,
    @param:ServerJson private val json: Json,
) {

    data class TermResult(
        val term: SearchTerm,
        val outcome: TermOutcome,
        val snapshot: QuerySnapshot?,
        val usage: UsageUpdate?,
    )

    data class SearchResult(
        val query: SearchQuery,
        val terms: List<TermResult> = emptyList(),
        val aircraft: List<Aircraft> = emptyList(),
        val cacheOnly: List<Aircraft> = emptyList(),
        val latestUsage: UsageUpdate? = null,
        val error: Throwable? = null,
    )

    /** Deliberate submit only, every term is charged against the daily allowance. */
    suspend fun search(query: SearchQuery): SearchResult {
        log(TAG) { "search($query)" }
        if (query.isEmpty) return SearchResult(query = query)

        // An operation the app sent but never applied was already charged, resubmitting pays twice
        val unapplied = operationStore.pending(OperationStore.Kind.SEARCH, serverClock.now()).toMutableList()

        val chunks = query.terms.chunked(AircraftRepo.MAX_BATCH_ITEMS).map { terms ->
            val items = terms.map { WireTerm(text = it.text, categories = it.categories.map { c -> c.wire }.sorted()) }
            val reusable = unapplied.firstOrNull { row ->
                runCatching { json.decodeFromString(SearchBatchRequest.serializer(), row.requestJson) }
                    .getOrNull()
                    ?.terms == items
            }
            unapplied.remove(reusable)
            AircraftRepo.Chunk(
                items = items,
                ownerIds = terms.map { it.id },
                operationId = reusable?.operationId,
            )
        }

        val results = try {
            aircraftRepo.search(chunks)
        } catch (e: ServerApiException) {
            log(TAG, WARN) { "Search failed: ${e.asLog()}" }
            val cached = cachedMatches(query)
            return SearchResult(query = query, cacheOnly = cached, aircraft = cached, error = e)
        } catch (e: IOException) {
            log(TAG, WARN) { "Search failed: ${e.asLog()}" }
            val cached = cachedMatches(query)
            return SearchResult(query = query, cacheOnly = cached, aircraft = cached, error = e)
        }

        val termResults = mutableListOf<TermResult>()
        var termIndex = 0
        results.forEach { batch ->
            batch.outcomes.forEach { outcome ->
                termResults.add(
                    TermResult(
                        term = query.terms[termIndex++],
                        outcome = outcome,
                        snapshot = batch.snapshot,
                        usage = batch.usage,
                    )
                )
            }
        }

        val answered = termResults
            .mapNotNull { it.outcome as? TermOutcome.Answered }
            .flatMap { it.aircraft }
            .distinctBy { it.hex }
        val answeredHexes = answered.map { it.hex.uppercase() }.toSet()

        // A complete answer is authoritative, cached extras would resurrect aircraft it excluded
        val unresolved = termResults
            .filterNot { it.outcome is TermOutcome.Answered && it.outcome.complete }
            .map { it.term }
        val extras = cachedMatches(SearchQuery(unresolved)).filter { it.hex.uppercase() !in answeredHexes }

        if (termResults.any { it.outcome is TermOutcome.Rejected && it.outcome.code == ServerCodes.TIER_RESTRICTED }) {
            accessRepo.refreshThrottled("search-restricted")
        }
        if (termResults.any { it.outcome is TermOutcome.Rejected && it.outcome.code == ServerCodes.DAILY_ALLOWANCE_EXHAUSTED }) {
            accessRepo.refreshThrottled("search-exhausted")
        }

        return SearchResult(
            query = query,
            terms = termResults,
            aircraft = answered + extras,
            cacheOnly = extras,
            latestUsage = results.lastOrNull()?.usage,
        )
    }

    /**
     * The server has no position search, this is a one shot viewing snapshot around a point, which
     * costs a viewing unit instead of a search term.
     */
    suspend fun nearby(latitude: Double, longitude: Double, radiusNm: Double): SearchResult {
        log(TAG) { "nearby($latitude, $longitude, $radiusNm)" }
        val maxRadius = accessRepo.state.value?.maxArRadiusNm?.toDouble() ?: DEFAULT_MAX_RADIUS_NM
        val term = SearchTerm(text = "$latitude,$longitude")
        val query = SearchQuery(listOf(term))

        return try {
            val snapshot = aircraftRepo.nearby(
                AircraftRepo.ViewingQuery.Ar(latitude, longitude, radiusNm.coerceIn(1.0, maxRadius))
            )
            SearchResult(
                query = query,
                terms = listOf(
                    TermResult(
                        term = term,
                        outcome = TermOutcome.Answered(
                            aircraft = snapshot.aircraft,
                            complete = snapshot.complete,
                            capped = snapshot.capped,
                            totalMatching = snapshot.totalMatching,
                            expiresAt = snapshot.snapshot.expiresAt,
                            charged = true,
                        ),
                        snapshot = snapshot.snapshot,
                        usage = snapshot.usage,
                    )
                ),
                aircraft = snapshot.aircraft,
                latestUsage = snapshot.usage,
            )
        } catch (e: ServerApiException) {
            log(TAG, WARN) { "Nearby search failed: ${e.asLog()}" }
            SearchResult(query = query, error = e)
        } catch (e: IOException) {
            log(TAG, WARN) { "Nearby search failed: ${e.asLog()}" }
            SearchResult(query = query, error = e)
        }
    }

    suspend fun cachedMatches(query: SearchQuery): List<Aircraft> {
        if (query.isEmpty) return emptyList()
        val cached = aircraftRepo.cache.first().values
        return cached
            .filter { aircraft -> query.terms.any { it.matches(aircraft) } }
            .distinctBy { it.hex }
    }

    private fun SearchTerm.matches(aircraft: Aircraft): Boolean {
        if (categories.isNotEmpty()) {
            val categoryMatch = categories.any {
                when (it) {
                    SearchCategory.MILITARY -> aircraft.military
                    SearchCategory.LADD -> aircraft.ladd
                    SearchCategory.PIA -> aircraft.pia
                }
            }
            if (!categoryMatch) return false
            if (text.isBlank()) return true
        }
        if (text.isBlank()) return false
        return listOf(
            aircraft.hex,
            aircraft.callsign,
            aircraft.registration,
            aircraft.airframe,
            aircraft.squawk,
        ).any { it?.equals(text, ignoreCase = true) == true }
    }

    companion object {
        private const val DEFAULT_MAX_RADIUS_NM = 25.0
        private val TAG = logTag("Search", "Repo")
    }
}
