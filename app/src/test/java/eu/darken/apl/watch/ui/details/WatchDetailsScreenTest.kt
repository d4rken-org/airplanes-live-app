package eu.darken.apl.watch.ui.details

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnit
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.UnitFormatter
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.history.HourAircraft
import eu.darken.apl.watch.core.history.HourAircraftState
import eu.darken.apl.watch.core.history.HourCoverage
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.ui.AltitudeBand
import eu.darken.apl.watch.ui.AltitudeBandUnit
import eu.darken.apl.watch.ui.preview.mockLocationWatchStatus
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class WatchDetailsScreenTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val baseStatus = mockLocationWatchStatus()
    private val bands = mutableListOf<AltitudeBand>()
    private val pickedSlots = mutableListOf<WatchActivityGrid.Slot>()
    private val openedAircraft = mutableListOf<HourAircraft>()
    private var retries = 0

    /** Seven UTC days up to [NOW], each hour a slot holding that UTC hour, later hours today not yet there. */
    private val grid = WatchActivityGrid(
        days = (6 downTo 0).map { back ->
            val date = NOW.atZone(ZoneOffset.UTC).toLocalDate().minusDays(back.toLong())
            WatchActivityGrid.Day(
                date = date,
                slots = List(24) { hour ->
                    if (back == 0 && hour > 12) {
                        WatchActivityGrid.Slot(WatchActivityGrid.State.NOT_YET)
                    } else {
                        WatchActivityGrid.Slot(
                            WatchActivityGrid.State.INACTIVE,
                            hours = listOf(date.atTime(hour, 0).toInstant(ZoneOffset.UTC)),
                        )
                    }
                },
            )
        },
    )

    private fun hourState(
        selected: List<Instant> = emptyList(),
        aircraft: HourAircraftState? = null,
        available: Boolean = true,
    ) = detailsState(baseStatus).copy(
        activityState = ChartState.Ready(grid),
        hourSelection = if (available) {
            WatchDetailsViewModel.HourSelection(
                range = WatchDetailsViewModel.HourSelection.rangeAt(NOW),
                selected = selected,
                aircraft = aircraft,
            )
        } else {
            null
        },
    )

    private fun picked(aircraft: HourAircraftState) = hourState(selected = listOf(PICKED), aircraft = aircraft)

    private fun row(date: LocalDate) = composeRule.onNodeWithContentDescription(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(date),
        substring = true,
    )

    private fun clickHour(date: LocalDate, hour: Int) {
        row(date).performScrollTo().performTouchInput {
            val label = 40.dp.toPx()
            val cell = (width - label) / 24f
            click(Offset(label + cell * (hour + 0.5f), height / 2f))
        }
    }

    private fun seen(callsign: String, minute: Long) = HourAircraft(
        hex = "3C65A3",
        lastSeenAt = PICKED.plusSeconds(minute * 60),
        callsign = callsign,
        registration = "D-AIBC",
    )

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

    private fun setScreen(
        progress: () -> Float = { 1f },
        state: () -> WatchDetailsViewModel.State,
    ) = composeRule.setContent {
        PreviewWrapper {
            CompositionLocalProvider(
                LocalUnitFormatter provides UnitFormatter(AircraftUnits.METRIC, DistanceUnit.KILOMETERS, Locale.US)
            ) {
                WatchDetailsContent(
                    state = state(),
                    progress = progress,
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
                    onHourSlotClick = { pickedSlots.add(it) },
                    onRetryHour = { retries++ },
                    onHourAircraftClick = { openedAircraft.add(it) },
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

    private fun titleMaxLines(): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("London Heathrow", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.maxLines
    }

    @Test
    fun `the title keeps to one line while the sheet is partially expanded`() {
        setScreen(progress = { 0f }) { detailsState(baseStatus) }

        titleMaxLines() shouldBe 1
    }

    @Test
    fun `the title may take two lines once the sheet is fully expanded`() {
        setScreen(progress = { 1f }) { detailsState(baseStatus) }

        titleMaxLines() shouldBe 2
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

    @Test
    fun `a check refused by the daily limit says so and keeps the earlier matches`() {
        val state = detailsState(baseStatus).copy(
            matches = WatchDetailsViewModel.Matches(
                aircraft = listOf(WatchDetailsViewModel.Match(FakeAircraft(callsign = "DLH123"), distanceInMeter = null)),
                checkAt = Instant.parse("2026-11-09T11:50:00Z"),
                total = 1,
                fromWatchCenter = true,
                sortedByDistance = false,
                checkLimitReached = true,
            ),
        )
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_matches_check_limit_reached)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_matches_check_failed)).assertDoesNotExist()
        composeRule.onNodeWithText("DLH123").assertExists()
    }

    private fun matchesState(
        aircraft: List<WatchDetailsViewModel.Match> = emptyList(),
        checkAt: Instant? = CHECK_AT,
        total: Int? = aircraft.size,
        fromWatchCenter: Boolean = false,
        sortedByDistance: Boolean = false,
        checkFailed: Boolean = false,
    ) = detailsState(baseStatus).copy(
        matches = WatchDetailsViewModel.Matches(
            aircraft = aircraft,
            checkAt = checkAt,
            total = total,
            fromWatchCenter = fromWatchCenter,
            sortedByDistance = sortedByDistance,
            checkFailed = checkFailed,
        ),
    )

    private fun relativeText(at: Instant) = DateUtils.getRelativeTimeSpanString(
        at.toEpochMilli(),
        NOW.toEpochMilli(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

    private val fromCheckText get() = context.getString(R.string.watch_details_matches_from_check_x, relativeText(CHECK_AT))

    @Test
    fun `no matches at a check say so in one line`() {
        val state = matchesState(total = 0)
        setScreen { state }

        composeRule.onNodeWithText(
            context.getString(R.string.watch_details_matches_none_at_check_x, relativeText(CHECK_AT))
        ).assertExists()
        composeRule.onNodeWithText(fromCheckText).assertDoesNotExist()
    }

    @Test
    fun `no matches in recent data say so without a source caption`() {
        val state = matchesState(checkAt = null, total = null)
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_matches_none_recent)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_matches_from_cache)).assertDoesNotExist()
    }

    @Test
    fun `counted matches without details keep their source caption`() {
        val state = matchesState(total = 3)
        setScreen { state }

        composeRule.onNodeWithText(fromCheckText).assertExists()
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.watch_details_matches_no_details_x, 3, 3)
        ).assertExists()
    }

    @Test
    fun `matches sorted by distance name source and order in one caption`() {
        val state = matchesState(
            aircraft = listOf(WatchDetailsViewModel.Match(FakeAircraft(callsign = "DLH123"), distanceInMeter = null)),
            sortedByDistance = true,
        )
        setScreen { state }

        composeRule.onNodeWithText(
            context.getString(
                R.string.watch_details_matches_caption_x_y,
                fromCheckText,
                context.getString(R.string.watch_details_matches_sorted_you_inline),
            )
        ).assertExists()
        composeRule.onNodeWithText(fromCheckText).assertDoesNotExist()
    }

    @Test
    fun `a failed check's matches sorted by distance name both in one caption`() {
        val state = matchesState(
            aircraft = listOf(WatchDetailsViewModel.Match(FakeAircraft(callsign = "DLH123"), distanceInMeter = null)),
            fromWatchCenter = true,
            sortedByDistance = true,
            checkFailed = true,
        )
        setScreen { state }

        composeRule.onNodeWithText(
            context.getString(
                R.string.watch_details_matches_caption_x_y,
                context.getString(R.string.watch_details_matches_check_failed),
                context.getString(R.string.watch_details_matches_sorted_center_inline),
            )
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_matches_check_failed)).assertDoesNotExist()
    }

    @Test
    fun `a tap in a day row picks the hour under it`() {
        val state = hourState()
        setScreen { state }

        clickHour(grid.days[5].date, 7)

        pickedSlots shouldBe listOf(grid.days[5].slots[7])
    }

    @Test
    fun `a tap on an hour not there yet picks nothing`() {
        val state = hourState()
        setScreen { state }

        clickHour(grid.days[6].date, 20)

        pickedSlots shouldBe emptyList()
    }

    @Test
    fun `a screen reader's click on a row picks the day's newest hour`() {
        val state = hourState()
        setScreen { state }

        row(grid.days[6].date).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        pickedSlots shouldBe listOf(grid.days[6].slots[12])
    }

    @Test
    fun `without hour lists a row can't be clicked`() {
        val state = hourState(available = false)
        setScreen { state }

        row(grid.days[5].date).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        clickHour(grid.days[5].date, 7)

        pickedSlots shouldBe emptyList()
    }

    @Test
    fun `the hour list shows its hour, what it is and its aircraft`() {
        val aircraft = seen("DLH4AB", minute = 10)
        val state = picked(HourAircraftState.Complete(listOf(aircraft), provisional = false, coverage = HourCoverage.FULL))
        setScreen { state }

        composeRule.onNodeWithText(hourRangeText(context, PICKED, PICKED, ZoneId.systemDefault())).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_note)).assertExists()
        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.watch_details_hour_count_x, 1, 1))
            .assertExists()
        composeRule.onNodeWithText("DLH4AB").performScrollTo().performClick()

        openedAircraft shouldBe listOf(aircraft)
    }

    @Test
    fun `a loading hour shows how many were found so far`() {
        val state = picked(
            HourAircraftState.Loading(
                listOf(seen("DLH4AB", 10), seen("BAW12", 20), seen("RYR3", 30)),
                provisional = false,
                coverage = HourCoverage.FULL,
            )
        )
        setScreen { state }

        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.watch_details_hour_loading_x, 3, 3))
            .assertExists()
        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.watch_details_hour_count_x, 3, 3))
            .assertDoesNotExist()
        composeRule.onNodeWithText("BAW12").assertExists()
    }

    @Test
    fun `more than 50 aircraft show 50 more at a time`() {
        val aircraft = List(60) { index -> seen("AC%02d".format(index), minute = index / 2L) }
        val state = picked(HourAircraftState.Complete(aircraft, provisional = false, coverage = HourCoverage.FULL))
        setScreen { state }

        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.watch_details_hour_count_x, 60, 60))
            .assertExists()
        composeRule.onNodeWithText("AC49").assertExists()
        composeRule.onNodeWithText("AC50").assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_show_more_action))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("AC59").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_show_more_action)).assertDoesNotExist()
    }

    @Test
    fun `a provisional hour with gaps says both`() {
        val state = picked(
            HourAircraftState.Complete(listOf(seen("DLH4AB", 10)), provisional = true, coverage = HourCoverage.PARTIAL)
        )
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_provisional)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_coverage_incomplete)).assertExists()
    }

    @Test
    fun `an empty fully covered hour says no aircraft passed`() {
        val state = picked(HourAircraftState.Complete(emptyList(), provisional = false, coverage = HourCoverage.FULL))
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_empty)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_empty_incomplete)).assertDoesNotExist()
    }

    @Test
    fun `an empty hour with gaps says its history is incomplete`() {
        val state = picked(HourAircraftState.Complete(emptyList(), provisional = false, coverage = HourCoverage.NONE))
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_empty_incomplete)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_empty)).assertDoesNotExist()
    }

    @Test
    fun `a changed watch says so and can be retried`() {
        val state = picked(HourAircraftState.Changed)
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_changed)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_retry_action))
            .performScrollTo()
            .performClick()

        retries shouldBe 1
    }

    @Test
    fun `an hour outside the kept history says so`() {
        val state = picked(HourAircraftState.NotAvailable(HourAircraftState.NotAvailable.Reason.OUT_OF_RANGE))
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_out_of_range)).assertExists()
    }

    @Test
    fun `a failed hour says so and can be retried`() {
        val state = picked(HourAircraftState.Failed(IOException("offline")))
        setScreen { state }

        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_failed)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.watch_details_hour_retry_action))
            .performScrollTo()
            .performClick()

        retries shouldBe 1
    }

    companion object {
        private val NOW = Instant.parse("2026-11-09T12:00:00Z")
        private val PICKED = Instant.parse("2026-11-09T09:00:00Z")
        private val CHECK_AT = Instant.parse("2026-11-09T11:50:00Z")
    }
}
