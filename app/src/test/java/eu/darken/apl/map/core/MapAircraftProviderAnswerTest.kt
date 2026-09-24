package eu.darken.apl.map.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.query.MapSnapshot
import eu.darken.apl.main.core.query.QuerySnapshot
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.MapAircraft
import eu.darken.apl.server.api.TrailPoint
import eu.darken.apl.server.api.UsageUpdate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

/**
 * The provider ticks on real time here, the unconfined test dispatcher has no virtual clock, so
 * each step waits a few ticks before looking at the newest frame.
 */
class MapAircraftProviderAnswerTest : BaseTest() {

    private val aircraftRepo = mockk<AircraftRepo>()
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = System.nanoTime() / 1_000_000
    })
    private val queries = mutableListOf<AircraftRepo.ViewingQuery.Map>()
    private val answers = MutableSharedFlow<AircraftRepo.MapViewingState>(extraBufferCapacity = 16)
    @Volatile private var latest: MapAircraftProvider.Frame? = null
    private var collector: Job? = null

    @AfterEach
    fun teardown() {
        collector?.cancel()
    }

    private fun provider(): MapAircraftProvider {
        every { aircraftRepo.mapViewing(any(), any()) } answers {
            val source = firstArg<Flow<AircraftRepo.ViewingQuery.Map>>()
            channelFlow {
                launch { source.collect { synchronized(queries) { queries.add(it) } } }
                answers.collect { send(it) }
            }
        }
        return MapAircraftProvider(aircraftRepo, serverClock, TestDispatcherProvider())
    }

    // Outside runBlocking's scope, which would otherwise wait for the endless frame stream
    private fun start(provider: MapAircraftProvider) {
        collector = CoroutineScope(Dispatchers.Default).launch { provider.frames.collect { latest = it } }
    }

    private suspend fun settle() = delay(350)

    private fun view(zoom: Double = 8.0) = MapViewport(south = 50.0, north = 52.0, west = 8.0, east = 12.0, zoom = zoom)

    // One level closer shows half the span, well inside what the wider view fetched
    private fun zoomedIn() = MapViewport(south = 50.5, north = 51.5, west = 9.0, east = 11.0, zoom = 9.0)

    private fun answer(
        query: AircraftRepo.ViewingQuery.Map,
        capped: Boolean = false,
        trail: List<TrailPoint>? = null,
    ): AircraftRepo.MapViewingState {
        val now = serverClock.now().toEpochMilli()
        return AircraftRepo.MapViewingState.Snapshot(
            MapSnapshot(
                query = query,
                trailSince = if (query.selected != null) 0L else null,
                aircraft = listOf(
                    MapAircraft(id = "3c65a3", position = AircraftPosition(51.0, 10.0, now), callsign = "DLH453"),
                ),
                selected = query.selected?.let { FakeAircraft(hex = it.uppercase(), callsign = "gen${query.selectionGeneration}") },
                trail = trail,
                trailReset = trail != null,
                complete = true,
                capped = capped,
                totalMatching = null,
                snapshot = QuerySnapshot(
                    serverTime = Instant.ofEpochMilli(now),
                    receivedAtElapsed = serverClock.elapsed(),
                    sourceTime = Instant.ofEpochMilli(now),
                    fetchedAt = Instant.ofEpochMilli(now),
                    expiresAt = Instant.ofEpochMilli(now + 120_000),
                    complete = true,
                    stale = false,
                    unpositionedAircraft = 0,
                ),
                usage = UsageUpdate("principal", "VIEWING", 0, Allowance(1, 0, 0, 1)),
            )
        )
    }

    private fun lastQuery() = synchronized(queries) { queries.last() }

    private fun queryCount() = synchronized(queries) { queries.size }

    @Test
    fun `an answer is drawn, a capped one is asked again when zooming in`() = runBlocking<Unit> {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 8.0))
        settle()

        answers.emit(answer(lastQuery(), capped = true))
        settle()
        latest?.traffic?.map { it.hex } shouldBe listOf("3C65A3")
        latest?.capped shouldBe true

        // Still inside what was fetched, but a capped answer may have left aircraft out
        val before = queryCount()
        provider.onViewport(zoomedIn())
        settle()
        (queryCount() > before) shouldBe true
    }

    @Test
    fun `an uncapped answer is not asked again when zooming in`() = runBlocking<Unit> {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 8.0))
        settle()

        answers.emit(answer(lastQuery(), capped = false))
        settle()
        val before = queryCount()
        provider.onViewport(zoomedIn())
        settle()

        queryCount() shouldBe before
    }

    @Test
    fun `a late answer for an earlier selection of the same aircraft is ignored`() = runBlocking<Unit> {
        val provider = provider()
        start(provider)
        provider.onViewport(view())
        provider.select("3C65A3")
        settle()
        val first = lastQuery()

        provider.select("3C65A3")
        settle()
        val second = lastQuery()
        (second.selectionGeneration > first.selectionGeneration) shouldBe true

        answers.emit(answer(first, trail = listOf(TrailPoint(50.0, 9.0, null, 1_000))))
        settle()
        latest?.selectedDetails shouldBe null
        latest?.selectedTrail shouldBe emptyList()

        answers.emit(answer(second, trail = listOf(TrailPoint(50.0, 9.0, null, 1_000), TrailPoint(50.1, 9.1, null, 6_000))))
        settle()
        latest?.selectedDetails?.callsign shouldBe "gen${second.selectionGeneration}"
        latest?.selectedTrail?.single()?.size shouldBe 2
    }

    @Test
    fun `an earlier selection's answer arriving after the current one changes nothing`() = runBlocking<Unit> {
        val provider = provider()
        start(provider)
        provider.onViewport(view())
        provider.select("3C65A3")
        settle()
        val first = lastQuery()
        provider.select("3C65A3")
        settle()
        val second = lastQuery()

        answers.emit(answer(second, trail = listOf(TrailPoint(50.0, 9.0, null, 1_000), TrailPoint(50.1, 9.1, null, 6_000))))
        settle()
        answers.emit(answer(first, trail = listOf(TrailPoint(40.0, 5.0, null, 500), TrailPoint(40.1, 5.1, null, 900))))
        settle()

        latest?.selectedDetails?.callsign shouldBe "gen${second.selectionGeneration}"
        latest?.selectedTrail?.single()?.map { it.observedAt } shouldBe listOf(1_000L, 6_000L)
    }
}
