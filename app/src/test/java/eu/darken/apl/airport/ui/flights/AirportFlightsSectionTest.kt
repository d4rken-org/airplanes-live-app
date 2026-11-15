package eu.darken.apl.airport.ui.flights

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.apl.airport.core.airportFlightListFixture
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant
import java.time.ZoneId

class AirportFlightsSectionTest : BaseComposeTest() {

    /** 30 seconds after the fixtures' evaluation, before they expire. */
    private val now = Instant.ofEpochMilli(1794571230000)

    private var state by mutableStateOf<AirportFlightsUiState>(AirportFlightsUiState.Ready)

    private var showFlights = 0
    private var refreshes = 0
    private val shownMore = mutableListOf<String>()
    private val opened = mutableListOf<String>()

    private fun loaded(fixture: String, airportId: String, now: Instant = this.now) = AirportFlightsUiState.Loaded(
        display = AirportFlightsDisplay.from(
            list = airportFlightListFixture(fixture),
            pass = null,
            airportId = airportId,
            airportTimeZone = null,
            now = now,
            deviceZone = ZoneId.of("UTC"),
        ),
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
                        onRetry = {},
                        onReload = {},
                        onOpenAircraft = { opened += it },
                    )
                }
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(LIST).performScrollToNode(hasText(text))
    }

    @Test
    fun `a history only list shows its flights`() {
        setSection(loaded("history-only.json", airportId = "oa:5120"))

        listOf("Arrivals", "XLJ120", "XLJ122", "Departures", "XLJ121").forEach {
            scrollTo(it)
            composeRule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun `a schedule only list shows its flights`() {
        setSection(loaded("schedule-only.json", airportId = "oa:6031"))

        listOf("Arrivals", "XK301", "XK305", "Departures", "XK302", "XK304", "XK306").forEach {
            scrollTo(it)
            composeRule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun `show flights loads the list`() {
        setSection(AirportFlightsUiState.Ready)

        composeRule.onNodeWithText("Show flights").performClick()

        showFlights shouldBe 1
        composeRule.onNodeWithContentDescription("Refresh").assertDoesNotExist()
    }

    @Test
    fun `refresh is disabled while refreshing`() {
        val list = loaded("answered-load.json", airportId = "oa:2212")
        setSection(list.copy(refreshing = true))

        composeRule.onNodeWithContentDescription("Refresh").assertIsNotEnabled().performClick()
        refreshes shouldBe 0

        state = list.copy(refreshing = false)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Refresh").assertIsEnabled().performClick()
        refreshes shouldBe 1
    }

    @Test
    fun `show more under other pages its first direction with a cursor`() {
        val list = loaded("answered-other-directions.json", airportId = "oa:2212")
        setSection(list)

        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").assertIsEnabled().performClick()
        shownMore shouldContainExactly listOf("unknown")

        state = list.copy(pagingDirections = setOf("unknown"))
        composeRule.waitForIdle()

        scrollTo("Show more")
        composeRule.onNodeWithText("Show more").assertIsNotEnabled().performClick()
        shownMore shouldContainExactly listOf("unknown")
    }

    @Test
    fun `only a row with a known aircraft opens it`() {
        setSection(loaded("answered-load.json", airportId = "oa:2212"))

        scrollTo("XLA401")
        composeRule.onNodeWithText("XLA401").assertHasClickAction().performClick()
        opened shouldContainExactly listOf("3c6444")

        // Its aircraft is not among the answer's aircraft
        scrollTo("XLC1418")
        composeRule.onNodeWithText("XLC1418").assertHasNoClickAction()
        // No aircraft at all
        scrollTo("XA1105")
        composeRule.onNodeWithText("XA1105").assertHasNoClickAction()
    }

    @Test
    fun `an expired list says it is out of date`() {
        setSection(loaded("answered-load.json", airportId = "oa:2212"))
        composeRule.onNodeWithText("Out of date", substring = true).assertDoesNotExist()

        state = loaded("answered-load.json", airportId = "oa:2212", now = now.plusSeconds(60))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Out of date", substring = true).assertExists()
    }

    private companion object {
        const val LIST = "list"
    }
}
