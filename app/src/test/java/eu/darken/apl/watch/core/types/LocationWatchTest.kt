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
}
