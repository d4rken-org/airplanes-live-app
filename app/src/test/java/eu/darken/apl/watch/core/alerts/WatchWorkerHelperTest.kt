package eu.darken.apl.watch.core.alerts

import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.isProNow
import eu.darken.apl.watch.core.WatchSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration

class WatchWorkerHelperTest : BaseTest() {

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

    @Test
    fun `a free installation checks at most hourly`() {
        WatchWorkerHelper.effectiveInterval(Duration.ofMinutes(15), isPro = false) shouldBe
                WatchSettings.FREE_MIN_CHECK_INTERVAL
        WatchWorkerHelper.effectiveInterval(Duration.ofMinutes(1), isPro = false) shouldBe
                WatchSettings.FREE_MIN_CHECK_INTERVAL
    }

    @Test
    fun `an upgraded installation checks as often as every 15 minutes`() {
        WatchWorkerHelper.effectiveInterval(Duration.ofMinutes(15), isPro = true) shouldBe
                Duration.ofMinutes(15)
        WatchWorkerHelper.effectiveInterval(Duration.ofMinutes(1), isPro = true) shouldBe
                WatchSettings.PRO_MIN_CHECK_INTERVAL
    }

    @Test
    fun `an interval above the floor is used as stored`() {
        WatchWorkerHelper.effectiveInterval(Duration.ofHours(6), isPro = false) shouldBe Duration.ofHours(6)
        WatchWorkerHelper.effectiveInterval(Duration.ofHours(6), isPro = true) shouldBe Duration.ofHours(6)
    }

    @Test
    fun `the clamp never changes the stored preference`() {
        val stored = Duration.ofMinutes(15)

        WatchWorkerHelper.effectiveInterval(stored, isPro = false) shouldBe Duration.ofMinutes(60)
        // The same stored value, so linking a feeder restores it without touching settings
        WatchWorkerHelper.effectiveInterval(stored, isPro = true) shouldBe Duration.ofMinutes(15)

        stored shouldBe Duration.ofMinutes(15)
    }

    @Test
    fun `an unknown tier gets the free floor`() = runTest {
        val repo = TestRepo(flowOf(Info(isPro = false, isSettled = false)))

        WatchWorkerHelper.effectiveInterval(Duration.ofMinutes(15), repo.isProNow()) shouldBe
                WatchSettings.FREE_MIN_CHECK_INTERVAL
    }
}
