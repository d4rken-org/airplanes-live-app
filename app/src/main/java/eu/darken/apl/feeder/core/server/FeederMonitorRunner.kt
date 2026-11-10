package eu.darken.apl.feeder.core.server

import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.monitor.FeederMonitorNotifications
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** One feeder monitoring run in server mode: sync with the server, then notify from what it left. */
@Singleton
class FeederMonitorRunner @Inject constructor(
    private val mode: FeederServerMode,
    private val sync: FeederMonitorSync,
    private val store: FeederServerStore,
    private val alerts: FeederServerAlerts,
    private val notifications: FeederMonitorNotifications,
) {

    /** Exceptions of the sync are passed on, the notifications still go out for what it stored before. */
    suspend fun run(reason: String, foreground: Boolean = false): FeederMonitorSync.Result {
        // Not isActive, only the sync resets the stored state of another installation
        if (mode.current().access?.offersFeederMonitor != true) {
            log(TAG) { "run($reason): not in server mode" }
            return FeederMonitorSync.Result.Inactive
        }
        val installationBefore = store.current().installationId
        var result: FeederMonitorSync.Result? = null
        var failure: Throwable? = null
        try {
            val done = sync.run(reason, foreground)
            result = done
            return done
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            val primary = failure
            if (primary !is CancellationException) {
                try {
                    // The sync drops the server state on an identity change and starts over for another
                    // installation, the notified state went with it and can no longer cancel anything
                    val installation = store.current().installationId
                    if (result == FeederMonitorSync.Result.Aborted || (installationBefore != null && installation != installationBefore)) {
                        log(TAG, INFO) { "run($reason): server state of $installationBefore dropped, cancelling its notifications" }
                        notifications.cancelAllServer()
                    }
                    // A sync that failed before resetting another installation's state left that state behind
                    if (mode.current().isActive) alerts.deliver()
                } catch (e: Exception) {
                    if (e is CancellationException || primary == null) throw e
                    primary.addSuppressed(e)
                }
            }
        }
    }

    companion object {
        private val TAG = logTag("Feeder", "Server", "Runner")
    }
}
