package eu.darken.apl.main.core

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.aircraft.toAircraft
import eu.darken.apl.main.core.db.toAircraft
import eu.darken.apl.main.core.query.BatchResult
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.main.core.query.ViewingSnapshot
import eu.darken.apl.main.core.query.WatchOutcome
import eu.darken.apl.main.core.query.toSnapshot
import eu.darken.apl.main.core.query.toTermResults
import eu.darken.apl.main.core.query.toWatchResults
import eu.darken.apl.main.core.request.Bucket
import eu.darken.apl.main.core.request.OperationRunner
import eu.darken.apl.main.core.request.OperationStore
import eu.darken.apl.main.core.request.RequestCoordinator
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.ServerJson
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ArRequest
import eu.darken.apl.server.api.BatchResponse
import eu.darken.apl.server.api.MapRequest
import eu.darken.apl.server.api.SearchBatchRequest
import eu.darken.apl.server.api.SearchTerm
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.WatchBatchRequest
import eu.darken.apl.server.api.WatchDefinition
import eu.darken.apl.server.identity.newOperationId
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class AircraftRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val aircraftDatabase: AircraftDatabase,
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val accessRepo: AccessRepo,
    private val requestCoordinator: RequestCoordinator,
    private val operationRunner: OperationRunner,
    private val operationStore: OperationStore,
    private val serverClock: ServerClock,
    @param:ServerJson private val json: Json,
) {

    val cache: Flow<Map<AircraftHex, Aircraft>> = aircraftDatabase.current()
        .map { entities -> entities.map { it.toAircraft() }.associateBy { it.hex } }
        .replayingShare(appScope)

    sealed interface ViewingQuery {
        data class Ar(val latitude: Double, val longitude: Double, val radiusNm: Double) : ViewingQuery

        data class Map(
            val south: Double,
            val north: Double,
            val west: Double,
            val east: Double,
            val selected: String? = null,
        ) : ViewingQuery
    }

    sealed interface ViewingState {
        data class Waiting(val reason: Reason) : ViewingState

        /** [error] is set when the last poll failed, the previous snapshot is kept. */
        data class Snapshot(val value: ViewingSnapshot, val error: Throwable? = null) : ViewingState

        sealed interface Reason {
            data object AccessUnknown : Reason
            data object Offline : Reason
            data class Exhausted(val resetsAt: Instant) : Reason
            data object Restricted : Reason
            data object Revoked : Reason
        }
    }

    /** One batch operation worth of items plus the local ids they belong to, in submission order. */
    data class Chunk<T>(
        val items: List<T>,
        val ownerIds: List<String>,
        val operationId: String? = null,
    )

    private val viewingLock = Mutex()
    private var viewingJob: Job? = null

    @Volatile private var viewingSession = 0L

    suspend fun update(aircraft: Collection<Aircraft>) {
        if (aircraft.isEmpty()) return
        log(TAG, VERBOSE) { "update(aircraft=${aircraft.size})" }
        aircraftDatabase.update(aircraft)
    }

    /**
     * Polls the server for the current viewing query. Only one viewing screen may run at a time,
     * so starting a new collection stops the previous loop before its own first request.
     */
    fun viewing(queries: Flow<ViewingQuery>): Flow<ViewingState> = channelFlow {
        val previous = viewingLock.withLock {
            viewingJob?.also { it.cancel() }
        }
        previous?.join()

        val sessionId = viewingLock.withLock {
            viewingSession += 1
            viewingSession
        }

        val latest = queries.stateIn(this, SharingStarted.Eagerly, null)
        val job = launch { runViewingLoop(sessionId, latest) { send(it) } }
        viewingLock.withLock { viewingJob = job }

        awaitClose { job.cancel() }
    }

    private suspend fun runViewingLoop(
        sessionId: Long,
        latest: StateFlow<ViewingQuery?>,
        emit: suspend (ViewingState) -> Unit,
    ) {
        var lastSnapshot: ViewingSnapshot? = null
        var backoffMillis = INITIAL_BACKOFF_MS
        var sequence = 0L
        var emittedSequence = 0L

        while (currentCoroutineContext().isActive && viewingSession == sessionId) {
            val access = accessRepo.state.value
            if (access == null) {
                emit(ViewingState.Waiting(ViewingState.Reason.AccessUnknown))
                accessRepo.state.filterNotNull().first()
                continue
            }
            if (access.restricted) {
                emit(ViewingState.Waiting(ViewingState.Reason.Restricted))
                return
            }
            val query = latest.value
            if (query == null) {
                latest.filterNotNull().first()
                continue
            }

            val startedAtElapsed = serverClock.elapsed()
            val ownSequence = ++sequence
            try {
                val response = requestCoordinator.execute(Bucket.VIEWING) {
                    sessionManager.authed { token ->
                        when (query) {
                            is ViewingQuery.Ar -> endpoint.ar(
                                token,
                                ArRequest(query.latitude, query.longitude, query.radiusNm),
                            )

                            is ViewingQuery.Map -> endpoint.map(
                                token,
                                MapRequest(query.south, query.north, query.west, query.east, query.selected),
                            )
                        }
                    }
                }
                if (viewingSession != sessionId) return

                val snapshot = response.toSnapshot(serverClock)
                accessRepo.applyUsage(snapshot.usage)
                aircraftDatabase.update(snapshot.aircraft)

                if (ownSequence > emittedSequence) {
                    emittedSequence = ownSequence
                    lastSnapshot = snapshot
                    emit(ViewingState.Snapshot(snapshot))
                }
                backoffMillis = INITIAL_BACKOFF_MS
            } catch (e: SessionRevokedException) {
                emit(ViewingState.Waiting(ViewingState.Reason.Revoked))
                return
            } catch (e: ServerApiException) {
                when (e.code) {
                    ServerCodes.DAILY_ALLOWANCE_EXHAUSTED -> {
                        emit(ViewingState.Waiting(ViewingState.Reason.Exhausted(access.resetsAt)))
                        accessRepo.refreshThrottled("viewing-exhausted")
                        awaitNewAllowance(access.resetsAt)
                        continue
                    }

                    ServerCodes.INSTALLATION_RESTRICTED -> {
                        emit(ViewingState.Waiting(ViewingState.Reason.Restricted))
                        return
                    }

                    else -> {
                        log(TAG, WARN) { "Viewing poll failed: ${e.asLog()}" }
                        emit(failureState(lastSnapshot, e))
                        delay(backoffMillis)
                        backoffMillis = min(backoffMillis * 2, MAX_BACKOFF_MS)
                        continue
                    }
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Viewing poll failed: ${e.asLog()}" }
                emit(failureState(lastSnapshot, e))
                delay(backoffMillis)
                backoffMillis = min(backoffMillis * 2, MAX_BACKOFF_MS)
                continue
            }

            val interval = access.viewingInterval.toMillis()
            delay((interval - (serverClock.elapsed() - startedAtElapsed)).coerceAtLeast(0))
        }
    }

    private fun failureState(lastSnapshot: ViewingSnapshot?, error: Throwable): ViewingState =
        lastSnapshot
            ?.let { ViewingState.Snapshot(it, error) }
            ?: ViewingState.Waiting(ViewingState.Reason.Offline)

    /** Suspends until the access policy reports a period after [previousResetsAt]. */
    private suspend fun awaitNewAllowance(previousResetsAt: Instant) {
        accessRepo.state.filterNotNull().first { it.resetsAt.isAfter(previousResetsAt) }
    }

    /** A single viewing snapshot around a point, outside the polling loop. */
    suspend fun nearby(query: ViewingQuery.Ar): ViewingSnapshot {
        val response = requestCoordinator.execute(Bucket.VIEWING) {
            sessionManager.authed { token ->
                endpoint.ar(token, ArRequest(query.latitude, query.longitude, query.radiusNm))
            }
        }
        val snapshot = response.toSnapshot(serverClock)
        accessRepo.applyUsage(snapshot.usage)
        aircraftDatabase.update(snapshot.aircraft)
        return snapshot
    }

    suspend fun search(chunks: List<Chunk<SearchTerm>>): List<BatchResult<TermOutcome>> = chunks.map { chunk ->
        val operationId = chunk.operationId ?: newOperationId()
        val request = SearchBatchRequest(operationId, chunk.items)
        val response = operationRunner.run(
            kind = OperationStore.Kind.SEARCH,
            operationId = operationId,
            requestJson = json.encodeToString(SearchBatchRequest.serializer(), request),
            ownerIds = chunk.ownerIds,
            encodeResult = { json.encodeToString(BatchResponse.serializer(), it) },
        ) { id ->
            requestCoordinator.execute(Bucket.SEARCH) {
                sessionManager.authed { token -> endpoint.search(token, request.copy(operationId = id)) }
            }
        }
        // Nothing durable is derived from a search beyond the cache write, which is newer-wins
        applyBatch(response)
        operationStore.complete(operationId)
        response.toTermResults(serverClock)
    }

    /**
     * The caller applies the outcomes and then retires the operation through [OperationStore], so a
     * crash in between replays the stored result instead of losing it.
     */
    suspend fun checkWatches(chunks: List<Chunk<WatchDefinition>>): List<BatchResult<WatchOutcome>> =
        chunks.map { chunk ->
            val operationId = chunk.operationId ?: newOperationId()
            val request = WatchBatchRequest(operationId, chunk.items)
            val response = operationRunner.run(
                kind = OperationStore.Kind.WATCH,
                operationId = operationId,
                requestJson = json.encodeToString(WatchBatchRequest.serializer(), request),
                ownerIds = chunk.ownerIds,
                encodeResult = { json.encodeToString(BatchResponse.serializer(), it) },
            ) { id ->
                requestCoordinator.execute(Bucket.WATCH) {
                    sessionManager.authed { token -> endpoint.checkWatches(token, request.copy(operationId = id)) }
                }
            }
            applyBatch(response)
            response.toWatchResults(serverClock)
        }

    /** Replays an operation the app sent but never applied, from its receipt when there is one. */
    suspend fun replayWatchOperation(pending: OperationStore.PendingOperation): BatchResult<WatchOutcome> {
        pending.resultJson?.let { stored ->
            log(TAG, VERBOSE) { "Applying stored result of ${pending.operationId}" }
            val response = json.decodeFromString(BatchResponse.serializer(), stored)
            applyBatch(response)
            return response.toWatchResults(serverClock)
        }

        val request = json.decodeFromString(WatchBatchRequest.serializer(), pending.requestJson)
        val response = operationRunner.run(
            kind = OperationStore.Kind.WATCH,
            operationId = pending.operationId,
            requestJson = pending.requestJson,
            ownerIds = pending.ownerIds,
            encodeResult = { json.encodeToString(BatchResponse.serializer(), it) },
        ) { id ->
            requestCoordinator.execute(Bucket.WATCH) {
                sessionManager.authed { token -> endpoint.checkWatches(token, request.copy(operationId = id)) }
            }
        }
        applyBatch(response)
        return response.toWatchResults(serverClock)
    }

    private suspend fun applyBatch(response: BatchResponse) {
        accessRepo.applyUsage(response.usage)
        val fetchedAt = Instant.ofEpochMilli(response.serverTime)
        val observed = response.aircraft.map { it.toAircraft(fetchedAt) }
        if (observed.isNotEmpty()) aircraftDatabase.update(observed)
    }

    companion object {
        const val MAX_BATCH_ITEMS = 100
        private const val INITIAL_BACKOFF_MS = 3_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private val TAG = logTag("Aircraft", "Repo")
    }
}
