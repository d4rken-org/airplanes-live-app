package eu.darken.apl.airport.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
    private var centered = 0
    private var watchAreaTapped = 0
    private var websiteOpened = 0
    private var wikipediaOpened = 0

    /** As in Search's sheet, the map's own sheet centers on the airport instead. */
    private val showOnMap = AirportPlaceAction.ShowOnMap { shownOnMap++ }
    private val centerOnAirport = AirportPlaceAction.CenterOnAirport { centered++ }

    private val opensUpgrade = SemanticsMatcher("its click opens the upgrade") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Upgrade"
    }

    /** What the sheet's formatter makes of [feet], read in the same composition. */
    private var elevationText: String? = null
    private var elevationFeet: Int? = null

    private fun setSheet(airport: Airport, placeAction: AirportPlaceAction = showOnMap) =
        setSheet(AirportTarget.Full(airport), placeAction = placeAction)

    private fun setSheet(
        target: AirportTarget,
        lookingUp: Boolean = false,
        placeAction: AirportPlaceAction = showOnMap,
    ) = setSheet(AirportDetailsDisplay.from(target), lookingUp, placeAction)

    private fun setSheet(
        display: AirportDetailsDisplay,
        lookingUp: Boolean = false,
        placeAction: AirportPlaceAction = showOnMap,
    ) = composeRule.setContent {
        PreviewWrapper {
            elevationText = elevationFeet?.let { LocalUnitFormatter.current.altitude(it).text() }
            AirportDetailsSheetContent(
                display = display,
                lookingUp = lookingUp,
                watchArea = watchArea,
                placeAction = placeAction,
                onWatchArea = { watchAreaTapped++ },
                onOpenWebsite = { websiteOpened++ },
                onOpenWikipedia = { wikipediaOpened++ },
                progress = { 0f },
                topInset = 0,
                maxPhotoHeight = 0,
                onCollapsedHeight = {},
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
        composeRule.onNodeWithText("HAM · EDDH").assertExists()
    }

    @Test
    fun `show on map is offered for an airport with a position`() {
        setSheet(danville)

        composeRule.onNodeWithText("Show on map").performScrollTo().performClick()

        shownOnMap shouldBe 1
    }

    @Test
    fun `the map's own sheet centers on the airport instead of showing it on the map`() {
        setSheet(danville, placeAction = centerOnAirport)

        composeRule.onNodeWithText("Show on map").assertDoesNotExist()
        composeRule.onNodeWithText("Center on airport").performScrollTo().performClick()

        centered shouldBe 1
        shownOnMap shouldBe 0
    }

    @Test
    fun `search's sheet shows the airport on the map`() {
        setSheet(danville, placeAction = showOnMap)

        composeRule.onNodeWithText("Center on airport").assertDoesNotExist()
        composeRule.onNodeWithText("Show on map").performScrollTo().performClick()

        shownOnMap shouldBe 1
        centered shouldBe 0
    }

    @Test
    fun `without a position the map's own sheet has no center action`() {
        setSheet(AirportTarget.Partial(hamburgRef), placeAction = centerOnAirport)

        composeRule.onNodeWithText("Center on airport").assertDoesNotExist()
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
        composeRule.onNodeWithText("HAM · EDDH").assertExists()
        composeRule.onNodeWithText("Germany").assertExists()
        composeRule.onNodeWithText("Type").assertDoesNotExist()
        composeRule.onNodeWithText("Elevation").assertDoesNotExist()
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
    fun `the header has the title and both codes`() {
        setSheet(frankfurt)

        composeRule.onNodeWithText("Frankfurt am Main Airport").assertExists()
        composeRule.onNodeWithText("FRA · EDDF").assertExists()
    }

    @Test
    fun `an airport with one of the two codes has it alone in the header`() {
        setSheet(
            AirportDetailsDisplay(
                title = "Frankfurt am Main Airport",
                codes = listOf(AirportDetailsDisplay.Code(AirportDetailsDisplay.CodeKind.IATA, "FRA")),
            )
        )

        composeRule.onNodeWithText("FRA").assertExists()
    }

    @Test
    fun `the header says where the airport is, the region is among the facts`() {
        setSheet(danville)

        composeRule.onNodeWithText("Val-des-Sources", substring = true).assertExists()
        composeRule.onNodeWithText("Region").assertExists()
        composeRule.onNodeWithText("CA-QC").assertExists()
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
        composeRule.onNodeWithText("Local code").assertExists()
        // Neither IATA nor ICAO, so no code line in the header
        composeRule.onAllNodesWithText("·", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("CA-0027", substring = true).assertCountEquals(1)
    }

    @Test
    fun `an ident equal to the icao code is shown once`() {
        setSheet(frankfurt)

        composeRule.onNodeWithText("FRA · EDDF").assertExists()
        composeRule.onAllNodesWithText("EDDF", substring = true).assertCountEquals(1)
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

        composeRule.onAllNodesWithText("00AA", substring = true).assertCountEquals(1)
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
    fun `an airport without a position or links has no actions`() {
        setSheet(AirportTarget.Partial(hamburgRef))

        listOf("Show on map", "Watch this area", "Website", "Wikipedia").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `an airport with a position and both links has all four actions`() {
        setSheet(frankfurtWithFacts)

        listOf("Show on map", "Watch this area", "Website", "Wikipedia").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
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
        composeRule.onNodeWithText("Watch this area").assert(!opensUpgrade)
        composeRule.onNodeWithText("Watch availability is not known yet").assertDoesNotExist()
    }

    @Test
    fun `a watch outside the tier is marked and opens the upgrade`() {
        watchArea = LocationWatchAvailability.TIER_EXCLUDED
        setSheet(frankfurt)

        composeRule.onNodeWithText("Watch this area")
            .performScrollTo()
            .assertIsEnabled()
            .assert(opensUpgrade)
            .performSemanticsAction(SemanticsActions.OnClick)
        watchAreaTapped shouldBe 1

        composeRule.onNodeWithText("Watch this area").performClick()
        watchAreaTapped shouldBe 2
        shownOnMap shouldBe 0
        composeRule.onNodeWithText("Watch availability is not known yet").assertDoesNotExist()
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

            composeRule.onNodeWithText("Watch this area")
                .performScrollTo()
                .assertIsNotEnabled()
                .assert(!opensUpgrade)
                .performClick()
            composeRule.onNodeWithText(message).assertExists()
        }
        watchAreaTapped shouldBe 0
    }
}
