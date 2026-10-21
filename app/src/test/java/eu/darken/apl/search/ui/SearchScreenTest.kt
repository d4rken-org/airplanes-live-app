package eu.darken.apl.search.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.onNodeWithText
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.search.core.SearchInput
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class SearchScreenTest : BaseComposeTest() {

    private val seenAt = Instant.parse("2026-10-21T12:00:00Z")
    private var now = seenAt.plusSeconds(5)

    private fun setScreen() = composeRule.setContent {
        PreviewWrapper {
            SearchScreen(
                state = SearchViewModel.State(
                    input = SearchInput(text = "DLH123"),
                    items = listOf(
                        SearchViewModel.SearchItem.AircraftResult(
                            aircraft = FakeAircraft(messageSeenAt = seenAt, positionSeenAt = seenAt),
                            watch = null,
                            distanceInMeter = null,
                        )
                    ),
                    resultRevision = 1,
                ),
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
                serverNow = { now.toEpochMilli() },
            )
        }
    }

    @Test
    fun `a live result stops being live as time passes without a new result`() {
        setScreen()
        composeRule.onNodeWithText("Live").assertExists()

        now = seenAt.plusSeconds(20)
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Live").assertDoesNotExist()
    }
}
