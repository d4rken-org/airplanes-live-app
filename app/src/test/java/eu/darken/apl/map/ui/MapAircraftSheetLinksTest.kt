package eu.darken.apl.map.ui

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.map.core.MapAircraftDetails
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class MapAircraftSheetLinksTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val munich = Airport(icaoCode = "EDDM", iataCode = "MUC", name = "Munich Airport", countryName = "Germany")
    private val heathrow = Airport(icaoCode = "EGLL", iataCode = "LHR", name = "Heathrow Airport", countryName = "United Kingdom")

    // The glance shows the airport's name, the callsign and the long type, the details these
    private val munichValue = "Munich Airport (MUC) · Germany"
    private val details = MapAircraftDetails(
        hex = "4CA2B1",
        callsign = "RYR1234",
        registration = "EI-ABC",
        country = "Ireland",
        icaoType = "B738",
        typeLong = "Boeing 737-800",
        squawk = "1234",
    )

    private val airportClicks = mutableListOf<Airport>()
    private val typeClicks = mutableListOf<String>()

    private fun setSheet(
        details: MapAircraftDetails = this.details,
        airportLinks: Boolean = true,
        typeLinks: Boolean = true,
    ) {
        composeRule.setContent {
            PreviewWrapper {
                AircraftDetailsSheetContent(
                    details = details,
                    loading = false,
                    note = null,
                    route = FlightRoute(callsign = "RYR1234", origin = munich, destination = heathrow, seenAt = Instant.EPOCH),
                    routeProgress = RouteProgress.Unknown,
                    progress = { 0f },
                    topInset = 0,
                    maxPhotoHeight = 0,
                    onCollapsedHeight = {},
                    watched = false,
                    seen = null,
                    serverNow = { 0L },
                    placeAction = AircraftPlaceAction.ShowOnMap(onClick = {}),
                    onWatchAction = {},
                    onWatchWithNote = {},
                    onPhotoClick = {},
                    onAirportClick = if (airportLinks) { airport -> airportClicks.add(airport) } else null,
                    onTypeClick = if (typeLinks) { type -> typeClicks.add(type) } else null,
                )
            }
        }
        // The details are the list's second item, below the glance
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label is '$label'") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun node(text: String) = composeRule.onNode(hasText(text), useUnmergedTree = true).performScrollTo()

    @Test
    fun `a route airport opens that airport`() {
        setSheet()

        node(munichValue)
            .assert(clickLabel(context.getString(R.string.route_airport_show_details_x, "Munich Airport")))
            .assert(isButton)
            .performClick()

        airportClicks shouldBe listOf(munich)
    }

    @Test
    fun `route airports are plain text without airport links`() {
        setSheet(airportLinks = false)

        node(munichValue).assert(hasClickAction().not())
    }

    @Test
    fun `the airframe searches for its type`() {
        setSheet()

        node("B738")
            .assert(clickLabel(context.getString(R.string.map_aircraft_details_search_type_x, "B738")))
            .assert(isButton)
            .performClick()

        typeClicks shouldBe listOf("B738")
    }

    @Test
    fun `the airframe is plain text without type links`() {
        setSheet(typeLinks = false)

        node("B738").assert(hasClickAction().not())
    }

    @Test
    fun `registration and hex are not links`() {
        setSheet()

        node("EI-ABC").assert(hasClickAction().not())
        node("4CA2B1").assert(hasClickAction().not())
    }
}
