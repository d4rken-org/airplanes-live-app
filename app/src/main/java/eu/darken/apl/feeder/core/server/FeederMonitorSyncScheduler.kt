package eu.darken.apl.feeder.core.server

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
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Queues feeder monitor runs one after the other, a request never cancels a run that is going. */
@Singleton
class FeederMonitorSyncScheduler @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val workManager: WorkManager,
    private val serverClock: ServerClock,
    private val accessRepo: AccessRepo,
    private val feederSettings: FeederSettings,
) {

    private data class AccessProjection(
        val installationId: String?,
        val offersFeederMonitor: Boolean,
        val maxActive: Int?,
        val maxStored: Int?,
    )

    private var isInit = false

    /**
     * A run on start and whenever the feeder monitoring or the monitors the server should hold change.
     * Only ids and thresholds count, a feeder's label or address is no business of the server.
     */
    fun setup() {
        log(TAG) { "setup()" }
        require(!isInit)
        isInit = true

        val access = accessRepo.state.map { state ->
            AccessProjection(
                installationId = state?.installationId,
                offersFeederMonitor = state?.offersFeederMonitor == true,
                maxActive = state?.limits?.maxActiveFeederMonitors,
                maxStored = state?.limits?.maxStoredFeederMonitors,
            )
        }
        val desired = feederSettings.feederGroup.flow.map { DesiredFeederMonitor.from(it.configs) }

        appScope.launch {
            combine(access, desired) { a, d -> a to d }
                .distinctUntilChanged()
                .filter { (a, _) -> a.offersFeederMonitor }
                .collect { requestSync("changed") }
        }
    }

    fun requestSync(reason: String) {
        log(TAG) { "requestSync($reason)" }
        enqueue(OneTimeWorkRequestBuilder<FeederMonitorSyncWorker>(), reason, Duration.ZERO)
    }

    /** Called from the running worker, the run is queued behind it. */
    fun followUp(reason: String, at: Instant) {
        val delay = Duration.between(serverClock.now(), at).coerceAtLeast(Duration.ZERO)
        log(TAG) { "followUp($reason, in $delay)" }
        enqueue(OneTimeWorkRequestBuilder<FeederMonitorSyncWorker>(), reason, delay)
    }

    /**
     * A wake-up for when held back work may go on. Its own queue keeps runs requested meanwhile from
     * waiting behind its delay; it only requests a run, so the newest deadline replaces an older one.
     */
    private fun deadline(reason: String, at: Instant) {
        val delay = Duration.between(serverClock.now(), at).coerceAtLeast(Duration.ZERO)
        log(TAG) { "deadline($reason, in $delay)" }
        enqueue(
            OneTimeWorkRequestBuilder<FeederMonitorSyncDeadlineWorker>(),
            reason,
            delay,
            DEADLINE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
        )
    }

    /**
     * A wake-up for reading the list once the server checked a new monitor. Later runs keep a pending
     * one instead of pushing it back; the run it triggers queues the next while the check is still due.
     */
    private fun listCheck(reason: String, at: Instant) {
        val delay = Duration.between(serverClock.now(), at).coerceAtLeast(Duration.ZERO)
        log(TAG) { "listCheck($reason, in $delay)" }
        enqueue(
            OneTimeWorkRequestBuilder<FeederMonitorSyncDeadlineWorker>(),
            reason,
            delay,
            LIST_CHECK_WORK_NAME,
            ExistingWorkPolicy.KEEP,
        )
    }

    /** Queues what a finished run left to do. */
    fun afterRun(result: FeederMonitorSync.Result) {
        when (result) {
            is FeederMonitorSync.Result.Done -> {
                result.resumeAt?.let { deadline("held-back", it) }
                result.listCheckAt?.let { listCheck("list-check", it) }
                if (result.moreWork) followUp("more-work", serverClock.now().plus(MORE_WORK_DELAY))
            }

            FeederMonitorSync.Result.Aborted -> followUp("identity-changed", serverClock.now())
            FeederMonitorSync.Result.Inactive -> {}
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
            .setInputData(workDataOf(FeederMonitorSyncWorker.KEY_REASON to reason))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(name, policy, request)
    }

    companion object {
        internal const val WORK_NAME = "feeder.monitor.sync"
        internal const val DEADLINE_WORK_NAME = "feeder.monitor.sync.deadline"
        internal const val LIST_CHECK_WORK_NAME = "feeder.monitor.sync.listcheck"
        private val MORE_WORK_DELAY: Duration = Duration.ofSeconds(10)
        private val TAG = logTag("Feeder", "Server", "Scheduler")
    }
}
