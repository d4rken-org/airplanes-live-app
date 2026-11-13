package eu.darken.apl.watch.ui

import android.location.Location
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.preview.mockAircraftWatchStatus
import eu.darken.apl.watch.ui.preview.mockLocationWatchStatus
import eu.darken.apl.watch.ui.preview.mockSquawkWatchStatus
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.floats.plusOrMinus
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

    private fun setScreen(status: Watch.Status, ourLocation: Location? = null) =
        setScreen(listOf(WatchListViewModel.WatchItem.Multi(status = status, ourLocation = ourLocation)))

    private fun setScreen(items: List<WatchListViewModel.WatchItem>) = composeRule.setContent {
        PreviewWrapper {
            WatchListScreen(
                state = WatchListViewModel.State(items = items, now = now),
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

    @Test
    fun `aircraft photo keeps its 3 to 2 size when a note makes the card taller`() {
        setScreen(
            listOf(
                WatchListViewModel.WatchItem.Single(
                    status = mockAircraftWatchStatus(aircraft = null, note = LONG_NOTE),
                    aircraft = null,
                    ourLocation = null,
                    registration = "D-NOTE",
                ),
                WatchListViewModel.WatchItem.Single(
                    status = mockAircraftWatchStatus(aircraft = null, note = ""),
                    aircraft = null,
                    ourLocation = null,
                    registration = "D-PLAIN",
                ),
            )
        )

        val photos = composeRule.onAllNodesWithContentDescription("Aircraft photo", useUnmergedTree = true)
        photos.assertCountEquals(2)
        val photoBounds = (0 until 2).map { photos[it].getUnclippedBoundsInRoot() }
        fun photoOfCard(title: String): DpRect {
            val titleTop = composeRule.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot().top
            return photoBounds.single { titleTop >= it.top && titleTop < it.bottom }
        }
        val notePhoto = photoOfCard("D-NOTE")
        val plainPhoto = photoOfCard("D-PLAIN")

        notePhoto.width shouldBe plainPhoto.width
        notePhoto.height shouldBe plainPhoto.height
        (notePhoto.width / notePhoto.height) shouldBe (1.5f plusOrMinus 0.02f)

        val noteBottom = composeRule.onNodeWithText(LONG_NOTE, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
        noteBottom shouldBeGreaterThan notePhoto.bottom
    }

    companion object {
        private const val LONG_NOTE = "Seen over the harbour twice last week, usually in the late afternoon on approach"
        private const val NONE_AT_LAST_CHECK = "None at last check"
    }
}
