package eu.darken.apl.map.core

import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.query.MapSnapshot
import eu.darken.apl.main.core.query.QuerySnapshot
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.TrailPoint
import eu.darken.apl.server.api.UsageUpdate
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class SelectedTrailTest : BaseTest() {

    private fun query(generation: Long, selected: String? = "3c65a3") =
        AircraftRepo.ViewingQuery.Map(49.0, 51.0, 7.0, 9.0, selected = selected, selectionGeneration = generation)

    private fun point(observedAt: Long) = TrailPoint(50.0, 8.0, 30_000.0, observedAt)

    private fun cursor(since: Long, legStart: Long? = null) = AircraftRepo.TrailCursor(since, legStart)

    private val window = SelectedTrail.TRAIL_WINDOW.inWholeMilliseconds

    private fun answer(
        generation: Long,
        trailSince: Long?,
        trail: List<TrailPoint>?,
        reset: Boolean,
        selected: Aircraft? = null,
        legStart: Long? = null,
        serverTime: Instant = Instant.EPOCH,
    ) = MapSnapshot(
        query = query(generation),
        trailSince = trailSince,
        aircraft = emptyList(),
        selected = selected,
        trail = trail,
        trailReset = reset,
        complete = true,
        capped = false,
        totalMatching = null,
        snapshot = QuerySnapshot(
            serverTime = serverTime,
            receivedAtElapsed = 0,
            sourceTime = Instant.EPOCH,
            fetchedAt = Instant.EPOCH,
            expiresAt = Instant.EPOCH,
            complete = true,
            stale = false,
            unpositionedAircraft = 0,
        ),
        usage = UsageUpdate("principal", "VIEWING", 0, Allowance(1, 0, 0, 1)),
        trailLegStart = legStart,
    )

    @Test
    fun `a new selection asks for the whole trail, then continues from its newest point`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.cursor(query(1)) shouldBe cursor(0)

        trail.apply(answer(1, trailSince = 0, trail = listOf(point(2_000), point(1_000)), reset = true))
        trail.points.map { it.observedAt } shouldBe listOf(1_000L, 2_000L)
        trail.cursor(query(1)) shouldBe cursor(2_000)

        trail.apply(answer(1, trailSince = 2_000, trail = listOf(point(2_000), point(7_000)), reset = false))
        trail.points.map { it.observedAt } shouldBe listOf(1_000L, 2_000L, 7_000L)
    }

    @Test
    fun `a reset replaces what was kept`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000)), reset = true))

        trail.apply(answer(1, trailSince = 2_000, trail = listOf(point(50_000)), reset = true))

        trail.points.map { it.observedAt } shouldBe listOf(50_000L)
    }

    @Test
    fun `an answer for an earlier selection of the same aircraft is ignored`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000)), reset = true))
        trail.select(2)

        // A delta asked for selection 1 must not become the start of selection 2's trail
        trail.apply(answer(1, trailSince = 1_000, trail = listOf(point(6_000)), reset = false))

        trail.points shouldBe emptyList()
        trail.cursor(query(2)) shouldBe cursor(0)
    }

    @Test
    fun `a request for another generation starts from zero`() {
        val trail = SelectedTrail()
        trail.select(3)
        trail.apply(answer(3, trailSince = 0, trail = listOf(point(1_000)), reset = true))

        trail.cursor(query(2)) shouldBe cursor(0)
        trail.cursor(query(3, selected = null)).shouldBeNull()
    }

    @Test
    fun `an answer without the aircraft changes nothing`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000)), reset = true))

        trail.apply(answer(1, trailSince = 1_000, trail = null, reset = false))

        trail.points.map { it.observedAt } shouldBe listOf(1_000L)
    }

    @Test
    fun `an empty answered trail does not ask for the whole trail again`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = emptyList(), reset = true))

        // Without a position time to continue from, it stays at zero
        trail.cursor(query(1)) shouldBe cursor(0)
    }

    @Test
    fun `an empty answered trail continues from the aircraft's position time`() {
        val trail = SelectedTrail()
        trail.select(1)
        val seenAt = Instant.ofEpochMilli(1_710_000_000_000)
        trail.apply(
            answer(1, trailSince = 0, trail = emptyList(), reset = true, selected = FakeAircraft(positionSeenAt = seenAt))
        )

        trail.cursor(query(1)) shouldBe cursor(seenAt.toEpochMilli())
    }

    @Test
    fun `a reset with a leg start drops the points before it and sends it back`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000), point(3_000)), reset = true, legStart = 2_000))

        trail.points.map { it.observedAt } shouldBe listOf(2_000L, 3_000L)
        trail.cursor(query(1)) shouldBe cursor(3_000, legStart = 2_000)
    }

    @Test
    fun `a delta with a leg start behind the cursor drops older held points`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000), point(3_000)), reset = true))

        // Landed and took off again while selected
        trail.apply(answer(1, trailSince = 3_000, trail = listOf(point(4_000)), reset = false, legStart = 2_500))

        trail.points.map { it.observedAt } shouldBe listOf(3_000L, 4_000L)
        trail.cursor(query(1)) shouldBe cursor(4_000, legStart = 2_500)
    }

    @Test
    fun `a delta without a leg start keeps the trail and the applied leg start`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(2_000), point(3_000)), reset = true, legStart = 2_000))

        trail.apply(answer(1, trailSince = 3_000, trail = listOf(point(4_000)), reset = false))

        trail.points.map { it.observedAt } shouldBe listOf(2_000L, 3_000L, 4_000L)
        trail.cursor(query(1)) shouldBe cursor(4_000, legStart = 2_000)
    }

    @Test
    fun `a reset without a leg start stops sending one`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(2_000), point(3_000)), reset = true, legStart = 2_000))

        trail.apply(
            answer(1, trailSince = 3_000, trail = listOf(point(1_000), point(2_000), point(3_000), point(4_000)), reset = true)
        )

        trail.points.map { it.observedAt } shouldBe listOf(1_000L, 2_000L, 3_000L, 4_000L)
        trail.cursor(query(1)) shouldBe cursor(4_000)
    }

    @Test
    fun `a reset with an earlier leg start keeps the older points it brings`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(3_000), point(4_000)), reset = true, legStart = 3_000))

        trail.apply(
            answer(
                1,
                trailSince = 4_000,
                trail = listOf(point(1_000), point(2_000), point(3_000), point(4_000), point(5_000)),
                reset = true,
                legStart = 1_000,
            )
        )

        trail.points.map { it.observedAt } shouldBe listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L)
        trail.cursor(query(1)) shouldBe cursor(5_000, legStart = 1_000)
    }

    @Test
    fun `an answer to cursor 0 without a leg start replaces the trail and stops sending one`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(2_000), point(3_000)), reset = true, legStart = 2_000))

        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(5_000)), reset = false))

        trail.points.map { it.observedAt } shouldBe listOf(1_000L, 5_000L)
        trail.cursor(query(1)) shouldBe cursor(5_000)
    }

    @Test
    fun `a new selection asks for the whole trail without a leg start`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(2_000), point(3_000)), reset = true, legStart = 2_000))
        trail.cursor(query(1)) shouldBe cursor(3_000, legStart = 2_000)

        trail.select(2)

        trail.cursor(query(2)) shouldBe cursor(0)
        trail.cursor(query(1)) shouldBe cursor(0)
    }

    @Test
    fun `points older than the trail window are dropped without a leg start`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000), point(3_000)), reset = true))

        trail.apply(
            answer(
                1,
                trailSince = 3_000,
                trail = listOf(point(4_000)),
                reset = false,
                serverTime = Instant.ofEpochMilli(window + 2_500),
            )
        )

        trail.points.map { it.observedAt } shouldBe listOf(3_000L, 4_000L)
        trail.cursor(query(1)) shouldBe cursor(4_000)
    }

    @Test
    fun `a leg start at the newest point keeps that point`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000), point(3_000)), reset = true, legStart = 3_000))

        trail.points.map { it.observedAt } shouldBe listOf(3_000L)
        trail.cursor(query(1)) shouldBe cursor(3_000, legStart = 3_000)
    }

    @Test
    fun `points exactly at the leg start or the window start stay, a millisecond earlier go`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_999), point(2_000), point(3_000)), reset = true, legStart = 2_000))
        trail.points.map { it.observedAt } shouldBe listOf(2_000L, 3_000L)

        trail.select(2)
        trail.apply(
            answer(
                2,
                trailSince = 0,
                trail = listOf(point(4_999), point(5_000), point(6_000)),
                reset = true,
                serverTime = Instant.ofEpochMilli(window + 5_000),
            )
        )
        trail.points.map { it.observedAt } shouldBe listOf(5_000L, 6_000L)
    }

    @Test
    fun `an empty delta with a newer leg start drops the held points before it`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000), point(3_000)), reset = true))

        trail.apply(answer(1, trailSince = 3_000, trail = emptyList(), reset = false, legStart = 2_000))

        trail.points.map { it.observedAt } shouldBe listOf(2_000L, 3_000L)
        trail.cursor(query(1)) shouldBe cursor(3_000, legStart = 2_000)
    }

    @Test
    fun `a trail emptied by the window continues from the aircraft's position time`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000)), reset = true, legStart = 1_000))

        val serverTime = Instant.ofEpochMilli(window + 10_000)
        trail.apply(answer(1, trailSince = 2_000, trail = emptyList(), reset = false, serverTime = serverTime))
        trail.points shouldBe emptyList()

        val seenAt = Instant.ofEpochMilli(window + 9_000)
        trail.apply(
            answer(
                1,
                trailSince = 2_000,
                trail = emptyList(),
                reset = false,
                selected = FakeAircraft(positionSeenAt = seenAt),
                serverTime = serverTime,
            )
        )

        trail.points shouldBe emptyList()
        trail.cursor(query(1)) shouldBe cursor(seenAt.toEpochMilli(), legStart = 1_000)
    }

    @Test
    fun `segments are the same instance until the points change`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(point(1_000), point(2_000)), reset = true))

        val first = trail.segments()
        (trail.segments() === first) shouldBe true

        trail.apply(answer(1, trailSince = 2_000, trail = listOf(point(3_000)), reset = false))
        (trail.segments() === first) shouldBe false
    }

    @Test
    fun `long gaps split the drawn trail`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(
            answer(
                1,
                trailSince = 0,
                trail = listOf(point(0), point(60_000), point(120_000), point(400_000), point(405_000), point(900_000)),
                reset = true,
            )
        )

        trail.segments().map { segment -> segment.map { it.observedAt } } shouldBe listOf(
            listOf(0L, 60_000L, 120_000L),
            listOf(400_000L, 405_000L),
        )
    }

    @Test
    fun `gaps are kept until the trail changes`() {
        // Northbound at 450 kt, lost for 30 minutes
        fun flying(minute: Long) = TrailPoint(50.0 + 0.125 * minute, 8.0, 35_000.0, minute * 60_000L)
        val trail = SelectedTrail()
        trail.select(1)
        trail.apply(answer(1, trailSince = 0, trail = listOf(0L, 1L, 2L, 32L, 33L).map { flying(it) }, reset = true))

        val first = trail.gaps()
        first shouldBe listOf(TrailGap(flying(2), flying(32)))
        (trail.gaps() === first) shouldBe true

        trail.apply(answer(1, trailSince = 33 * 60_000L, trail = listOf(flying(34)), reset = false))
        (trail.gaps() === first) shouldBe false
    }
}
