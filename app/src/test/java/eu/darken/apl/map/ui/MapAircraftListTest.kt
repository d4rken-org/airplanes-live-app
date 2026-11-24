package eu.darken.apl.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.AircraftKind
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.MapSidebarData.Controls
import eu.darken.apl.map.core.MapSidebarData.MapFilter
import eu.darken.apl.map.core.MapSidebarData.SidebarAircraft
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import testhelper.BaseComposeTest

// Robolectric's default screen is short enough to scroll the controls and notes away with the rows
@Config(qualifiers = "w411dp-h891dp")
class MapAircraftListTest : BaseComposeTest() {

    private val clicked = mutableListOf<String>()
    private val longClicked = mutableListOf<String>()
    private val haptics = mutableListOf<HapticFeedbackType>()
    private var closeClicks = 0
    private val queries = mutableListOf<String>()
    private var clearClicks = 0
    private var watchedClicks = 0
    private var militaryClicks = 0
    private val toggledKinds = mutableListOf<AircraftKind>()
    private var showAllKindsClicks = 0
    private var isolateClicks = 0

    private val actions = MapListActions(
        onClose = { closeClicks++ },
        onQueryChange = { queries += it },
        onToggleWatchedOnly = { watchedClicks++ },
        onToggleMilitaryOnly = { militaryClicks++ },
        onToggleAircraftKind = { toggledKinds += it },
        onShowAllAircraftKinds = { showAllKindsClicks++ },
        onToggleIsolate = { isolateClicks++ },
        onSort = {},
        onClearFilters = { clearClicks++ },
        onAircraftClick = { clicked += it },
        onAircraftLongClick = { longClicked += it },
    )

    private val recordingHaptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
        }
    }

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
        mapFilters: List<MapFilter> = emptyList(),
    ) = MapSidebarData(
        inView = inView,
        matches = matches,
        mapFilters = mapFilters,
        serverLimited = serverLimited,
        controls = controls,
    )

    private fun setList(
        data: MapSidebarData?,
        controls: Controls = data?.controls ?: Controls(),
        filters: MapListFilters = MapListFilters(),
        pinned: Collection<String> = emptyList(),
        height: Dp? = null,
    ) = composeRule.setContent {
        PreviewWrapper {
            CompositionLocalProvider(LocalHapticFeedback provides recordingHaptics) {
                Box(modifier = if (height != null) Modifier.height(height) else Modifier) {
                    MapAircraftList(
                        data = data,
                        controls = controls,
                        filters = filters,
                        shapes = null,
                        selectedHex = null,
                        pinned = pinned,
                        actions = actions,
                    )
                }
            }
        }
    }

    @Test
    fun `tapping a row picks its aircraft`() {
        setList(data(listOf(aircraft("3C6444", "DLH4AB"))))

        composeRule.onNodeWithText("DLH4AB").performClick()

        clicked shouldContainExactly listOf("3C6444")
    }

    @Test
    fun `long-pressing a row pins its aircraft with one haptic tick`() {
        setList(data(listOf(aircraft("3C6444", "DLH4AB"), aircraft("4CA123", "RYR1"))))

        composeRule.onNodeWithText("DLH4AB").performTouchInput { longClick() }

        longClicked shouldContainExactly listOf("3C6444")
        clicked.shouldBeEmpty()
        haptics shouldContainExactly listOf(HapticFeedbackType.LongPress)
    }

    @Test
    fun `pinned rows show the pin`() {
        setList(data(listOf(aircraft("3C6444", "DLH4AB"), aircraft("4CA123", "RYR1"))), pinned = listOf("3C6444"))

        composeRule.onNode(hasText("DLH4AB") and hasContentDescription("Pinned")).assertExists()
        composeRule.onNode(hasText("RYR1") and hasContentDescription("Pinned")).assertDoesNotExist()
    }

    @Test
    fun `the long-press is announced as pin or unpin`() {
        setList(data(listOf(aircraft("3C6444", "DLH4AB"), aircraft("4CA123", "RYR1"))), pinned = listOf("3C6444"))

        composeRule.onNode(hasText("DLH4AB") and hasLongClickLabel("Unpin")).assertExists()
        composeRule.onNode(hasText("RYR1") and hasLongClickLabel("Pin")).assertExists()
    }

    private fun hasLongClickLabel(label: String) = SemanticsMatcher("long click label $label") {
        it.config.getOrNull(SemanticsActions.OnLongClick)?.label == label
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

    private fun matches(count: Int) = (0 until count).map { aircraft("%06X".format(it)) }

    private fun assertLimitNotes(capped: Boolean, serverLimited: Boolean) {
        composeRule.onNodeWithText(CAP_NOTE).run { if (capped) assertExists() else assertDoesNotExist() }
        composeRule.onNodeWithText(SERVER_LIMITED_NOTE).run { if (serverLimited) assertExists() else assertDoesNotExist() }
        composeRule.onNodeWithText("refine the filter for the rest", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Map results are limited, zoom in for more").assertDoesNotExist()
    }

    @Test
    fun `without limits the header has no notes`() {
        setList(data(matches(3)))

        assertLimitNotes(capped = false, serverLimited = false)
    }

    @Test
    fun `exactly the row limit is not capped`() {
        setList(data(matches(MapSidebarData.ROW_LIMIT)))

        assertLimitNotes(capped = false, serverLimited = false)
    }

    @Test
    fun `more matches than the row limit note the cap`() {
        setList(data(matches(MapSidebarData.ROW_LIMIT + 1)))

        assertLimitNotes(capped = true, serverLimited = false)
    }

    @Test
    fun `server limited results are noted`() {
        setList(data(matches(3), serverLimited = true))

        assertLimitNotes(capped = false, serverLimited = true)
    }

    @Test
    fun `server limited results are noted without matches`() {
        setList(data(emptyList(), serverLimited = true))

        assertLimitNotes(capped = false, serverLimited = true)
    }

    @Test
    fun `both limits are noted`() {
        setList(data(matches(MapSidebarData.ROW_LIMIT + 1), serverLimited = true))

        assertLimitNotes(capped = true, serverLimited = true)
    }

    @Test
    fun `both limits are noted on a short panel`() {
        setList(data(matches(MapSidebarData.ROW_LIMIT + 1), serverLimited = true), height = 500.dp)

        assertLimitNotes(capped = true, serverLimited = true)
    }

    private fun setLiveControls(initial: Controls): MutableState<Controls> {
        val controls = mutableStateOf(initial)
        composeRule.setContent {
            PreviewWrapper {
                MapAircraftList(
                    data = null,
                    controls = controls.value,
                    filters = MapListFilters(),
                    shapes = null,
                    selectedHex = null,
                    pinned = emptyList(),
                    actions = actions,
                )
            }
        }
        return controls
    }

    @Test
    fun `a query cleared from outside empties the field`() {
        val controls = setLiveControls(Controls(query = "dlh"))
        composeRule.onNodeWithText("dlh").assertExists()

        controls.value = Controls()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("dlh").assertDoesNotExist()
        composeRule.onNodeWithText("Filter aircraft").assertExists()
    }

    @Test
    fun `the clear button empties the filter text`() {
        setList(data(listOf(aircraft("3C6444"))))
        composeRule.onNodeWithContentDescription("Clear the filter text").assertDoesNotExist()

        composeRule.onNode(hasSetTextAction()).performTextInput("dlh")
        composeRule.onNodeWithContentDescription("Clear the filter text").performClick()

        queries shouldContainExactly listOf("dlh", "")
        composeRule.onNodeWithText("Filter aircraft").assertExists()
    }

    @Test
    fun `the header shows the count without a title`() {
        setList(data(listOf(aircraft("3C6444", "DLH4AB"))))

        composeRule.onNodeWithText("1 in view").assertExists()
        composeRule.onNodeWithText("Aircraft").assertDoesNotExist()
    }

    @Test
    fun `nothing loaded yet still closes`() {
        setList(null)

        composeRule.onNodeWithContentDescription("Close aircraft list").performClick()

        closeClicks shouldBe 1
    }

    @Test
    fun `the list names its pane`() {
        setList(data(listOf(aircraft("3C6444"))))

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Aircraft list")).assertExists()
    }

    @Test
    fun `a changed query from outside leaves the field as typed`() {
        val controls = setLiveControls(Controls(query = "dlh"))

        controls.value = Controls(query = "dl")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("dlh").assertExists()
    }

    private fun setLive(initial: MapSidebarData): MutableState<MapSidebarData> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            PreviewWrapper {
                MapAircraftList(
                    data = state.value,
                    controls = state.value.controls,
                    filters = MapListFilters(),
                    shapes = null,
                    selectedHex = null,
                    pinned = emptyList(),
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
    fun `an aircraft that left can be neither picked nor pinned`() {
        val first = aircraft("AAAAAA", "AAA1")
        val second = aircraft("BBBBBB", "BBB1")
        val live = setLive(data(listOf(first, second)))

        composeRule.onNodeWithText("BBB1").performTouchInput { down(center) }
        live.value = data(listOf(second))
        composeRule.waitForIdle()

        // The first finger still holds the rows, and long-presses its own row meanwhile
        composeRule.onNodeWithText("AAA1").assertIsNotEnabled().performTouchInput {
            down(1, center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up(1)
            down(1, center)
            up(1)
        }
        composeRule.onNodeWithText("AAA1").assertExists()

        longClicked shouldNotContain "AAAAAA"
        clicked.shouldBeEmpty()
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

    @Test
    fun `a changed map filter shows its rows right away, also while held`() {
        val first = aircraft("AAAAAA", "AAA1")
        val second = aircraft("BBBBBB", "BBB1")
        val live = setLive(data(listOf(first, second)))

        composeRule.onNodeWithText("AAA1").performTouchInput { down(center) }
        live.value = data(listOf(second), mapFilters = listOf(MapFilter.MilitaryOnly))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AAA1").assertDoesNotExist()
        composeRule.onNodeWithText("Updates paused while you scroll").assertDoesNotExist()

        // Still pressed, so the next update with the same map filter waits again
        live.value = data(listOf(second, aircraft("CCCCCC", "CCC1")), mapFilters = listOf(MapFilter.MilitaryOnly))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Updates paused while you scroll").assertExists()
    }

    @Test
    fun `the military chip toggles the map filter`() {
        setList(data(listOf(aircraft("3C6444"))), filters = MapListFilters(militaryOnly = true))

        composeRule.onNodeWithTag(LIST_MILITARY_TAG).assertIsSelected().performClick()

        militaryClicks shouldBe 1
    }

    private fun kindItem(kind: AircraftKind) = composeRule.onNodeWithTag(aircraftKindTag(kind))

    private fun allKindsItem() = composeRule.onNodeWithTag(AIRCRAFT_KINDS_ALL_TAG)

    @Test
    fun `the kinds chip opens the kinds, picking one keeps them open`() {
        setList(data(listOf(aircraft("3C6444"))))
        kindItem(AircraftKind.HELICOPTER).assertDoesNotExist()

        composeRule.onNodeWithTag(LIST_KINDS_TAG).performClick()
        kindItem(AircraftKind.HELICOPTER).performSemanticsAction(SemanticsActions.OnClick)

        toggledKinds shouldContainExactly listOf(AircraftKind.HELICOPTER)
        kindItem(AircraftKind.GLIDER).assertExists()
    }

    @Test
    fun `all kinds shows every kind again and closes the kinds`() {
        setList(data(listOf(aircraft("3C6444"))), filters = MapListFilters(kinds = setOf(AircraftKind.HELICOPTER)))

        composeRule.onNodeWithTag(LIST_KINDS_TAG).performClick()
        allKindsItem().performSemanticsAction(SemanticsActions.OnClick)

        showAllKindsClicks shouldBe 1
        allKindsItem().assertDoesNotExist()
    }

    @Test
    fun `the chosen kinds are reported as selected`() {
        setList(
            data(listOf(aircraft("3C6444"))),
            filters = MapListFilters(kinds = setOf(AircraftKind.HELICOPTER, AircraftKind.GLIDER)),
        )

        composeRule.onNode(hasTestTag(LIST_KINDS_TAG) and hasText("2 categories")).assertIsSelected()
        composeRule.onNodeWithTag(LIST_KINDS_TAG).performClick()

        kindItem(AircraftKind.HELICOPTER).assertIsSelected()
        kindItem(AircraftKind.GLIDER).assertIsSelected()
        kindItem(AircraftKind.PLANE).assertIsNotSelected()
        allKindsItem().assertIsNotSelected()
    }

    @Test
    fun `without a chosen kind all kinds is reported as selected`() {
        setList(data(listOf(aircraft("3C6444"))))

        composeRule.onNode(hasTestTag(LIST_KINDS_TAG) and hasText("Category")).assertIsNotSelected()
        composeRule.onNodeWithTag(LIST_KINDS_TAG).performClick()

        allKindsItem().assertIsSelected()
        AircraftKind.entries.forEach { kindItem(it).assertIsNotSelected() }
    }

    @Test
    fun `isolating needs something selected or pinned`() {
        setList(data(listOf(aircraft("3C6444"))), filters = MapListFilters(isolate = true))

        composeRule.onNodeWithTag(LIST_ISOLATE_TAG).assertIsNotEnabled().assertIsNotSelected()
    }

    @Test
    fun `the isolate chip toggles isolating`() {
        setList(data(listOf(aircraft("3C6444"))), filters = MapListFilters(canIsolate = true))

        composeRule.onNodeWithTag(LIST_ISOLATE_TAG).assertIsEnabled().assertIsNotSelected().performClick()

        isolateClicks shouldBe 1
    }

    @Test
    fun `a map filter without matches says so and clears from there`() {
        setList(data(emptyList()), filters = MapListFilters(militaryOnly = true))

        composeRule.onNodeWithText("No aircraft match the filters").assertExists()
        composeRule.onNodeWithText("Clear filters").performClick()

        clearClicks shouldBe 1
    }

    @Test
    fun `an isolation with nothing to keep offers nothing to clear`() {
        setList(data(emptyList()), filters = MapListFilters(isolate = true))

        composeRule.onNodeWithText("No aircraft in view").assertExists()
        composeRule.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    companion object {
        private const val CAP_NOTE = "200 shown, filter for more"
        private const val SERVER_LIMITED_NOTE = "Partial results, zoom in for more"
    }
}
