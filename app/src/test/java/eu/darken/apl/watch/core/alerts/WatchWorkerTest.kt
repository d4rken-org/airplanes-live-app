package eu.darken.apl.watch.core.alerts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.Bugs
import eu.darken.apl.feeder.core.server.FeederMonitorRunner
import eu.darken.apl.feeder.core.server.FeederMonitorSync
import eu.darken.apl.feeder.core.server.FeederMonitorSyncScheduler
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.session.SessionRevokedException
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchWorkerTest {

    private val watchMonitor = mockk<WatchMonitor>()
    private val feederRunner = mockk<FeederMonitorRunner>().apply {
        coEvery { run(any(), any()) } returns FeederMonitorSync.Result.Inactive
    }
    private val feederScheduler = mockk<FeederMonitorSyncScheduler>().apply {
        every { afterRun(any()) } returns Unit
    }

    @After
    fun teardown() {
        unmockkObject(Bugs)
    }

    private fun worker(dispatcherProvider: DispatcherProvider = TestDispatcherProvider()): WatchWorker {
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
                    dispatcherProvider = dispatcherProvider,
                    watchMonitor = watchMonitor,
                    feederMonitorRunner = feederRunner,
                    feederMonitorSyncScheduler = feederScheduler,
                )
            })
            .build()
    }

    @Test
    fun `a completed check succeeds`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } just Runs

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
    fun `a refusal for an outdated app fails without a bug report`() {
        runBlocking {
            mockkObject(Bugs)
            coEvery { watchMonitor.check(any()) } throws ServerApiException(
                code = ServerCodes.APP_UPDATE_REQUIRED,
                status = 403,
            )

            worker().doWork()::class shouldBe ListenableWorker.Result.failure()::class

            verify(exactly = 0) { Bugs.report(any()) }
        }
    }

    @Test
    fun `another refusal fails with a bug report`() {
        runBlocking {
            mockkObject(Bugs)
            coEvery { watchMonitor.check(any()) } throws ServerApiException(
                code = ServerCodes.INVALID_REQUEST,
                status = 400,
            )

            worker().doWork()::class shouldBe ListenableWorker.Result.failure()::class

            verify(exactly = 1) { Bugs.report(any()) }
        }
    }

    @Test
    fun `a check that runs into the timeout asks for a retry`() {
        runTest {
            coEvery { watchMonitor.check(any()) } coAnswers { awaitCancellation() }
            val worker = worker(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))

            val result = async { worker.doWork() }
            advanceUntilIdle()

            result.await() shouldBe ListenableWorker.Result.retry()
        }
    }

    @Test
    fun `a revoked installation fails without a retry`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } throws SessionRevokedException()

            worker().doWork()::class shouldBe ListenableWorker.Result.failure()::class
        }
    }

    @Test
    fun `the feeder step runs after a completed check`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } just Runs
            coEvery { feederRunner.run(any(), any()) } returns FEEDER_DONE

            worker().doWork()::class shouldBe ListenableWorker.Result.success()::class

            coVerify(exactly = 1) { feederRunner.run("watch-worker", false) }
            verify(exactly = 1) { feederScheduler.afterRun(FEEDER_DONE) }
        }
    }

    @Test
    fun `the feeder step runs after a check that ran into the timeout`() {
        runTest {
            coEvery { watchMonitor.check(any()) } coAnswers { awaitCancellation() }
            coEvery { feederRunner.run(any(), any()) } returns FEEDER_DONE
            val worker = worker(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))

            val result = async { worker.doWork() }
            advanceUntilIdle()

            result.await() shouldBe ListenableWorker.Result.retry()
            coVerify(exactly = 1) { feederRunner.run("watch-worker", false) }
            verify(exactly = 1) { feederScheduler.afterRun(FEEDER_DONE) }
        }
    }

    @Test
    fun `the feeder step runs after a failed check`() {
        runBlocking {
            mockkObject(Bugs)
            coEvery { watchMonitor.check(any()) } throws ServerApiException(
                code = ServerCodes.INVALID_REQUEST,
                status = 400,
            )

            worker().doWork()::class shouldBe ListenableWorker.Result.failure()::class

            coVerify(exactly = 1) { feederRunner.run("watch-worker", false) }
        }
    }

    @Test
    fun `a failing feeder step keeps the check's result`() {
        runBlocking {
            mockkObject(Bugs)
            coEvery { watchMonitor.check(any()) } just Runs

            coEvery { feederRunner.run(any(), any()) } throws IOException("offline")
            worker().doWork()::class shouldBe ListenableWorker.Result.success()::class

            coEvery { feederRunner.run(any(), any()) } throws SessionRevokedException()
            worker().doWork()::class shouldBe ListenableWorker.Result.success()::class

            coEvery { feederRunner.run(any(), any()) } throws ServerApiException(code = ServerCodes.INVALID_REQUEST, status = 400)
            worker().doWork()::class shouldBe ListenableWorker.Result.success()::class

            verify(exactly = 0) { Bugs.report(any()) }

            coEvery { feederRunner.run(any(), any()) } throws IllegalStateException("broken")
            worker().doWork()::class shouldBe ListenableWorker.Result.success()::class

            verify(exactly = 0) { feederScheduler.afterRun(any()) }
        }
    }

    @Test
    fun `a failing feeder step keeps a retry of the check`() {
        runBlocking {
            coEvery { watchMonitor.check(any()) } throws IOException("offline")
            coEvery { feederRunner.run(any(), any()) } throws IllegalStateException("broken")

            worker().doWork() shouldBe ListenableWorker.Result.retry()
        }
    }

    @Test
    fun `a feeder step that runs into its timeout keeps the check's result`() {
        runTest {
            coEvery { watchMonitor.check(any()) } just Runs
            coEvery { feederRunner.run(any(), any()) } coAnswers { awaitCancellation() }
            val worker = worker(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))

            val result = async { worker.doWork() }
            advanceUntilIdle()

            result.await()::class shouldBe ListenableWorker.Result.success()::class
            verify(exactly = 0) { feederScheduler.afterRun(any()) }
        }
    }

    companion object {
        private val FEEDER_DONE = FeederMonitorSync.Result.Done(moreWork = true)
    }
}
