package eu.darken.apl.search.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportAggregation
import eu.darken.apl.airport.core.AirportRow
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.search.core.SearchInput
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class SearchAirportsSectionTest : BaseComposeTest() {

    private val airports = List(5) { i ->
        Airport(
            id = "oa:$i",
            name = "Test Airport $i",
            type = "small",
            ident = "XX0$i",
            latitude = 50.0 + i,
            longitude = 8.0 + i,
        )
    }

    private val clickedAirports = mutableListOf<Airport>()
    private val expandedChanges = mutableListOf<Boolean>()
    private var retries = 0
    private var reportedSelection: Set<String>? = null

    private var state by mutableStateOf(stateFor(SearchViewModel.AirportStatus.Loading))

    private fun results(
        rows: List<Airport> = airports,
        truncated: Boolean = false,
        invalidTerms: List<String> = emptyList(),
        searchedAny: Boolean = true,
    ) = SearchViewModel.AirportStatus.Results(
        AirportAggregation.Shown(
            rows = rows.map { AirportRow(it, municipalityAmbiguous = false) },
            truncated = truncated,
            invalidTerms = invalidTerms,
            searchedAny = searchedAny,
        )
    )

    private fun stateFor(
        status: SearchViewModel.AirportStatus,
        expanded: Boolean = false,
        terms: List<String> = listOf("Test"),
        cutoff: Boolean = false,
        aircraft: Int = 0,
    ) = SearchViewModel.State(
        input = SearchInput(text = terms.joinToString(", ")),
        items = listOf(
            SearchViewModel.SearchItem.Airports(
                SearchViewModel.AirportSection(
                    terms = terms,
                    cutoff = cutoff,
                    status = status,
                    expanded = expanded,
                )
            )
        ) + List(aircraft) { i ->
            SearchViewModel.SearchItem.AircraftResult(
                aircraft = FakeAircraft(hex = "AA%04d".format(i), registration = "REG-%02d".format(i)),
                watch = null,
                distanceInMeter = null,
            )
        },
        resultRevision = 1,
    )

    private fun setScreen() = composeRule.setContent {
        PreviewWrapper {
            SearchScreen(
                state = state,
                snackbarHostState = SnackbarHostState(),
                onClear = {},
                onSubmit = {},
                onToggleCategoryChip = {},
                onToggleNearby = {},
                onNearbyPlace = {},
                onAircraftClick = {},
                onThumbnailClick = {},
                onWatchClick = {},
                onShowOnMap = {},
                onGrantLocation = {},
                onDismissLocation = {},
                onStartFeeding = {},
                onAirportClick = { clickedAirports.add(it) },
                onRetryAirports = { retries++ },
                onAirportsExpandedChange = { expandedChanges.add(it) },
                onSelectionChange = { _, selection -> reportedSelection = selection },
            )
        }
    }

    @Test
    fun `a collapsed section shows three airports and offers the rest`() {
        state = stateFor(results(), expanded = false)
        setScreen()

        (0..2).forEach { composeRule.onNodeWithText("Test Airport $it").assertExists() }
        (3..4).forEach { composeRule.onNodeWithText("Test Airport $it").assertDoesNotExist() }

        composeRule.onNodeWithText("Show all (5)").performScrollTo().performClick()
        expandedChanges shouldBe listOf(true)
    }

    @Test
    fun `an expanded section shows every airport and can collapse`() {
        state = stateFor(results(), expanded = true)
        setScreen()

        (0..4).forEach { composeRule.onNodeWithText("Test Airport $it").assertExists() }
        composeRule.onNodeWithText("Show all (5)").assertDoesNotExist()

        composeRule.onNodeWithText("Show fewer").performScrollTo().performClick()
        expandedChanges shouldBe listOf(false)
    }

    @Test
    fun `three airports or fewer need no expansion`() {
        state = stateFor(results(rows = airports.take(3)))
        setScreen()

        composeRule.onNodeWithText("Test Airport 2").assertExists()
        composeRule.onNodeWithText("Show all (3)").assertDoesNotExist()
        composeRule.onNodeWithText("Show fewer").assertDoesNotExist()
    }

    @Test
    fun `tapping an airport opens it`() {
        state = stateFor(results())
        setScreen()

        composeRule.onNodeWithText("Test Airport 1").performScrollTo().performClick()

        clickedAirports shouldBe listOf(airports[1])
    }

    @Test
    fun `a failed airport search can be retried`() {
        state = stateFor(SearchViewModel.AirportStatus.Failed)
        setScreen()

        composeRule.onNodeWithText("Couldn't load airports").assertExists()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()

        retries shouldBe 1
    }

    @Test
    fun `a revoked installation is explained without a retry`() {
        state = stateFor(SearchViewModel.AirportStatus.Revoked)
        setScreen()

        composeRule.onNodeWithText("The server no longer accepts this installation.").assertExists()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `searched terms without airports say so`() {
        state = stateFor(results(rows = emptyList()))
        setScreen()

        composeRule.onNodeWithText("No airports found").assertExists()
    }

    @Test
    fun `terms the airport search rejected are named`() {
        state = stateFor(results(rows = emptyList(), invalidTerms = listOf("X", "1"), searchedAny = false))
        setScreen()

        composeRule.onNodeWithText("Not searched for airports: X, 1").assertExists()
        composeRule.onNodeWithText("No airports found").assertDoesNotExist()
    }

    @Test
    fun `a truncated answer asks to refine the search`() {
        state = stateFor(results(truncated = true))
        setScreen()

        composeRule.onNodeWithText("More matches, refine your search").assertExists()
    }

    @Test
    fun `a search with too many terms says which were searched`() {
        state = stateFor(
            SearchViewModel.AirportStatus.Loading,
            terms = List(10) { "T$it" },
            cutoff = true,
        )
        setScreen()

        composeRule.onNodeWithText("Only the first 10 terms were searched for airports").assertExists()
    }

    @Test
    fun `airport updates of the same result keep the aircraft selection`() {
        state = stateFor(SearchViewModel.AirportStatus.Loading, aircraft = 2)
        setScreen()

        composeRule.onNodeWithText("REG-00").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.waitForIdle()
        reportedSelection shouldBe setOf("AA0000")

        listOf(
            results(),
            SearchViewModel.AirportStatus.Failed,
            SearchViewModel.AirportStatus.Loading,
        ).forEach { status ->
            state = stateFor(status, aircraft = 2)
            composeRule.waitForIdle()

            composeRule.onNodeWithText("1 selected").assertExists()
            reportedSelection shouldBe setOf("AA0000")
        }
    }
}
