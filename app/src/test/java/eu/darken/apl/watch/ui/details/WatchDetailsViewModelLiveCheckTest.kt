package eu.darken.apl.watch.ui.details

import androidx.lifecycle.viewModelScope
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.main.core.query.WatchOutcome
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.watch.core.WatchLiveCheck
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.LiveNow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.time.Instant

// Location watches and aircraft carry android.location.Location
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WatchDetailsViewModelLiveCheckTest {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val liveCheck = mockk<WatchLiveCheck>()
    private val statuses = MutableStateFlow<List<Watch.Status>>(emptyList())

    @Before
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { watchRepo.status } returns statuses
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val viewModels = mutableListOf<WatchDetailsViewModel>()

    /** The ViewModel's clock ticks forever on the test's scheduler, it ends with the test. */
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
        historyRepo = mockk(relaxed = true),
        webpageTool = mockk(relaxed = true),
        serverClock = mockk<ServerClock> { every { now() } returns NOW },
        liveCheck = liveCheck,
        accessRepo = mockk<AccessRepo> { every { state } returns MutableStateFlow(null) },
        serverMode = mockk { every { state } returns flowOf(WatchServerMode.State(access = null, isActive = true)) },
    ).apply {
        init(WATCH_ID)
        viewModels.add(this)
    }

    @Test
    fun `a held area with aircraft lists what a check finds now`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        coEvery { liveCheck.check(any()) } returns WatchOutcome.Matched(
            aircraft = listOf(FakeAircraft(hex = "3C65A3"), FakeAircraft(hex = "3C6752")),
            totalMatching = 7,
            capped = true,
            expiresAt = NOW.plusSeconds(60),
        )

        val state = createViewModel().state.first { it.matches?.checking == false }

        state.live shouldBe LiveNow(count = 7)
        val matches = state.matches!!
        matches.aircraft.map { it.aircraft.hex }.toSet() shouldBe setOf("3C65A3", "3C6752")
        matches.total shouldBe 7
        matches.checkAt shouldBe NOW
        matches.checkFailed shouldBe false
    }

    @Test
    fun `a failed check keeps the server's presence and says so`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        coEvery { liveCheck.check(any()) } throws IOException("offline")

        val state = createViewModel().state.first { it.matches?.checking == false }

        state.live shouldBe LiveNow(count = null)
        state.matches!!.checkFailed shouldBe true
    }

    @Test
    fun `a check that finds nothing ends the active state`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        coEvery { liveCheck.check(any()) } returns WatchOutcome.Absent(expiresAt = NOW.plusSeconds(60))

        val state = createViewModel().state.first { it.matches?.checking == false }

        state.live shouldBe null
        state.matches!!.total shouldBe 0
    }

    @Test
    fun `an area the server finds empty is not checked`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.ABSENT))

        val state = createViewModel().state.first { it.matches?.checking == false }

        state.live shouldBe null
        coVerify(exactly = 0) { liveCheck.check(any()) }
    }

    @Test
    fun `the same definition is checked once, an edited one again`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        coEvery { liveCheck.check(any()) } returns WatchOutcome.Absent(expiresAt = NOW.plusSeconds(60))
        val vm = createViewModel()
        vm.state.first { it.matches?.checking == false }

        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT, lastCheckAt = NOW))
        vm.state.first { it.matches?.checking == false }
        coVerify(exactly = 1) { liveCheck.check(any()) }

        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT, revision = 2))
        vm.state.first { it.matches?.checking == false }
        coVerify(exactly = 2) { liveCheck.check(any()) }
    }

    @Test
    fun `a capped answer without a total shows its aircraft but no count`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        coEvery { liveCheck.check(any()) } returns WatchOutcome.Matched(
            aircraft = listOf(FakeAircraft(hex = "3C65A3")),
            totalMatching = null,
            capped = true,
            expiresAt = NOW.plusSeconds(60),
        )

        val state = createViewModel().state.first { it.matches?.checking == false }

        state.live shouldBe LiveNow(count = null)
        val matches = state.matches!!
        matches.aircraft.size shouldBe 1
        matches.total shouldBe null
    }

    @Test
    fun `an expired answer no longer decides what is there now`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        coEvery { liveCheck.check(any()) } returns WatchOutcome.Absent(expiresAt = NOW.minusSeconds(1))

        val state = createViewModel().state.first { it.matches?.checking == false }

        state.live shouldBe LiveNow(count = null)
        state.matches!!.checkAt shouldBe NOW
    }

    @Test
    fun `an answer for the definition before an edit is not shown`() = vmTest {
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        val first = CompletableDeferred<WatchOutcome>()
        coEvery { liveCheck.check(match { it.definitionRevision == 1L }) } coAnswers { first.await() }
        coEvery { liveCheck.check(match { it.definitionRevision == 2L }) } returns
            WatchOutcome.Absent(expiresAt = NOW.plusSeconds(60))
        val vm = createViewModel()
        vm.state.first { it.matches?.checking == true }

        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT, revision = 2))
        first.complete(
            WatchOutcome.Matched(listOf(FakeAircraft(hex = "3C65A3")), totalMatching = 1, capped = false, expiresAt = NOW.plusSeconds(60))
        )

        val state = vm.state.first { it.matches?.checking == false }
        state.matches!!.aircraft shouldBe emptyList()
        state.live shouldBe null
    }

    @Test
    fun `a screen subscribing again after its state stopped does not check again`() = vmTest {
        // The state stops 5s after its last subscriber, as for a sheet left in the background
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        statuses.value = listOf(status(presence = WatchServerStatus.Presence.PRESENT))
        coEvery { liveCheck.check(any()) } returns WatchOutcome.Absent(expiresAt = NOW.plusSeconds(60))
        val vm = createViewModel()

        vm.state.first { it.matches?.checking == false }
        advanceTimeBy(10_000)
        vm.state.first { it.matches?.checking == false }

        coVerify(exactly = 1) { liveCheck.check(any()) }
    }

    private fun status(
        presence: WatchServerStatus.Presence,
        revision: Long = 1,
        lastCheckAt: Instant? = null,
    ) = LocationWatch.Status(
        watch = LocationWatch(
            BaseWatchEntity(
                id = WATCH_ID,
                watchType = LocationWatchEntity.TYPE_KEY,
                latitude = 52.5,
                longitude = 13.4,
                radius = 25_000f,
                definitionRevision = revision,
                lastCheckAt = lastCheckAt,
            ),
            LocationWatchEntity(id = WATCH_ID, label = "Berlin"),
        ),
        lastCheck = null,
        lastHit = null,
        server = WatchServerStatus(
            isHeld = true,
            status = WatchServerStatus.Status.ACTIVE,
            pauseReason = null,
            rejection = null,
            presence = presence,
            lastMatch = null,
        ),
    )

    companion object {
        private const val WATCH_ID = "location-1"
        private val NOW = Instant.ofEpochSecond(1_700_000_000)
    }
}
