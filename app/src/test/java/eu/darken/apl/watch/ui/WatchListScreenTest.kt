package eu.darken.apl.watch.ui

import android.location.Location
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.preview.mockLocationWatchStatus
import eu.darken.apl.watch.ui.preview.mockSquawkWatchStatus
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class WatchListScreenTest : BaseComposeTest() {

    private val now = Instant.parse("2026-11-04T12:00:00Z")
    private val shownAll = mutableListOf<Watch.Status>()

    private val callsigns = listOf("AAA111", "BBB222", "CCC333", "DDD444", "EEE555")

    private fun aircraft(
        index: Int,
        seenAt: Instant = now.minusSeconds(10),
        location: Location? = null,
    ): Aircraft = FakeAircraft(
        hex = "A0000$index",
        callsign = callsigns[index],
        registration = null,
        description = null,
        airframe = null,
        location = location,
        messageSeenAt = seenAt,
    )

    private fun location(latitude: Double, longitude: Double) = Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
    }

    private fun setScreen(status: Watch.Status, ourLocation: Location? = null) = composeRule.setContent {
        PreviewWrapper {
            WatchListScreen(
                state = WatchListViewModel.State(
                    items = listOf(WatchListViewModel.WatchItem.Multi(status = status, ourLocation = ourLocation)),
                    now = now,
                ),
                onRefresh = {},
                onAddWatch = {},
                onWatchClick = {},
                onThumbnailClick = {},
                onAircraftTap = {},
                onShowAllTracked = { shownAll.add(it) },
                onDeleteSelected = {},
                onSortModeSelected = {},
            )
        }
    }

    private fun assertShown(vararg callsigns: String) = callsigns.forEach {
        composeRule.onNodeWithText(it).assertExists()
    }

    private fun assertHidden(vararg callsigns: String) = callsigns.forEach {
        composeRule.onNodeWithText(it).assertDoesNotExist()
    }

    @Test
    fun `squawk watch with five aircraft shows two rows and a show-all button`() {
        val status = mockSquawkWatchStatus((0..4).map { aircraft(it) }.toSet())
        setScreen(status)

        assertShown("AAA111", "BBB222")
        assertHidden("CCC333", "DDD444", "EEE555")

        composeRule.onNodeWithText("Show all 5").performClick()
        shownAll shouldBe listOf(status)
    }

    @Test
    fun `squawk watch with three aircraft shows every row and no show-all button`() {
        setScreen(mockSquawkWatchStatus((0..2).map { aircraft(it) }.toSet()))

        assertShown("AAA111", "BBB222", "CCC333")
        composeRule.onNodeWithText("Show all", substring = true).assertDoesNotExist()
    }

    @Test
    fun `squawk watch with four aircraft shows two rows and a show-all button`() {
        setScreen(mockSquawkWatchStatus((0..3).map { aircraft(it) }.toSet()))

        assertShown("AAA111", "BBB222")
        assertHidden("CCC333", "DDD444")
        composeRule.onNodeWithText("Show all 4").assertExists()
    }

    @Test
    fun `location watch with four aircraft shows two rows and the map action`() {
        setScreen(mockLocationWatchStatus((0..3).map { aircraft(it) }.toSet()))

        assertShown("AAA111", "BBB222")
        assertHidden("CCC333", "DDD444")
        composeRule.onNodeWithText("Show on map").assertExists()
    }

    @Test
    fun `rows are limited after sorting live first and by distance`() {
        val status = mockSquawkWatchStatus(
            setOf(
                aircraft(0, seenAt = now.minusSeconds(600), location = location(50.0, 8.0)),
                aircraft(1, location = location(52.0, 8.0)),
                aircraft(2, location = location(50.01, 8.0)),
                aircraft(3, location = location(50.02, 8.0)),
            )
        )
        setScreen(status, ourLocation = location(50.0, 8.0))

        assertShown("CCC333", "DDD444")
        assertHidden("AAA111", "BBB222")
    }

    @Test
    fun `selection mode keeps the rows and hides the show-all button`() {
        setScreen(mockSquawkWatchStatus((0..3).map { aircraft(it) }.toSet()))

        composeRule.onNodeWithText("Squawk 7700").performTouchInput { longClick() }

        assertShown("AAA111", "BBB222")
        assertHidden("CCC333", "DDD444")
        composeRule.onNodeWithText("Show all", substring = true).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Cancel").performClick()

        composeRule.onNodeWithText("Show all 4").assertExists()
    }

    private fun heldAbsent(status: LocationWatch.Status) = status.copy(
        server = WatchServerStatus(
            isHeld = true,
            status = WatchServerStatus.Status.ACTIVE,
            pauseReason = null,
            rejection = null,
            presence = WatchServerStatus.Presence.ABSENT,
            lastMatch = null,
        ),
    )

    @Test
    fun `location watch with nothing at the last check says so in the text column`() {
        setScreen(heldAbsent(mockLocationWatchStatus()))

        composeRule.onAllNodesWithText(NONE_AT_LAST_CHECK, useUnmergedTree = true).assertCountEquals(1)
        val noneLeft = composeRule.onNodeWithText(NONE_AT_LAST_CHECK, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().left
        val typeLeft = composeRule.onNodeWithText("Location ·", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().left
        noneLeft shouldBe typeLeft
    }

    @Test
    fun `location watch the server does not hold says nothing about the last check`() {
        setScreen(mockLocationWatchStatus())

        composeRule.onAllNodesWithText(NONE_AT_LAST_CHECK, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `location watch with tracked aircraft says nothing about the last check`() {
        setScreen(heldAbsent(mockLocationWatchStatus(setOf(aircraft(0)))))

        composeRule.onAllNodesWithText(NONE_AT_LAST_CHECK, useUnmergedTree = true).assertCountEquals(0)
    }

    companion object {
        private const val NONE_AT_LAST_CHECK = "None at last check"
    }
}
