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
import eu.darken.apl.watch.core.db.server.WatchServerDao
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchRepoLimitTest : BaseTest() {

    private var savedWatches = 0
    private val accessState = MutableStateFlow<AccessState?>(null)

    private val serverDao = mockk<WatchServerDao>().apply {
        every { otherInstallation(any()) } returns flowOf(false)
        every { allStates() } returns flowOf(emptyList())
        coEvery { hasOtherInstallation(any()) } returns false
        coEvery { getAllStates() } returns emptyList()
    }

    private val allocation = Mutex()

    private val db = mockk<WatchDatabase>(relaxed = true).apply {
        every { server } returns serverDao
        coEvery { withAllocation<Any?>(any()) } coAnswers { allocation.withLock { firstArg<suspend () -> Any?>()() } }
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
        serverMode = WatchServerMode(accessRepo, db),
        syncScheduler = mockk(relaxed = true),
    )

    /** Real watches for the server mode cases, which count by type. */
    private val stored = mutableListOf<Watch>()

    private fun useStoredWatches() {
        every { db.watches } returns flow { emit(stored.toList()) }
        coEvery { db.createAircraft(any(), any()) } coAnswers {
            aircraft("new-${stored.size}", firstArg()).also { stored += it }
        }
    }

    private fun aircraft(id: String, hex: String = "3C65A3") = AircraftWatch(
        BaseWatchEntity(id = id, watchType = AircraftWatchEntity.TYPE_KEY),
        AircraftWatchEntity(id = id, hexCode = hex),
    )

    private fun squawk(id: String) = SquawkWatch(
        BaseWatchEntity(id = id, watchType = SquawkWatchEntity.TYPE_KEY),
        SquawkWatchEntity(id = id, code = "7700"),
    )

    private fun location(id: String, radiusKm: Int) = LocationWatch(
        BaseWatchEntity(
            id = id,
            watchType = LocationWatchEntity.TYPE_KEY,
            latitude = 50.0,
            longitude = 8.5,
            radius = radiusKm * 1000f,
        ),
        LocationWatchEntity(id = id, label = id),
    )

    private fun refused(id: String, uploadState: String) = WatchServerStateEntity(
        watchId = id,
        installationId = "installation",
        uploadState = uploadState,
        updatedAt = Instant.EPOCH,
    )

    private fun useServerStates(vararg states: WatchServerStateEntity) {
        every { serverDao.allStates() } returns flowOf(states.toList())
        coEvery { serverDao.getAllStates() } returns states.toList()
    }

    private fun serverAccess(
        tier: AccessState.Tier = AccessState.Tier.FREE,
        maxActive: Int? = 2,
        maxStored: Int? = 10,
    ) = accessStateOf(tier).let {
        it.copy(
            limits = it.limits.copy(
                watchTypes = listOf("hex", "callsign", "location"),
                maxLocationWatchRadiusKm = 25,
                maxActiveWatches = maxActive,
                maxStoredWatches = maxStored,
            ),
            aircraftOperations = listOf(AccessState.OPERATION_WATCHLIST),
        )
    }

    @Test
    fun `server mode counts only watches the tier evaluates against the active limit`() = runTest {
        accessState.value = serverAccess(maxActive = 2)
        useStoredWatches()
        stored += listOf(
            aircraft("a1"),
            squawk("s1"),
            location("far", radiusKm = 100),
        )
        val repo = create()

        repo.limit.first() shouldBe WatchLimit(
            count = 1,
            max = 2,
            active = WatchLimit.Cap(count = 1, max = 2),
            stored = WatchLimit.Cap(count = 3, max = 10),
        )

        repo.createAircraft("4B1805")
        stored.size shouldBe 4

        shouldThrow<WatchLimitReachedException> { repo.createAircraft("4B1806") }.max shouldBe 2
        stored.size shouldBe 4
    }

    @Test
    fun `server mode holds adding to the stored limit`() = runTest {
        accessState.value = serverAccess(maxActive = 10, maxStored = 3)
        useStoredWatches()
        stored += listOf(aircraft("a1"), aircraft("a2"), aircraft("a3"))
        val repo = create()

        repo.limit.first().canAdd shouldBe false
        shouldThrow<WatchLimitReachedException> { repo.createAircraft("4B1805") }.max shouldBe 3
        stored.size shouldBe 3
    }

    @Test
    fun `a feeder is shown no cap but is still held to the server's`() = runTest {
        accessState.value = serverAccess(tier = AccessState.Tier.FEEDER, maxActive = 2)
        useStoredWatches()
        stored += listOf(aircraft("a1"), aircraft("a2"))
        val repo = create()

        val limit = repo.limit.first()
        limit.max shouldBe null
        limit.canAdd shouldBe false
        limit.reached shouldBe WatchLimit.Reached(WatchLimit.Kind.ACTIVE, max = 2, isShown = false)
        shouldThrow<WatchLimitReachedException> { repo.createAircraft("4B1805") }.run {
            max shouldBe 2
            kind shouldBe WatchLimit.Kind.ACTIVE
            isShown shouldBe false
        }
    }

    @Test
    fun `a watch the server refused takes no active slot but stays stored`() = runTest {
        accessState.value = serverAccess(maxActive = 1, maxStored = 10)
        useStoredWatches()
        stored += listOf(aircraft("invalid"), aircraft("full"), aircraft("a1"))
        useServerStates(
            refused("invalid", WatchServerStateEntity.UPLOAD_REJECTED_INVALID),
            refused("full", WatchServerStateEntity.UPLOAD_REJECTED_STORAGE),
        )
        val repo = create()

        repo.limit.first() shouldBe WatchLimit(
            count = 1,
            max = 1,
            active = WatchLimit.Cap(count = 1, max = 1),
            stored = WatchLimit.Cap(count = 3, max = 10),
        )

        shouldThrow<WatchLimitReachedException> { repo.createAircraft("4B1805") }.max shouldBe 1
        stored.size shouldBe 3
    }

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
