package eu.darken.apl.main.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration

class BackgroundSyncLevelTest : BaseTest() {

    @Test
    fun `never chosen follows the tier`() {
        BackgroundSyncLevel.effective(null, isPro = false) shouldBe BackgroundSyncLevel.BALANCED
        BackgroundSyncLevel.effective(null, isPro = true) shouldBe BackgroundSyncLevel.FAST
    }

    @Test
    fun `a stored fast runs balanced without Pro and fast again with it`() {
        val stored = BackgroundSyncLevel.FAST

        BackgroundSyncLevel.effective(stored, isPro = false) shouldBe BackgroundSyncLevel.BALANCED
        BackgroundSyncLevel.effective(stored, isPro = true) shouldBe BackgroundSyncLevel.FAST
    }

    @Test
    fun `balanced and slow ignore the tier`() {
        listOf(false, true).forEach { isPro ->
            BackgroundSyncLevel.effective(BackgroundSyncLevel.BALANCED, isPro) shouldBe BackgroundSyncLevel.BALANCED
            BackgroundSyncLevel.effective(BackgroundSyncLevel.SLOW, isPro) shouldBe BackgroundSyncLevel.SLOW
        }
    }

    @Test
    fun `each level carries its interval`() {
        BackgroundSyncLevel.FAST.interval shouldBe Duration.ofMinutes(15)
        BackgroundSyncLevel.BALANCED.interval shouldBe Duration.ofMinutes(60)
        BackgroundSyncLevel.SLOW.interval shouldBe Duration.ofHours(6)
    }
}
