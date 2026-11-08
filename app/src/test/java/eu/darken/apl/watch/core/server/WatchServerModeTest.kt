package eu.darken.apl.watch.core.server

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
import eu.darken.apl.watch.core.types.AircraftWatch
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchServerModeTest : BaseTest() {

    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }
    private val dao = mockk<WatchServerDao>()
    private val watchDb = mockk<WatchDatabase>().apply {
        every { server } returns dao
    }
    private val serverMode = WatchServerMode(accessRepo, watchDb)

    private fun watch(id: String = "w1") = AircraftWatch(
        BaseWatchEntity(id = id, watchType = AircraftWatchEntity.TYPE_KEY),
        AircraftWatchEntity(id = id, hexCode = "3C65A3"),
    )

    private fun state(
        id: String = "w1",
        serverWatchId: String? = null,
        uploadState: String = WatchServerStateEntity.UPLOAD_PENDING,
        installationId: String = INSTALLATION,
    ) = WatchServerStateEntity(
        watchId = id,
        installationId = installationId,
        serverWatchId = serverWatchId,
        uploadState = uploadState,
        updatedAt = Instant.EPOCH,
    )

    private fun active(vararg states: WatchServerStateEntity) =
        WatchServerMode.State(access = null, isActive = true, states = states.associateBy { it.watchId })

    @Test
    fun `without server mode no watch has a server status`() {
        val off = WatchServerMode.State(
            access = null,
            isActive = false,
            states = mapOf(
                "held" to state(id = "held", serverWatchId = "s1", uploadState = WatchServerStateEntity.UPLOAD_SYNCED),
                "refused" to state(id = "refused", uploadState = WatchServerStateEntity.UPLOAD_REJECTED_STORAGE),
            ),
        )

        off.serverStatus(watch(id = "held")) shouldBe null
        off.serverStatus(watch(id = "refused")) shouldBe null
    }

    @Test
    fun `in server mode a watch without a state row has no server status`() {
        active().serverStatus(watch()) shouldBe null
    }

    @Test
    fun `server mode needs the watch list on offer`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = false)

        serverMode.current().isActive shouldBe false
        serverMode.state.first().isActive shouldBe false
    }

    @Test
    fun `server mode is off while the stored server state is another installation's`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = true)
        coEvery { dao.hasOtherInstallation(INSTALLATION) } returns true
        every { dao.otherInstallation(INSTALLATION) } returns flowOf(true)
        every { dao.allStates() } returns flowOf(listOf(state(installationId = "other")))

        serverMode.current().isActive shouldBe false
        serverMode.state.first().isActive shouldBe false
    }

    @Test
    fun `server mode is on for the installation the server state belongs to, or without any`() = runBlocking<Unit> {
        accessState.value = access(offersWatchList = true)
        val held = state(serverWatchId = "s1", uploadState = WatchServerStateEntity.UPLOAD_SYNCED)
        coEvery { dao.hasOtherInstallation(INSTALLATION) } returns false
        coEvery { dao.getAllStates() } returns listOf(held)
        every { dao.otherInstallation(INSTALLATION) } returns flowOf(false)
        every { dao.allStates() } returns flowOf(listOf(held))

        val current = serverMode.current()
        current.isActive shouldBe true
        current.serverStatus(watch())?.isHeld shouldBe true
        serverMode.state.first() shouldBe current

        coEvery { dao.getAllStates() } returns emptyList()
        serverMode.current().serverStatus(watch()) shouldBe null
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
    }
}
