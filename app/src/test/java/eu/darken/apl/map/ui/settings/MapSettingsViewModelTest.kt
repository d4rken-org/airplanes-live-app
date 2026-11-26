package eu.darken.apl.map.ui.settings

import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class MapSettingsViewModelTest : BaseTest() {

    private val dispatcher = UnconfinedTestDispatcher()

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY,
        override val source: UpgradeRepo.Source? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    private val styleValue = mockk<DataStoreValue<String>> {
        every { flow } returns MutableStateFlow(NativeMapStyle.LIBERTY.key)
    }
    private val restoreLastViewValue = mockk<DataStoreValue<Boolean>> {
        every { flow } returns MutableStateFlow(true)
    }
    private val mapSettings = mockk<MapSettings> {
        every { nativeMapStyle } returns styleValue
        every { isRestoreLastViewEnabled } returns restoreLastViewValue
    }

    private val upgradeInfo = MutableStateFlow<UpgradeRepo.Info>(Info(isPro = true, isSettled = true))
    private val upgradeRepo = mockk<UpgradeRepo> {
        every { upgradeInfo } returns this@MapSettingsViewModelTest.upgradeInfo
    }

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MapSettingsViewModel(
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        mapSettings = mapSettings,
        upgradeRepo = upgradeRepo,
    )

    @Test
    fun `the stored style stays across tier changes and only a settled pro answer counts as upgraded`() = runTest(dispatcher) {
        val vm = createViewModel()
        val states = mutableListOf<MapSettingsViewModel.State>()
        backgroundScope.launch { vm.state.collect { states += it } }

        states.last().nativeMapStyle shouldBe NativeMapStyle.LIBERTY
        states.last().isUpgraded shouldBe true

        upgradeInfo.value = Info(isPro = true, isSettled = false)
        states.last().nativeMapStyle shouldBe NativeMapStyle.LIBERTY
        states.last().isUpgraded shouldBe false

        upgradeInfo.value = Info(isPro = false, isSettled = true)
        states.last().nativeMapStyle shouldBe NativeMapStyle.LIBERTY
        states.last().isUpgraded shouldBe false

        coVerify(exactly = 0) { styleValue.update(any()) }
    }
}
