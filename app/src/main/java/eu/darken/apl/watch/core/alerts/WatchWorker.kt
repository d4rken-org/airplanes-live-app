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
import eu.darken.apl.feeder.core.server.FeederMonitorRunner
import eu.darken.apl.feeder.core.server.FeederMonitorSyncScheduler
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException


@HiltWorker
class WatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val watchMonitor: WatchMonitor,
    private val feederMonitorRunner: FeederMonitorRunner,
    private val feederMonitorSyncScheduler: FeederMonitorSyncScheduler,
) : CoroutineWorker(context, params) {

    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    /** The feeder step follows whatever the watch step ended with, the watch step's result is the worker's. */
    override suspend fun doWork(): Result = try {
        val result = watchStep()
        feederStep()
        result
    } finally {
        workerScope.cancel("Worker finished.")
    }

    /** A transient sync failure is left to [Result.retry] and WorkManager's backoff, everything else is terminal. */
    private suspend fun watchStep(): Result = try {
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
        } else if (e.code == ServerCodes.APP_UPDATE_REQUIRED) {
            log(TAG, WARN) { "The server requires a newer app, skipping this check" }
            Result.failure(inputData)
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
    }

    /** The server's feeder monitoring under its own time budget. Failures are only logged, the next run retries. */
    private suspend fun feederStep() {
        try {
            val result = withContext(dispatcherProvider.IO) {
                withTimeoutOrNull(FEEDER_TIMEOUT_MS) { feederMonitorRunner.run("watch-worker") }
            }
            if (result == null) {
                log(TAG, WARN) { "Feeder monitoring ran into its timeout" }
            } else {
                log(TAG, VERBOSE) { "Feeder monitoring finished: $result" }
                feederMonitorSyncScheduler.afterRun(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionRevokedException) {
            log(TAG, WARN) { "Feeder monitoring: installation is revoked" }
        } catch (e: ServerApiException) {
            log(TAG, WARN) { "Feeder monitoring: server refused with ${e.code}" }
        } catch (e: IOException) {
            log(TAG, WARN) { "Feeder monitoring: network unavailable: ${e.message}" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Feeder monitoring failed: ${e.asLog()}" }
            Bugs.report(e)
        }
    }

    companion object {
        private const val TIMEOUT_MS = 60 * 1000L
        private const val FEEDER_TIMEOUT_MS = 30 * 1000L
        val TAG = logTag("Watch", "Monitor", "Worker")
    }
}
