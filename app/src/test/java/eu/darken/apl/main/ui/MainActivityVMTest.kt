package eu.darken.apl.main.ui

import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnitSetting
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.update.UpdateChecker
import eu.darken.apl.main.ui.navigation.StatusSlotState
import eu.darken.apl.main.ui.settings.DestinationDeleteData
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.deletion.DeletionState
import eu.darken.apl.server.deletion.InstallationDeletion
import eu.darken.apl.server.status.PlannedMaintenance
import eu.darken.apl.server.status.PlannedMaintenanceRepo
import eu.darken.apl.server.status.ServerStatus
import eu.darken.apl.server.status.ServerStatusTracker
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.db.types.LocationWatchEntity
import eu.darken.apl.watch.core.db.types.SquawkWatchEntity
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant
import java.time.OffsetDateTime

class MainActivityVMTest : BaseTest() {

    private val compatState = MutableStateFlow<CompatState>(CompatState.Unknown)
    private val dismissedUpdate = MutableStateFlow<String?>(null)

    private val release = GithubApi.ReleaseInfo(
        name = "v1.2.0",
        tagName = "v1.2.0",
        htmlUrl = "https://github.com/d4rken-org/airplanes-live-app/releases/tag/v1.2.0",
        publishedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        body = "",
        assets = emptyList(),
    )

    private val updateChecker = mockk<UpdateChecker> {
        coEvery { checkForUpdate() } returns release
    }

    private val generalSettings = mockk<GeneralSettings>(relaxed = true) {
        every { dismissedUpdateVersion } returns mockk { every { flow } returns dismissedUpdate }
        every { themeMode } returns mockk { every { flow } returns flowOf(ThemeMode.SYSTEM) }
        every { themeStyle } returns mockk { every { flow } returns flowOf(ThemeStyle.DEFAULT) }
        every { themeColor } returns mockk { every { flow } returns flowOf(ThemeColor.BLUE) }
        every { themeCustomSeed } returns mockk { every { flow } returns flowOf(null) }
        every { themeCustomPalette } returns mockk { every { flow } returns flowOf(ThemePalette.TONAL_SPOT) }
        every { aircraftUnits } returns mockk { every { flow } returns flowOf(AircraftUnits.AVIATION) }
        every { distanceUnit } returns mockk { every { flow } returns flowOf(DistanceUnitSetting.AUTOMATIC) }
    }

    private val feederSettings = mockk<FeederSettings> {
        every { isNavTabVisible } returns mockk { every { flow } returns flowOf(true) }
    }

    private val compatibility = mockk<AppCompatibility> {
        every { state } returns compatState
    }

    private val installationDeletion = mockk<InstallationDeletion> {
        coEvery { takeLaunchPrompt() } returns null
    }

    private val serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Ok)

    private val serverStatusTracker = mockk<ServerStatusTracker> {
        every { status } returns serverStatus
    }

    private val upcomingMaintenance = MutableStateFlow<PlannedMaintenance?>(null)

    private val plannedMaintenanceRepo = mockk<PlannedMaintenanceRepo> {
        every { upcoming } returns upcomingMaintenance
        coEvery { dismiss(any()) } returns Unit
    }

    private val watchStatus = MutableStateFlow<Collection<Watch.Status>>(emptyList())

    private val watchRepo = mockk<WatchRepo>(relaxed = true) {
        every { status } returns watchStatus
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher, runTest shares its scheduler
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel() = MainActivityVM(
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        watchRepo = watchRepo,
        updateChecker = updateChecker,
        generalSettings = generalSettings,
        feederSettings = feederSettings,
        appCompatibility = compatibility,
        installationDeletion = installationDeletion,
        serverStatusTracker = serverStatusTracker,
        plannedMaintenanceRepo = plannedMaintenanceRepo,
    ).also { vm ->
        backgroundScope.launch { vm.updateRelease.collect {} }
        runCurrent()
    }

    @Test
    fun `the release dialog waits for the server's answer`() = runTest {
        val vm = createViewModel()

        vm.updateRelease.value.shouldBeNull()

        compatState.value = CompatState.Ok
        runCurrent()

        vm.updateRelease.value shouldBe release
    }

    @Test
    fun `no release dialog while the server asks for an update`() = runTest {
        compatState.value = CompatState.UpdateRequired
        val vm = createViewModel()

        vm.updateRelease.value.shouldBeNull()

        compatState.value = CompatState.UpdateRecommended(recommendedVersion = 10200)
        runCurrent()

        vm.updateRelease.value.shouldBeNull()

        compatState.value = CompatState.Ok
        runCurrent()

        vm.updateRelease.value shouldBe release
    }

    @Test
    fun `a snoozed release stays hidden`() = runTest {
        compatState.value = CompatState.Ok
        dismissedUpdate.value = release.tagName
        val vm = createViewModel()

        vm.updateRelease.value.shouldBeNull()
    }

    @Test
    fun `an unfinished deletion opens the deletion screen once the tabs show`() = runTest {
        coEvery { installationDeletion.takeLaunchPrompt() } returns DeletionState.REQUEST_PENDING
        val vm = createViewModel()
        val events = mutableListOf<NavEvent>()
        backgroundScope.launch { vm.navEvents.toList(events) }

        vm.onTabsShown()
        runCurrent()

        events shouldBe listOf(NavEvent.GoTo(DestinationDeleteData))
    }

    @Test
    fun `no launch prompt leaves the navigation alone`() = runTest {
        val vm = createViewModel()
        val events = mutableListOf<NavEvent>()
        backgroundScope.launch { vm.navEvents.toList(events) }

        vm.onTabsShown()
        runCurrent()

        events shouldBe emptyList()
        coVerify(exactly = 1) { installationDeletion.takeLaunchPrompt() }
    }

    @Test
    fun `the status slot follows the server status and the announced maintenance`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch { vm.statusSlot.collect {} }
        runCurrent()

        vm.statusSlot.value shouldBe StatusSlotState.Hidden

        upcomingMaintenance.value = notice
        runCurrent()
        vm.statusSlot.value shouldBe StatusSlotState.Planned(notice)

        serverStatus.value = ServerStatus.Unreachable
        runCurrent()
        vm.statusSlot.value shouldBe StatusSlotState.Unreachable
    }

    @Test
    fun `dismissing the planned maintenance forwards its id`() = runTest {
        val vm = createViewModel()

        vm.dismissPlannedMaintenance(notice.id)
        runCurrent()

        coVerify(exactly = 1) { plannedMaintenanceRepo.dismiss(notice.id) }
    }

    private fun locationStatus(
        tracked: Set<Aircraft> = emptySet(),
        server: WatchServerStatus? = null,
    ) = LocationWatch.Status(
        watch = LocationWatch(
            BaseWatchEntity(
                id = LOCATION_ID,
                watchType = LocationWatchEntity.TYPE_KEY,
                latitude = 52.5,
                longitude = 13.4,
                radius = 25_000f,
            ),
            LocationWatchEntity(id = LOCATION_ID, label = "Berlin"),
        ),
        lastHit = null,
        tracked = tracked,
        server = server,
    )

    private fun squawkStatus(server: WatchServerStatus? = null) = SquawkWatch.Status(
        watch = SquawkWatch(
            BaseWatchEntity(id = SQUAWK_ID, watchType = SquawkWatchEntity.TYPE_KEY),
            SquawkWatchEntity(id = SQUAWK_ID, code = "7700"),
        ),
        lastHit = null,
        server = server,
    )

    private fun held() = WatchServerStatus(
        isHeld = true,
        status = WatchServerStatus.Status.ACTIVE,
        pauseReason = null,
        rejection = null,
        presence = null,
        lastMatch = null,
    )

    private fun TestScope.alertEvents(
        vm: MainActivityVM,
        watchId: String,
        hex: String?,
        observedAt: Long?,
    ): List<NavEvent> {
        val events = mutableListOf<NavEvent>()
        backgroundScope.launch { vm.navEvents.toList(events) }
        vm.showWatchAlert(watchId, hex, observedAt)
        runCurrent()
        return events
    }

    @Test
    fun `a location alert with an aircraft opens it at the time it was seen`() = runTest {
        watchStatus.value = listOf(locationStatus(server = held()))
        val vm = createViewModel()

        alertEvents(vm, LOCATION_ID, hex = "3C65A3", observedAt = 1_700_000_000_000L) shouldBe listOf(
            NavEvent.GoTo(
                DestinationMap(
                    mapOptions = MapOptions.focus("3C65A3").copy(follow = true, replayAt = 1_700_000_000_000L)
                )
            )
        )
    }

    @Test
    fun `a location alert without an aircraft keeps the tracked aircraft routing`() = runTest {
        val tracked = mockk<Aircraft> { every { hex } returns "4B1814" }
        watchStatus.value = listOf(locationStatus(tracked = setOf(tracked), server = held()))
        val vm = createViewModel()

        alertEvents(vm, LOCATION_ID, hex = null, observedAt = null) shouldBe listOf(
            NavEvent.GoTo(DestinationMap(mapOptions = MapOptions.focus(listOf("4B1814"))))
        )
    }

    @Test
    fun `a location alert without an aircraft opens the details of a held watch`() = runTest {
        watchStatus.value = listOf(locationStatus(server = held()))
        val vm = createViewModel()

        alertEvents(vm, LOCATION_ID, hex = null, observedAt = null) shouldBe listOf(
            NavEvent.GoTo(DestinationWatchDetails(LOCATION_ID))
        )
    }

    @Test
    fun `other alerts ignore an aircraft hex`() = runTest {
        watchStatus.value = listOf(squawkStatus(server = held()))
        val vm = createViewModel()

        alertEvents(vm, SQUAWK_ID, hex = "3C65A3", observedAt = 1_700_000_000_000L) shouldBe listOf(
            NavEvent.GoTo(DestinationWatchDetails(SQUAWK_ID))
        )
    }

    companion object {
        private val notice = PlannedMaintenance(
            id = "notice-1",
            startsAt = Instant.parse("2026-12-01T02:00:00Z"),
            endsAt = Instant.parse("2026-12-01T04:00:00Z"),
        )
        private const val LOCATION_ID = "location-watch"
        private const val SQUAWK_ID = "squawk-watch"
    }
}
