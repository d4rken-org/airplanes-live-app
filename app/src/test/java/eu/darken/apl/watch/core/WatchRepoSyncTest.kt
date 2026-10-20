package eu.darken.apl.watch.core

import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchListSyncScheduler
import eu.darken.apl.watch.core.server.WatchServerMode
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class WatchRepoSyncTest : BaseTest() {

    private val db = mockk<WatchDatabase>(relaxed = true).apply {
        every { watches } returns flowOf(emptyList())
        coEvery { withAllocation<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>()() }
    }
    private val watchHistory = mockk<WatchHistoryRepo>(relaxed = true).apply {
        every { firehose } returns emptyFlow()
    }
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true).apply {
        every { cache } returns flowOf(emptyMap())
    }
    private val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns MutableStateFlow(null)
    }
    private val scheduler = mockk<WatchListSyncScheduler>(relaxed = true)

    private fun TestScope.create() = WatchRepo(
        appScope = backgroundScope,
        db = db,
        watchHistory = watchHistory,
        aircraftRepo = aircraftRepo,
        serverMode = WatchServerMode(accessRepo, db),
        syncScheduler = scheduler,
    )

    @Test
    fun `creating a watch requests a sync after it is stored`() = runTest {
        val repo = create()

        repo.createAircraft("3C65A3")
        repo.createFlight("DLH123")
        repo.createSquawk("7700")
        repo.createLocation(50.0, 8.5, 5_000f, "Home")

        coVerifyOrder {
            db.createAircraft("3C65A3", "")
            scheduler.requestSync(any())
            db.createFlight("DLH123", "")
            scheduler.requestSync(any())
            db.createSquawk("7700", "")
            scheduler.requestSync(any())
            db.createLocation(50.0, 8.5, 5_000f, "Home", "")
            scheduler.requestSync(any())
        }
    }

    @Test
    fun `changes the server has to learn about request a sync after they are stored`() = runTest {
        val repo = create()

        repo.delete("a")
        repo.deleteBatch(setOf("b", "c"))
        repo.updateLocation("d", 50.0, 8.5, 5_000f, "Home")

        coVerifyOrder {
            db.deleteWatch("a")
            scheduler.requestSync(any())
            db.deleteBatch(setOf("b", "c"))
            scheduler.requestSync(any())
            db.updateLocation("d", 50.0, 8.5, 5_000f, "Home")
            scheduler.requestSync(any())
        }
    }

    @Test
    fun `a note or notification change needs no sync`() = runTest {
        val repo = create()

        repo.updateNote("a", "note")
        repo.setNotification("a", true)

        verify(exactly = 0) { scheduler.requestSync(any()) }
    }
}
