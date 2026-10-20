package eu.darken.apl.watch.core.server

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Queues watch list runs one after the other, a request never cancels a run that is going. */
@Singleton
class WatchListSyncScheduler @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val workManager: WorkManager,
    private val serverClock: ServerClock,
    private val accessRepo: AccessRepo,
) {

    private var isInit = false

    /** A run on start and for every installation the watch list becomes available to. */
    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        appScope.launch {
            accessRepo.state
                .map { state -> state?.takeIf { it.offersWatchList }?.installationId }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { requestSync("access") }
        }
    }

    fun requestSync(reason: String) {
        log(TAG) { "requestSync($reason)" }
        enqueue(OneTimeWorkRequestBuilder<WatchListSyncWorker>(), reason, Duration.ZERO)
    }

    /** Called from the running worker, the run is queued behind it. */
    fun followUp(reason: String, at: Instant) {
        val delay = Duration.between(serverClock.now(), at).coerceAtLeast(Duration.ZERO)
        log(TAG) { "followUp($reason, in $delay)" }
        enqueue(OneTimeWorkRequestBuilder<WatchListSyncWorker>(), reason, delay)
    }

    /**
     * A wake-up for when held back work may go on. It has its own queue, so runs requested meanwhile
     * do not wait behind its delay. The deadline work only requests a run, so the newest deadline can
     * replace an older one without cancelling a sync.
     */
    private fun deadline(reason: String, at: Instant) {
        val delay = Duration.between(serverClock.now(), at).coerceAtLeast(Duration.ZERO)
        log(TAG) { "deadline($reason, in $delay)" }
        enqueue(
            OneTimeWorkRequestBuilder<WatchListSyncDeadlineWorker>(),
            reason,
            delay,
            DEADLINE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
        )
    }

    /** Queues what a finished run left to do. */
    fun afterRun(result: WatchListSync.Result) {
        when (result) {
            is WatchListSync.Result.Done -> {
                result.resumeAt?.let { deadline("held-back", it) }
                if (result.moreWork) followUp("more-work", serverClock.now().plus(MORE_WORK_DELAY))
            }

            // The new installation starts its migration from scratch
            WatchListSync.Result.Aborted -> followUp("identity-changed", serverClock.now())
            WatchListSync.Result.Inactive -> {}
        }
    }

    private fun enqueue(
        builder: OneTimeWorkRequest.Builder,
        reason: String,
        delay: Duration,
        name: String = WORK_NAME,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
    ) {
        val request = builder
            .setInitialDelay(delay)
            .setInputData(workDataOf(WatchListSyncWorker.KEY_REASON to reason))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(name, policy, request)
    }

    companion object {
        private const val WORK_NAME = "watch.list.sync"
        private const val DEADLINE_WORK_NAME = "watch.list.sync.deadline"
        private val MORE_WORK_DELAY: Duration = Duration.ofSeconds(10)
        private val TAG = logTag("Watch", "List", "Scheduler")
    }
}
