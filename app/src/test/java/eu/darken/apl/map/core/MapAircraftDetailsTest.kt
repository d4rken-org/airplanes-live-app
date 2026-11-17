package eu.darken.apl.map.core

import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Airline
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapAircraftDetailsTest : BaseTest() {

    @Test
    fun `the details carry the aircraft's airline`() {
        val details = MapAircraftDetails.from(FakeAircraft(airline = Airline("DLH", "Lufthansa")), country = "Germany")

        details.airline shouldBe Airline(designator = "DLH", name = "Lufthansa")
    }

    @Test
    fun `an aircraft without an airline gives details without one`() {
        MapAircraftDetails.from(FakeAircraft(airline = null), country = "Germany").airline shouldBe null
    }
}
