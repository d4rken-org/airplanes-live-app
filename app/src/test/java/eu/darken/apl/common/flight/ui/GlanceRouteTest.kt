package eu.darken.apl.common.flight.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class GlanceRouteTest : BaseComposeTest() {

    private val frankfurt = Airport(
        icaoCode = "EDDF",
        iataCode = "FRA",
        name = "Frankfurt am Main Airport",
        countryName = "Germany",
    )
    private val heathrow = Airport(
        icaoCode = "EGLL",
        iataCode = "LHR",
        name = "London Heathrow Airport",
        countryName = "United Kingdom",
    )
    private val codeOnly = Airport(icaoCode = "KXYZ", iataCode = null, name = null, countryName = null)

    private val clicked = mutableListOf<Airport>()

    private fun setRoute(origin: Airport?, destination: Airport?, clickable: Boolean = true) = composeRule.setContent {
        PreviewWrapper {
            GlanceRoute(
                route = FlightRoute(callsign = "DLH123", origin = origin, destination = destination, seenAt = Instant.EPOCH),
                progress = RouteProgress.Airborne(0.4f),
                onAirportClick = if (clickable) { airport -> clicked.add(airport) } else null,
            )
        }
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label is '$label'") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun tapOriginCode(description: String) =
        composeRule.onNodeWithContentDescription(description).performTouchInput { click(centerLeft) }

    private fun tapDestinationCode(description: String) =
        composeRule.onNodeWithContentDescription(description).performTouchInput { click(centerRight) }

    @Test
    fun `the name and the code of an airport both open it`() {
        setRoute(frankfurt, heathrow)

        composeRule.onNodeWithText("Frankfurt am Main Airport").performClick()
        clicked shouldBe listOf(frankfurt)
        tapOriginCode("In flight from FRA to LHR")
        clicked shouldBe listOf(frankfurt, frankfurt)

        clicked.clear()
        composeRule.onNodeWithText("London Heathrow Airport").performClick()
        clicked shouldBe listOf(heathrow)
        tapDestinationCode("In flight from FRA to LHR")
        clicked shouldBe listOf(heathrow, heathrow)
    }

    @Test
    fun `each airport has exactly one accessibility action, on its name`() {
        setRoute(frankfurt, heathrow)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(2)
        composeRule.onAllNodes(clickLabel("Show details for Frankfurt am Main Airport")).assertCountEquals(1)
        composeRule.onAllNodes(clickLabel("Show details for London Heathrow Airport")).assertCountEquals(1)
        composeRule.onNodeWithText("Frankfurt am Main Airport")
            .assert(clickLabel("Show details for Frankfurt am Main Airport"))
            .assert(isButton)
        composeRule.onNodeWithText("London Heathrow Airport")
            .assert(clickLabel("Show details for London Heathrow Airport"))
            .assert(isButton)
    }

    @Test
    fun `the route description stays with links`() {
        setRoute(frankfurt, heathrow)

        composeRule.onNodeWithContentDescription("In flight from FRA to LHR").assertExists()
    }

    @Test
    fun `an unknown airport has no action`() {
        setRoute(null, heathrow)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasText("?") and hasClickAction(), useUnmergedTree = true).assertCountEquals(0)

        tapOriginCode("Flight to LHR, origin unknown")
        clicked.shouldBeEmpty()

        composeRule.onNodeWithText("London Heathrow Airport").performClick()
        clicked shouldBe listOf(heathrow)
    }

    @Test
    fun `an airport without a name is a link on its code`() {
        setRoute(codeOnly, heathrow)

        composeRule.onNodeWithContentDescription("In flight from KXYZ to LHR").assertExists()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(2)
        composeRule.onAllNodes(clickLabel("Show details for KXYZ")).assertCountEquals(1)
        composeRule.onNodeWithText("KXYZ")
            .assert(hasClickAction())
            .assert(clickLabel("Show details for KXYZ"))
            .assert(isButton)
            .performClick()
        clicked shouldBe listOf(codeOnly)
    }

    @Test
    fun `airports without names are links on their codes`() {
        val otherCodeOnly = Airport(icaoCode = "KABC", iataCode = null, name = null, countryName = null)
        setRoute(codeOnly, otherCodeOnly)

        composeRule.onNodeWithContentDescription("In flight from KXYZ to KABC").assertExists()
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("KXYZ").assert(clickLabel("Show details for KXYZ")).performClick()
        composeRule.onNodeWithText("KABC").assert(clickLabel("Show details for KABC")).performClick()
        clicked shouldBe listOf(codeOnly, otherCodeOnly)
    }

    @Test
    fun `without a callback nothing is clickable`() {
        setRoute(frankfurt, heathrow, clickable = false)

        composeRule.onNodeWithContentDescription("In flight from FRA to LHR").assertExists()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)

        composeRule.onNodeWithText("Frankfurt am Main Airport").performClick()
        tapOriginCode("In flight from FRA to LHR")
        tapDestinationCode("In flight from FRA to LHR")
        clicked.shouldBeEmpty()
    }
}
