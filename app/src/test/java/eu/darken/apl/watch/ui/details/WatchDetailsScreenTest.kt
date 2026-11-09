package eu.darken.apl.watch.ui.details

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnit
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.UnitFormatter
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.ui.AltitudeBand
import eu.darken.apl.watch.ui.AltitudeBandUnit
import eu.darken.apl.watch.ui.preview.mockLocationWatchStatus
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant
import java.util.Locale

class WatchDetailsScreenTest : BaseComposeTest() {

    private val baseStatus = mockLocationWatchStatus()
    private val bands = mutableListOf<AltitudeBand>()

    private fun withBand(minFt: Int?, maxFt: Int?): LocationWatch.Status {
        val watch = baseStatus.watch
        return baseStatus.copy(
            watch = watch.copy(
                base = BaseWatchEntity(
                    id = watch.id,
                    watchType = LocationWatchEntity.TYPE_KEY,
                    latitude = watch.latitude,
                    longitude = watch.longitude,
                    radius = watch.radiusInMeters,
                    minAltitudeFt = minFt,
                    maxAltitudeFt = maxFt,
                ),
            ),
        )
    }

    private fun detailsState(status: LocationWatch.Status) = WatchDetailsViewModel.State(
        status = status,
        now = Instant.parse("2026-11-09T12:00:00Z"),
        aircraft = null,
        registration = null,
        liveCount = 0,
        distanceInMeter = null,
    )

    private fun setScreen(state: () -> WatchDetailsViewModel.State) = composeRule.setContent {
        PreviewWrapper {
            CompositionLocalProvider(
                LocalUnitFormatter provides UnitFormatter(AircraftUnits.METRIC, DistanceUnit.KILOMETERS, Locale.US)
            ) {
                WatchDetailsContent(
                    state = state(),
                    progress = { 1f },
                    maxPhotoHeight = 0,
                    onPhotoClick = {},
                    onNoteChanged = {},
                    onNotificationsChanged = {},
                    onShowOnMap = {},
                    onShowInSearch = {},
                    onRemove = {},
                    onThumbnailClick = {},
                    onAircraftClick = {},
                    onLocationSaved = { _, band, _, onDone ->
                        bands.add(band)
                        onDone(true)
                    },
                    onExpand = {},
                )
            }
        }
    }

    private fun openEditor() {
        composeRule.onNodeWithText("Edit area").performScrollTo().performClick()
    }

    private fun save() {
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
    }

    @Test
    fun `a new label alone keeps the stored band exactly`() {
        val state = detailsState(withBand(1_000, 59_999))
        setScreen { state }

        openEditor()
        composeRule.onNodeWithText("Label").performScrollTo().performTextReplacement("Heathrow Airport")
        save()

        bands shouldBe listOf(AltitudeBand(1_000, 59_999))
    }

    @Test
    fun `a stored band changed while editing saves the new one`() {
        val state = mutableStateOf(detailsState(withBand(1_000, 59_999)))
        setScreen { state.value }

        openEditor()
        composeRule.onNodeWithText(AltitudeBandUnit.METERS.fromFeet(1_000).toString()).assertExists()
        composeRule.onNodeWithText(AltitudeBandUnit.METERS.fromFeet(59_999).toString()).assertExists()
        composeRule.runOnIdle { state.value = detailsState(withBand(2_000, 30_000)) }
        composeRule.waitForIdle()
        save()

        bands shouldBe listOf(AltitudeBand(2_000, 30_000))
    }

    @Test
    fun `a typed max in meters saves in feet and keeps the untouched min`() {
        val state = detailsState(withBand(1_000, 59_999))
        setScreen { state }

        openEditor()
        composeRule.onNodeWithText("Max altitude").performScrollTo().performTextClearance()
        composeRule.onNodeWithText("Max altitude").performTextInput("3000")
        save()

        bands.size shouldBe 1
        bands.single().minFt shouldBe 1_000
        bands.single().maxFt shouldBe AltitudeBandUnit.METERS.toFeet(3000)
    }
}
