package eu.darken.apl.watch.core.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.Bugs
import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.isRetryableSameRequest
import eu.darken.apl.server.session.SessionRevokedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException


@HiltWorker
class WatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val watchMonitor: WatchMonitor,
) : CoroutineWorker(context, params) {

    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    /**
     * A retry is only worth scheduling while the pending operation can still be replayed, which is
     * what [Result.retry] and WorkManager's backoff are for. Everything else is terminal.
     */
    override suspend fun doWork(): Result = try {
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }
        val start = System.currentTimeMillis()

        withContext(dispatcherProvider.IO) {
            withTimeout(TIMEOUT_MS) { watchMonitor.check(WatchMonitor.Trigger.PERIODIC) }
        }

        log(TAG, VERBOSE) { "Execution finished after ${System.currentTimeMillis() - start}ms" }
        Result.success(inputData)
    } catch (e: TimeoutCancellationException) {
        log(TAG, WARN) { "Worker ran into timeout" }
        Result.retry()
    } catch (e: SessionRevokedException) {
        log(TAG, ERROR) { "Installation is revoked, not retrying" }
        Result.failure(inputData)
    } catch (e: ServerApiException) {
        if (e.isRetryableSameRequest) {
            log(TAG, WARN) { "Retryable server error: ${e.code}" }
            Result.retry()
        } else {
            log(TAG, ERROR) { "Server rejected the check: ${e.asLog()}" }
            Bugs.report(e)
            Result.failure(inputData)
        }
    } catch (e: IOException) {
        log(TAG, WARN) { "Network unavailable: ${e.message}" }
        Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, ERROR) { "Watch check failed: ${e.asLog()}" }
        Bugs.report(e)
        Result.failure(inputData)
    } finally {
        workerScope.cancel("Worker finished.")
    }

    companion object {
        private const val TIMEOUT_MS = 60 * 1000L
        val TAG = logTag("Watch", "Monitor", "Worker")
    }
}
