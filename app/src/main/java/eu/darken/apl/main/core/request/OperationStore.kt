package eu.darken.apl.main.core.request

import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.db.PendingOperationEntity
import eu.darken.apl.server.ServerJson
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationStore @Inject constructor(
    private val aircraftDatabase: AircraftDatabase,
    private val dispatcherProvider: DispatcherProvider,
    @param:ServerJson private val json: Json,
) {

    enum class Kind {
        SEARCH,
        WATCH,
        ;
    }

    data class PendingOperation(
        val operationId: String,
        val kind: Kind,
        val requestJson: String,
        val ownerIds: List<String>,
        val createdAt: Instant,
        val resultJson: String?,
    )

    private val dao get() = aircraftDatabase.pendingOperations

    suspend fun start(
        kind: Kind,
        operationId: String,
        requestJson: String,
        ownerIds: List<String>,
        now: Instant,
    ) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "start($kind, $operationId)" }
        dao.insertIfAbsent(
            PendingOperationEntity(
                operationId = operationId,
                kind = kind.name,
                requestJson = requestJson,
                ownerIds = json.encodeToString(OWNER_IDS, ownerIds),
                createdAt = now.toEpochMilli(),
            )
        )
    }

    /** The receipt makes the result survive a crash between the response and its application. */
    suspend fun storeResult(operationId: String, resultJson: String) = withContext(dispatcherProvider.IO) {
        dao.setResult(operationId, resultJson)
    }

    suspend fun complete(operationId: String) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "complete($operationId)" }
        dao.delete(operationId)
    }

    /**
     * Operations the server no longer replays are dropped instead of being retried in vain, and only
     * within [kind]: another kind's rows are another caller's to retire.
     */
    suspend fun pending(kind: Kind, now: Instant): List<PendingOperation> = withContext(dispatcherProvider.IO) {
        dao.deleteUnansweredOlderThan(kind.name, now.minus(REPLAY_WINDOW).toEpochMilli())
        dao.deleteAnsweredOlderThan(kind.name, now.minus(RECEIPT_WINDOW).toEpochMilli())
        dao.getByKind(kind.name).map { entity ->
            PendingOperation(
                operationId = entity.operationId,
                kind = kind,
                requestJson = entity.requestJson,
                ownerIds = runCatching { json.decodeFromString(OWNER_IDS, entity.ownerIds) }.getOrDefault(emptyList()),
                createdAt = Instant.ofEpochMilli(entity.createdAt),
                resultJson = entity.resultJson,
            )
        }
    }

    companion object {
        val REPLAY_WINDOW: Duration = Duration.ofMinutes(5)

        /** A stored result outlives the replay window: applying it needs no server at all. */
        val RECEIPT_WINDOW: Duration = Duration.ofHours(24)
        private val OWNER_IDS = ListSerializer(String.serializer())
        private val TAG = logTag("Aircraft", "OperationStore")
    }
}
