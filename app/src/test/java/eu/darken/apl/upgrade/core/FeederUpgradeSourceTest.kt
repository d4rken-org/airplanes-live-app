package eu.darken.apl.upgrade.core

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class FeederUpgradeSourceTest : BaseTest() {

    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo>().apply {
        every { state } returns accessState
    }

    private fun create() = FeederUpgradeSource(accessRepo)

    @Test
    fun `an unknown access state is unsettled and not pro`() = runTest {
        val info = create().state(UpgradeRepo.Type.FOSS).first()
        info.type shouldBe UpgradeRepo.Type.FOSS
        info.isPro shouldBe false
        info.isSettled shouldBe false
        info.source shouldBe null
    }

    @Test
    fun `the feeder tier is pro`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FEEDER)
        val info = create().state(UpgradeRepo.Type.GPLAY).first()
        info.type shouldBe UpgradeRepo.Type.GPLAY
        info.isPro shouldBe true
        info.isSettled shouldBe true
        info.source shouldBe UpgradeRepo.Source.FEEDER
    }

    @Test
    fun `the free tier is settled and not pro`() = runTest {
        accessState.value = accessStateOf(AccessState.Tier.FREE)
        val info = create().state(UpgradeRepo.Type.FOSS).first()
        info.isPro shouldBe false
        info.isSettled shouldBe true
        info.source shouldBe null
    }

    @Test
    fun `settledness and pro travel on the same emission`() = runTest {
        val collected = mutableListOf<UpgradeRepo.Info>()
        val job = launch { create().state(UpgradeRepo.Type.FOSS).toList(collected) }
        runCurrent()

        accessState.value = accessStateOf(AccessState.Tier.FEEDER)
        runCurrent()
        job.cancel()

        collected.map { it.isSettled to it.isPro } shouldBe listOf(false to false, true to true)
    }

    private fun accessStateOf(tier: AccessState.Tier) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = tier,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5, viewingBurst = 3, viewingPerDay = 25000, searchesPerDay = 25,
            watchesPerDay = 1000, maxRadiusNm = 25, watchTypes = listOf("hex", "callsign"),
        ),
        usage = Usage(
            resetsAt = 3_600_000,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 0.2, burst = 3),
            search = RequestRate(perSecond = 1.0, burst = 3),
            watch = RequestRate(perSecond = 1.0, burst = 3),
            concurrency = 3,
        ),
    )
}
