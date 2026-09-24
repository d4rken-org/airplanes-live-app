package eu.darken.apl.map.core

import eu.darken.apl.main.core.AircraftRepo
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

    private fun answer(
        generation: Long,
        trailSince: Long?,
        trail: List<TrailPoint>?,
        reset: Boolean,
    ) = MapSnapshot(
        query = query(generation),
        trailSince = trailSince,
        aircraft = emptyList(),
        selected = null,
        trail = trail,
        trailReset = reset,
        complete = true,
        capped = false,
        totalMatching = null,
        snapshot = QuerySnapshot(
            serverTime = Instant.EPOCH,
            receivedAtElapsed = 0,
            sourceTime = Instant.EPOCH,
            fetchedAt = Instant.EPOCH,
            expiresAt = Instant.EPOCH,
            complete = true,
            stale = false,
            unpositionedAircraft = 0,
        ),
        usage = UsageUpdate("principal", "VIEWING", 0, Allowance(1, 0, 0, 1)),
    )

    @Test
    fun `a new selection asks for the whole trail, then continues from its newest point`() {
        val trail = SelectedTrail()
        trail.select(1)
        trail.cursor(query(1)) shouldBe 0L

        trail.apply(answer(1, trailSince = 0, trail = listOf(point(2_000), point(1_000)), reset = true))
        trail.points.map { it.observedAt } shouldBe listOf(1_000L, 2_000L)
        trail.cursor(query(1)) shouldBe 2_000L

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
        trail.cursor(query(2)) shouldBe 0L
    }

    @Test
    fun `a request for another generation starts from zero`() {
        val trail = SelectedTrail()
        trail.select(3)
        trail.apply(answer(3, trailSince = 0, trail = listOf(point(1_000)), reset = true))

        trail.cursor(query(2)) shouldBe 0L
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
        trail.cursor(query(1)) shouldBe 0L
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
}
