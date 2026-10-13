package eu.darken.apl.watch.core.history

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.WatchRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one history run queued for the earliest moment a watch is due. A new, moved or restored
 * watch and a change in what the server offers ask for a run right away.
 */
@Singleton
class WatchHistoryScheduler @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val workManager: WorkManager,
    private val watchRepo: WatchRepo,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
    private val sync: WatchHistorySync,
) {

    private var isInit = false

    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        appScope.launch {
            watchRepo.watches
                .map { watches -> watches.map { it.id to it.definitionRevision }.toSet() }
                .distinctUntilChanged()
                .collect { runNow("watches") }
        }
        appScope.launch {
            accessRepo.state
                .map { state -> state?.takeIf { it.offersWatchHistory }?.tier }
                .distinctUntilChanged()
                .filter { it != null }
                .collect { runNow("access") }
        }
    }

    fun runNow(reason: String) {
        // Replacing a running worker would cancel its page; it runs again right after instead
        if (sync.isRunning) {
            log(TAG) { "runNow($reason): a run is going, asking it for another" }
            sync.requestRerun()
            return
        }
        log(TAG) { "runNow($reason)" }
        enqueue(Duration.ZERO, ExistingWorkPolicy.REPLACE)
    }

    /** Called from the running worker: queued behind it, a null [at] means shortly. */
    fun followUp(at: Instant?) {
        val delay = at?.let { Duration.between(serverClock.now(), it) } ?: YIELD_DELAY
        log(TAG) { "followUp(in $delay)" }
        enqueue(delay.coerceAtLeast(Duration.ZERO), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(delay: Duration, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<WatchHistoryWorker>()
            .setInitialDelay(delay)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        private const val WORK_NAME = "watch.history.worker"
        private val YIELD_DELAY: Duration = Duration.ofMinutes(1)
        private val TAG = logTag("Watch", "History", "Scheduler")
    }
}
