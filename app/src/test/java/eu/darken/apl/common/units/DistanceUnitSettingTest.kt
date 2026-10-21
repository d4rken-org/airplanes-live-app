package eu.darken.apl.common.units

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class DistanceUnitSettingTest : BaseTest() {

    @Test
    fun `automatic uses miles where roads are signed in miles`() {
        listOf("US", "GB", "LR", "MM", "us").forEach {
            DistanceUnitSetting.AUTOMATIC.resolve(it) shouldBe DistanceUnit.MILES
        }
    }

    @Test
    fun `automatic uses kilometers elsewhere`() {
        listOf("DE", "FR", "AU", "CA", "", null).forEach {
            DistanceUnitSetting.AUTOMATIC.resolve(it) shouldBe DistanceUnit.KILOMETERS
        }
    }

    @Test
    fun `an explicit unit ignores the region`() {
        DistanceUnitSetting.NAUTICAL_MILES.resolve("US") shouldBe DistanceUnit.NAUTICAL_MILES
        DistanceUnitSetting.KILOMETERS.resolve("US") shouldBe DistanceUnit.KILOMETERS
        DistanceUnitSetting.MILES.resolve("DE") shouldBe DistanceUnit.MILES
    }
}
