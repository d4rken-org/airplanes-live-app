package eu.darken.apl.airport.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRef
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.Test
import testhelper.BaseComposeTest

class AirportDetailsSheetTest : BaseComposeTest() {

    /** From a name search, so no code matched. */
    private val danville = Airport(
        id = "oa:39673",
        name = "Danville Airport",
        type = "small",
        ident = "CA-0027",
        local = "CDN2",
        municipality = "Val-des-Sources",
        country = "CA",
        region = "CA-QC",
        latitude = 45.79425,
        longitude = -71.995339,
    )

    private val frankfurt = Airport(
        id = "oa:2212",
        name = "Frankfurt am Main Airport",
        type = "large",
        ident = "EDDF",
        latitude = 50.033333,
        longitude = 8.570556,
        municipality = "Frankfurt am Main",
        country = "DE",
        region = "DE-HE",
        icao = "EDDF",
        iata = "FRA",
    )

    /** As a route knows it, without a position. */
    private val hamburgRef = AirportRef.create(
        icao = "EDDH",
        iata = "HAM",
        name = "Hamburg Airport",
        countryName = "Germany",
    )!!

    private var shownOnMap = 0

    private fun setSheet(airport: Airport) = setSheet(AirportTarget.Full(airport))

    private fun setSheet(target: AirportTarget, lookingUp: Boolean = false) = setSheet(
        AirportDetailsDisplay.from(target),
        lookingUp,
    )

    private fun setSheet(display: AirportDetailsDisplay, lookingUp: Boolean) = composeRule.setContent {
        PreviewWrapper {
            AirportDetailsSheetContent(
                display = display,
                lookingUp = lookingUp,
                onShowOnMap = { shownOnMap++ },
            )
        }
    }

    @Test
    fun `an airport restored from the back stack shows its codes`() {
        val encoded = Json.encodeToString(
            DestinationAirportDetails.serializer(),
            DestinationAirportDetails(AirportTarget.Full(danville)),
        )
        val restored = Json.decodeFromString(DestinationAirportDetails.serializer(), encoded)

        restored.target shouldBe AirportTarget.Full(danville)
        setSheet(restored.target)

        composeRule.onNodeWithText("Danville Airport").assertExists()
        composeRule.onNodeWithText("CA-0027").assertExists()
        composeRule.onNodeWithText("CDN2").assertExists()
    }

    @Test
    fun `a partial airport restored from the back stack stays partial`() {
        val encoded = Json.encodeToString(
            DestinationAirportDetails.serializer(),
            DestinationAirportDetails(AirportTarget.Partial(hamburgRef)),
        )
        val restored = Json.decodeFromString(DestinationAirportDetails.serializer(), encoded)

        restored.target shouldBe AirportTarget.Partial(hamburgRef)
        setSheet(restored.target)

        composeRule.onNodeWithText("Hamburg Airport").assertExists()
        composeRule.onNodeWithText("HAM").assertExists()
        composeRule.onNodeWithText("EDDH").assertExists()
    }

    @Test
    fun `show on map is offered for an airport with a position`() {
        setSheet(danville)

        composeRule.onNodeWithText("Show on map").performScrollTo().performClick()

        shownOnMap shouldBe 1
    }

    @Test
    fun `without a position there is no show on map and no coordinates`() {
        setSheet(AirportTarget.Partial(hamburgRef))

        composeRule.onNodeWithText("Show on map").assertDoesNotExist()
        composeRule.onNodeWithText("Coordinates").assertDoesNotExist()
    }

    @Test
    fun `a partial airport shows only what it knows`() {
        setSheet(AirportTarget.Partial(hamburgRef))

        composeRule.onNodeWithText("Hamburg Airport").assertExists()
        composeRule.onNodeWithText("IATA").assertExists()
        composeRule.onNodeWithText("ICAO").assertExists()
        composeRule.onNodeWithText("Country").assertExists()
        composeRule.onNodeWithText("Germany").assertExists()
        composeRule.onNodeWithText("Type").assertDoesNotExist()
        composeRule.onNodeWithText("Municipality").assertDoesNotExist()
        composeRule.onNodeWithText("Region").assertDoesNotExist()
        composeRule.onNodeWithText("Ident").assertDoesNotExist()
        composeRule.onNodeWithText("Local code").assertDoesNotExist()
    }

    @Test
    fun `a partial airport with a position can be shown on the map`() {
        val ref = AirportRef.create(icao = "EDDH", iata = "HAM", latitude = 53.6304, longitude = 9.98823)!!
        setSheet(AirportTarget.Partial(ref))

        composeRule.onNodeWithText("53.6304, 9.9882").assertExists()
        composeRule.onNodeWithText("Show on map").performScrollTo().performClick()

        shownOnMap shouldBe 1
    }

    @Test
    fun `the progress indicator shows while looking up`() {
        setSheet(AirportTarget.Partial(hamburgRef), lookingUp = true)

        composeRule.onNodeWithContentDescription("Looking up the airport").assertExists()
    }

    @Test
    fun `no progress indicator when not looking up`() {
        setSheet(AirportTarget.Partial(hamburgRef), lookingUp = false)

        composeRule.onNodeWithContentDescription("Looking up the airport").assertDoesNotExist()
    }

    @Test
    fun `codes the airport lacks are not shown`() {
        setSheet(danville)

        composeRule.onNodeWithText("Ident").assertExists()
        composeRule.onNodeWithText("IATA").assertDoesNotExist()
        composeRule.onNodeWithText("ICAO").assertDoesNotExist()
    }

    @Test
    fun `an ident equal to the icao code is shown once`() {
        setSheet(frankfurt)

        composeRule.onNodeWithText("FRA").assertExists()
        composeRule.onAllNodesWithText("EDDF").assertCountEquals(1)
        composeRule.onNodeWithText("ICAO").assertExists()
        composeRule.onNodeWithText("Ident").assertDoesNotExist()
    }

    @Test
    fun `a local code equal to the ident is shown once`() {
        setSheet(
            Airport(
                id = "oa:6523",
                name = "Ever Fly Airport",
                type = "small",
                ident = "00AA",
                local = "00AA",
                latitude = 38.704022,
                longitude = -101.473911,
            )
        )

        composeRule.onAllNodesWithText("00AA").assertCountEquals(1)
        composeRule.onNodeWithText("Ident").assertExists()
        composeRule.onNodeWithText("Local code").assertDoesNotExist()
    }

    @Test
    fun `distinct ident and local codes are both shown`() {
        setSheet(danville)

        composeRule.onAllNodesWithText("CA-0027").assertCountEquals(1)
        composeRule.onAllNodesWithText("CDN2").assertCountEquals(1)
    }
}
