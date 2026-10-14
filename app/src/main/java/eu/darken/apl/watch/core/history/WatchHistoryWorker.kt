package eu.darken.apl.watch.core.history

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.isRetryableSameRequest
import eu.darken.apl.server.session.SessionRevokedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.Instant

@HiltWorker
class WatchHistoryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val sync: WatchHistorySync,
    private val scheduler: WatchHistoryScheduler,
) : CoroutineWorker(context, params) {

    /** The next run is queued behind this one, never in place of it: replacing would cancel this worker. */
    override suspend fun doWork(): Result = try {
        val result = withContext(dispatcherProvider.IO) { withTimeout(TIMEOUT_MS) { sync.run() } }
        log(TAG) { "Finished: $result" }
        val rerun = sync.consumeRerun()
        when {
            // The live check that made this run stop goes first, whatever else is waiting
            result is WatchHistorySync.Result.Yielded -> scheduler.followUp(null)
            // Something changed while this run was going, it gets its own run right away
            rerun -> scheduler.followUp(Instant.EPOCH)
            result is WatchHistorySync.Result.Done -> result.at?.let { scheduler.followUp(it) }
        }
        Result.success()
    } catch (e: TimeoutCancellationException) {
        log(TAG, WARN) { "Ran into the timeout" }
        Result.retry()
    } catch (e: SessionRevokedException) {
        log(TAG, ERROR) { "Installation is revoked, not retrying" }
        Result.failure()
    } catch (e: ServerApiException) {
        if (e.isRetryableSameRequest) Result.retry() else {
            log(TAG, ERROR) { "Server rejected the history page: ${e.asLog()}" }
            Result.failure()
        }
    } catch (e: IOException) {
        log(TAG, WARN) { "Network unavailable: ${e.message}" }
        Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, ERROR) { "History sync failed: ${e.asLog()}" }
        Result.failure()
    }

    companion object {
        private const val TIMEOUT_MS = 5 * 60 * 1000L
        private val TAG = logTag("Watch", "History", "Worker")
    }
}
