package eu.darken.apl.feeder.core.server

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FeederMonitorSyncWorkerTest {

    private val runner = mockk<FeederMonitorRunner>()
    private val scheduler = mockk<FeederMonitorSyncScheduler>().also {
        every { it.afterRun(any()) } returns Unit
    }

    private fun worker(): FeederMonitorSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<FeederMonitorSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = FeederMonitorSyncWorker(
                    context = appContext,
                    params = workerParameters,
                    dispatcherProvider = TestDispatcherProvider(),
                    runner = runner,
                    scheduler = scheduler,
                )
            })
            .build()
    }

    @Test
    fun `a server outage asks for a retry`() {
        runBlocking {
            listOf(
                ServerApiException(code = ServerCodes.SERVER_MAINTENANCE, status = 503),
                ServerApiException(code = "http_503", status = 503),
            ).forEach { error ->
                coEvery { runner.run(any(), any()) } throws error

                worker().doWork() shouldBe ListenableWorker.Result.retry()
            }

            verify(exactly = 0) { scheduler.afterRun(any()) }
        }
    }
}
