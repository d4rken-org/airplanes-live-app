package eu.darken.apl.common.location

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Runs the blocking [block] in [scope] instead of the caller's scope, so the caller can stop waiting after [timeout]
 * while the thread stays blocked. A late result or exception of an abandoned [block] is dropped.
 *
 * @return null on timeout, otherwise the outcome of [block]
 */
internal suspend fun <T> awaitBounded(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    timeout: Duration,
    block: () -> T,
): Result<T>? {
    val lookup = scope.async(dispatcher) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    return try {
        withTimeoutOrNull(timeout) { lookup.await() }
    } finally {
        if (!lookup.isCompleted) lookup.cancel()
    }
}
