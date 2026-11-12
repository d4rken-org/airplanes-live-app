package eu.darken.apl.watch.core.alerts

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.common.util.concurrent.Futures
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.BackgroundSync
import eu.darken.apl.main.core.BackgroundSyncLevel
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchSettings
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.time.Duration
import java.time.Instant

class WatchWorkerHelperTest : BaseTest() {

    private class TestRepo(override val upgradeInfo: Flow<UpgradeRepo.Info>) : UpgradeRepo {
        override suspend fun refresh() = Unit
    }

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean = true,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        override val source: UpgradeRepo.Source? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    private data class Enqueued(
        val name: String,
        val policy: ExistingPeriodicWorkPolicy,
        val interval: Duration,
        val flex: Duration,
    )

    private val stored = MutableStateFlow<BackgroundSyncLevel?>(null)
    private val infos = MutableStateFlow<UpgradeRepo.Info>(Info(isPro = false))

    private val backgroundSync = run {
        val value = mockk<DataStoreValue<BackgroundSyncLevel?>>().apply {
            every { flow } returns stored
        }
        val settings = mockk<GeneralSettings>().apply {
            every { backgroundSyncLevel } returns value
        }
        BackgroundSync(settings, TestRepo(infos))
    }

    private val watchSettings = mockk<WatchSettings>().apply {
        // Recent enough that the start does not check
        val recentCheck = mockk<DataStoreValue<Instant>>()
        every { recentCheck.flow } returns flowOf(Instant.now())
        every { lastCheck } returns recentCheck
    }

    /** The outcome of each enqueue in order, null succeeds. Once empty, every enqueue succeeds. */
    private val failures = ArrayDeque<Throwable?>()
    private val enqueued = mutableListOf<Enqueued>()

    private val workManager = mockk<WorkManager>(relaxed = true).apply {
        every { enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) } answers {
            val request = thirdArg<PeriodicWorkRequest>()
            enqueued += Enqueued(
                name = firstArg(),
                policy = secondArg(),
                interval = Duration.ofMillis(request.workSpec.intervalDuration),
                flex = Duration.ofMillis(request.workSpec.flexDuration),
            )
            operation(failures.removeFirstOrNull())
        }
    }

    // A relaxed mock's future never completes, so the await would never return
    private fun operation(failure: Throwable?): Operation = mockk<Operation>().apply {
        every { result } returns if (failure == null) {
            Futures.immediateFuture(Operation.SUCCESS)
        } else {
            Futures.immediateFailedFuture<Operation.State.SUCCESS>(failure)
        }
    }

    private fun TestScope.started() {
        WatchWorkerHelper(
            appScope = backgroundScope,
            workManager = workManager,
            monitor = mockk(),
            watchSettings = watchSettings,
            backgroundSync = backgroundSync,
        ).setup()
        runCurrent()
    }

    private fun intervals() = enqueued.map { it.interval }

    @Test
    fun `start schedules the level in effect once`() = runTest {
        started()

        enqueued shouldBe listOf(
            Enqueued(
                name = "alerts.monitor.worker",
                policy = ExistingPeriodicWorkPolicy.UPDATE,
                interval = Duration.ofMinutes(60),
                flex = Duration.ofMinutes(10),
            ),
        )
    }

    @Test
    fun `a level change reschedules`() = runTest {
        started()

        stored.value = BackgroundSyncLevel.SLOW
        runCurrent()

        intervals() shouldBe listOf(Duration.ofMinutes(60), Duration.ofHours(6))
        enqueued.last().name shouldBe "alerts.monitor.worker"
        enqueued.last().policy shouldBe ExistingPeriodicWorkPolicy.UPDATE
    }

    @Test
    fun `a stored fast follows Pro being gained, lost and regained`() = runTest {
        stored.value = BackgroundSyncLevel.FAST
        started()

        infos.value = Info(isPro = true)
        runCurrent()
        infos.value = Info(isPro = false)
        runCurrent()
        infos.value = Info(isPro = true)
        runCurrent()

        intervals() shouldBe listOf(
            Duration.ofMinutes(60),
            Duration.ofMinutes(15),
            Duration.ofMinutes(60),
            Duration.ofMinutes(15),
        )
    }

    @Test
    fun `a failed operation is retried after the first delay`() = runTest {
        failures += IOException("enqueue failed")
        started()
        intervals() shouldBe listOf(Duration.ofMinutes(60))

        advanceTimeBy(9_999)
        runCurrent()
        intervals() shouldBe listOf(Duration.ofMinutes(60))

        advanceTimeBy(1)
        runCurrent()
        intervals() shouldBe listOf(Duration.ofMinutes(60), Duration.ofMinutes(60))

        stored.value = BackgroundSyncLevel.SLOW
        runCurrent()
        intervals() shouldBe listOf(Duration.ofMinutes(60), Duration.ofMinutes(60), Duration.ofHours(6))
    }

    @Test
    fun `a newer level replaces a pending retry`() = runTest {
        failures += IOException("enqueue failed")
        started()

        stored.value = BackgroundSyncLevel.SLOW
        runCurrent()
        advanceTimeBy(Duration.ofMinutes(10).toMillis())
        runCurrent()

        intervals() shouldBe listOf(Duration.ofMinutes(60), Duration.ofHours(6))
    }

    @Test
    fun `after all retries failed a later level change is still scheduled`() = runTest {
        repeat(4) { failures += IOException("enqueue failed") }
        started()

        advanceTimeBy(Duration.ofHours(1).toMillis())
        runCurrent()
        // The first attempt and three retries, after 10 s, 1 min and 5 min
        intervals() shouldBe List(4) { Duration.ofMinutes(60) }

        stored.value = BackgroundSyncLevel.SLOW
        runCurrent()

        intervals() shouldBe List(4) { Duration.ofMinutes(60) } + Duration.ofHours(6)
    }

    @Test
    fun `an app start retires the history worker and leaves the other watch work alone`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val helper = WatchWorkerHelper(
            // Never advanced: only what setup does before its coroutines start is under test
            appScope = CoroutineScope(StandardTestDispatcher(TestCoroutineScheduler())),
            workManager = workManager,
            monitor = mockk(),
            watchSettings = mockk(),
            backgroundSync = mockk(),
        )

        helper.setup()

        verify(exactly = 1) { workManager.cancelUniqueWork("watch.history.worker") }
        verify(exactly = 0) { workManager.cancelUniqueWork(neq("watch.history.worker")) }
        verify(exactly = 0) { workManager.cancelAllWork() }
        verify(exactly = 0) { workManager.cancelAllWorkByTag(any()) }
    }
}
