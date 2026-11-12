package eu.darken.apl.feeder.core.server

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederGroup
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class FeederMonitorSyncSchedulerTest : BaseTest() {

    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW.toEpochMilli()) }

    private data class Enqueued(val name: String, val worker: String, val delay: Duration, val policy: ExistingWorkPolicy)

    private val enqueued = mutableListOf<Enqueued>()

    private val workManager = mockk<WorkManager>().apply {
        every { enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) } answers {
            val request = thirdArg<OneTimeWorkRequest>()
            enqueued += Enqueued(
                name = firstArg(),
                worker = request.workSpec.workerClassName,
                delay = Duration.ofMillis(request.workSpec.initialDelay),
                policy = secondArg(),
            )
            mockk(relaxed = true)
        }
    }

    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }

    /** Stands in for what the DataStore emits, whoever wrote the group: the app or a backup restore. */
    private val group = MutableStateFlow(FeederGroup())
    private val feederSettings = mockk<FeederSettings>().apply {
        val value = mockk<DataStoreValue<FeederGroup>>()
        every { value.flow } returns group
        every { feederGroup } returns value
    }

    private fun syncRequests() = enqueued.count { it.name == FeederMonitorSyncScheduler.WORK_NAME }

    private fun TestScope.scheduler() = FeederMonitorSyncScheduler(
        appScope = backgroundScope,
        workManager = workManager,
        serverClock = serverClock,
        accessRepo = accessRepo,
        feederSettings = feederSettings,
    )

    private fun TestScope.started(): FeederMonitorSyncScheduler = scheduler().also {
        it.setup()
        runCurrent()
    }

    private fun TestScope.change(block: () -> Unit) {
        block()
        runCurrent()
    }

    private fun config(receiverId: String, order: Long, label: String? = null, timeout: Duration? = Duration.ofHours(48)) =
        FeederConfig(
            receiverId = receiverId,
            addedAt = NOW.plusSeconds(order),
            label = label,
            offlineCheckTimeout = timeout,
        )

    private fun access(feederMonitor: Boolean = true, maxActive: Int? = 3, maxStored: Int? = 20) = AccessState(
        installationId = "installation-a",
        fetchedAt = NOW,
        tier = AccessState.Tier.FEEDER,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25_000,
            searchesPerDay = 25,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            watchTypes = listOf("hex"),
            maxActiveFeederMonitors = maxActive,
            maxStoredFeederMonitors = maxStored,
        ),
        usage = Usage(
            resetsAt = 0,
            viewing = Allowance(25_000, 0, 0, 25_000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(RequestRate(1.0, 3), RequestRate(1.0, 3), RequestRate(1.0, 3), 3),
        aircraftOperations = if (feederMonitor) listOf(AccessState.OPERATION_FEEDER_MONITOR) else emptyList(),
    )

    @Test
    fun `start requests a sync, a label edit does not, an added feeder does`() = runTest {
        accessState.value = access()
        group.value = FeederGroup(setOf(config(ID_1, 0, label = "Roof")))

        started()
        syncRequests() shouldBe 1

        change { group.value = FeederGroup(setOf(config(ID_1, 0, label = "Garage"))) }
        syncRequests() shouldBe 1

        change { group.value = FeederGroup(setOf(config(ID_1, 0, label = "Garage"), config(ID_2, 1))) }
        syncRequests() shouldBe 2
    }

    @Test
    fun `a changed threshold requests a sync`() = runTest {
        accessState.value = access()
        group.value = FeederGroup(setOf(config(ID_1, 0)))
        started()

        change { group.value = FeederGroup(setOf(config(ID_1, 0, timeout = Duration.ofHours(6)))) }

        syncRequests() shouldBe 2
    }

    @Test
    fun `changed feeder limits of the access request a sync`() = runTest {
        accessState.value = access()
        started()

        change { accessState.value = access(maxActive = 5) }
        syncRequests() shouldBe 2

        change { accessState.value = access(maxActive = 5, maxStored = 30) }
        syncRequests() shouldBe 3

        // A refreshed access with the same feeder monitoring is no reason for a sync
        change { accessState.value = access(maxActive = 5, maxStored = 30).copy(fetchedAt = NOW.plusSeconds(60)) }
        syncRequests() shouldBe 3
    }

    @Test
    fun `nothing is requested while the access has no feeder monitoring`() = runTest {
        accessState.value = access(feederMonitor = false)
        started()

        change { group.value = FeederGroup(setOf(config(ID_1, 0))) }
        change { accessState.value = access(feederMonitor = false, maxActive = 5) }
        change { accessState.value = null }
        syncRequests() shouldBe 0

        change { accessState.value = access() }
        syncRequests() shouldBe 1
    }

    @Test
    fun `more work follows soon, a deadline waits on its own queue without running the sync itself`() = runTest {
        scheduler().afterRun(FeederMonitorSync.Result.Done(moreWork = true, resumeAt = NOW.plus(Duration.ofHours(1))))

        withClue("enqueued: $enqueued") {
            enqueued.size shouldBe 2
            val (name, worker, delay) = enqueued.single { it.name == FeederMonitorSyncScheduler.DEADLINE_WORK_NAME }
            worker shouldBe FeederMonitorSyncDeadlineWorker::class.java.name
            delay shouldBe Duration.ofHours(1)
            val followUp = enqueued.single { it.name == FeederMonitorSyncScheduler.WORK_NAME }
            followUp.worker shouldBe FeederMonitorSyncWorker::class.java.name
            followUp.delay shouldBe Duration.ofSeconds(10)
            name shouldBe FeederMonitorSyncScheduler.DEADLINE_WORK_NAME
        }
    }

    @Test
    fun `an aborted run follows up right away, an inactive one not at all`() = runTest {
        val scheduler = scheduler()

        scheduler.afterRun(FeederMonitorSync.Result.Inactive)
        enqueued.size shouldBe 0

        scheduler.afterRun(FeederMonitorSync.Result.Aborted)
        enqueued.single().let {
            it.name shouldBe FeederMonitorSyncScheduler.WORK_NAME
            it.delay shouldBe Duration.ZERO
        }
    }

    @Test
    fun `a due list check wakes up on its own queue and keeps a wake-up already pending`() = runTest {
        scheduler().afterRun(FeederMonitorSync.Result.Done(moreWork = false, listCheckAt = NOW.plusSeconds(60)))

        enqueued.single() shouldBe Enqueued(
            name = FeederMonitorSyncScheduler.LIST_CHECK_WORK_NAME,
            worker = FeederMonitorSyncDeadlineWorker::class.java.name,
            delay = Duration.ofSeconds(60),
            policy = ExistingWorkPolicy.KEEP,
        )
    }

    @Test
    fun `a list check and a deadline of one run go to separate queues`() = runTest {
        scheduler().afterRun(
            FeederMonitorSync.Result.Done(
                moreWork = false,
                resumeAt = NOW.plus(Duration.ofHours(1)),
                listCheckAt = NOW.plusSeconds(60),
            )
        )

        enqueued.toSet() shouldBe setOf(
            Enqueued(
                name = FeederMonitorSyncScheduler.DEADLINE_WORK_NAME,
                worker = FeederMonitorSyncDeadlineWorker::class.java.name,
                delay = Duration.ofHours(1),
                policy = ExistingWorkPolicy.REPLACE,
            ),
            Enqueued(
                name = FeederMonitorSyncScheduler.LIST_CHECK_WORK_NAME,
                worker = FeederMonitorSyncDeadlineWorker::class.java.name,
                delay = Duration.ofSeconds(60),
                policy = ExistingWorkPolicy.KEEP,
            ),
        )
    }

    @Test
    fun `a later run's deadline leaves the pending list check alone`() = runTest {
        val scheduler = scheduler()

        scheduler.afterRun(FeederMonitorSync.Result.Done(moreWork = false, listCheckAt = NOW.plusSeconds(60)))
        scheduler.afterRun(FeederMonitorSync.Result.Done(moreWork = false, resumeAt = NOW.plusSeconds(30)))

        enqueued shouldBe listOf(
            Enqueued(
                name = FeederMonitorSyncScheduler.LIST_CHECK_WORK_NAME,
                worker = FeederMonitorSyncDeadlineWorker::class.java.name,
                delay = Duration.ofSeconds(60),
                policy = ExistingWorkPolicy.KEEP,
            ),
            Enqueued(
                name = FeederMonitorSyncScheduler.DEADLINE_WORK_NAME,
                worker = FeederMonitorSyncDeadlineWorker::class.java.name,
                delay = Duration.ofSeconds(30),
                policy = ExistingWorkPolicy.REPLACE,
            ),
        )
    }

    @Test
    fun `a run without a due list check queues none`() = runTest {
        val scheduler = scheduler()

        scheduler.afterRun(FeederMonitorSync.Result.Done(moreWork = false))
        enqueued.size shouldBe 0

        scheduler.afterRun(FeederMonitorSync.Result.Done(moreWork = true, resumeAt = NOW.plus(Duration.ofHours(1))))
        enqueued.map { it.name }.toSet() shouldBe setOf(
            FeederMonitorSyncScheduler.DEADLINE_WORK_NAME,
            FeederMonitorSyncScheduler.WORK_NAME,
        )
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-11-09T12:00:00Z")
        private const val ID_1 = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_2 = "11111111-2222-4333-8444-555555555555"
    }
}
