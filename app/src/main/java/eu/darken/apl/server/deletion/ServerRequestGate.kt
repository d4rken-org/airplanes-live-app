package eu.darken.apl.server.deletion

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/** Holds back server requests while a deletion runs and after the server confirmed one. */
@Singleton
class ServerRequestGate internal constructor(
    private val persisted: Flow<DeletionState>,
) {
    @Inject constructor(store: DeletionStateStore) : this(store.state)

    private val activeOperations = MutableStateFlow(0)

    suspend fun awaitOpen() {
        if (currentCoroutineContext()[DeletionScope] != null) return
        combine(activeOperations, persisted) { active, state -> active == 0 && state != DeletionState.WIPE_PENDING }
            .first { it }
    }

    suspend fun <T> deletionOperation(block: suspend () -> T): T {
        activeOperations.update { it + 1 }
        return try {
            withContext(DeletionScope) { block() }
        } finally {
            activeOperations.update { it - 1 }
        }
    }
}

/** Marks work that belongs to a deletion operation, its requests pass the closed [ServerRequestGate]. */
object DeletionScope : CoroutineContext.Element, CoroutineContext.Key<DeletionScope> {
    override val key: CoroutineContext.Key<*> get() = this
}
