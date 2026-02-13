package eu.darken.apl.feeder.core.config

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederPositionTest : BaseTest() {

    @Test
    fun `valid lat long with space`() {
        val result = FeederPosition.fromString("50.8006, 6.0619")
        result shouldBe FeederPosition(latitude = 50.8006, longitude = 6.0619)
    }

    @Test
    fun `valid lat long without space`() {
        val result = FeederPosition.fromString("50.8006,6.0619")
        result shouldBe FeederPosition(latitude = 50.8006, longitude = 6.0619)
    }

    @Test
    fun `valid lat long with extra spaces`() {
        val result = FeederPosition.fromString("  50.8006 ,  6.0619  ")
        result shouldBe FeederPosition(latitude = 50.8006, longitude = 6.0619)
    }

    @Test
    fun `valid negative coordinates`() {
        val result = FeederPosition.fromString("-33.8688, 151.2093")
        result shouldBe FeederPosition(latitude = -33.8688, longitude = 151.2093)
    }

    @Test
    fun `valid both negative`() {
        val result = FeederPosition.fromString("-33.8688, -151.2093")
        result shouldBe FeederPosition(latitude = -33.8688, longitude = -151.2093)
    }

    @Test
    fun `single number returns null`() {
        FeederPosition.fromString("50.8006").shouldBeNull()
    }

    @Test
    fun `three parts returns null`() {
        FeederPosition.fromString("50.8006, 6.0619, 100").shouldBeNull()
    }

    @Test
    fun `non-numeric text returns null`() {
        FeederPosition.fromString("abc, def").shouldBeNull()
    }

    @Test
    fun `empty string returns null`() {
        FeederPosition.fromString("").shouldBeNull()
    }

    @Test
    fun `blank string returns null`() {
        FeederPosition.fromString("   ").shouldBeNull()
    }

    @Test
    fun `integer coordinates are valid`() {
        val result = FeederPosition.fromString("51, 6")
        result shouldBe FeederPosition(latitude = 51.0, longitude = 6.0)
    }
}
