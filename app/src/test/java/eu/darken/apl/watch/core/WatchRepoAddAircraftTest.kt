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
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchRepoAddAircraftTest : BaseTest() {

    private val saved = mutableListOf<Watch>()
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
        every { watches } returns flow { emit(saved.toList()) }
        coEvery { createAircraft(any(), any()) } coAnswers {
            // Long enough for a second add to interleave if the lookup and insert were not serialized
            delay(10)
            aircraftWatch(firstArg()).also { saved.add(it) }
        }
        coEvery { createFlight(any(), any()) } coAnswers {
            delay(10)
            mockk<FlightWatch>(relaxed = true).also { saved.add(it) }
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
    private val syncScheduler = mockk<WatchListSyncScheduler>(relaxed = true)

    private fun TestScope.create() = WatchRepo(
        appScope = backgroundScope,
        db = db,
        watchHistory = watchHistory,
        aircraftRepo = aircraftRepo,
        serverMode = WatchServerMode(accessRepo, db),
        syncScheduler = syncScheduler,
    )

    private fun aircraftWatch(hex: String) = AircraftWatch(
        base = BaseWatchEntity(id = "id-$hex", watchType = AircraftWatchEntity.TYPE_KEY),
        specific = AircraftWatchEntity(id = "id-$hex", hexCode = hex),
    )

    /** A free installation with every free slot taken, the first by [first]. */
    private fun fillFreeSlots(first: AircraftWatch) {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        saved.add(first)
        repeat(AccessState.FREE_MAX_WATCHES - 1) { saved.add(aircraftWatch("FILL%02d".format(it))) }
    }

    @Test
    fun `a new aircraft is created with a trimmed uppercase hex and no note`() = runTest {
        val repo = create()

        val result = repo.addAircraft(" abc123 ")

        result.shouldBeInstanceOf<WatchRepo.AircraftAdd.Created>()
        result.watch.hex shouldBe "ABC123"
        coVerify(exactly = 1) { db.createAircraft("ABC123", "") }
        verify(exactly = 1) { syncScheduler.requestSync(any()) }
    }

    @Test
    fun `an already watched aircraft is returned instead of a duplicate`() = runTest {
        val existing = aircraftWatch("ABC123").also { saved.add(it) }
        val repo = create()

        val result = repo.addAircraft("abc123")

        result shouldBe WatchRepo.AircraftAdd.Existing(existing)
        coVerify(exactly = 0) { db.createAircraft(any(), any()) }
        verify(exactly = 0) { syncScheduler.requestSync(any()) }
    }

    @Test
    fun `an aircraft saved with surrounding whitespace counts as watched`() = runTest {
        val existing = aircraftWatch(" abc123 ").also { saved.add(it) }
        val repo = create()

        repo.addAircraft("ABC123") shouldBe WatchRepo.AircraftAdd.Existing(existing)
        repo.findAircraft("ABC123") shouldBe existing
        coVerify(exactly = 0) { db.createAircraft(any(), any()) }
    }

    @Test
    fun `an already watched aircraft is found at the limit`() = runTest {
        val existing = aircraftWatch("ABC123")
        fillFreeSlots(existing)
        val repo = create()

        repo.addAircraft("ABC123") shouldBe WatchRepo.AircraftAdd.Existing(existing)
    }

    @Test
    fun `a new aircraft at the limit is refused`() = runTest {
        fillFreeSlots(aircraftWatch("ABC123"))
        val repo = create()

        val error = runCatching { repo.addAircraft("ABC124") }.exceptionOrNull()

        error.shouldBeInstanceOf<WatchLimitReachedException>()
        error.max shouldBe AccessState.FREE_MAX_WATCHES
        coVerify(exactly = 0) { db.createAircraft(any(), any()) }
    }

    @Test
    fun `concurrent adds of the same aircraft create one watch`() = runTest {
        val repo = create()

        val results = List(2) { async { repo.addAircraft("ABC123") } }.awaitAll()

        results.count { it is WatchRepo.AircraftAdd.Created } shouldBe 1
        results.count { it is WatchRepo.AircraftAdd.Existing } shouldBe 1
        saved.size shouldBe 1
    }

    @Test
    fun `an add and another create at the last free slot create one watch`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        repeat(AccessState.FREE_MAX_WATCHES - 1) { saved.add(aircraftWatch("FILL%02d".format(it))) }
        val repo = create()

        val results = listOf(
            async { runCatching { repo.addAircraft("ABC123") } },
            async { runCatching { repo.createFlight("DLH123") } },
        ).awaitAll()

        results.count { it.isSuccess } shouldBe 1
        results.count { it.exceptionOrNull() is WatchLimitReachedException } shouldBe 1
        saved.size shouldBe AccessState.FREE_MAX_WATCHES
    }

    @Test
    fun `with the server's watch list an already watched aircraft is found at the stored limit`() = runTest {
        accessState.value = serverAccess(maxActive = 10, maxStored = 2)
        val existing = aircraftWatch("ABC123").also { saved.add(it) }
        saved.add(aircraftWatch("ABC124"))
        val repo = create()

        repo.addAircraft("ABC123") shouldBe WatchRepo.AircraftAdd.Existing(existing)
    }

    @Test
    fun `with the server's watch list a new aircraft at the active limit is refused`() = runTest {
        accessState.value = serverAccess(maxActive = 1, maxStored = 10)
        saved.add(aircraftWatch("ABC123"))
        val repo = create()

        val error = runCatching { repo.addAircraft("ABC124") }.exceptionOrNull()

        error.shouldBeInstanceOf<WatchLimitReachedException>()
        error.kind shouldBe WatchLimit.Kind.ACTIVE
        error.max shouldBe 1
        coVerify(exactly = 0) { db.createAircraft(any(), any()) }
    }

    private fun serverAccess(maxActive: Int, maxStored: Int) = accessStateOf(AccessState.Tier.FREE).let {
        it.copy(
            limits = it.limits.copy(maxActiveWatches = maxActive, maxStoredWatches = maxStored),
            aircraftOperations = listOf(AccessState.OPERATION_WATCHLIST),
        )
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
