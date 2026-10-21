package eu.darken.apl.server.compat

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.history.WatchHistoryScheduler
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Once the server serves this build again, restarts what its refusal stopped. */
@Singleton
class AppCompatibilityRecovery @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val compatibility: AppCompatibility,
    private val accessRepo: AccessRepo,
    private val watchListSyncScheduler: WatchListSyncScheduler,
    private val watchHistoryScheduler: WatchHistoryScheduler,
) {

    private var isInit = false

    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        appScope.launch {
            var previous: CompatState? = null
            compatibility.state.collect { current ->
                val unblocked = previous == CompatState.UpdateRequired &&
                        (current == CompatState.Ok || current is CompatState.UpdateRecommended)
                previous = current
                if (unblocked) recover()
            }
        }
    }

    private fun recover() {
        log(TAG, INFO) { "The server serves this build again, resuming" }
        appScope.launch {
            try {
                accessRepo.refresh(REASON)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Access refresh failed: ${e.asLog()}" }
            }
        }
        watchListSyncScheduler.requestSync(REASON)
        appScope.launch { watchHistoryScheduler.runAfterCurrent(REASON) }
    }

    companion object {
        internal const val REASON = "update-unblocked"
        private val TAG = logTag("Server", "Compat", "Recovery")
    }
}
