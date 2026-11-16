package eu.darken.apl.airport.ui.flights

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import eu.darken.apl.airport.core.AirportFlightList
import eu.darken.apl.airport.core.airportFlightListFixture
import eu.darken.apl.airport.core.airportFlightsFixture
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.server.api.AirportFlightAirlineDto
import eu.darken.apl.server.api.AirportFlightDto
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelper.BaseComposeTest
import java.time.Instant
import java.time.ZoneId

/**
 * Which rows show their airline. Whether it fits depends on text widths, so it draws natively, on a phone screen
 * 412dp wide.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp")
class AirportFlightRowAirlineTest : BaseComposeTest() {

    /** 30 seconds after the fixture's evaluation. */
    private val now = Instant.ofEpochMilli(1794571230000)

    /** Shows the row with [key], in its own section. */
    private fun setRow(key: String, list: AirportFlightList = airportFlightListFixture("answered-airlines.json")) {
        val display = AirportFlightsDisplay.from(
            list = list,
            airportTimeZone = null,
            now = now,
            deviceZone = ZoneId.of("UTC"),
        ).let { display ->
            display.copy(
                sections = display.sections.map { section ->
                    section.copy(rows = section.rows.filter { it.id.endsWith("/$key") })
                }
            )
        }
        val state = AirportFlightsUiState.Loaded(display = display, selected = display.selection(null))

        composeRule.setContent {
            PreviewWrapper {
                LazyColumn {
                    airportFlights(
                        state = state,
                        onShowFlights = {},
                        onRefresh = {},
                        onShowMore = {},
                        onSelectSection = {},
                        onRetry = {},
                        onReload = {},
                        onOpenAircraft = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun placed(text: String): SemanticsNode = composeRule
        .onAllNodes(hasText(text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single { it.layoutInfo.isPlaced }

    private fun textsMatching(regex: Regex) = composeRule.onAllNodes(
        SemanticsMatcher("text matching $regex") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { regex.containsMatchIn(it.text) } == true
        },
        useUnmergedTree = true,
    )

    private fun SemanticsNode.isShortened(): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().isLineEllipsized(0)
    }

    @Test
    fun `a row with an airline names it before the flight`() {
        setRow("k-l1")

        val airline = placed("Example Air")
        val separator = placed(SEPARATOR)
        val details = placed("XLA401 · A320 · Approaching")
        airline.isShortened() shouldBe false
        airline.boundsInRoot.right shouldBeLessThanOrEqual separator.boundsInRoot.left + 0.5f
        separator.boundsInRoot.right shouldBeLessThanOrEqual details.boundsInRoot.left + 0.5f
    }

    @Test
    fun `a row named by its registration still names the airline`() {
        setRow("k-l5")

        placed("Sample Air")
        placed(SEPARATOR)
        placed("X-SMPL · B738 · On the ground")
    }

    @Test
    fun `an unidentified flight still names the airline`() {
        val base = airportFlightsFixture("answered-airlines.json")
        val unidentified = base.entries.single { it.key == "k-l1" }.copy(
            key = "k-unidentified",
            flight = AirportFlightDto(airline = AirportFlightAirlineDto(name = "Example Air")),
            aircraftId = null,
        )
        setRow("k-unidentified", AirportFlightList.from(base.copy(entries = listOf(unidentified)))!!)

        placed("Example Air")
        placed("Unidentified flight · Approaching")
        composeRule.onNodeWithText("Unidentified flight", substring = true).assertHasNoClickAction()
    }

    @Test
    fun `a departure with only an estimate shows it marked`() {
        setRow("k-l8")

        placed("Example Air")
        placed("XLA400 · Departed")
        textsMatching(CLOCK).assertCountEquals(1)
        textsMatching(ESTIMATED).assertCountEquals(1)
    }

    private companion object {
        const val SEPARATOR = " · "
        val CLOCK = Regex("""\d:\d\d""")
        val ESTIMATED = Regex("^~")
    }
}
