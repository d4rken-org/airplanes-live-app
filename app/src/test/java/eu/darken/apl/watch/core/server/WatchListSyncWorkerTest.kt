package eu.darken.apl.watch.core.server

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
class WatchListSyncWorkerTest {

    private val sync = mockk<WatchListSync>()
    private val scheduler = mockk<WatchListSyncScheduler>().also {
        every { it.afterRun(any()) } returns Unit
    }

    private fun worker(): WatchListSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<WatchListSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = WatchListSyncWorker(
                    context = appContext,
                    params = workerParameters,
                    dispatcherProvider = TestDispatcherProvider(),
                    sync = sync,
                    scheduler = scheduler,
                )
            })
            .build()
    }

    @Test
    fun `a refusal for an outdated app succeeds so appended work is not failed with it`() {
        runBlocking {
            coEvery { sync.run(any(), any()) } throws ServerApiException(
                code = ServerCodes.APP_UPDATE_REQUIRED,
                status = 403,
            )

            worker().doWork() shouldBe ListenableWorker.Result.success()

            verify(exactly = 0) { scheduler.afterRun(any()) }
        }
    }
}
