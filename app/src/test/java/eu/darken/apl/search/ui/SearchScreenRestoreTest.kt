package eu.darken.apl.search.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.search.core.SearchInput
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class SearchScreenRestoreTest : BaseComposeTest() {

    private var shown by mutableStateOf(true)
    private var state by mutableStateOf(stateFor(revision = 1))
    private var draft by mutableStateOf<SearchViewModel.Draft?>(null)
    private val saved = mutableMapOf<Int, SearchViewModel.GridPosition>()
    private val savedSelection = mutableMapOf<Int, Set<String>>()
    private val reportedSelection = mutableMapOf<Int, Set<String>>()

    private fun stateFor(revision: Int, text: String = "A320") = SearchViewModel.State(
        input = SearchInput(text = text),
        items = List(40) { i ->
            SearchViewModel.SearchItem.AircraftResult(
                aircraft = FakeAircraft(hex = "AA%04d".format(i), registration = "REG-%02d".format(i)),
                watch = null,
                distanceInMeter = null,
            )
        },
        resultRevision = revision,
    )

    // What the ViewModel hands out: scroll and selection only for the result they were saved for
    private fun screenState(revision: Int) = SearchViewModel.ScreenState(
        revision = revision,
        selection = savedSelection[revision].orEmpty(),
        gridPosition = saved[revision] ?: SearchViewModel.GridPosition(),
    )

    private fun setScreen() = composeRule.setContent {
        PreviewWrapper {
            // Stands in for the tab's entry, whose saved state outlives leaving the tab
            val holder = rememberSaveableStateHolder()
            if (shown) {
                holder.SaveableStateProvider("search") {
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
                        draft = draft,
                        screenState = ::screenState,
                        onSelectionChange = { revision, selection -> reportedSelection[revision] = selection },
                        onGridPositionChange = { revision, position -> saved[revision] = position },
                    )
                }
            }
        }
    }

    @Test
    fun `coming back to the same result keeps the scroll position`() {
        setScreen()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)

        shown = false
        composeRule.waitForIdle()
        shown = true

        composeRule.onNodeWithText("REG-30").assertIsDisplayed()
    }

    @Test
    fun `a result that landed while away starts at the top`() {
        setScreen()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)

        shown = false
        composeRule.waitForIdle()
        state = stateFor(revision = 2)
        shown = true

        composeRule.onNodeWithText("REG-00").assertIsDisplayed()
    }

    @Test
    fun `a result that landed while away starts with nothing selected`() {
        savedSelection[1] = setOf("AA0001")
        setScreen()
        composeRule.waitForIdle()
        reportedSelection[1] shouldBe setOf("AA0001")

        shown = false
        composeRule.waitForIdle()
        state = stateFor(revision = 2)
        shown = true
        composeRule.waitForIdle()

        reportedSelection[2] shouldBe emptySet()
    }

    @Test
    fun `a draft typed against an older input is dropped`() {
        draft = SearchViewModel.Draft(base = "A320", text = "A32")
        setScreen()
        composeRule.onNode(hasSetTextAction()).assert(hasText("A32"))

        shown = false
        composeRule.waitForIdle()
        // The draft stays as it was, the screen has to see that it belongs to the old input
        state = stateFor(revision = 2, text = "B738")
        shown = true

        composeRule.onNode(hasSetTextAction()).assert(hasText("B738"))
    }
}
