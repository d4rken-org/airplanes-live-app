package eu.darken.apl.main.core.request

import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.isRetryableSameRequest
import eu.darken.apl.server.api.retryAfter
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class OperationFailedException(val code: String) : RuntimeException("Operation cannot be replayed: $code")

/**
 * Runs one batch operation so it survives process death: the id is stored before the first send and
 * reused by every retry, which is what makes a retry idempotent. The result is stored on the row
 * before the caller applies it.
 */
@Singleton
class OperationRunner @Inject constructor(
    private val store: OperationStore,
    private val serverClock: ServerClock,
) {

    suspend fun <Res> run(
        kind: OperationStore.Kind,
        operationId: String,
        requestJson: String,
        ownerIds: List<String>,
        encodeResult: (Res) -> String,
        send: suspend (operationId: String) -> Res,
    ): Res {
        store.start(kind, operationId, requestJson, ownerIds, serverClock.now())

        var attempt = 0
        while (true) {
            attempt++
            try {
                val result = send(operationId)
                store.storeResult(operationId, encodeResult(result))
                return result
            } catch (e: ServerApiException) {
                if (e.code in UNREPLAYABLE_CODES) {
                    store.complete(operationId)
                    throw OperationFailedException(e.code)
                }
                if (!e.isRetryableSameRequest || attempt >= MAX_ATTEMPTS) throw e
                log(TAG, WARN) { "Attempt $attempt of $operationId failed with ${e.code}, retrying" }
                delay(e.retryAfter.toMillis().coerceIn(MIN_RETRY_MS, MAX_RETRY_MS))
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS) throw e
                log(TAG, WARN) { "Attempt $attempt of $operationId failed with ${e.javaClass.simpleName}, retrying" }
                delay(MIN_RETRY_MS)
            }
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
        private const val MIN_RETRY_MS = 1_000L
        private const val MAX_RETRY_MS = 30_000L
        private val UNREPLAYABLE_CODES = setOf(
            ServerCodes.OPERATION_EXPIRED,
            ServerCodes.OPERATION_RESULT_UNAVAILABLE,
            ServerCodes.OPERATION_MISMATCH,
            ServerCodes.RESULT_TOO_LARGE,
        )
        private val TAG = logTag("Aircraft", "OperationRunner")
    }
}
