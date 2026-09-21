package eu.darken.apl.main.core.request

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
    fun `a cancelled call gives its slot back`() = runTest {
        accessState.value = policy(concurrency = 3)
        val coordinator = coordinator()

        val cancelled = launch { coordinator.execute(Bucket.WATCH) { delay(10_000) } }
        runCurrent()
        cancelled.cancelAndJoin()

        val startedAt = mutableListOf<Long>()
        val jobs = (1..3).map {
            launch {
                coordinator.execute(Bucket.WATCH) {
                    startedAt.add(testScheduler.currentTime)
                    delay(1_000)
                }
            }
        }
        jobs.joinAll()

        // A leaked slot would push the last of the three into the slot poll
        startedAt.size shouldBe 3
        startedAt.toSet() shouldBe setOf(0L)
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
    fun `an upgrade releases the allowance hold it made moot`() = runTest {
        accessState.value = policy()
        val coordinator = coordinator()

        shouldThrow<ServerApiException> {
            coordinator.execute(Bucket.VIEWING) {
                throw ServerApiException(
                    code = ServerCodes.DAILY_ALLOWANCE_EXHAUSTED,
                    status = 429,
                    retryAfterSeconds = 60,
                )
            }
        }

        // The allowance that ran out is not the one in force anymore
        accessState.value = policy().copy(tier = AccessState.Tier.FEEDER)

        var ranAt = -1L
        launch { coordinator.execute(Bucket.VIEWING) { ranAt = testScheduler.currentTime } }.join()

        ranAt shouldBe 0L
    }

    @Test
    fun `an upgrade does not release a rate hold`() = runTest {
        accessState.value = policy()
        val coordinator = coordinator()

        shouldThrow<ServerApiException> {
            coordinator.execute(Bucket.VIEWING) {
                throw ServerApiException(
                    code = ServerCodes.INSTALLATION_RATE_EXCEEDED,
                    status = 429,
                    retryAfterSeconds = 2,
                )
            }
        }

        accessState.value = policy().copy(tier = AccessState.Tier.FEEDER)

        var ranAt = -1L
        launch { coordinator.execute(Bucket.VIEWING) { ranAt = testScheduler.currentTime } }.join()

        ranAt shouldBe 2_000L
    }

    @Test
    fun `an allowance hold does not swallow a rate hold on the same bucket`() = runTest {
        accessState.value = policy()
        val coordinator = coordinator()

        // Both calls have to be admitted before either rejects, or the second simply waits out the
        // first hold and the two deadlines never coexist
        val release = CompletableDeferred<Unit>()
        val rateCall = launch {
            shouldThrow<ServerApiException> {
                coordinator.execute(Bucket.VIEWING) {
                    release.await()
                    throw ServerApiException(
                        code = ServerCodes.INSTALLATION_RATE_EXCEEDED,
                        status = 429,
                        retryAfterSeconds = 30,
                    )
                }
            }
        }
        val allowanceCall = launch {
            shouldThrow<ServerApiException> {
                coordinator.execute(Bucket.VIEWING) {
                    release.await()
                    throw ServerApiException(
                        code = ServerCodes.DAILY_ALLOWANCE_EXHAUSTED,
                        status = 429,
                        retryAfterSeconds = 60,
                    )
                }
            }
        }
        runCurrent()
        release.complete(Unit)
        rateCall.join()
        allowanceCall.join()

        // Dropping the longer allowance hold must not admit what the rate limit still forbids
        accessState.value = policy().copy(tier = AccessState.Tier.FEEDER)

        var ranAt = -1L
        launch { coordinator.execute(Bucket.VIEWING) { ranAt = testScheduler.currentTime } }.join()

        ranAt shouldBe 30_000L
    }

    @Test
    fun `an upgrade reaches a caller that is already waiting`() = runTest {
        accessState.value = policy()
        val coordinator = coordinator()

        shouldThrow<ServerApiException> {
            coordinator.execute(Bucket.VIEWING) {
                throw ServerApiException(
                    code = ServerCodes.DAILY_ALLOWANCE_EXHAUSTED,
                    status = 429,
                    retryAfterSeconds = 600,
                )
            }
        }

        var ranAt = -1L
        val waiter = launch { coordinator.execute(Bucket.VIEWING) { ranAt = testScheduler.currentTime } }
        // Let it start sleeping on the hold before the entitlement that imposed it is replaced
        advanceTimeBy(5_000)
        accessState.value = policy().copy(tier = AccessState.Tier.FEEDER)
        waiter.join()

        (ranAt < 600_000L) shouldBe true
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

    @Test
    fun `a call waiting for a token honours a hold imposed meanwhile`() = runTest {
        accessState.value = policy(rate = RequestRate(perSecond = 1.0, burst = 3), concurrency = 10)
        val coordinator = coordinator()
        repeat(3) { coordinator.execute(Bucket.SEARCH) { } }

        var fourthAt = -1L
        val waiter = launch {
            coordinator.execute(Bucket.SEARCH) { fourthAt = testScheduler.currentTime }
        }
        runCurrent()

        shouldThrow<ServerApiException> {
            coordinator.execute(Bucket.WATCH) {
                throw ServerApiException(code = "quota_exceeded", status = 429, retryAfterSeconds = 10)
            }
        }
        val heldAt = testScheduler.currentTime
        waiter.join()

        fourthAt shouldBeGreaterThanOrEqual heldAt + 10_000
    }
}
