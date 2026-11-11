package eu.darken.apl.airport.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRef
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.text
import eu.darken.apl.watch.core.LocationWatchAvailability
import io.kotest.matchers.nulls.shouldNotBeNull
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

    private val frankfurtWithFacts = frankfurt.copy(
        elevationFt = 364,
        scheduledService = true,
        website = "https://www.frankfurt-airport.de/",
        wikipedia = "https://en.wikipedia.org/wiki/Frankfurt_Airport",
    )

    private var watchArea by mutableStateOf(LocationWatchAvailability.AVAILABLE)

    private var shownOnMap = 0
    private var watchAreaTapped = 0
    private var websiteOpened = 0
    private var wikipediaOpened = 0

    /** What the sheet's formatter makes of [feet], read in the same composition. */
    private var elevationText: String? = null
    private var elevationFeet: Int? = null

    private fun setSheet(airport: Airport) = setSheet(AirportTarget.Full(airport))

    private fun setSheet(target: AirportTarget, lookingUp: Boolean = false) = setSheet(
        AirportDetailsDisplay.from(target),
        lookingUp,
    )

    private fun setSheet(display: AirportDetailsDisplay, lookingUp: Boolean = false) = composeRule.setContent {
        PreviewWrapper {
            elevationText = elevationFeet?.let { LocalUnitFormatter.current.altitude(it).text() }
            AirportDetailsSheetContent(
                display = display,
                lookingUp = lookingUp,
                watchArea = watchArea,
                onShowOnMap = { shownOnMap++ },
                onWatchArea = { watchAreaTapped++ },
                onOpenWebsite = { websiteOpened++ },
                onOpenWikipedia = { wikipediaOpened++ },
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

    @Test
    fun `the elevation is shown in the formatter's units`() {
        elevationFeet = 364
        setSheet(frankfurtWithFacts)

        composeRule.onNodeWithText("Elevation").assertExists()
        composeRule.onNodeWithText(elevationText.shouldNotBeNull()).assertExists()
    }

    @Test
    fun `an elevation of zero is shown`() {
        elevationFeet = 0
        setSheet(frankfurtWithFacts.copy(elevationFt = 0))

        composeRule.onNodeWithText("Elevation").assertExists()
        composeRule.onNodeWithText(elevationText.shouldNotBeNull()).assertExists()
    }

    @Test
    fun `without an elevation there is no elevation row`() {
        setSheet(frankfurtWithFacts.copy(elevationFt = null))

        composeRule.onNodeWithText("Elevation").assertDoesNotExist()
    }

    @Test
    fun `an airport with airline service has the badge next to its type`() {
        setSheet(frankfurtWithFacts)

        composeRule.onNodeWithText("Large airport").assertExists()
        composeRule.onNodeWithText("Airline service").assertExists()
    }

    @Test
    fun `an airport without airline service has no badge`() {
        setSheet(frankfurtWithFacts.copy(scheduledService = false))

        composeRule.onNodeWithText("Large airport").assertExists()
        composeRule.onNodeWithText("Airline service").assertDoesNotExist()
    }

    @Test
    fun `an airport with unknown airline service has no badge`() {
        setSheet(frankfurtWithFacts.copy(scheduledService = null))

        composeRule.onNodeWithText("Large airport").assertExists()
        composeRule.onNodeWithText("Airline service").assertDoesNotExist()
    }

    @Test
    fun `the website and wikipedia actions open their links`() {
        setSheet(frankfurtWithFacts)

        composeRule.onNodeWithText("Website").performScrollTo().performClick()
        websiteOpened shouldBe 1
        wikipediaOpened shouldBe 0

        composeRule.onNodeWithText("Wikipedia").performScrollTo().performClick()
        wikipediaOpened shouldBe 1
        websiteOpened shouldBe 1
        shownOnMap shouldBe 0
    }

    @Test
    fun `only the links the airport has are offered`() {
        setSheet(frankfurtWithFacts.copy(website = null))

        composeRule.onNodeWithText("Website").assertDoesNotExist()
        composeRule.onNodeWithText("Wikipedia").assertExists()
    }

    @Test
    fun `an airport without links has no link actions`() {
        setSheet(frankfurt)

        composeRule.onNodeWithText("Website").assertDoesNotExist()
        composeRule.onNodeWithText("Wikipedia").assertDoesNotExist()
        composeRule.onNodeWithText("Show on map").assertExists()
    }

    @Test
    fun `links without a position are offered without show on map`() {
        setSheet(
            AirportDetailsDisplay(
                title = "Frankfurt am Main Airport",
                codes = emptyList(),
                website = "https://www.frankfurt-airport.de/",
                wikipedia = "https://en.wikipedia.org/wiki/Frankfurt_Airport",
            )
        )

        composeRule.onNodeWithText("Show on map").assertDoesNotExist()
        composeRule.onNodeWithText("Website").performScrollTo().performClick()
        composeRule.onNodeWithText("Wikipedia").performScrollTo().performClick()

        websiteOpened shouldBe 1
        wikipediaOpened shouldBe 1
    }

    @Test
    fun `watch this area is offered for an airport with a position`() {
        setSheet(danville)

        composeRule.onNodeWithText("Watch this area").assertExists()
    }

    @Test
    fun `watch this area is offered for a partial airport with a position`() {
        val ref = AirportRef.create(icao = "EDDH", iata = "HAM", latitude = 53.6304, longitude = 9.98823)!!
        setSheet(AirportTarget.Partial(ref))

        composeRule.onNodeWithText("Watch this area").assertExists()
    }

    @Test
    fun `without a position there is no watch this area`() {
        setSheet(AirportTarget.Partial(hamburgRef))

        composeRule.onNodeWithText("Watch this area").assertDoesNotExist()
    }

    @Test
    fun `an available watch is enabled without the upgrade marker`() {
        watchArea = LocationWatchAvailability.AVAILABLE
        setSheet(frankfurt)

        composeRule.onNodeWithText("Watch this area").performScrollTo().assertIsEnabled().performClick()

        watchAreaTapped shouldBe 1
        shownOnMap shouldBe 0
        composeRule.onNodeWithText("UPGRADE").assertDoesNotExist()
        composeRule.onNodeWithText("Watch availability is not known yet").assertDoesNotExist()
    }

    @Test
    fun `a watch outside the tier shows the upgrade marker and both open the upgrade`() {
        watchArea = LocationWatchAvailability.TIER_EXCLUDED
        setSheet(frankfurt)

        composeRule.onNodeWithText("Watch this area")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        watchAreaTapped shouldBe 1

        composeRule.onNodeWithText("UPGRADE").performScrollTo().performClick()
        watchAreaTapped shouldBe 2
        shownOnMap shouldBe 0
    }

    @Test
    fun `an unavailable watch is disabled and says why`() {
        val messages = mapOf(
            LocationWatchAvailability.REVOKED to "The server no longer accepts this installation.",
            LocationWatchAvailability.RESTRICTED to "This installation is restricted",
            LocationWatchAvailability.NOT_LOADED to "Watch availability is not known yet",
            LocationWatchAvailability.SESSION_UNUSABLE to "Watch availability is not known yet",
        )
        watchArea = LocationWatchAvailability.REVOKED
        setSheet(frankfurt)

        messages.forEach { (availability, message) ->
            watchArea = availability
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Watch this area").performScrollTo().assertIsNotEnabled().performClick()
            composeRule.onNodeWithText(message).assertExists()
            composeRule.onNodeWithText("UPGRADE").assertDoesNotExist()
        }
        watchAreaTapped shouldBe 0
    }
}
