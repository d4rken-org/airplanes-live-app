package eu.darken.apl.common.location

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class BoundedLookupTest : BaseTest() {

    @Test
    fun `quick lookup returns its result`() = runTest {
        val result = awaitBounded(backgroundScope, StandardTestDispatcher(testScheduler), 5.seconds) { "Berlin" }

        result shouldBe Result.success("Berlin")
        currentTime shouldBe 0L
    }

    @Test
    fun `failing lookup returns the failure`() = runTest {
        val result = awaitBounded(backgroundScope, StandardTestDispatcher(testScheduler), 5.seconds) {
            throw IOException("service not available")
        }

        result!!.exceptionOrNull().shouldBeInstanceOf<IOException>()
        currentTime shouldBe 0L
    }

    @Test
    fun `lookup that never returns gives null after the timeout`() = runTest {
        val release = CountDownLatch(1)
        try {
            val result = awaitBounded(backgroundScope, Dispatchers.IO, 5.seconds) {
                release.await()
                "late"
            }

            result.shouldBeNull()
            currentTime shouldBe 5_000L
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `abandoned lookup failing later does not fail its scope`() = runTest {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val unhandled = mutableListOf<Throwable>()
        val lookupJob = Job()
        val lookupScope = CoroutineScope(lookupJob + CoroutineExceptionHandler { _, e -> unhandled.add(e) })

        val result = async {
            awaitBounded(lookupScope, Dispatchers.IO, 5.seconds) {
                started.countDown()
                try {
                    release.await()
                    throw IOException("too late")
                } finally {
                    finished.countDown()
                }
            }
        }
        runCurrent()
        // Otherwise the virtual timeout could cancel the lookup before the IO thread starts it.
        started.await(1, TimeUnit.SECONDS) shouldBe true
        result.await().shouldBeNull()

        release.countDown()
        finished.await(1, TimeUnit.SECONDS) shouldBe true
        lookupJob.complete()
        lookupJob.join()

        lookupJob.isCancelled shouldBe false
        unhandled shouldBe emptyList()
    }
}
