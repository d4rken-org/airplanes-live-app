package eu.darken.apl.watch.core.alerts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.session.SessionRevokedException
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class WatchWorkerTest {

    private val watchMonitor = mockk<WatchMonitor>()

    private fun worker(): WatchWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<WatchWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = WatchWorker(
                    context = appContext,
                    params = workerParameters,
                    dispatcherProvider = TestDispatcherProvider(),
                    watchMonitor = watchMonitor,
                )
            })
            .build()
    }

    @Test
    fun `a completed check succeeds`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } returns WatchMonitor.CheckSummary(evaluated = 1)

            worker().doWork()::class shouldBe ListenableWorker.Result.success()::class
        }
    }

    @Test
    fun `a network failure asks for a retry`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } throws IOException("offline")

            worker().doWork() shouldBe ListenableWorker.Result.retry()
        }
    }

    @Test
    fun `a retryable server error asks for a retry`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } throws ServerApiException(
                code = "operation_in_progress",
                status = 409,
                retryAfterSeconds = 1,
            )

            worker().doWork() shouldBe ListenableWorker.Result.retry()
        }
    }

    @Test
    fun `a revoked installation fails without a retry`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } throws SessionRevokedException()

            worker().doWork()::class shouldBe ListenableWorker.Result.failure()::class
        }
    }
}
