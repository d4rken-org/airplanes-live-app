package eu.darken.apl.upgrade.core

import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.debug.autoreport.DebugSettings
import eu.darken.apl.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class DebugUpgradeRepoTest : BaseTest() {

    private data class Info(
        override val isPro: Boolean,
        override val isSettled: Boolean,
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY,
        override val source: UpgradeRepo.Source? = UpgradeRepo.Source.FEEDER,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info

    private val delegateInfo = Info(isPro = false, isSettled = false)
    private val delegate = mockk<UpgradeRepo>(relaxed = true).apply {
        every { upgradeInfo } returns flowOf(delegateInfo)
    }

    private val fakeUpgradeFlag = MutableStateFlow(false)
    private val fakeUpgradeValue = mockk<DataStoreValue<Boolean>>().apply {
        every { flow } returns fakeUpgradeFlag
    }
    private val debugSettings = mockk<DebugSettings>().apply {
        every { isFakeUpgradeEnabled } returns fakeUpgradeValue
    }

    private fun createDev() = delegate.withDebugOverride(debugSettings, BuildConfigWrap.BuildType.DEV)

    @Test
    fun `beta builds keep the real repo`() {
        delegate.withDebugOverride(debugSettings, BuildConfigWrap.BuildType.BETA) shouldBeSameInstanceAs delegate
    }

    @Test
    fun `release builds keep the real repo`() {
        delegate.withDebugOverride(debugSettings, BuildConfigWrap.BuildType.RELEASE) shouldBeSameInstanceAs delegate
    }

    @Test
    fun `debug builds wrap the real repo`() {
        createDev().shouldBeInstanceOf<DebugUpgradeRepo>()
    }

    @Test
    fun `flag off passes the delegate info through unchanged`() = runTest {
        createDev().upgradeInfo.first() shouldBeSameInstanceAs delegateInfo
    }

    @Test
    fun `flag on reports a settled upgrade of the delegate type`() = runTest {
        fakeUpgradeFlag.value = true

        val info = createDev().upgradeInfo.first()
        info.isPro shouldBe true
        info.isSettled shouldBe true
        info.type shouldBe UpgradeRepo.Type.GPLAY
        info.source shouldBe null
        info.error shouldBe null
    }

    @Test
    fun `toggling the flag switches between real and fake info`() = runTest {
        val collected = mutableListOf<UpgradeRepo.Info>()
        val job = launch { createDev().upgradeInfo.toList(collected) }
        runCurrent()

        fakeUpgradeFlag.value = true
        runCurrent()
        fakeUpgradeFlag.value = false
        runCurrent()
        job.cancel()

        collected.map { it.isSettled to it.isPro } shouldBe listOf(false to false, true to true, false to false)
        collected.last() shouldBeSameInstanceAs delegateInfo
    }

    @Test
    fun `refresh reaches the delegate`() = runTest {
        createDev().refresh()

        coVerify(exactly = 1) { delegate.refresh() }
    }
}
