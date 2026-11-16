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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import eu.darken.apl.airport.core.AirportFlightList
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
    private fun loaded(fixture: String, selected: SectionKind? = null): AirportFlightsUiState.Loaded =
        loaded(airportFlightListFixture(fixture), selected)

    private fun loaded(list: AirportFlightList, selected: SectionKind? = null): AirportFlightsUiState.Loaded {
        val display = AirportFlightsDisplay.from(
            list = list,
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

    private fun showState(new: AirportFlightsUiState) {
        state = new
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(LIST).performScrollToNode(hasText(text, substring = true))
    }

    private fun scrollToEnd() {
        composeRule.onNodeWithTag(LIST).performScrollToKey("airport_flights_footer")
    }

    private fun assertShown(text: String) {
        scrollTo(text)
        composeRule.onNodeWithText(text, substring = true).assertExists()
    }

    private fun assertNotShown(text: String) {
        composeRule.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    /** Whether the text node with exactly [text] is laid out in its row, not only composed. */
    private fun isPlaced(text: String): Boolean = composeRule
        .onAllNodesWithText(text, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .any { it.layoutInfo.isPlaced }

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

        showState(list.copy(selected = SectionKind.DEPARTURES))

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

        showState(list.copy(selected = SectionKind.DEPARTURES))

        listOf("XK302", "XK304", "XK306").forEach { assertShown(it) }
        assertNotShown("XK301")
        assertNotShown("XK305")
    }

    @Test
    fun `flights in other directions are not shown`() {
        // k-u1 "XPRIV12" is in direction "unknown"
        val load = loaded("answered-load.json")
        setSection(load)
        composeRule.onNodeWithText("Arrivals").assertExists()
        composeRule.onNodeWithText("Departures").assertExists()
        composeRule.onNodeWithText("Other").assertDoesNotExist()
        scrollToEnd()
        assertNotShown("XPRIV12")

        showState(load.copy(selected = SectionKind.DEPARTURES))
        scrollToEnd()
        assertNotShown("XPRIV12")

        // Only "unknown", "overflight" and "diverted_in", all with a next page
        val others = loaded("answered-other-directions.json")
        listOf(others, others.copy(selected = SectionKind.DEPARTURES)).forEach {
            showState(it)
            composeRule.onNodeWithText("Other").assertDoesNotExist()
            listOf("XPRIV12", "XLG5501", "XLH88", "Show more").forEach { text -> assertNotShown(text) }
            assertShown("No flights found right now")
        }
    }

    @Test
    fun `show more only pages the chosen section`() {
        val list = loaded("answered-load.json")
        setSection(list)

        // Arrivals have no next page
        scrollToEnd()
        composeRule.onNodeWithText("Show more").assertDoesNotExist()

        showState(list.copy(selected = SectionKind.DEPARTURES))
        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").assertIsEnabled().performClick()
        shownMore shouldContainExactly listOf("departure")

        showState(list.copy(selected = SectionKind.DEPARTURES, pagingDirections = setOf("departure")))
        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").assertIsNotEnabled().performClick()
        shownMore shouldContainExactly listOf("departure")
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
        showState(list.onlyRow("k-c3"))
        textsMatching(CLOCK).assertCountEquals(1)
        textsMatching(ESTIMATED).assertCountEquals(0)
    }

    @Test
    fun `a row's second line names the flight, then its aircraft type and status`() {
        val list = loaded("answered-compact.json")
        // 3c6444 is an A320
        setSection(list.onlyRow("k-c1"))
        composeRule.onNodeWithText("XLA401 · A320 · Approaching").assertExists()

        // No aircraft
        showState(list.onlyRow("k-c4"))
        composeRule.onNodeWithText("XLF12 · En route").assertExists()
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
    fun `a reached search limit says so`() {
        setSection(AirportFlightsUiState.AllowanceExhausted)
        assertShown("Daily search limit reached")
        assertNotShown("Your daily allowance is used up.")

        showState(loaded("answered-load.json").copy(notice = AirportFlightsUiState.Notice.AllowanceExhausted))
        assertShown("Daily search limit reached")
        assertNotShown("Your daily allowance is used up.")
    }

    @Test
    fun `show flights loads the list`() {
        setSection(AirportFlightsUiState.Ready)

        composeRule.onNodeWithText("Estimates, the list may be incomplete.").assertExists()
        assertNotShown("allowance")
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

        showState(list.copy(refreshing = false))

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

    @Test
    fun `a row without an airline keeps its single second line`() {
        val list = loaded("answered-airlines.json")
        setSection(list.onlyRow("k-l2"))
        isPlaced("XLE52 · En route") shouldBe true
        composeRule.onAllNodesWithText(SEPARATOR, useUnmergedTree = true).assertCountEquals(0)

        // Only spaces
        showState(list.onlyRow("k-l3"))
        isPlaced("XA1105 · Landed") shouldBe true
        composeRule.onAllNodesWithText(SEPARATOR, useUnmergedTree = true).assertCountEquals(0)

        // An airline without a name
        showState(list.onlyRow("k-l7"))
        isPlaced("XLH3 · Scheduled") shouldBe true
        composeRule.onAllNodesWithText(SEPARATOR, useUnmergedTree = true).assertCountEquals(0)
    }

    private companion object {
        const val LIST = "list"
        val CLOCK = Regex("""\d:\d\d""")
        val ESTIMATED = Regex("^~")
        const val SEPARATOR = " · "
    }
}
