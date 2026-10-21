package eu.darken.apl.watch.core.history

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.WatchRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class WatchHistorySchedulerTest : BaseTest() {

    private val running = MutableStateFlow(false)
    private val sync = mockk<WatchHistorySync>(relaxed = true).apply {
        every { running } returns this@WatchHistorySchedulerTest.running
        every { isRunning } answers { this@WatchHistorySchedulerTest.running.value }
    }

    /** Unique work name and policy of every enqueue. */
    private val enqueued = mutableListOf<Pair<String, ExistingWorkPolicy>>()

    private val workManager = mockk<WorkManager>().apply {
        every { enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) } answers {
            enqueued += firstArg<String>() to secondArg<ExistingWorkPolicy>()
            mockk(relaxed = true)
        }
    }

    private val scheduler = WatchHistoryScheduler(
        appScope = CoroutineScope(Dispatchers.Unconfined),
        workManager = workManager,
        watchRepo = mockk<WatchRepo>(relaxed = true),
        accessRepo = mockk<AccessRepo>(relaxed = true),
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = 0L
        }),
        sync = sync,
    )

    @Test
    fun `a run after the current one waits for it to end instead of asking it for a rerun`() = runTest {
        running.value = true

        val job = launch { scheduler.runAfterCurrent("test") }
        runCurrent()

        enqueued shouldBe emptyList()
        verify(exactly = 0) { sync.requestRerun() }

        running.value = false
        runCurrent()

        job.isCompleted shouldBe true
        enqueued shouldBe listOf("watch.history.worker" to ExistingWorkPolicy.REPLACE)
        verify(exactly = 0) { sync.requestRerun() }
    }

    @Test
    fun `a run after the current one starts right away when nothing runs`() = runTest {
        scheduler.runAfterCurrent("test")

        enqueued shouldBe listOf("watch.history.worker" to ExistingWorkPolicy.REPLACE)
    }
}
