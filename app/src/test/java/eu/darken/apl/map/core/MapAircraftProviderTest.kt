package eu.darken.apl.map.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.server.ServerClock
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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

    private fun provider(): MapAircraftProvider {
        every { aircraftRepo.mapViewing(any(), any()) } answers {
            val source = firstArg<Flow<AircraftRepo.ViewingQuery.Map>>()
            flow { source.collect { queries.add(it) } }
        }
        return MapAircraftProvider(aircraftRepo, serverClock, TestDispatcherProvider())
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
}
