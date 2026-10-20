package eu.darken.apl.watch.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
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
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchRepoStatusTest : BaseTest() {

    private val accessState = MutableStateFlow<AccessState?>(access(offersWatchList = true))
    private var states = listOf<WatchServerStateEntity>()
    private var cache = mapOf<AircraftHex, Aircraft>()

    private val serverDao = mockk<WatchServerDao>().apply {
        every { otherInstallation(any()) } returns flowOf(false)
        every { allStates() } answers { flowOf(states) }
        coEvery { hasOtherInstallation(any()) } returns false
        coEvery { getAllStates() } answers { states }
    }
    private val watch = AircraftWatch(
        BaseWatchEntity(id = "w1", watchType = AircraftWatchEntity.TYPE_KEY),
        AircraftWatchEntity(id = "w1", hexCode = "3C65A3"),
    )
    private val db = mockk<WatchDatabase>(relaxed = true).apply {
        every { server } returns serverDao
        every { watches } returns flowOf(listOf(watch))
    }
    private val legacyCheck = WatchCheck("w1", Instant.parse("2026-10-01T10:00:00Z"), 1, setOf("3C65A3"))
    private val watchHistory = mockk<WatchHistoryRepo>().apply {
        every { firehose } returns flowOf(null)
        coEvery { getLastCheck(any()) } returns legacyCheck
        coEvery { getLastHit(any()) } returns legacyCheck
    }
    private val aircraftRepo = mockk<AircraftRepo>().apply {
        every { cache } answers { flowOf(this@WatchRepoStatusTest.cache) }
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

    private fun held(presence: String) = WatchServerStateEntity(
        watchId = "w1",
        installationId = INSTALLATION,
        serverWatchId = "server-1",
        serverRevision = 1,
        uploadState = WatchServerStateEntity.UPLOAD_SYNCED,
        ackedDefinitionRevision = 0,
        status = "active",
        presence = presence,
        lastMatchSeq = 7,
        lastMatchAt = MATCH_AT,
        lastMatchAircraftIds = "3c65a3,4b1805",
        lastMatchTotal = 3,
        updatedAt = MATCH_AT,
    )

    private suspend fun WatchRepo.single(): Watch.Status = status.first().single()

    @Test
    fun `a held watch that is present tracks the cached aircraft of its last match`() = runTest {
        states = listOf(held(presence = "present"))
        val cached = mockk<Aircraft>()
        cache = mapOf("3C65A3" to cached)

        val status = create().single()

        status.tracked shouldBe setOf(cached)
        status.lastHit shouldBe WatchCheck("w1", MATCH_AT, 3, setOf("3c65a3", "4b1805"))
        status.lastCheck shouldBe null
        status.server?.isHeld shouldBe true
        status.server?.status shouldBe WatchServerStatus.Status.ACTIVE
        status.server?.presence shouldBe WatchServerStatus.Presence.PRESENT
    }

    @Test
    fun `a held watch whose matched aircraft are not cached tracks nothing`() = runTest {
        states = listOf(held(presence = "present"))
        cache = emptyMap()

        val status = create().single()

        status.tracked shouldBe emptySet()
        status.lastHit?.checkAt shouldBe MATCH_AT
        status.lastCheck shouldBe null
    }

    @Test
    fun `a held watch that is absent tracks nothing but keeps its last match`() = runTest {
        states = listOf(held(presence = "absent"))
        cache = mapOf("3C65A3" to mockk())

        val status = create().single()

        status.tracked shouldBe emptySet()
        status.lastHit?.checkAt shouldBe MATCH_AT
    }

    @Test
    fun diagF4bHeldWatchMovedLocallyShowsNoEvidenceOfTheOldDefinition() = runTest {
        // The server's evidence is for definition 0, the local watch was moved to definition 1 and not uploaded yet
        states = listOf(held(presence = "present").copy(ackedDefinitionRevision = 0))
        val moved = AircraftWatch(
            BaseWatchEntity(id = "w1", watchType = AircraftWatchEntity.TYPE_KEY, definitionRevision = 1),
            AircraftWatchEntity(id = "w1", hexCode = "3C65A3"),
        )
        every { db.watches } returns flowOf(listOf(moved))
        cache = mapOf("3C65A3" to mockk())

        val status = create().single()

        status.server?.isHeld shouldBe true
        status.server?.presence shouldBe WatchServerStatus.Presence.UNKNOWN
        status.server?.lastMatch shouldBe null
        status.lastHit shouldBe null
        status.tracked shouldBe emptySet()
    }

    @Test
    fun `a watch the server does not hold yet keeps the app's own checks`() = runTest {
        states = listOf(
            WatchServerStateEntity(
                watchId = "w1",
                installationId = INSTALLATION,
                uploadState = WatchServerStateEntity.UPLOAD_PENDING,
                updatedAt = MATCH_AT,
            )
        )
        val cached = mockk<Aircraft>()
        cache = mapOf("3C65A3" to cached)

        val status = create().single()

        status.lastCheck shouldBe legacyCheck
        status.lastHit shouldBe legacyCheck
        status.tracked shouldBe setOf(cached)
        status.server?.isHeld shouldBe false
    }

    @Test
    fun `without server mode a stored server state is not shown`() = runTest {
        accessState.value = access(offersWatchList = false)
        states = listOf(held(presence = "present"))

        val status = create().single()

        status.lastCheck shouldBe legacyCheck
        status.server shouldBe null
    }

    private fun access(offersWatchList: Boolean) = AccessState(
        installationId = INSTALLATION,
        fetchedAt = Instant.EPOCH,
        tier = AccessState.Tier.FREE,
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
        aircraftOperations = if (offersWatchList) listOf(AccessState.OPERATION_WATCHLIST) else emptyList(),
    )

    companion object {
        private const val INSTALLATION = "installation"
        private val MATCH_AT = Instant.parse("2026-10-13T10:00:00Z")
    }
}
