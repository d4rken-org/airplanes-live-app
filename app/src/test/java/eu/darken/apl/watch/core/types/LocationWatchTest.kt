package eu.darken.apl.watch.core.types

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.Locale

class LocationWatchTest : BaseTest() {

    private val defaultLocale = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `coordinates use a decimal point whatever the device language`() {
        Locale.setDefault(Locale.GERMANY)

        LocationWatch.formatCoordinates(50.8123, 6.0712) shouldBe "50.81, 6.07"
        LocationWatch.formatCoordinates(50.8123, 6.0712, decimals = 4) shouldBe "50.8123, 6.0712"
        LocationWatch.formatCoordinates(-33.8688, -151.2093) shouldBe "-33.87, -151.21"
    }

    @Test
    fun `an altitude band bound is open or within 0 to 60000 ft`() {
        LocationWatch.altitudeBandAllowed(null, null) shouldBe true
        LocationWatch.altitudeBandAllowed(0, null) shouldBe true
        LocationWatch.altitudeBandAllowed(null, 0) shouldBe true
        LocationWatch.altitudeBandAllowed(null, 60_000) shouldBe true
        LocationWatch.altitudeBandAllowed(60_000, null) shouldBe true
        LocationWatch.altitudeBandAllowed(0, 60_000) shouldBe true

        LocationWatch.altitudeBandAllowed(-1, null) shouldBe false
        LocationWatch.altitudeBandAllowed(null, -1) shouldBe false
        LocationWatch.altitudeBandAllowed(60_001, null) shouldBe false
        LocationWatch.altitudeBandAllowed(null, 60_001) shouldBe false
        LocationWatch.altitudeBandAllowed(0, 60_001) shouldBe false
    }

    @Test
    fun `an altitude band's minimum is not above its maximum`() {
        LocationWatch.altitudeBandAllowed(10_000, 10_000) shouldBe true
        LocationWatch.altitudeBandAllowed(9_999, 10_000) shouldBe true
        LocationWatch.altitudeBandAllowed(10_001, 10_000) shouldBe false
    }
}
