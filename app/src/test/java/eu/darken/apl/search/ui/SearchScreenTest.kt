package eu.darken.apl.search.ui

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.search.core.SearchInput
import eu.darken.apl.search.core.SearchRepo
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class SearchScreenTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val seenAt = Instant.parse("2026-10-21T12:00:00Z")
    private var now = seenAt.plusSeconds(5)

    private fun setScreen(
        items: List<SearchViewModel.SearchItem> = listOf(
            SearchViewModel.SearchItem.AircraftResult(
                aircraft = FakeAircraft(messageSeenAt = seenAt, positionSeenAt = seenAt),
                watch = null,
                distanceInMeter = null,
            )
        ),
        nowMillis: Long = System.currentTimeMillis(),
    ) = composeRule.setContent {
        PreviewWrapper {
            SearchScreen(
                state = SearchViewModel.State(
                    input = SearchInput(text = "DLH123"),
                    items = items,
                    nowMillis = nowMillis,
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

    private fun result(
        distanceInMeter: Float? = null,
        squawk: String? = "1000",
        cacheOnly: Boolean = false,
    ) = SearchViewModel.SearchItem.AircraftResult(
        aircraft = FakeAircraft(messageSeenAt = seenAt, positionSeenAt = seenAt, squawk = squawk),
        watch = null,
        distanceInMeter = distanceInMeter,
        cacheOnly = cacheOnly,
    )

    private fun assertExistsUnmerged(matcher: SemanticsMatcher) =
        composeRule.onNode(matcher, useUnmergedTree = true).assertExists()

    private fun assertDoesNotExistUnmerged(matcher: SemanticsMatcher) =
        composeRule.onNode(matcher, useUnmergedTree = true).assertDoesNotExist()

    private val livePill = hasContentDescription("Live")
    private val squawkCode = hasText("1000")
    private val cachedLabel get() = hasText(context.getString(R.string.search_result_cached_label))
    private val inPillLine = hasAnyAncestor(hasTestTag(SEARCH_RESULT_PILL_LINE_TAG))

    private fun setBanner(resetsAt: Instant?, charged: SearchRepo.Charged) = setScreen(
        items = listOf(
            SearchViewModel.SearchItem.UpgradeBanner(SearchViewModel.BannerState.Exhausted(resetsAt), charged)
        ),
        // The server's clock, years away from the device's
        nowMillis = SERVER_NOW.toEpochMilli(),
    )

    @Test
    fun `a live result stops being live as time passes without a new result`() {
        setScreen()
        composeRule.onNodeWithContentDescription("Live").assertExists()

        now = seenAt.plusSeconds(20)
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithContentDescription("Live").assertDoesNotExist()
    }

    @Test
    fun `the seen pill sits in the result text, not on the photo`() {
        setScreen()

        assertExistsUnmerged(livePill and hasAnyAncestor(hasTestTag(SEARCH_RESULT_TEXT_TAG)))
        assertDoesNotExistUnmerged(livePill and hasAnyAncestor(hasTestTag(SEARCH_RESULT_PHOTO_TAG)))
    }

    @Test
    fun `the seen pill shares the distance and squawk line`() {
        setScreen(items = listOf(result(distanceInMeter = 12_000f)))

        assertExistsUnmerged(squawkCode and inPillLine)
        assertExistsUnmerged(livePill and inPillLine)
    }

    @Test
    fun `the seen pill shares the squawk line when there is no distance`() {
        setScreen(items = listOf(result(distanceInMeter = null)))

        assertExistsUnmerged(squawkCode and inPillLine)
        assertExistsUnmerged(livePill and inPillLine)
    }

    @Test
    fun `the seen pill of a cached result shares the cached line, not the squawk line`() {
        setScreen(items = listOf(result(distanceInMeter = 12_000f, cacheOnly = true)))

        assertExistsUnmerged(cachedLabel and inPillLine)
        assertExistsUnmerged(livePill and inPillLine)
        assertExistsUnmerged(squawkCode)
        assertDoesNotExistUnmerged(squawkCode and inPillLine)
    }

    @Test
    fun `the seen pill of a cached result without distance or squawk shares the cached line`() {
        setScreen(items = listOf(result(squawk = null, cacheOnly = true)))

        assertExistsUnmerged(cachedLabel and inPillLine)
        assertExistsUnmerged(livePill and inPillLine)
    }

    @Test
    fun `the seen pill stands alone at the bottom of the text without distance, squawk or cached line`() {
        setScreen(items = listOf(result(squawk = null)))

        assertExistsUnmerged(livePill and hasAnyAncestor(hasTestTag(SEARCH_RESULT_TEXT_TAG)))
        composeRule.onNodeWithTag(SEARCH_RESULT_PILL_LINE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a reached viewing limit says when it resets and offers no searches`() {
        setBanner(resetsAt = SERVER_NOW.plusSeconds(5 * 3600), charged = SearchRepo.Charged.VIEWING)

        composeRule.onNodeWithText(context.getString(R.string.search_banner_exhausted_title_viewing)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.search_banner_exhausted_msg_viewing_x, "in 5 hours"))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.search_banner_exhausted_msg), substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `a reached viewing limit without a reset time shows only the title`() {
        setBanner(resetsAt = null, charged = SearchRepo.Charged.VIEWING)

        composeRule.onNodeWithText(context.getString(R.string.search_banner_exhausted_title_viewing)).assertExists()
        composeRule.onNodeWithText("Resets", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.search_banner_exhausted_msg), substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `a reached search limit says when it resets and offers more searches`() {
        setBanner(resetsAt = SERVER_NOW.plusSeconds(5 * 3600), charged = SearchRepo.Charged.SEARCH)

        composeRule.onNodeWithText(context.getString(R.string.search_banner_exhausted_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.search_banner_exhausted_msg_x, "in 5 hours"))
            .assertExists()
    }

    companion object {
        private val SERVER_NOW = Instant.parse("2020-01-01T00:00:00Z")
    }
}
