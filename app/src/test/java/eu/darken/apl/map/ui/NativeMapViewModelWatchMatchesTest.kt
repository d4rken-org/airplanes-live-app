package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.MapWatches
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class NativeMapViewModelWatchMatchesTest : BaseTest() {

    private val watches = MapWatches(
        callsigns = mapOf("DLH453" to listOf("flight")),
        squawks = mapOf("0425" to listOf("squawk")),
    )

    private val plane = MapAircraftDetails(hex = "3C65A3", callsign = "DLH453")

    private fun matches(
        selectedHex: String? = "3C65A3",
        plane: MapAircraftDetails? = this.plane,
        drawn: Boolean = true,
        squawk: String? = "0425",
    ) = NativeMapViewModel.watchMatchesOf(watches, selectedHex, plane, drawn, squawk)

    @Test
    fun `the drawn selection lists its callsign and squawk watches`() {
        matches() shouldBe listOf(
            MapWatches.Match("flight", MapWatches.Match.Kind.CALLSIGN, "DLH453"),
            MapWatches.Match("squawk", MapWatches.Match.Kind.SQUAWK, "0425"),
        )
    }

    @Test
    fun `a selection the map doesn't draw live lists nothing`() {
        matches(drawn = false) shouldBe emptyList()
        matches(plane = null) shouldBe emptyList()
    }

    @Test
    fun `a frame of another selection lists nothing`() {
        matches(selectedHex = "4B1805") shouldBe emptyList()
        matches(selectedHex = null) shouldBe emptyList()
    }

    @Test
    fun `a callsign and squawk without watches list nothing`() {
        matches(plane = plane.copy(callsign = "BAW1"), squawk = "7000") shouldBe emptyList()
    }
}
