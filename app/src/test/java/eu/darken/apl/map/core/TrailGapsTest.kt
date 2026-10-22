package eu.darken.apl.map.core

import eu.darken.apl.common.geo.Spherical
import eu.darken.apl.server.api.TrailPoint
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class TrailGapsTest : BaseTest() {

    private val minute = 60_000L
    private val maxGap = SelectedTrail.MAX_GAP_MS

    /** 450 kt due north is 0.125 degrees of latitude a minute. */
    private val cruise = 0.125

    private fun point(minutes: Long, lat: Double, lon: Double = 8.0) = TrailPoint(lat, lon, 35_000.0, minutes * minute)

    /** [count] points a minute apart from [startMinute], moving [dLat] and [dLon] degrees a minute. */
    private fun run(startMinute: Long, lat: Double, count: Int, dLat: Double = cruise, lon: Double = 8.0, dLon: Double = 0.0) =
        (0 until count).map { point(startMinute + it, lat + dLat * it, lon + dLon * it) }

    @Test
    fun `flying on, out of reception and back, is joined`() {
        val before = run(0, 50.0, 3)
        val after = run(32, 50.0 + cruise * 32, 3)

        TrailGaps.find(before + after, maxGap) shouldBe listOf(TrailGap(before.last(), after.first()))
    }

    @Test
    fun `out and back the opposite way is not joined`() {
        // Lost northbound, found southbound a little south of where it was lost
        val before = run(0, 50.0, 3)
        val after = run(60, 50.1, 3, dLat = -cruise)

        TrailGaps.find(before + after, maxGap).shouldBeEmpty()
    }

    @Test
    fun `found heading away from where it was lost is not joined`() {
        // Lost northbound, found further north but flying back south
        val before = run(0, 50.0, 3)
        val after = run(40, 54.0, 3, dLat = -cruise)

        TrailGaps.find(before + after, maxGap).shouldBeEmpty()
    }

    @Test
    fun `a turn within 90 degrees during the gap is still joined`() {
        // Lost northbound, found north-east flying east
        val before = run(0, 50.0, 3)
        val after = run(40, 52.0, 3, dLat = 0.0, lon = 10.0, dLon = 0.19)

        TrailGaps.find(before + after, maxGap).size shouldBe 1
    }

    @Test
    fun `landing and taking off again the same way out of reception is not joined`() {
        // Lost on final northbound at 140 kt, found an hour later 16 km on, climbing out northbound
        val approach = 0.0389
        val before = run(0, 50.0, 3, dLat = approach)
        val after = run(62, 50.0 + approach * 2 + 0.144, 3, dLat = approach)

        TrailGaps.find(before + after, maxGap).shouldBeEmpty()
    }

    @Test
    fun `slow but still flying across the gap is joined`() {
        // About 57 kt on average over an hour, e.g. held or vectored on the way
        val before = run(0, 50.0, 3)
        val after = run(62, 50.25 + 0.95, 3)

        TrailGaps.find(before + after, maxGap).size shouldBe 1
    }

    @Test
    fun `parked or taxiing on either side is not joined`() {
        val parked = run(0, 50.0, 3, dLat = 0.0)
        val flying = run(30, 50.5, 3)

        TrailGaps.find(parked + flying, maxGap).shouldBeEmpty()
        TrailGaps.find(run(0, 50.0, 3) + run(30, 54.0, 3, dLat = 0.0001), maxGap).shouldBeEmpty()
    }

    @Test
    fun `a single sighting on either side is not joined`() {
        val lone = listOf(point(0, 50.0))
        val after = run(30, 50.0 + cruise * 30, 3)

        TrailGaps.find(lone + after, maxGap).shouldBeEmpty()
        TrailGaps.find(run(0, 50.0, 3) + listOf(point(30, 50.0 + cruise * 30)), maxGap).shouldBeEmpty()
    }

    @Test
    fun `longer than six hours is not joined`() {
        val before = run(0, 10.0, 3)
        val hours = TrailGaps.MAX_GAP_DURATION.inWholeMinutes

        // The run before ends at minute 2
        TrailGaps.find(before + run(hours + 3, 10.0 + 20.0, 3), maxGap).shouldBeEmpty()
        TrailGaps.find(before + run(hours + 2, 10.0 + 20.0, 3), maxGap).size shouldBe 1
    }

    @Test
    fun `a neighbour with the same time is skipped for the one before it`() {
        val before = run(0, 50.0, 3)
        val duplicate = before.last().copy()
        val after = run(32, 50.0 + cruise * 32, 3)

        TrailGaps.find(before + duplicate + after, maxGap) shouldBe listOf(TrailGap(duplicate, after.first()))
    }

    @Test
    fun `a neighbour with the same time after the gap is skipped for the one after it`() {
        val before = run(0, 50.0, 3)
        val after = run(32, 50.0 + cruise * 32, 3)

        TrailGaps.find(before + after.first() + after.first().copy() + after.drop(1), maxGap) shouldBe
                listOf(TrailGap(before.last(), after.first()))
    }

    @Test
    fun `headings either side of north still agree`() {
        // Lost at about 350 degrees, found at about 10
        val before = run(0, 50.0, 3, dLon = -0.03)
        val after = run(32, 54.0, 3, lon = 8.2, dLon = 0.03)

        TrailGaps.find(before + after, maxGap).size shouldBe 1
    }

    @Test
    fun `each heading is compared with the great circle where it applies`() {
        // Near the pole the great circle from (80, -60) to (80, 60) sets off at 30 degrees and arrives at 150
        val from = TrailPoint(80.0, -60.0, null, 0)
        val to = TrailPoint(80.0, 60.0, null, 0)
        fun along(fraction: Double, minutes: Long): TrailPoint {
            val (lat, lon) = Spherical.intermediate(from.latitude, from.longitude, to.latitude, to.longitude, fraction)
            return TrailPoint(lat, lon, 35_000.0, minutes * minute)
        }
        val points = listOf(along(-0.02, 0), along(-0.01, 1), along(0.0, 2), along(1.0, 242), along(1.01, 243), along(1.02, 244))

        TrailGaps.find(points, maxGap).size shouldBe 1
    }

    @Test
    fun `faster than any aircraft across the gap is not joined`() {
        // Half the earth in five hours
        val before = run(0, 0.0, 3, dLat = 0.0, lon = -0.5, dLon = 0.125)
        val after = run(302, 0.0, 3, dLat = 0.0, lon = 179.5, dLon = 0.125)

        TrailGaps.find(before + after, maxGap).shouldBeEmpty()
    }

    @Test
    fun `the caller can rule a gap out`() {
        val points = run(0, 50.0, 3) + run(32, 50.0 + cruise * 32, 3)

        TrailGaps.find(points, maxGap) { false }.shouldBeEmpty()
    }

    @Test
    fun `the path follows the great circle, which bows towards the pole`() {
        // Newfoundland to Ireland
        val gap = TrailGap(TrailPoint(47.6, -52.7, null, 0), TrailPoint(53.4, -6.3, null, 4 * 3_600_000L))

        val path = gap.path()

        path.first() shouldBe (47.6 to -52.7)
        path.last() shouldBe (53.4 to -6.3)
        // A straight line on the map passes halfway at 50.5
        path[path.size / 2].first shouldBeGreaterThan 52.5
        path.zipWithNext { a, b -> Spherical.haversineDistanceM(a.first, a.second, b.first, b.second) }
            .forEach { it shouldBeLessThan TrailGap.PATH_STEP_M + 1.0 }
    }

    @Test
    fun `the path across the antimeridian takes the short way`() {
        val gap = TrailGap(TrailPoint(40.0, 175.0, null, 0), TrailPoint(40.0, -175.0, null, 3_600_000L))

        val path = gap.path()

        path.zipWithNext { a, b -> Spherical.haversineDistanceM(a.first, a.second, b.first, b.second) }
            .forEach { it shouldBeLessThan TrailGap.PATH_STEP_M + 1.0 }
    }
}
