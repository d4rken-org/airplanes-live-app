package eu.darken.apl.main.core.request

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class RequestCoordinatorTest : BaseTest() {

    private val accessRepo = mockk<AccessRepo>()
    private val accessState = MutableStateFlow<AccessState?>(null)

    private fun TestScope.coordinator(): RequestCoordinator {
        every { accessRepo.state } returns accessState
        val clock = object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        }
        return RequestCoordinator(accessRepo, clock)
    }

    private fun policy(
        rate: RequestRate = RequestRate(perSecond = 1000.0, burst = 1000),
        concurrency: Int = 3,
    ) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = AccessState.Tier.FREE,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5,
            viewingBurst = 3,
            viewingPerDay = 25000,
            searchesPerDay = 25,
            watchesPerDay = 1000,
            maxRadiusNm = 25,
            watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 0L,
            viewing = Allowance(1, 0, 0, 1),
            search = Allowance(1, 0, 0, 1),
            watch = Allowance(1, 0, 0, 1),
        ),
        installationRequests = RequestLimits(
            viewing = rate,
            search = rate,
            watch = rate,
            concurrency = concurrency,
        ),
    )

    @Test
    fun `a call beyond the concurrency limit waits for a slot`() = runTest {
        accessState.value = policy(concurrency = 3)
        val coordinator = coordinator()
        val startedAt = mutableListOf<Long>()

        val jobs = (1..4).map {
            launch {
                coordinator.execute(Bucket.WATCH) {
                    startedAt.add(testScheduler.currentTime)
                    delay(1_000)
                }
            }
        }
        jobs.joinAll()

        startedAt.size shouldBe 4
        startedAt.take(3).toSet() shouldBe setOf(0L)
        (startedAt[3] >= 1_000L) shouldBe true
    }

    @Test
    fun `a throttled request holds the bucket for the retry hint`() = runTest {
        accessState.value = policy()
        val coordinator = coordinator()

        shouldThrow<ServerApiException> {
            coordinator.execute(Bucket.SEARCH) {
                throw ServerApiException(
                    code = "installation_rate_exceeded",
                    status = 429,
                    retryAfterSeconds = 2,
                )
            }
        }

        var secondCallAt = -1L
        launch {
            coordinator.execute(Bucket.SEARCH) { secondCallAt = testScheduler.currentTime }
        }.join()

        secondCallAt shouldBe 2_000L
    }

    @Test
    fun `requests are paced by the policy rate`() = runTest {
        accessState.value = policy(rate = RequestRate(perSecond = 1.0, burst = 1))
        val coordinator = coordinator()
        val startedAt = mutableListOf<Long>()

        launch {
            repeat(3) {
                coordinator.execute(Bucket.SEARCH) { startedAt.add(testScheduler.currentTime) }
            }
        }.join()

        startedAt shouldBe listOf(0L, 1_000L, 2_000L)
    }
}
