package eu.darken.apl.map.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.map.core.MapAircraftDetails
import io.kotest.matchers.collections.shouldBeEmpty
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
    private val copies = mutableListOf<String>()

    private fun setSheet(
        details: MapAircraftDetails = this.details,
        airportLinks: Boolean = true,
        typeLinks: Boolean = true,
        copying: Boolean = true,
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
                    onCopy = if (copying) { text -> copies.add(text) } else null,
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

    private val copyLabel get() = context.getString(R.string.common_copy_action)
    private val hasLongClick = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
    private val copyLongClick
        get() = SemanticsMatcher("long click label is '$copyLabel'") {
            it.config.getOrNull(SemanticsActions.OnLongClick)?.label == copyLabel
        }

    private fun copyButton(@StringRes description: Int) =
        composeRule.onNodeWithContentDescription(context.getString(description)).performScrollTo()

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

    @Test
    fun `long-pressing a route airport copies it instead of opening it`() {
        setSheet()

        node(munichValue).assert(copyLongClick).performTouchInput { longClick() }

        copies shouldBe listOf(munichValue)
        airportClicks.shouldBeEmpty()
    }

    @Test
    fun `registration, hex and callsign each have a copy button`() {
        setSheet()

        copyButton(R.string.map_aircraft_details_copy_registration).assert(isButton).performClick()
        copyButton(R.string.map_aircraft_details_copy_hex).performClick()
        copyButton(R.string.map_aircraft_details_copy_callsign).performClick()

        copies shouldBe listOf("EI-ABC", "4CA2B1", "RYR1234")
    }

    @Test
    fun `flight data has a callsign row`() {
        setSheet()

        node(context.getString(R.string.map_aircraft_details_callsign_label)).assertExists()
        composeRule.onAllNodes(hasText("RYR1234") and hasAnyAncestor(hasTestTag(AIRCRAFT_SHEET_HEADLINE_TAG)).not(), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun `a plain value copies on a long press and offers no click`() {
        setSheet()

        node("1234")
            .assert(copyLongClick)
            .assert(hasClickAction().not())
            .performTouchInput { longClick() }

        copies shouldBe listOf("1234")
    }

    @Test
    fun `an emergency squawk copies on a long press and offers no click`() {
        setSheet(details = details.copy(squawk = "7700"))

        composeRule.onNode(hasLongClick and hasAnyChild(hasText("7700")), useUnmergedTree = true)
            .performScrollTo()
            .assert(copyLongClick)
            .assert(hasClickAction().not())
            .performTouchInput { longClick() }

        copies shouldBe listOf("7700")
    }

    @Test
    fun `without copying there are no copy buttons and no long presses`() {
        setSheet(copying = false)

        listOf(
            R.string.map_aircraft_details_copy_registration,
            R.string.map_aircraft_details_copy_hex,
            R.string.map_aircraft_details_copy_callsign,
        ).forEach { description ->
            composeRule.onAllNodes(hasContentDescription(context.getString(description)), useUnmergedTree = true)
                .assertCountEquals(0)
        }
        composeRule.onAllNodes(hasLongClick, useUnmergedTree = true).assertCountEquals(0)
        // The links keep their tap
        node(munichValue).assert(hasClickAction())
    }
}
