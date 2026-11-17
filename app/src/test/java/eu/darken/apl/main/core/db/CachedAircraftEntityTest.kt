package eu.darken.apl.main.core.db

import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Airline
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CachedAircraftEntityTest {

    @Test
    fun `an airline survives the cache`() {
        val airline = Airline(designator = "DLH", name = "Lufthansa")

        FakeAircraft(airline = airline).toEntity().toAircraft().airline shouldBe airline
    }

    @Test
    fun `no airline stays no airline`() {
        FakeAircraft(airline = null).toEntity().toAircraft().airline.shouldBeNull()
    }

    @Test
    fun `a half stored airline reads as no airline`() {
        val entity = FakeAircraft(airline = Airline(designator = "DLH", name = "Lufthansa")).toEntity()

        entity.copy(airlineName = null).toAircraft().airline.shouldBeNull()
        entity.copy(airlineDesignator = null).toAircraft().airline.shouldBeNull()
    }
}
