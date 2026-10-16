package eu.darken.apl.watch.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.Watch
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchRepoLimitTest : BaseTest() {

    private var savedWatches = 0
    private val accessState = MutableStateFlow<AccessState?>(null)

    private val db = mockk<WatchDatabase>(relaxed = true).apply {
        // Read at collection time, like the Room query it stands in for
        every { watches } returns flow { emit(List(savedWatches) { mockk<Watch>(relaxed = true) }) }
        coEvery { createAircraft(any(), any()) } coAnswers {
            // Long enough for a second create to interleave if the count and insert were not serialized
            delay(10)
            savedWatches++
            mockk(relaxed = true)
        }
        coEvery { createFlight(any(), any()) } coAnswers { savedWatches++; mockk(relaxed = true) }
        coEvery { createSquawk(any(), any()) } coAnswers { savedWatches++; mockk(relaxed = true) }
        coEvery { createLocation(any(), any(), any(), any(), any()) } coAnswers {
            savedWatches++
            mockk(relaxed = true)
        }
    }
    private val watchHistory = mockk<WatchHistoryRepo>(relaxed = true).apply {
        every { firehose } returns emptyFlow()
    }
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true).apply {
        every { cache } returns flowOf(emptyMap())
    }
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }

    private fun TestScope.create() = WatchRepo(
        appScope = backgroundScope,
        db = db,
        watchHistory = watchHistory,
        aircraftRepo = aircraftRepo,
        accessRepo = accessRepo,
    )

    @Test
    fun `free tier adds up to the limit`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        savedWatches = AccessState.FREE_MAX_WATCHES - 1
        val repo = create()

        repo.createAircraft("ABC123")
        savedWatches shouldBe AccessState.FREE_MAX_WATCHES

        shouldThrow<WatchLimitReachedException> { repo.createAircraft("ABC124") }
            .max shouldBe AccessState.FREE_MAX_WATCHES
        savedWatches shouldBe AccessState.FREE_MAX_WATCHES
    }

    @Test
    fun `watches saved above the limit are kept, only adding stops`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        savedWatches = 23
        val repo = create()

        shouldThrow<WatchLimitReachedException> { repo.createFlight("DLH123") }
        savedWatches shouldBe 23
        coVerify(exactly = 0) { db.deleteWatch(any()) }
        coVerify(exactly = 0) { db.deleteBatch(any()) }
    }

    @Test
    fun `every watch type is held to the limit`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        savedWatches = AccessState.FREE_MAX_WATCHES
        val repo = create()

        shouldThrow<WatchLimitReachedException> { repo.createAircraft("ABC123") }
        shouldThrow<WatchLimitReachedException> { repo.createFlight("DLH123") }
        shouldThrow<WatchLimitReachedException> { repo.createSquawk("7700") }
        shouldThrow<WatchLimitReachedException> { repo.createLocation(52.0, 13.0, 5000f, "Home") }
        savedWatches shouldBe AccessState.FREE_MAX_WATCHES
    }

    @Test
    fun `upgraded tier has no limit`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FEEDER)
        savedWatches = 50
        val repo = create()

        repo.createAircraft("ABC123")
        savedWatches shouldBe 51
    }

    @Test
    fun `access that was never fetched does not block adding`() = runTest {
        savedWatches = 50
        val repo = create()

        repo.createAircraft("ABC123")
        savedWatches shouldBe 51
    }

    @Test
    fun `persisted access is restored before the limit is decided`() = runTest {
        coEvery { accessRepo.awaitLoaded() } answers { accessState.value = accessStateOf(AccessState.Tier.FREE) }
        savedWatches = AccessState.FREE_MAX_WATCHES
        val repo = create()

        shouldThrow<WatchLimitReachedException> { repo.createAircraft("ABC123") }
    }

    @Test
    fun `concurrent adds at the last free slot create one watch`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        savedWatches = AccessState.FREE_MAX_WATCHES - 1
        val repo = create()

        val results = listOf("ABC123", "ABC124")
            .map { hex -> async { runCatching { repo.createAircraft(hex) } } }
            .awaitAll()

        results.count { it.isSuccess } shouldBe 1
        results.count { it.exceptionOrNull() is WatchLimitReachedException } shouldBe 1
        savedWatches shouldBe AccessState.FREE_MAX_WATCHES
    }

    @Test
    fun `limit follows saved watches and tier`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        every { db.watches } returns flowOf(List(AccessState.FREE_MAX_WATCHES) { mockk(relaxed = true) })
        val repo = create()

        repo.limit.first() shouldBe WatchLimit(count = AccessState.FREE_MAX_WATCHES, max = AccessState.FREE_MAX_WATCHES)
        repo.limit.first().canAdd shouldBe false

        accessState.value = accessStateOf(AccessState.Tier.FEEDER)
        repo.limit.first() shouldBe WatchLimit(count = AccessState.FREE_MAX_WATCHES, max = null)
        repo.limit.first().canAdd shouldBe true
    }

    private fun accessStateOf(tier: AccessState.Tier) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = tier,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5, viewingBurst = 3, viewingPerDay = 25000, searchesPerDay = 25,
            watchesPerDay = 1000, maxRadiusNm = 25, watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 3_600_000,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 0.2, burst = 3),
            search = RequestRate(perSecond = 1.0, burst = 3),
            watch = RequestRate(perSecond = 1.0, burst = 3),
            concurrency = 3,
        ),
    )
}
