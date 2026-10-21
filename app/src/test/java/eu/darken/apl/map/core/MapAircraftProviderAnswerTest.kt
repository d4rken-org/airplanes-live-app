package eu.darken.apl.map.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.query.MapSnapshot
import eu.darken.apl.main.core.query.QuerySnapshot
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.HistoryAircraftResponse
import eu.darken.apl.server.api.HistoryRunSpan
import eu.darken.apl.server.api.MapAircraft
import eu.darken.apl.server.api.TrailPoint
import eu.darken.apl.server.api.UsageUpdate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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
import java.io.IOException
import java.time.Instant

/** The provider's ticker, the clock and the answers all run on the test scheduler's virtual time. */
class MapAircraftProviderAnswerTest : BaseTest() {

    private val aircraftRepo = mockk<AircraftRepo>()
    private lateinit var serverClock: ServerClock
    private val queries = mutableListOf<AircraftRepo.ViewingQuery.Map>()
    private val answers = MutableSharedFlow<AircraftRepo.MapViewingState>(extraBufferCapacity = 16)
    private var latest: MapAircraftProvider.Frame? = null
    private var trailCursor: ((AircraftRepo.ViewingQuery.Map) -> Long?)? = null

    private fun TestScope.provider(): MapAircraftProvider {
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        }).apply { noteServerTime(SERVER_TIME) }
        every { aircraftRepo.mapViewing(any(), any(), any()) } answers {
            val source = firstArg<Flow<AircraftRepo.ViewingQuery.Map>>()
            trailCursor = secondArg()
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
        return answerWith(
            query,
            aircraft = listOfNotNull(
                MapAircraft(id = "3c65a3", position = AircraftPosition(51.0, 10.0, now - age), callsign = "DLH453"),
                MapAircraft(id = "4b1805", position = AircraftPosition(51.0, 16.0, now)).takeIf { farAway },
            ),
            capped = capped,
            trail = trail,
        )
    }

    private fun answerWith(
        query: AircraftRepo.ViewingQuery.Map,
        aircraft: List<MapAircraft>,
        capped: Boolean = false,
        trail: List<TrailPoint>? = null,
        withSelected: Boolean = true,
    ): AircraftRepo.MapViewingState {
        val now = serverClock.now().toEpochMilli()
        return AircraftRepo.MapViewingState.Snapshot(
            MapSnapshot(
                query = query,
                trailSince = if (query.selected != null) 0L else null,
                aircraft = aircraft,
                selected = query.selected
                    ?.takeIf { withSelected }
                    ?.let { FakeAircraft(hex = it.uppercase(), callsign = "gen${query.selectionGeneration}") },
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

    @Test
    fun `an answer for the other military filter is pending until the matching one arrives`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view())
        settle()
        answers.emit(answer(lastQuery(), capped = true))
        settle()
        latest?.filterPending shouldBe false

        provider.setFilter(MapAircraftProvider.Filter(militaryOnly = true))
        settle()
        latest?.filterPending shouldBe true

        answers.emit(answer(lastQuery()))
        settle()
        latest?.filterPending shouldBe false
        lastQuery().militaryOnly shouldBe true
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

    private fun now() = serverClock.now().toEpochMilli()

    private fun seen(lat: Double, observedAt: Long, track: Double? = null, speed: Double? = null, military: Boolean = false) =
        MapAircraft(
            id = if (military) "ae1234" else "3c65a3",
            position = AircraftPosition(lat, 10.0, observedAt),
            trackDegrees = track,
            groundSpeedKnots = speed,
            military = military,
        )

    @Test
    fun `a track ends where its aircraft is drawn, ahead of the newest position`() = runTest {
        val provider = provider()
        provider.setTracksEnabled(true)
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, now()))))
        settle()

        // Heading north at speed, the drawn position runs ahead of the observed one until the next answer
        answers.emit(answerWith(lastQuery(), listOf(seen(51.1, now(), track = 0.0, speed = 400.0))))
        settle(5_000)

        val plane = latest!!.traffic.single()
        val track = latest!!.tracks!!.single()
        (plane.latitude > 51.1) shouldBe true
        track.latitudes shouldBe listOf(51.0, 51.1, plane.latitude)
        track.longitudes.last() shouldBe plane.longitude
    }

    @Test
    fun `a track leaves out positions newer than the one its aircraft is drawn from`() = runTest {
        val provider = provider()
        provider.setTracksEnabled(true)
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        val base = now()
        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, base - 10_000))))
        settle()
        answers.emit(answerWith(lastQuery(), listOf(seen(51.2, base - 5_000))))
        settle()

        // An older position than the last one, the aircraft is drawn back there
        answers.emit(answerWith(lastQuery(), listOf(seen(51.1, base - 8_000))))
        settle(5_000)

        latest!!.tracks!!.single().latitudes shouldBe listOf(51.0, 51.1)
    }

    @Test
    fun `filtered out and selected aircraft get no track`() = runTest {
        val provider = provider()
        provider.setTracksEnabled(true)
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        val aircraft = listOf(seen(51.0, now()), seen(51.5, now(), military = true))
        answers.emit(answerWith(lastQuery(), aircraft))
        settle()
        latest!!.tracks!!.map { it.plane.hex }.sorted() shouldBe listOf("3C65A3", "AE1234")

        provider.setFilter(MapAircraftProvider.Filter(militaryOnly = true))
        settle()
        latest!!.tracks!!.map { it.plane.hex } shouldBe listOf("AE1234")

        provider.select("AE1234")
        settle()
        answers.emit(answerWith(lastQuery(), aircraft))
        settle()
        latest!!.tracks shouldBe emptyList()
    }

    @Test
    fun `tracks are only built while they are on`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view(zoom = 5.0))
        settle()
        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, now()))))
        settle()

        latest!!.tracks shouldBe null
    }

    @Test
    fun `the trail is joined to the aircraft only from a recent point that is not ahead of it`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view())
        provider.select("3C65A3")
        settle()
        val seenAt = now()
        fun trailTo(observedAt: Long) = listOf(TrailPoint(50.0, 9.0, null, observedAt - 60_000), TrailPoint(50.1, 9.1, null, observedAt))

        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, seenAt)), trail = trailTo(seenAt - 100_000)))
        settle()
        latest!!.selectedTrailHead?.observedAt shouldBe seenAt - 100_000

        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, seenAt)), trail = trailTo(seenAt - 200_000)))
        settle()
        latest!!.selectedTrailHead shouldBe null

        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, seenAt)), trail = trailTo(seenAt + 1_000)))
        settle()
        latest!!.selectedTrailHead shouldBe null
    }

    @Test
    fun `the newest trail point is kept, also one too far from the others to be drawn`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view())
        provider.select("3C65A3")
        settle()
        val seenAt = now()
        // Ten minutes apart, too far to be joined, so the drawn trail has no segment at all
        val trail = listOf(TrailPoint(50.0, 9.0, null, seenAt - 700_000), TrailPoint(50.1, 9.1, null, seenAt - 100_000))

        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, seenAt)), trail = trail))
        settle()
        latest!!.selectedTrail shouldBe emptyList()
        latest!!.selectedTrailNewest shouldBe trail.last()

        provider.select(null)
        settle()
        latest!!.selectedTrailNewest shouldBe null
    }

    @Test
    fun `a selection the answer leaves out is missing until an answer brings it back`() = runTest {
        val provider = provider()
        start(provider)
        provider.onViewport(view())
        provider.select("3C65A3")
        settle()

        answers.emit(answerWith(lastQuery(), emptyList(), withSelected = false))
        settle()
        latest!!.selectedMissing shouldBe "3C65A3"
        latest!!.selectedDetails shouldBe null

        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, now()))))
        settle()
        latest!!.selectedMissing shouldBe null
        latest!!.selectedDetails?.hex shouldBe "3C65A3"

        // A new selection starts out waiting, not missing
        answers.emit(answerWith(lastQuery(), emptyList(), withSelected = false))
        settle()
        provider.select("4B1805")
        settle()
        latest!!.selectedMissing shouldBe null
    }

    private fun history(points: List<TrailPoint>) = HistoryAircraftResponse(
        serverTime = SERVER_TIME,
        aircraftId = "3c65a3",
        from = 0,
        until = SERVER_TIME,
        maxGapMs = 300_000,
        points = points,
        runs = listOf(HistoryRunSpan("callsign", "DLH453", 0, SERVER_TIME)),
        registration = "D-AIZZ",
        usage = UsageUpdate("principal", "VIEWING", 0, Allowance(1, 0, 0, 1)),
    )

    @Test
    fun `a loaded history carries the answer's range and names, a failed one throws`() = runTest {
        val provider = provider()
        val at = SERVER_TIME - 600_000
        coEvery { aircraftRepo.historyAircraft("3C65A3", any(), any()) } returns
                history(listOf(TrailPoint(50.0, 9.0, null, at), TrailPoint(50.1, 9.1, null, at + 60_000)))

        val track = provider.loadHistory("3c65a3")!!
        track.until shouldBe SERVER_TIME
        track.points.size shouldBe 2
        track.registration shouldBe "D-AIZZ"
        track.runs.single().value shouldBe "DLH453"

        coEvery { aircraftRepo.historyAircraft(any(), any(), any()) } throws IOException("offline")
        shouldThrow<IOException> { provider.loadHistory("3C65A3") }
        // A failed load is no longer out, the next one goes ahead
        coEvery { aircraftRepo.historyAircraft(any(), any(), any()) } returns history(emptyList())
        provider.loadHistory("3C65A3")!!.points shouldBe emptyList()
    }

    @Test
    fun `back from the history, nothing of before is drawn until the first new answer`() = runTest {
        val provider = provider()
        provider.setTracksEnabled(true)
        start(provider)
        provider.onViewport(view())
        provider.select("3C65A3")
        settle()
        answers.emit(answerWith(lastQuery(), listOf(seen(51.0, now())), trail = listOf(TrailPoint(50.0, 9.0, null, now() - 120_000), TrailPoint(50.1, 9.1, null, now() - 60_000))))
        settle()
        latest!!.selectedTrail.isEmpty() shouldBe false

        provider.prepareResume()
        settle()

        latest!!.traffic shouldBe emptyList()
        latest!!.selected shouldBe null
        latest!!.selectedTrail shouldBe emptyList()
        provider.state.value shouldBe null
        // The selection stays, the trail is asked for in full again
        latest!!.selectedHex shouldBe "3C65A3"
        latest!!.selectedDetails?.hex shouldBe "3C65A3"
        trailCursor!!(lastQuery()) shouldBe 0L
    }

    companion object {
        private const val SERVER_TIME = 1_710_000_000_000L
    }
}
