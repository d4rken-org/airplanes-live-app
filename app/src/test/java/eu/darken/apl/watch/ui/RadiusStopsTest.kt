package eu.darken.apl.watch.ui

import eu.darken.apl.common.units.DistanceUnit
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class RadiusStopsTest : BaseTest() {

    @Test
    fun `kilometers give every whole km between the limits`() {
        RadiusStops.build(2_000f, 6_000f, DistanceUnit.KILOMETERS) shouldContainExactly
                listOf(2_000f, 3_000f, 4_000f, 5_000f, 6_000f)
    }

    @Test
    fun `miles keep both limits exactly and whole miles between`() {
        val stops = RadiusStops.build(2_000f, 25_000f, DistanceUnit.MILES)

        stops.first() shouldBe 2_000f
        stops.last() shouldBe 25_000f
        // 2 to 15 mi between 2 km (1.24 mi) and 25 km (15.53 mi)
        stops.size shouldBe 2 + 14
        stops[1] shouldBe (3_218.688f plusOrMinus 0.01f)
        stops[stops.lastIndex - 1] shouldBe (24_140.16f plusOrMinus 0.01f)
    }

    @Test
    fun `a whole unit on a limit is not offered twice`() {
        RadiusStops.build(1_852f, 5_556f, DistanceUnit.NAUTICAL_MILES).size shouldBe 3
    }

    @Test
    fun `equal limits leave one radius`() {
        RadiusStops.build(2_000f, 2_000f, DistanceUnit.MILES) shouldContainExactly listOf(2_000f)
    }

    @Test
    fun `a limit below the minimum leaves none`() {
        RadiusStops.build(2_000f, 1_000f, DistanceUnit.KILOMETERS) shouldBe emptyList()
    }

    @Test
    fun `a picked radius above a dropped limit goes down to it`() {
        val stops = RadiusStops.forLimit(maxRadiusKm = 25, DistanceUnit.KILOMETERS)
        RadiusStops.clamp(100_000f, stops) shouldBe 25_000f
        RadiusStops.clamp(10_000f, stops) shouldBe 10_000f
        RadiusStops.clamp(10_000f, emptyList()) shouldBe null
    }

    @Test
    fun `a unit change keeps the picked radius, the slider only moves to the nearest stop`() {
        val pickedInKm = 10_000f
        val milesStops = RadiusStops.forLimit(maxRadiusKm = 25, DistanceUnit.MILES)

        RadiusStops.clamp(pickedInKm, milesStops) shouldBe pickedInKm
        // 10 km is 6.2 mi
        milesStops[RadiusStops.nearestIndex(milesStops, pickedInKm)] shouldBe (9_656.064f plusOrMinus 0.01f)
    }

    @Test
    fun `whole and approximate radii`() {
        RadiusStops.isWhole(25_000f, DistanceUnit.KILOMETERS) shouldBe true
        RadiusStops.isWhole(25_000f, DistanceUnit.MILES) shouldBe false
        RadiusStops.isWhole(16_093.44f, DistanceUnit.MILES) shouldBe true
        RadiusStops.isWhole(7_430f, DistanceUnit.KILOMETERS) shouldBe false
    }

    @Test
    fun `allowed radii are within the minimum and the limit`() {
        RadiusStops.allows(2_000f, maxRadiusKm = 25) shouldBe true
        RadiusStops.allows(25_000f, maxRadiusKm = 25) shouldBe true
        RadiusStops.allows(25_001f, maxRadiusKm = 25) shouldBe false
        RadiusStops.allows(1_999f, maxRadiusKm = 25) shouldBe false
    }

    @Test
    fun `touching the thumb of a radius between stops keeps it`() {
        val stops = RadiusStops.forLimit(maxRadiusKm = 25, DistanceUnit.KILOMETERS)
        // 17,342.5 m shows at the 17 km stop, index 15
        RadiusStops.pickOnMove(stops, 17_342.5f, position = 15f) shouldBe null
        RadiusStops.pickOnMove(stops, 17_342.5f, position = 15.3f) shouldBe null
        RadiusStops.pickOnMove(stops, 17_342.5f, position = 16f) shouldBe 18_000f
    }

    @Test
    fun `moving from a stop lands on the neighbour`() {
        val stops = RadiusStops.forLimit(maxRadiusKm = 25, DistanceUnit.KILOMETERS)
        RadiusStops.pickOnMove(stops, 10_000f, position = 7f) shouldBe 9_000f
        RadiusStops.pickOnMove(stops, 10_000f, position = 99f) shouldBe 25_000f
        RadiusStops.pickOnMove(emptyList(), 10_000f, position = 0f) shouldBe null
    }

    @Test
    fun `saving keeps a stored radius an older limit allowed`() {
        RadiusStops.saveAllowed(100_000f, storedMeters = 100_000f, maxRadiusKm = 25) shouldBe true
        RadiusStops.saveAllowed(30_000f, storedMeters = 100_000f, maxRadiusKm = 25) shouldBe false
        RadiusStops.saveAllowed(30_000f, storedMeters = null, maxRadiusKm = 25) shouldBe false
        RadiusStops.saveAllowed(25_000f, storedMeters = null, maxRadiusKm = 25) shouldBe true
    }
}
