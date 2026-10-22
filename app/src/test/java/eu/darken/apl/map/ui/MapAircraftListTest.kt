package eu.darken.apl.map.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.MapSidebarData.Controls
import eu.darken.apl.map.core.MapSidebarData.SidebarAircraft
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import testhelper.BaseComposeTest

// Robolectric's default screen is short enough to scroll the controls and notes away with the rows
@Config(qualifiers = "w411dp-h891dp")
class MapAircraftListTest : BaseComposeTest() {

    private val clicked = mutableListOf<String>()
    private var clearClicks = 0
    private var watchedClicks = 0

    private val actions = MapListActions(
        onClose = {},
        onQueryChange = {},
        onToggleWatchedOnly = { watchedClicks++ },
        onSort = {},
        onClearFilters = { clearClicks++ },
        onAircraftClick = { clicked += it },
    )

    private fun aircraft(hex: String, callsign: String? = null) = SidebarAircraft(
        hex = hex,
        callsign = callsign,
        icaoType = "A320",
        countryCode = "DE",
        altitudeFt = 36_000,
        onGround = false,
        speedKnots = 450,
        military = false,
        shape = null,
        watched = false,
        distanceMeters = null,
    )

    private fun data(
        matches: List<SidebarAircraft>,
        controls: Controls = Controls(),
        inView: Int = matches.size,
        serverLimited: Boolean = false,
    ) = MapSidebarData(
        inView = inView,
        matches = matches,
        mapFilter = null,
        serverLimited = serverLimited,
        controls = controls,
    )

    private fun setList(data: MapSidebarData?, controls: Controls = data?.controls ?: Controls()) =
        composeRule.setContent {
            PreviewWrapper {
                MapAircraftList(
                    data = data,
                    controls = controls,
                    shapes = null,
                    selectedHex = null,
                    actions = actions,
                )
            }
        }

    @Test
    fun `tapping a row picks its aircraft`() {
        setList(data(listOf(aircraft("3C6444", "DLH4AB"))))

        composeRule.onNodeWithText("DLH4AB").performClick()

        clicked shouldContainExactly listOf("3C6444")
    }

    @Test
    fun `a filter without matches says so and clears from there`() {
        val controls = Controls(query = "xyz")
        setList(data(emptyList(), controls = controls, inView = 12))

        composeRule.onNodeWithText("No aircraft match “xyz”").assertExists()
        composeRule.onNodeWithText("0 matches · 12 in view").assertExists()
        composeRule.onNodeWithText("Clear filters").performClick()

        clearClicks shouldBe 1
    }

    @Test
    fun `without a filter an empty view offers nothing to clear`() {
        setList(data(emptyList()))

        composeRule.onNodeWithText("No aircraft in view").assertExists()
        composeRule.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun `the watched chip toggles the filter`() {
        setList(data(listOf(aircraft("3C6444"))))

        composeRule.onNode(hasText("Watched") and hasClickAction()).performClick()

        watchedClicks shouldBe 1
    }

    @Test
    fun `nothing loaded yet is not an empty list`() {
        setList(null)

        composeRule.onNodeWithText("No aircraft in view").assertDoesNotExist()
    }

    @Test
    fun `limits are disclosed separately`() {
        val matches = (0 until MapSidebarData.ROW_LIMIT + 1).map { aircraft("%06X".format(it)) }
        setList(data(matches, serverLimited = true))

        composeRule.onNodeWithText("200 of 201 shown, refine the filter for the rest").assertExists()
        composeRule.onNodeWithText("Map results are limited, zoom in for more").assertExists()
    }

    private fun setLive(initial: MapSidebarData): MutableState<MapSidebarData> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            PreviewWrapper {
                MapAircraftList(
                    data = state.value,
                    controls = state.value.controls,
                    shapes = null,
                    selectedHex = null,
                    actions = actions,
                )
            }
        }
        return state
    }

    // The order itself is MapListRowsTest's, the pause only shows while the shown rows differ from the newest ones
    @Test
    fun `touched rows wait and follow the data once let go`() {
        val first = aircraft("AAAAAA", "AAA1")
        val second = aircraft("BBBBBB", "BBB1")
        val live = setLive(data(listOf(first, second)))

        composeRule.onNodeWithText("AAA1").performTouchInput { down(center) }
        live.value = data(listOf(second, first))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Updates paused while you scroll").assertExists()

        composeRule.onNodeWithText("AAA1").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_100)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Updates paused while you scroll").assertDoesNotExist()
    }

    @Test
    fun `a reversed sort starts at its new first row`() {
        val planes = (0 until 30).map { aircraft("%06X".format(it), "C%02d".format(it)) }
        val live = setLive(data(planes))

        live.value = data(planes.reversed(), controls = Controls(ascending = false))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("C29").assertIsDisplayed()
    }

    @Test
    fun `an aircraft that left stays in its place while held`() {
        val first = aircraft("AAAAAA", "AAA1")
        val second = aircraft("BBBBBB", "BBB1")
        val live = setLive(data(listOf(first, second)))

        composeRule.onNodeWithText("BBB1").performTouchInput { down(center) }
        live.value = data(listOf(second))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AAA1").assertExists()
        composeRule.onNodeWithContentDescription("No longer in view", substring = true).assertExists()
    }

    @Test
    fun `a changed filter shows its rows right away, also while held`() {
        val first = aircraft("AAAAAA", "AAA1")
        val second = aircraft("BBBBBB", "BBB1")
        val live = setLive(data(listOf(first, second)))

        composeRule.onNodeWithText("AAA1").performTouchInput { down(center) }
        live.value = data(listOf(second), controls = Controls(query = "bbb"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AAA1").assertDoesNotExist()
        composeRule.onNodeWithText("Updates paused while you scroll").assertDoesNotExist()

        // Still pressed, so the next update with the same filter waits again
        live.value = data(listOf(second, aircraft("CCCCCC", "BBB2")), controls = Controls(query = "bbb"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Updates paused while you scroll").assertExists()
    }
}
