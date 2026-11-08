package eu.darken.apl.airport.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.apl.airport.core.Airport
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

    private val shownOnMap = mutableListOf<Airport>()

    private fun setSheet(airport: Airport) = composeRule.setContent {
        PreviewWrapper {
            AirportDetailsSheetContent(
                airport = airport,
                onShowOnMap = { shownOnMap.add(it) },
            )
        }
    }

    @Test
    fun `an airport restored from the back stack shows its codes`() {
        val encoded = Json.encodeToString(DestinationAirportDetails.serializer(), DestinationAirportDetails(danville))
        val restored = Json.decodeFromString(DestinationAirportDetails.serializer(), encoded)

        setSheet(restored.airport)

        composeRule.onNodeWithText("Danville Airport").assertExists()
        composeRule.onNodeWithText("CA-0027").assertExists()
        composeRule.onNodeWithText("CDN2").assertExists()
    }

    @Test
    fun `show on map hands over the airport`() {
        setSheet(danville)

        composeRule.onNodeWithText("Show on map").performScrollTo().performClick()

        shownOnMap shouldBe listOf(danville)
    }

    @Test
    fun `codes the airport lacks are not shown`() {
        setSheet(danville)

        composeRule.onNodeWithText("Ident").assertExists()
        composeRule.onNodeWithText("IATA").assertDoesNotExist()
        composeRule.onNodeWithText("ICAO").assertDoesNotExist()
    }
}
