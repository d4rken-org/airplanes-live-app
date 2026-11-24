package eu.darken.apl.watch.ui.details

import androidx.lifecycle.viewModelScope
import eu.darken.apl.common.chart.ChartState
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.history.HourAircraft
import eu.darken.apl.watch.core.history.HourAircraftState
import eu.darken.apl.watch.core.history.HourCoverage
import eu.darken.apl.watch.core.history.WatchActivityGrid
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.history.WatchHourAircraftRepo
import eu.darken.apl.watch.core.history.WatchServerIdentity
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.LocationWatch
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Location watches carry android.location.Location
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchDetailsViewModelHourAircraftTest {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val historyRepo = mockk<WatchHistoryRepo>(relaxed = true)
    private val hourRepo = mockk<WatchHourAircraftRepo>()
    private val identities = MutableStateFlow<WatchServerIdentity?>(IDENTITY)
    private val access = MutableStateFlow<AccessState?>(null)

    @Before
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.status } returns MutableStateFlow(listOf(status()))
        every { historyRepo.serverIdentity(WATCH_ID) } returns identities
        access.value = accessState(offersHours = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val viewModels = mutableListOf<WatchDetailsViewModel>()

    /** The ViewModel's clocks tick forever, they end with the test. */
    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun createViewModel() = WatchDetailsViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        watchRepo = watchRepo,
        searchRepo = mockk(relaxed = true),
        aircraftRepo = mockk(relaxed = true),
        locationManager2 = mockk { every { state } returns MutableStateFlow<LocationManager2.State>(LocationManager2.State.Waiting) },
        flightRepo = mockk(relaxed = true),
        historyRepo = historyRepo,
        webpageTool = mockk(relaxed = true),
        serverClock = mockk<ServerClock> { every { now() } returns NOW },
        liveCheck = mockk(relaxed = true),
        hourAircraftRepo = hourRepo,
        accessRepo = mockk<AccessRepo> { every { state } returns access },
        serverMode = mockk { every { state } returns flowOf(WatchServerMode.State(access = null, isActive = true)) },
    ).apply {
        init(WATCH_ID)
        viewModels.add(this)
    }

    private suspend fun WatchDetailsViewModel.selection(): WatchDetailsViewModel.HourSelection? =
        state.first().hourSelection

    private suspend fun WatchDetailsViewModel.awaitPicked(vararg hours: Instant): WatchDetailsViewModel.HourSelection =
        state.first { it.hourSelection?.selected == hours.toList() }.hourSelection!!

    private suspend fun WatchDetailsViewModel.finished(): WatchDetailsViewModel.HourSelection = state
        .first { state ->
            val aircraft = state.hourSelection?.aircraft
            aircraft != null && aircraft !is HourAircraftState.Loading
        }
        .hourSelection!!

    @Test
    fun `picking an hour lists its aircraft for the current identity`() = vmTest {
        every { hourRepo.load(IDENTITY, HOUR) } returns flowOf(complete(aircraft("3C65A3", HOUR.plusSeconds(60))))
        val vm = createViewModel()

        vm.selectHourSlot(slot(HOUR))

        val selection = vm.finished()
        selection.selected shouldBe listOf(HOUR)
        selection.aircraft.shouldBeInstanceOf<HourAircraftState.Complete>().aircraft.map { it.hex } shouldBe listOf("3C65A3")
        verify(exactly = 1) { hourRepo.load(IDENTITY, HOUR) }
    }

    @Test
    fun `picking the picked slot again lets go of it`() = vmTest {
        every { hourRepo.load(IDENTITY, HOUR) } returns flowOf(complete())
        val vm = createViewModel()
        vm.selectHourSlot(slot(HOUR))
        vm.finished()

        vm.selectHourSlot(slot(HOUR))

        vm.awaitPicked().aircraft shouldBe null
    }

    @Test
    fun `a slot later today or outside the server's range is not picked`() = vmTest {
        val vm = createViewModel()

        vm.selectHourSlot(slot(HOUR, state = WatchActivityGrid.State.NOT_YET))
        vm.selectHourSlot(slot(OLDEST.minus(HOUR_STEP)))
        vm.selectHourSlot(slot(CURRENT.plus(HOUR_STEP)))

        vm.selection()!!.selected shouldBe emptyList()
        verify(exactly = 0) { hourRepo.load(any(), any()) }
    }

    @Test
    fun `the oldest and the current hour can be picked`() = vmTest {
        every { hourRepo.load(any(), any()) } returns flowOf(complete())
        val vm = createViewModel()

        vm.selectHourSlot(slot(OLDEST))
        vm.awaitPicked(OLDEST)

        vm.selectHourSlot(slot(CURRENT))
        vm.awaitPicked(CURRENT)
        verify { hourRepo.load(IDENTITY, OLDEST) }
        verify { hourRepo.load(IDENTITY, CURRENT) }
    }

    @Test
    fun `a new identity loads the same hour again`() = vmTest {
        every { hourRepo.load(IDENTITY, HOUR) } returns flow { awaitCancellation() }
        every { hourRepo.load(IDENTITY_2, HOUR) } returns flowOf(complete(aircraft("3C65A3", HOUR.plusSeconds(60))))
        val vm = createViewModel()
        vm.selectHourSlot(slot(HOUR))
        vm.state.first { it.hourSelection?.aircraft is HourAircraftState.Loading }

        identities.value = IDENTITY_2

        val selection = vm.finished()
        selection.selected shouldBe listOf(HOUR)
        selection.aircraft.shouldBeInstanceOf<HourAircraftState.Complete>()
        verify(exactly = 1) { hourRepo.load(IDENTITY_2, HOUR) }
    }

    @Test
    fun `a changed watch is shown until it is retried`() = vmTest {
        every { hourRepo.load(IDENTITY, HOUR) } returns flowOf(HourAircraftState.Changed)
        val vm = createViewModel()
        vm.selectHourSlot(slot(HOUR))
        vm.finished().aircraft shouldBe HourAircraftState.Changed

        every { hourRepo.load(IDENTITY, HOUR) } returns flowOf(complete())
        vm.retryHourAircraft()

        vm.state.first { it.hourSelection?.aircraft is HourAircraftState.Complete }
        verify(exactly = 2) { hourRepo.load(IDENTITY, HOUR) }
    }

    @Test
    fun `without the server offering it nothing can be picked`() = vmTest {
        access.value = accessState(offersHours = false)
        val vm = createViewModel()

        vm.selectHourSlot(slot(HOUR))

        vm.selection() shouldBe null
        verify(exactly = 0) { hourRepo.load(any(), any()) }
    }

    @Test
    fun `while the server does not hold the definition nothing can be picked`() = vmTest {
        identities.value = null
        val vm = createViewModel()

        vm.selectHourSlot(slot(HOUR))

        vm.selection() shouldBe null
        verify(exactly = 0) { hourRepo.load(any(), any()) }
    }

    @Test
    fun `the pick ends when the server stops offering it`() = vmTest {
        every { hourRepo.load(IDENTITY, HOUR) } returns flowOf(complete())
        val vm = createViewModel()
        vm.selectHourSlot(slot(HOUR))
        vm.finished()

        access.value = accessState(offersHours = false)
        vm.state.first { it.hourSelection == null }
        access.value = accessState(offersHours = true)

        vm.state.first { it.hourSelection != null }.hourSelection!!.selected shouldBe emptyList()
    }

    @Test
    fun `a two-hour slot lists each aircraft once with its latest sighting`() = vmTest {
        val second = HOUR.plus(HOUR_STEP)
        every { hourRepo.load(IDENTITY, HOUR) } returns flowOf(
            complete(aircraft("AAAAAA", HOUR.plusSeconds(600)), aircraft("BBBBBB", HOUR.plusSeconds(3_000)))
        )
        every { hourRepo.load(IDENTITY, second) } returns flowOf(
            HourAircraftState.Loading(listOf(aircraft("AAAAAA", second.plusSeconds(300))), provisional = false, coverage = HourCoverage.FULL),
            complete(
                aircraft("AAAAAA", second.plusSeconds(300)),
                aircraft("CCCCCC", second.plusSeconds(1_200)),
                provisional = true,
                coverage = HourCoverage.PARTIAL,
            ),
        )
        val vm = createViewModel()

        vm.selectHourSlot(slot(HOUR, second))

        val selection = vm.finished()
        selection.selected shouldBe listOf(HOUR, second)
        val result = selection.aircraft.shouldBeInstanceOf<HourAircraftState.Complete>()
        result.aircraft.map { it.hex to it.lastSeenAt } shouldBe listOf(
            "BBBBBB" to HOUR.plusSeconds(3_000),
            "AAAAAA" to second.plusSeconds(300),
            "CCCCCC" to second.plusSeconds(1_200),
        )
        result.provisional shouldBe true
        result.coverage shouldBe HourCoverage.PARTIAL
        verifyOrder {
            hourRepo.load(IDENTITY, HOUR)
            hourRepo.load(IDENTITY, second)
        }
    }

    @Test
    fun `a two-hour slot ends with the failure of either hour`() = vmTest {
        val second = HOUR.plus(HOUR_STEP)
        every { hourRepo.load(IDENTITY, HOUR) } returns flowOf(complete(aircraft("AAAAAA", HOUR.plusSeconds(600))))
        every { hourRepo.load(IDENTITY, second) } returns flowOf(HourAircraftState.Failed(IOException("offline")))
        val vm = createViewModel()

        vm.selectHourSlot(slot(HOUR, second))

        vm.finished().aircraft.shouldBeInstanceOf<HourAircraftState.Failed>()
    }

    @Test
    fun `opening an aircraft follows it on the map at the time it was seen`() = vmTest {
        val vm = createViewModel()
        val seen = aircraft("3C65A3", HOUR.plusSeconds(600))

        vm.showHourAircraft(seen)

        vm.navEvents.first() shouldBe NavEvent.GoTo(
            DestinationMap(
                mapOptions = MapOptions.focus("3C65A3").copy(follow = true, replayAt = seen.lastSeenAt.toEpochMilli())
            )
        )
    }

    @Test
    fun `stepping moves one UTC hour and stops at the ends of the range`() = vmTest {
        every { hourRepo.load(any(), any()) } returns flowOf(complete())
        val vm = createViewModel()

        vm.selectHourSlot(slot(CURRENT))
        vm.awaitPicked(CURRENT).canStepForward shouldBe false
        vm.showNextHour()
        vm.selection()!!.selected shouldBe listOf(CURRENT)
        vm.showPreviousHour()
        vm.awaitPicked(CURRENT.minus(HOUR_STEP)).canStepForward shouldBe true

        vm.clearHourSelection()
        vm.awaitPicked()
        vm.selectHourSlot(slot(OLDEST))
        vm.awaitPicked(OLDEST).canStepBack shouldBe false
        vm.showPreviousHour()
        vm.selection()!!.selected shouldBe listOf(OLDEST)
        vm.showNextHour()
        vm.awaitPicked(OLDEST.plus(HOUR_STEP))
        verify { hourRepo.load(IDENTITY, OLDEST.plus(HOUR_STEP)) }
    }

    @Test
    fun `a two-hour pick steps back from its first hour and forward from its last`() = vmTest {
        every { hourRepo.load(any(), any()) } returns flowOf(complete())
        val second = HOUR.plus(HOUR_STEP)
        val vm = createViewModel()

        vm.selectHourSlot(slot(HOUR, second))
        vm.awaitPicked(HOUR, second)
        vm.showNextHour()
        vm.awaitPicked(second.plus(HOUR_STEP))

        vm.clearHourSelection()
        vm.awaitPicked()
        vm.selectHourSlot(slot(HOUR, second))
        vm.awaitPicked(HOUR, second)
        vm.showPreviousHour()
        vm.awaitPicked(HOUR.minus(HOUR_STEP))
    }

    @Test
    fun `the grid slot holding the picked hour is the selected one`() = vmTest {
        val zone = ZoneId.systemDefault()
        val from = WatchActivityTimeline.hourOf(WatchActivityGrid.startOf(NOW, zone))
        val size = Duration.between(from, NOW).toHours().toInt() + 1
        every { historyRepo.activity(any(), any(), any()) } returns flowOf(
            WatchActivityTimeline(from, List(size) { WatchActivityTimeline.Cell.ACTIVE }, List(size) { 1 })
        )
        every { hourRepo.load(any(), any()) } returns flowOf(complete())
        val vm = createViewModel()
        val activity = vm.state.first { it.activityState is ChartState.Ready }.activityState
        val grid = (activity as ChartState.Ready<WatchActivityGrid>).data
        val slot = grid.days.flatMap { it.slots }.first { HOUR in it.hours }

        vm.selectHourSlot(slot)
        vm.awaitPicked(HOUR).selectedSlot shouldBe slot

        vm.showPreviousHour()
        vm.awaitPicked(HOUR.minus(HOUR_STEP)).selectedSlot!!.hours.contains(HOUR.minus(HOUR_STEP)) shouldBe true
    }

    private fun accessState(offersHours: Boolean) = mockk<AccessState> {
        every { offersWatchHourAircraft } returns offersHours
        every { maxLocationWatchRadiusKm } returns 25
        every { hasAircraftHistory } returns false
        every { offersAircraftHistory } returns false
    }

    private fun slot(vararg hours: Instant, state: WatchActivityGrid.State = WatchActivityGrid.State.ACTIVE) =
        WatchActivityGrid.Slot(state, 1, hours.toList())

    private fun aircraft(hex: String, lastSeenAt: Instant) = HourAircraft(hex = hex, lastSeenAt = lastSeenAt)

    private fun complete(
        vararg aircraft: HourAircraft,
        provisional: Boolean = false,
        coverage: HourCoverage = HourCoverage.FULL,
    ) = HourAircraftState.Complete(aircraft.toList(), provisional, coverage)

    private fun status() = LocationWatch.Status(
        watch = LocationWatch(
            BaseWatchEntity(
                id = WATCH_ID,
                watchType = LocationWatchEntity.TYPE_KEY,
                latitude = 52.5,
                longitude = 13.4,
                radius = 25_000f,
            ),
            LocationWatchEntity(id = WATCH_ID, label = "Berlin"),
        ),
        lastHit = null,
    )

    companion object {
        private const val WATCH_ID = "location-1"
        private val NOW = Instant.parse("2026-11-09T12:20:00Z")
        private val HOUR_STEP = Duration.ofHours(1)
        private val CURRENT = NOW.truncatedTo(ChronoUnit.HOURS)
        private val OLDEST = CURRENT.minus(Duration.ofDays(7))
        private val HOUR = CURRENT.minus(Duration.ofHours(3))
        private val IDENTITY = WatchServerIdentity(WATCH_ID, "server-1", revision = 1, installationId = "install-1")
        private val IDENTITY_2 = IDENTITY.copy(revision = 2)
    }
}
