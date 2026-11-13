package eu.darken.apl.map.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportRef
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.AircraftKind
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapFilterChipsTest : BaseComposeTest() {

    private val cleared = mutableListOf<String>()

    private fun setChips(
        militaryOnly: Boolean = false,
        kinds: Set<AircraftKind> = emptySet(),
        query: String = "",
        watchedOnly: Boolean = false,
        airport: AirportTarget? = null,
    ) = composeRule.setContent {
        PreviewWrapper {
            MapFilterChips(
                militaryOnly = militaryOnly,
                kinds = kinds,
                isolating = false,
                pinnedCount = 0,
                query = query,
                watchedOnly = watchedOnly,
                airport = airport,
                cap = null,
                onClearMilitaryOnly = { cleared += "military" },
                onClearKinds = { cleared += "kinds" },
                onClearIsolate = { cleared += "isolate" },
                onClearPinned = { cleared += "pinned" },
                onClearQuery = { cleared += "query" },
                onClearWatchedOnly = { cleared += "watched" },
                onClearAirport = { cleared += "airport" },
                onCapInfo = {},
            )
        }
    }

    @Test
    fun `a query chip clears only the query`() {
        setChips(query = " dlh ", watchedOnly = true)

        composeRule.onNodeWithText("dlh").performClick()

        cleared shouldContainExactly listOf("query")
    }

    @Test
    fun `a blank query shows no chip`() {
        setChips(query = "   ")

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `the watched chip clears only watched-only`() {
        setChips(query = "dlh", watchedOnly = true)

        composeRule.onNodeWithText("Watched").performClick()

        cleared shouldContainExactly listOf("watched")
    }

    @Test
    fun `without a query or watched-only neither chip shows`() {
        setChips()

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onNodeWithText("Watched").assertDoesNotExist()
    }

    @Test
    fun `the query chip describes the full query for accessibility`() {
        val trimmed = "LH400 Frankfurt to New York with a very long pasted search text"
        setChips(query = " $trimmed ")

        val chip = composeRule.onNode(hasClickAction() and hasText(trimmed))
        chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(trimmed)))
        chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label shouldBe "Clear the filter text"

        chip.performClick()

        cleared shouldContainExactly listOf("query")
    }

    @Test
    fun `military-only and several kinds each get a chip`() {
        setChips(militaryOnly = true, kinds = setOf(AircraftKind.HELICOPTER, AircraftKind.GLIDER))

        composeRule.onNodeWithText("Military").assertIsDisplayed()
        composeRule.onNodeWithText("2 categories").assertIsDisplayed()
    }

    @Test
    fun `tapping the kinds chip clears only the kinds`() {
        setChips(militaryOnly = true, kinds = setOf(AircraftKind.HELICOPTER, AircraftKind.GLIDER))

        composeRule.onNodeWithText("2 categories").performClick()

        cleared shouldContainExactly listOf("kinds")
    }

    @Test
    fun `a single kind is named`() {
        setChips(kinds = setOf(AircraftKind.HELICOPTER))

        composeRule.onNodeWithText("Helicopters").assertIsDisplayed()
    }

    private val frankfurt = AirportTarget.Full(
        Airport(
            id = "oa:2212",
            name = "Frankfurt am Main Airport",
            type = "large",
            ident = "EDDF",
            latitude = 50.0333,
            longitude = 8.5706,
            icao = "EDDF",
            iata = "FRA",
        )
    )

    @Test
    fun `an airport chip shows its code and clears only the airport`() {
        setChips(query = "dlh", airport = frankfurt)

        composeRule.onNodeWithText("FRA").assertIsDisplayed()
        composeRule.onNodeWithText("FRA").performClick()

        cleared shouldContainExactly listOf("airport")
    }

    @Test
    fun `the airport chip names the airport for accessibility`() {
        setChips(airport = frankfurt)

        val chip = composeRule.onNode(hasClickAction() and hasText("FRA"))
        chip.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Frankfurt am Main Airport (FRA)"))
        )
        chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label shouldBe "Remove the airport from the map"
    }

    @Test
    fun `a partial airport chip shows its code and names the airport`() {
        setChips(airport = AirportTarget.Partial(AirportRef.create(icao = "EDDH", iata = "HAM", name = "Hamburg Airport")!!))

        val chip = composeRule.onNode(hasClickAction() and hasText("HAM"))
        chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hamburg Airport (HAM)")))
    }

    @Test
    fun `without an airport there is no airport chip`() {
        setChips()

        composeRule.onNodeWithText("FRA").assertDoesNotExist()
    }
}
