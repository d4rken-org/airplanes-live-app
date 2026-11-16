package eu.darken.apl.airport.ui.flights

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import eu.darken.apl.airport.core.airportFlightListFixture
import eu.darken.apl.airport.ui.flights.AirportFlightsDisplay.SectionKind
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant
import java.time.ZoneId

class AirportFlightsSectionTest : BaseComposeTest() {

    /** 30 seconds after the fixtures' evaluation. */
    private val now = Instant.ofEpochMilli(1794571230000)

    private var state by mutableStateOf<AirportFlightsUiState>(AirportFlightsUiState.Ready)

    private var showFlights = 0
    private var refreshes = 0
    private val shownMore = mutableListOf<String>()
    private val selections = mutableListOf<SectionKind>()
    private val opened = mutableListOf<String>()

    /** Selects as the controller does without an earlier choice, unless [selected] is given. */
    private fun loaded(fixture: String, selected: SectionKind? = null): AirportFlightsUiState.Loaded {
        val display = AirportFlightsDisplay.from(
            list = airportFlightListFixture(fixture),
            airportTimeZone = null,
            now = now,
            deviceZone = ZoneId.of("UTC"),
        )
        return AirportFlightsUiState.Loaded(display = display, selected = display.selection(selected))
    }

    /** Keeps only the row with [key]. */
    private fun AirportFlightsUiState.Loaded.onlyRow(key: String) = copy(
        display = display.copy(
            sections = display.sections.map { section ->
                section.copy(rows = section.rows.filter { it.id.endsWith("/$key") })
            }
        )
    )

    private fun setSection(initial: AirportFlightsUiState) {
        state = initial
        composeRule.setContent {
            PreviewWrapper {
                LazyColumn(modifier = Modifier.testTag(LIST)) {
                    airportFlights(
                        state = state,
                        onShowFlights = { showFlights++ },
                        onRefresh = { refreshes++ },
                        onShowMore = { shownMore += it },
                        onSelectSection = { selections += it },
                        onRetry = {},
                        onReload = {},
                        onOpenAircraft = { opened += it },
                    )
                }
            }
        }
    }

    private fun setState(new: AirportFlightsUiState) {
        state = new
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(LIST).performScrollToNode(hasText(text, substring = true))
    }

    private fun scrollToEnd() {
        composeRule.onNodeWithTag(LIST).performScrollToKey("airport_flights_box_bottom")
    }

    private fun assertShown(text: String) {
        scrollTo(text)
        composeRule.onNodeWithText(text, substring = true).assertExists()
    }

    private fun assertNotShown(text: String) {
        composeRule.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    private fun textsMatching(regex: Regex) = composeRule.onAllNodes(
        SemanticsMatcher("text matching $regex") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { regex.containsMatchIn(it.text) } == true
        },
        useUnmergedTree = true,
    )

    @Test
    fun `a history only list shows the chosen section's flights`() {
        val list = loaded("history-only.json")
        setSection(list)

        listOf("XLJ120", "XLJ122").forEach { assertShown(it) }
        assertNotShown("XLJ121")

        setState(list.copy(selected = SectionKind.DEPARTURES))

        assertShown("XLJ121")
        assertNotShown("XLJ120")
        assertNotShown("XLJ122")
    }

    @Test
    fun `a schedule only list shows the chosen section's flights`() {
        val list = loaded("schedule-only.json")
        setSection(list)

        listOf("XK301", "XK305").forEach { assertShown(it) }
        assertNotShown("XK302")

        setState(list.copy(selected = SectionKind.DEPARTURES))

        listOf("XK302", "XK304", "XK306").forEach { assertShown(it) }
        assertNotShown("XK301")
        assertNotShown("XK305")
    }

    @Test
    fun `tapping a section chooses it`() {
        setSection(loaded("history-only.json"))

        composeRule.onNodeWithText("Departures").performClick()

        selections shouldContainExactly listOf(SectionKind.DEPARTURES)
    }

    @Test
    fun `other is only offered with flights in other directions, counting all of them`() {
        setSection(loaded("history-only.json"))
        composeRule.onNodeWithText("Arrivals").assertExists()
        composeRule.onNodeWithText("Other").assertDoesNotExist()

        // unknown 4, overflight 12, diverted_in 2
        setState(loaded("answered-other-directions.json"))
        composeRule.onNode(hasText("Other") and hasText("18")).assertExists()
    }

    @Test
    fun `show more only pages the chosen section`() {
        val list = loaded("answered-load.json")
        setSection(list)

        // Arrivals have no next page
        scrollToEnd()
        composeRule.onNodeWithText("Show more").assertDoesNotExist()

        setState(list.copy(selected = SectionKind.DEPARTURES))
        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").assertIsEnabled().performClick()
        shownMore shouldContainExactly listOf("departure")

        setState(list.copy(selected = SectionKind.OTHER))
        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").performClick()
        shownMore shouldContainExactly listOf("departure", "unknown")

        setState(list.copy(selected = SectionKind.OTHER, pagingDirections = setOf("unknown")))
        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").assertIsNotEnabled().performClick()
        shownMore shouldContainExactly listOf("departure", "unknown")
    }

    @Test
    fun `other pages its first direction with a cursor`() {
        setSection(loaded("answered-other-directions.json"))

        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").performClick()

        shownMore shouldContainExactly listOf("unknown")
    }

    @Test
    fun `a row names the other airport with its flag`() {
        setSection(loaded("answered-compact.json"))

        composeRule.onNodeWithText("🇩🇪 XYZ Example County International Airport").assertExists()
        assertShown("🇺🇸 XVR Example Valley")
    }

    @Test
    fun `a row without other airport says the route is unknown`() {
        // Its other airport is only a country
        setSection(loaded("answered-compact.json").onlyRow("k-c4"))

        composeRule.onNodeWithText("Route unknown").assertExists()
    }

    @Test
    fun `only an estimated time is marked`() {
        val list = loaded("answered-compact.json")
        setSection(list.onlyRow("k-c1"))
        textsMatching(CLOCK).assertCountEquals(1)
        textsMatching(ESTIMATED).assertCountEquals(1)

        // Actual
        setState(list.onlyRow("k-c3"))
        textsMatching(CLOCK).assertCountEquals(1)
        textsMatching(ESTIMATED).assertCountEquals(0)
    }

    @Test
    fun `a row without times shows no time`() {
        setSection(loaded("answered-compact.json").onlyRow("k-c4"))

        composeRule.onNodeWithText("XLF12", substring = true).assertExists()
        textsMatching(CLOCK).assertCountEquals(0)
        assertNotShown("Now")
    }

    @Test
    fun `a loaded list only explains itself in one line`() {
        setSection(loaded("answered-load.json"))

        composeRule.onNodeWithText("Estimates, the list may be incomplete.").assertExists()
        listOf("Updated", "Sources", "Free refreshes", "Out of date", "Based on", "(estimated)", "local").forEach {
            assertNotShown(it)
        }
    }

    @Test
    fun `a message has no explanation line`() {
        setSection(AirportFlightsUiState.NotOffered)

        composeRule.onNodeWithText("Flight lists aren't available on this server").assertExists()
        composeRule.onNodeWithText("Estimates, the list may be incomplete.").assertDoesNotExist()
    }

    @Test
    fun `show flights loads the list`() {
        setSection(AirportFlightsUiState.Ready)

        composeRule.onNodeWithText("Estimates, the list may be incomplete.").assertExists()
        composeRule.onNodeWithText("Show flights").performClick()

        showFlights shouldBe 1
        composeRule.onNodeWithContentDescription("Refresh").assertDoesNotExist()
    }

    @Test
    fun `refresh is disabled while refreshing`() {
        val list = loaded("answered-load.json")
        setSection(list.copy(refreshing = true))

        composeRule.onNodeWithContentDescription("Refresh").assertIsNotEnabled().performClick()
        refreshes shouldBe 0

        setState(list.copy(refreshing = false))

        composeRule.onNodeWithContentDescription("Refresh").assertIsEnabled().performClick()
        refreshes shouldBe 1
    }

    @Test
    fun `only a row with a known aircraft opens it`() {
        setSection(loaded("answered-load.json"))

        scrollTo("XLA401")
        composeRule.onNodeWithText("XLA401", substring = true).assertHasClickAction().performClick()
        opened shouldContainExactly listOf("3c6444")

        // Its aircraft is not among the answer's aircraft
        scrollTo("XLC1418")
        composeRule.onNodeWithText("XLC1418", substring = true).assertHasNoClickAction()
        // No aircraft at all
        scrollTo("XA1105")
        composeRule.onNodeWithText("XA1105", substring = true).assertHasNoClickAction()
        selections.shouldBeEmpty()
    }

    private companion object {
        const val LIST = "list"
        val CLOCK = Regex("""\d:\d\d""")
        val ESTIMATED = Regex("^~")
    }
}
