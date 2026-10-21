package eu.darken.apl.main.ui

import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnitSetting
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.main.core.GeneralSettings
import eu.darken.apl.main.core.update.UpdateChecker
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
        sponsorHelper = mockk(relaxed = true),
        watchRepo = mockk(relaxed = true),
        updateChecker = updateChecker,
        generalSettings = generalSettings,
        feederSettings = feederSettings,
        appCompatibility = compatibility,
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
}
