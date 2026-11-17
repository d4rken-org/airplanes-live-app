package eu.darken.apl.map.ui

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.seenAgeText
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.map.core.MapAircraftDetails
import org.junit.Test
import testhelper.BaseComposeTest

class MapAircraftSheetSeenPillTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val seenAt = 1_700_000_000_000L
    private val serverNow = seenAt + 7 * 60_000L

    private fun setSheet(
        seen: NativeMapViewModel.Seen?,
        note: String? = null,
        details: MapAircraftDetails = MapAircraftDetails(hex = "4CA2B1", callsign = "RYR1234", icaoType = "B738"),
    ) = composeRule.setContent {
        PreviewWrapper {
            AircraftDetailsSheetContent(
                details = details,
                loading = false,
                note = note,
                route = null,
                routeProgress = RouteProgress.Unknown,
                progress = { 0f },
                topInset = 0,
                maxPhotoHeight = 0,
                onCollapsedHeight = {},
                watched = false,
                seen = seen,
                serverNow = { serverNow },
                placeAction = AircraftPlaceAction.ShowOnMap(onClick = {}),
                onWatchAction = {},
                onWatchWithNote = {},
                onPhotoClick = {},
            )
        }
    }

    private fun assertExistsUnmerged(matcher: SemanticsMatcher) =
        composeRule.onNode(matcher, useUnmergedTree = true).assertExists()

    private fun assertDoesNotExistUnmerged(matcher: SemanticsMatcher) =
        composeRule.onNode(matcher, useUnmergedTree = true).assertDoesNotExist()

    private val inHeadline = hasAnyAncestor(hasTestTag(AIRCRAFT_SHEET_HEADLINE_TAG))
    private val onPhoto = hasAnyAncestor(hasTestTag(AIRCRAFT_SHEET_PHOTO_TAG))
    private val livePill get() = hasContentDescription(context.getString(R.string.common_live_label))
    private val unknownPill get() = hasContentDescription(context.getString(R.string.aircraft_details_last_seen_unknown))
    private val agePill
        get() = hasContentDescription(
            context.getString(R.string.search_aircraft_last_seen_description, seenAgeText(seenAt, serverNow))
        )

    @Test
    fun `the live pill sits in the headline row, not on the photo`() {
        setSheet(seen = NativeMapViewModel.Seen.Live)

        assertExistsUnmerged(livePill and inHeadline)
        assertDoesNotExistUnmerged(livePill and onPhoto)
    }

    @Test
    fun `the age pill sits in the headline row`() {
        setSheet(seen = NativeMapViewModel.Seen.At(seenAt))

        assertExistsUnmerged(agePill and inHeadline)
    }

    @Test
    fun `an unknown position shows its pill in the headline row`() {
        setSheet(seen = NativeMapViewModel.Seen.Unknown)

        assertExistsUnmerged(unknownPill and inHeadline)
    }

    @Test
    fun `no pill while the last position note shows`() {
        setSheet(seen = null, note = "Last position 7 min. ago")

        assertDoesNotExistUnmerged(livePill)
        assertDoesNotExistUnmerged(agePill)
        assertDoesNotExistUnmerged(unknownPill)
        composeRule.onNodeWithText("Last position 7 min. ago").assertExists()
    }

    @Test
    fun `the military badge stays on the photo while the pill moves to the headline row`() {
        setSheet(
            seen = NativeMapViewModel.Seen.Live,
            details = MapAircraftDetails(hex = "AE1234", callsign = "RCH123", military = true),
        )

        assertExistsUnmerged(
            hasContentDescription(context.getString(R.string.map_aircraft_details_flag_military)) and onPhoto
        )
        assertExistsUnmerged(livePill and inHeadline)
    }
}
