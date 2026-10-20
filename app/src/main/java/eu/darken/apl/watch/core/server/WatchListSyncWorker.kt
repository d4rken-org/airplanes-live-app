package eu.darken.apl.watch.core.server

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

@HiltWorker
class WatchListSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val sync: WatchListSync,
    private val scheduler: WatchListSyncScheduler,
): CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val reason = inputData.getString(KEY_REASON) ?: "worker"
        val result = withContext(dispatcherProvider.IO) { withTimeout(TIMEOUT_MS) { sync.run(reason) } }
        log(TAG) { "Finished: $result" }
        scheduler.afterRun(result)
        Result.success()
    } catch (e: TimeoutCancellationException) {
        log(TAG, WARN) { "Ran into the timeout" }
        Result.retry()
    } catch (e: SessionRevokedException) {
        log(TAG, ERROR) { "Installation is revoked, not retrying" }
        Result.failure()
    } catch (e: ServerApiException) {
        if (e.isRetryableSameRequest) Result.retry() else {
            log(TAG, ERROR) { "Server rejected the watch list sync: ${e.asLog()}" }
            Result.failure()
        }
    } catch (e: IOException) {
        log(TAG, WARN) { "Network unavailable: ${e.message}" }
        Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, ERROR) { "Watch list sync failed: ${e.asLog()}" }
        Result.failure()
    }

    companion object {
        const val KEY_REASON = "reason"
        private const val TIMEOUT_MS = 60 * 1000L
        private val TAG = logTag("Watch", "List", "Worker")
    }
}
