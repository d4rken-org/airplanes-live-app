package eu.darken.apl.airport.ui

import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelper.BaseTest

/** A saved back stack from a build whose airports had no elevation, airline service or links. */
internal const val DANVILLE_DETAILS_WITHOUT_FACTS_JSON =
    """{"target":{"type":"full","airport":{"id":"oa:39673","name":"Danville Airport","type":"small","ident":"CA-0027","latitude":45.79425,"longitude":-71.995339,"municipality":"Val-des-Sources","country":"CA","region":"CA-QC","local":"CDN2"}}}"""

class AirportDestinationsTest : BaseTest() {

    private val danville = Airport(
        id = "oa:39673",
        name = "Danville Airport",
        type = "small",
        ident = "CA-0027",
        latitude = 45.79425,
        longitude = -71.995339,
        municipality = "Val-des-Sources",
        country = "CA",
        region = "CA-QC",
        local = "CDN2",
    )

    private val seaLevel = Airport(
        id = "oa:2",
        name = "Sea Level Airport",
        type = "small",
        ident = "XX-0002",
        latitude = 3.5,
        longitude = 4.5,
        elevationFt = 0,
        scheduledService = false,
        website = "https://sea-level-airport.example/",
        wikipedia = "https://en.wikipedia.org/wiki/Sea_Level_Airport",
    )

    private val belowSeaLevel = Airport(
        id = "oa:3",
        name = "Below Sea Level Airport",
        type = "small",
        ident = "XX-0003",
        latitude = 31.5,
        longitude = 35.5,
        elevationFt = -1266,
        scheduledService = true,
        website = "http://below-sea-level-airport.example/",
        wikipedia = "https://en.wikipedia.org/wiki/Below_Sea_Level_Airport",
    )

    @Test
    fun `airport details keep elevation, airline service and links on the back stack`() {
        listOf(seaLevel, belowSeaLevel).forEach { airport ->
            val destination = DestinationAirportDetails(AirportTarget.Full(airport))

            val encoded = Json.encodeToString(DestinationAirportDetails.serializer(), destination)
            val restored = Json.decodeFromString(DestinationAirportDetails.serializer(), encoded)

            restored shouldBe destination
        }
    }

    @Test
    fun `the map keeps the airport's elevation, airline service and links on the back stack`() {
        listOf(seaLevel, belowSeaLevel).forEach { airport ->
            val destination = DestinationMap(mapOptions = MapOptions(airport = AirportTarget.Full(airport)))

            val encoded = Json.encodeToString(DestinationMap.serializer(), destination)
            val restored = Json.decodeFromString(DestinationMap.serializer(), encoded)

            restored shouldBe destination
        }
    }

    @Test
    fun `airport details saved without elevation, airline service and links still restore`() {
        val restored = Json.decodeFromString(DestinationAirportDetails.serializer(), DANVILLE_DETAILS_WITHOUT_FACTS_JSON)

        val airport = restored.target.shouldBeInstanceOf<AirportTarget.Full>().airport
        airport shouldBe danville
        airport.elevationFt.shouldBeNull()
        airport.scheduledService.shouldBeNull()
        airport.website.shouldBeNull()
        airport.wikipedia.shouldBeNull()
    }
}
