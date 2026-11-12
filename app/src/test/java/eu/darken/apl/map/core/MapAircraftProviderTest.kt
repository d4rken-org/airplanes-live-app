package eu.darken.apl.map.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.server.ServerClock
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class MapAircraftProviderTest : BaseTest() {

    private val aircraftRepo = mockk<AircraftRepo>()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = 0L
    })
    private val queries = mutableListOf<AircraftRepo.ViewingQuery.Map>()

    /** With [dispatcher] on the test's scheduler, frames tick on virtual time. */
    private fun provider(dispatcher: CoroutineDispatcher? = null): MapAircraftProvider {
        every { aircraftRepo.mapViewing(any(), any(), any()) } answers {
            val source = firstArg<Flow<AircraftRepo.ViewingQuery.Map>>()
            flow { source.collect { queries.add(it) } }
        }
        return MapAircraftProvider(aircraftRepo, serverClock, TestDispatcherProvider(dispatcher))
    }

    private fun view(west: Double = 8.0, east: Double = 12.0, zoom: Double = 8.0) =
        MapViewport(south = 50.0, north = 52.0, west = west, east = east, zoom = zoom)

    @Test
    fun `small pans stay inside what was fetched, leaving it asks again`() = runTest {
        val provider = provider()
        val job = launch { provider.frames.collect { } }

        provider.onViewport(view())
        runCurrent()
        provider.onViewport(view(west = 8.5, east = 12.5))
        runCurrent()
        queries.size shouldBe 1
        queries.single().west shouldBe 7.0

        provider.onViewport(view(west = 10.0, east = 14.0))
        runCurrent()
        job.cancel()

        queries.map { it.west } shouldBe listOf(7.0, 9.0)
    }

    @Test
    fun `zooming far into what was fetched asks for the smaller area`() = runTest {
        val provider = provider()
        val job = launch { provider.frames.collect { } }

        provider.onViewport(view())
        runCurrent()
        // Two levels closer, the fetched area is 16 times what the new view needs
        provider.onViewport(MapViewport(south = 50.75, north = 51.25, west = 9.5, east = 10.5, zoom = 10.0))
        runCurrent()
        job.cancel()

        queries.map { it.west } shouldBe listOf(7.0, 9.25)
    }

    @Test
    fun `aircraft keep their drawn position until they moved about a dp`() {
        fun interval(zoom: Double) = MapAircraftProvider.moveInterval(view(zoom = zoom)).inWholeMilliseconds

        interval(8.0) shouldBe 1_662L
        interval(12.0) shouldBe 103L
        // Far out an answer brings new positions sooner, close up the tick is the limit
        interval(4.0) shouldBe 5_000L
        interval(14.0) shouldBe 100L
        MapAircraftProvider.moveInterval(null).inWholeMilliseconds shouldBe 5_000L
    }

    @Test
    fun `every selection asks again, also for the same aircraft`() = runTest {
        val provider = provider()
        val job = launch { provider.frames.collect { } }
        provider.onViewport(view())
        runCurrent()

        provider.select("3C65A3")
        runCurrent()
        provider.select("3C65A3")
        runCurrent()
        job.cancel()

        queries.map { it.selected } shouldBe listOf(null, "3c65a3", "3c65a3")
        queries.map { it.selectionGeneration }.distinct().size shouldBe 3
    }

    @Test
    fun `switching military only asks again for the same view`() = runTest {
        val provider = provider()
        val job = launch { provider.frames.collect { } }
        provider.onViewport(view())
        runCurrent()

        provider.setFilter(MapAircraftProvider.Filter(militaryOnly = true))
        runCurrent()
        // Isolation is drawn from what was fetched
        provider.setFilter(MapAircraftProvider.Filter(militaryOnly = true, isolated = setOf("3C65A3")))
        runCurrent()
        provider.setFilter(MapAircraftProvider.Filter())
        runCurrent()
        job.cancel()

        queries.map { it.militaryOnly } shouldBe listOf(false, true, false)
        queries.map { it.west }.distinct() shouldBe listOf(7.0)
    }

    @Test
    fun `the list's search and watched filter ask nothing new`() = runTest {
        val provider = provider()
        val job = launch { provider.frames.collect { } }
        provider.onViewport(view())
        runCurrent()

        provider.setFilter(MapAircraftProvider.Filter(query = "dlh"))
        runCurrent()
        provider.setFilter(MapAircraftProvider.Filter(query = "dlh", watches = MapWatches(hexes = setOf("3C65A3"))))
        runCurrent()
        provider.setFilter(MapAircraftProvider.Filter())
        runCurrent()
        job.cancel()

        queries.size shouldBe 1
    }

    @Test
    fun `switching kinds asks again for the same view`() = runTest {
        val provider = provider()
        val job = launch { provider.frames.collect { } }
        provider.onViewport(view())
        runCurrent()

        provider.setFilter(MapAircraftProvider.Filter(kinds = setOf(AircraftKind.GLIDER, AircraftKind.HELICOPTER)))
        runCurrent()
        provider.setFilter(MapAircraftProvider.Filter())
        runCurrent()
        job.cancel()

        queries.map { it.kinds } shouldBe listOf(emptyList(), listOf("helicopter", "glider"), emptyList())
        queries.map { it.west }.distinct() shouldBe listOf(7.0)
    }

    @Test
    fun `pinned aircraft are sent lowercase and capped at the server limit`() = runTest {
        val provider = provider()
        val job = launch { provider.frames.collect { } }
        provider.pin((0 until 150).map { "%06X".format(it) })
        provider.onViewport(view())
        runCurrent()
        advanceTimeBy(100)
        job.cancel()

        queries.last().pinned.size shouldBe MapAircraftProvider.MAX_PINNED
        queries.last().pinned.first() shouldBe "000000"
    }

    @Test
    fun `the next frame reports the map filter it was built with`() = runTest {
        val provider = provider(StandardTestDispatcher(testScheduler))
        val frames = mutableListOf<MapAircraftProvider.Frame>()
        val job = launch { provider.frames.collect { frames += it } }
        provider.onViewport(view())
        runCurrent()
        frames.last().mapFilters shouldBe emptyList()

        provider.setFilter(MapAircraftProvider.Filter(militaryOnly = true, kinds = setOf(AircraftKind.HELICOPTER)))
        advanceTimeBy(100)
        runCurrent()
        frames.last().mapFilters shouldBe listOf(
            MapSidebarData.MapFilter.MilitaryOnly,
            MapSidebarData.MapFilter.Kinds(setOf(AircraftKind.HELICOPTER)),
        )

        // Isolated aircraft are exempt from the other filters
        provider.setFilter(MapAircraftProvider.Filter(militaryOnly = true, isolated = setOf("3C65A3")))
        advanceTimeBy(100)
        runCurrent()
        job.cancel()

        frames.last().mapFilters shouldBe listOf(MapSidebarData.MapFilter.Isolated)
    }
}
