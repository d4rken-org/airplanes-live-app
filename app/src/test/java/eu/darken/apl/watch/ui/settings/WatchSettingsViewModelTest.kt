package eu.darken.apl.watch.ui.settings

import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.watch.core.alerts.WatchWorkerHelper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Duration

class WatchSettingsViewModelTest : BaseTest() {

    private val settings = mockk<WatchSettings>()
    private val watchWorkerHelper = mockk<WatchWorkerHelper>(relaxed = true)
    private val intervalPref = mockk<DataStoreValue<Duration>>()

    // Sits below the free floor, so the effective interval differs from the stored one
    private val storedInterval = WatchSettings.PRO_MIN_CHECK_INTERVAL

    private class TestRepo(private val infos: Flow<UpgradeRepo.Info>) : UpgradeRepo {
        override val upgradeInfo = infos
        override suspend fun refresh() = Unit
    }

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
        override val source: UpgradeRepo.Source? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.watchMonitorInterval } returns intervalPref
        every { intervalPref.flow } returns flowOf(storedInterval)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(isPro: Boolean) = WatchSettingsViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        settings = settings,
        watchWorkerHelper = watchWorkerHelper,
        upgradeRepo = TestRepo(flowOf(Info(isPro = isPro, isSettled = true))),
    )

    @Test
    fun `a free installation can not pick below the free floor`() = runTest {
        createViewModel(isPro = false).state.first().floorMinutes shouldBe
                WatchSettings.FREE_MIN_CHECK_INTERVAL.toMinutes().toFloat()
    }

    @Test
    fun `an upgraded installation can pick down to the pro floor`() = runTest {
        createViewModel(isPro = true).state.first().floorMinutes shouldBe
                WatchSettings.PRO_MIN_CHECK_INTERVAL.toMinutes().toFloat()
    }

    @Test
    fun `the picker shows the effective interval, not the stored one`() = runTest {
        createViewModel(isPro = false).state.first().currentIntervalMinutes shouldBe
                WatchSettings.FREE_MIN_CHECK_INTERVAL.toMinutes().toFloat()

        createViewModel(isPro = true).state.first().currentIntervalMinutes shouldBe
                WatchSettings.PRO_MIN_CHECK_INTERVAL.toMinutes().toFloat()
    }
}
