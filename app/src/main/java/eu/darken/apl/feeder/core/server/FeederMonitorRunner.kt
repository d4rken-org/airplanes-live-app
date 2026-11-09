package eu.darken.apl.feeder.core.server

import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** One feeder monitoring run in server mode: sync with the server, then notify from what it left. */
@Singleton
class FeederMonitorRunner @Inject constructor(
    private val mode: FeederServerMode,
    private val sync: FeederMonitorSync,
    private val alerts: FeederServerAlerts,
) {

    /** Exceptions of the sync are passed on, the notifications still go out for what it stored before. */
    suspend fun run(reason: String, foreground: Boolean = false): FeederMonitorSync.Result {
        if (!mode.current().isActive) {
            log(TAG) { "run($reason): not in server mode" }
            return FeederMonitorSync.Result.Inactive
        }
        var failure: Throwable? = null
        try {
            return sync.run(reason, foreground)
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            if (failure !is CancellationException) {
                try {
                    alerts.deliver()
                } catch (e: Exception) {
                    if (e is CancellationException || failure == null) throw e
                    failure.addSuppressed(e)
                }
            }
        }
    }

    companion object {
        private val TAG = logTag("Feeder", "Server", "Runner")
    }
}
