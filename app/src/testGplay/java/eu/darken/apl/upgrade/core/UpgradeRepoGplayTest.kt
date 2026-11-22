package eu.darken.apl.upgrade.core

import eu.darken.apl.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class UpgradeRepoGplayTest : BaseTest() {

    private val unsettled = FeederUpgradeSource.Info(
        type = UpgradeRepo.Type.GPLAY,
        isPro = false,
        isSettled = false,
        source = null,
    )
    private val subscribed = FeederUpgradeSource.Info(
        type = UpgradeRepo.Type.GPLAY,
        isPro = true,
        isSettled = true,
        source = UpgradeRepo.Source.SUBSCRIPTION,
    )
    private val accessDerived = MutableStateFlow<UpgradeRepo.Info>(unsettled)
    private val feederSource = mockk<FeederUpgradeSource>().apply {
        every { state(UpgradeRepo.Type.GPLAY) } returns accessDerived
    }

    @Test
    fun `upgrade info is exactly what the server's access answer gives`() = runTest {
        val collected = mutableListOf<UpgradeRepo.Info>()
        val job = launch { UpgradeRepoGplay(feederSource).upgradeInfo.toList(collected) }
        runCurrent()

        accessDerived.value = subscribed
        runCurrent()
        job.cancel()

        collected shouldBe listOf(unsettled, subscribed)
    }
}
