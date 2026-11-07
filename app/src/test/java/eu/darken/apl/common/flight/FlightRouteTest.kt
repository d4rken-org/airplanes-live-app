package eu.darken.apl.common.flight

import eu.darken.apl.common.flight.db.AirportEntity
import eu.darken.apl.common.flight.db.FlightRouteEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant
import java.util.Locale

class FlightRouteTest : BaseTest() {

    private val defaultLocale = Locale.getDefault()

    @BeforeEach
    fun setup() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterEach
    fun teardown() {
        Locale.setDefault(defaultLocale)
    }

    private val entity = FlightRouteEntity(
        aircraftHex = "3c644a",
        callsign = "DLH067",
        originIcao = "EDDG",
        destinationIcao = "EDDF",
        seenAt = Instant.EPOCH,
        fetchedAt = Instant.EPOCH,
    )

    private fun airport(icao: String, country: String?) = AirportEntity(
        icaoCode = icao,
        iataCode = null,
        name = "Some Airport",
        country = country,
        latitude = null,
        longitude = null,
    )

    @Test
    fun `an ISO country code is shown as the country name`() {
        val route = entity.toDomain(airport("EDDG", "DE"), airport("EDDF", "GB"))
        route.origin!!.countryName shouldBe "Germany"
        route.destination!!.countryName shouldBe "United Kingdom"
    }

    @Test
    fun `a stored country name stays as it is`() {
        val route = entity.toDomain(airport("EDDG", "Germany"), airport("EDDF", null))
        route.origin!!.countryName shouldBe "Germany"
        route.destination!!.countryName shouldBe null
    }
}
