package eu.darken.apl.watch.core.server

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class WatchListSyncSchedulerTest : BaseTest() {

    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    }).apply { noteServerTime(NOW.toEpochMilli()) }

    /** Unique work name and initial delay of every enqueue. */
    private val enqueued = mutableListOf<Pair<String, Duration>>()

    private val workManager = mockk<WorkManager>().apply {
        every { enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) } answers {
            enqueued += firstArg<String>() to Duration.ofMillis(thirdArg<OneTimeWorkRequest>().workSpec.initialDelay)
            mockk(relaxed = true)
        }
    }

    private val scheduler = WatchListSyncScheduler(
        appScope = CoroutineScope(Dispatchers.Unconfined),
        workManager = workManager,
        serverClock = serverClock,
        accessRepo = mockk<AccessRepo>(relaxed = true),
    )

    @Test
    fun diagF6MoreWorkIsContinuedRightAwayEvenWithADeadline() {
        scheduler.afterRun(WatchListSync.Result.Done(moreWork = true, resumeAt = NOW.plus(Duration.ofHours(1))))

        withClue("enqueued: $enqueued") {
            enqueued.size shouldBe 2
            enqueued.map { it.first }.toSet().size shouldBe 2
            enqueued.count { it.second == Duration.ofHours(1) } shouldBe 1
            enqueued.count { it.second <= Duration.ofMinutes(1) } shouldBe 1
        }
    }

    @Test
    fun diagF9DeadlineQueueDoesNotRunTheSyncItself() {
        val requests = mutableListOf<Pair<String, OneTimeWorkRequest>>()
        every { workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) } answers {
            requests += firstArg<String>() to thirdArg<OneTimeWorkRequest>()
            mockk(relaxed = true)
        }

        scheduler.afterRun(WatchListSync.Result.Done(moreWork = true, resumeAt = NOW.plus(Duration.ofHours(1))))

        val described = requests.map { (name, request) -> name to request.workSpec.workerClassName }
        withClue("enqueued: $described") {
            val deadline = requests.single { it.first == "watch.list.sync.deadline" }.second
            deadline.workSpec.workerClassName shouldNotBe WatchListSyncWorker::class.java.name
            requests.filter { it.first == "watch.list.sync" }.forEach { (_, request) ->
                request.workSpec.workerClassName shouldBe WatchListSyncWorker::class.java.name
            }
        }
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-18T12:00:00Z")
    }
}
