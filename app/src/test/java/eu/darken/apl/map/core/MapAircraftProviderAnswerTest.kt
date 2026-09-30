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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

/** The provider's ticker, the clock and the answers all run on the test scheduler's virtual time. */
class MapAircraftProviderAnswerTest : BaseTest() {

    private val aircraftRepo = mockk<AircraftRepo>()
    private lateinit var serverClock: ServerClock
    private val queries = mutableListOf<AircraftRepo.ViewingQuery.Map>()
    private val answers = MutableSharedFlow<AircraftRepo.MapViewingState>(extraBufferCapacity = 16)
    private var latest: MapAircraftProvider.Frame? = null

    private fun TestScope.provider(): MapAircraftProvider {
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        }).apply { noteServerTime(SERVER_TIME) }
        every { aircraftRepo.mapViewing(any(), any(), any()) } answers {
            val source = firstArg<Flow<AircraftRepo.ViewingQuery.Map>>()
            channelFlow {
                launch { source.collect { synchronized(queries) { queries.add(it) } } }
                answers.collect { send(it) }
            }
        }
        return MapAircraftProvider(aircraftRepo, serverClock, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
    }

    private fun TestScope.start(provider: MapAircraftProvider) {
        backgroundScope.launch { provider.frames.collect { latest = it } }
    }

    private fun TestScope.settle(millis: Long = 350) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun view(zoom: Double = 8.0) = MapViewport(south = 50.0, north = 52.0, west = 8.0, east = 12.0, zoom = zoom)

    // One level closer shows half the span, well inside what the wider view fetched
    private fun zoomedIn() = MapViewport(south = 50.5, north = 51.5, west = 9.0, east = 11.0, zoom = 9.0)

    private fun answer(
        query: AircraftRepo.ViewingQuery.Map,
        capped: Boolean = false,
        trail: List<TrailPoint>? = null,
        farAway: Boolean = false,
        age: Long = 0,
    ): AircraftRepo.MapViewingState {
        val now = serverClock.now().toEpochMilli()
        return AircraftRepo.MapViewingState.Snapshot(
            MapSnapshot(
                query = query,
                trailSince = if (query.selected != null) 0L else null,
                aircraft = listOfNotNull(
                    MapAircraft(id = "3c65a3", position = AircraftPosition(51.0, 10.0, now - age), callsign = "DLH453"),
                    MapAircraft(id = "4b1805", position = AircraftPosition(51.0, 16.0, now)).takeIf { farAway },
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
    fun `an answer is drawn, a capped one is asked again when zooming in`() = runTest {
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

    // Far out the traffic is redrawn every 5 s at most, so only the view decides within these tests
    @Test
    fun `a view change inside the drawn area keeps the traffic, leaving it rebuilds`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        // The one at 16 east is beyond the drawn area until the view moves east
        answers.emit(answer(lastQuery(), farAway = true))
        settle()
        val drawn = latest!!.trafficVersion
        latest?.traffic?.map { it.hex } shouldBe listOf("3C65A3")
        latest?.onScreen?.map { it.hex } shouldBe listOf("3C65A3")

        // The aircraft at 10 east is now just out of sight, but still around the view
        provider.onViewport(MapViewport(south = 50.0, north = 52.0, west = 10.2, east = 12.9, zoom = 5.0))
        settle()
        latest?.trafficVersion shouldBe drawn
        latest?.traffic?.map { it.hex } shouldBe listOf("3C65A3")
        latest?.onScreen shouldBe emptyList()

        provider.onViewport(MapViewport(south = 50.0, north = 52.0, west = 12.0, east = 16.0, zoom = 5.0))
        settle()
        (latest!!.trafficVersion > drawn) shouldBe true
        latest?.traffic?.map { it.hex } shouldBe listOf("4B1805")
    }

    @Test
    fun `leaving the drawn area rebuilds nothing when nothing was left out`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        answers.emit(answer(lastQuery()))
        settle()
        val drawn = latest!!.trafficVersion

        provider.onViewport(MapViewport(south = 50.0, north = 52.0, west = 12.0, east = 16.0, zoom = 5.0))
        settle()

        latest?.trafficVersion shouldBe drawn
        latest?.traffic?.map { it.hex } shouldBe listOf("3C65A3")
        latest?.onScreen shouldBe emptyList()
    }

    @Test
    fun `a newer answer to the same request waits until aircraft have visibly moved`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        answers.emit(answer(lastQuery()))
        settle()
        val drawn = latest!!.trafficVersion

        answers.emit(answer(lastQuery()))
        settle()
        latest?.trafficVersion shouldBe drawn

        settle(5_000)
        (latest!!.trafficVersion > drawn) shouldBe true
    }

    @Test
    fun `fading moves on between rebuilds without resending the traffic`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        // 22 s old, drawn at 0.9, the next step to 0.85 comes just past 23 s
        answers.emit(answer(lastQuery(), age = 22_000))
        settle()
        val drawn = latest!!.trafficVersion
        latest?.fades shouldBe mapOf("3C65A3" to 0.9f)

        settle(2_100)

        latest?.trafficVersion shouldBe drawn
        latest?.fades shouldBe mapOf("3C65A3" to 0.85f)
    }

    @Test
    fun `zooming back out after a smaller request asks for and draws the wider area`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        answers.emit(answer(lastQuery(), farAway = true))
        settle()

        // Far enough in that only the smaller area is asked for
        provider.onViewport(MapViewport(south = 50.9, north = 51.1, west = 9.8, east = 10.2, zoom = 11.0))
        settle()
        val zoomedIn = lastQuery()
        answers.emit(answer(zoomedIn))
        settle()
        latest?.traffic?.map { it.hex } shouldBe listOf("3C65A3")

        provider.onViewport(MapViewport(south = 50.0, north = 52.0, west = 8.0, east = 17.0, zoom = 5.0))
        settle()
        val zoomedOut = lastQuery()
        (zoomedOut != zoomedIn) shouldBe true
        answers.emit(answer(zoomedOut, farAway = true))
        settle()
        latest?.traffic?.map { it.hex }?.sorted() shouldBe listOf("3C65A3", "4B1805")
    }

    @Test
    fun `an uncapped answer is not asked again when zooming in`() = runTest {
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
    fun `a late answer for an earlier selection of the same aircraft is ignored`() = runTest {
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
    fun `an earlier selection's answer arriving after the current one changes nothing`() = runTest {
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

    companion object {
        private const val SERVER_TIME = 1_710_000_000_000L
    }
}
