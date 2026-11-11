package eu.darken.apl.airport.core

import eu.darken.apl.server.api.AirportSummaryDto
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AirportTest : BaseTest() {

    @Test
    fun `elevation, airline service and links are kept`() {
        val airport = AirportSummaryDto(
            id = "oa:2212",
            name = "Frankfurt Main Airport",
            type = AirportSummaryDto.TYPE_LARGE,
            ident = "EDDF",
            latitude = 50.026706,
            longitude = 8.55835,
            elevationFt = 364,
            scheduledService = true,
            website = "https://www.frankfurt-airport.de/",
            wikipedia = "https://en.wikipedia.org/wiki/Frankfurt_Airport",
        ).toAirport()

        airport.elevationFt shouldBe 364
        airport.scheduledService shouldBe true
        airport.website shouldBe "https://www.frankfurt-airport.de/"
        airport.wikipedia shouldBe "https://en.wikipedia.org/wiki/Frankfurt_Airport"
    }

    @Test
    fun `no airline service and a negative elevation are kept`() {
        val airport = AirportSummaryDto(
            id = "oa:3",
            name = "Below Sea Level Airport",
            type = AirportSummaryDto.TYPE_SMALL,
            ident = "XX-0003",
            latitude = 31.5,
            longitude = 35.5,
            elevationFt = -1266,
            scheduledService = false,
        ).toAirport()

        airport.elevationFt shouldBe -1266
        airport.scheduledService shouldBe false
    }
}
