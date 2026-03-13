package eu.darken.apl.main.core.aircraft

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AircraftExtensionsTest : BaseTest() {

    private fun mockAircraft(
        altitude: String? = null,
        squawk: SquawkCode? = null,
    ): Aircraft = mockk {
        every { this@mockk.altitude } returns altitude
        every { this@mockk.squawk } returns squawk
    }

    @Nested
    inner class AltitudeFt {

        @Test
        fun `normal altitude`() {
            mockAircraft(altitude = "35000").altitudeFt shouldBe 35000
        }

        @Test
        fun `altitude with commas`() {
            mockAircraft(altitude = "35,000").altitudeFt shouldBe 35000
        }

        @Test
        fun `ground lowercase`() {
            mockAircraft(altitude = "ground").altitudeFt shouldBe 0
        }

        @Test
        fun `ground capitalized`() {
            mockAircraft(altitude = "Ground").altitudeFt shouldBe 0
        }

        @Test
        fun `null altitude`() {
            mockAircraft(altitude = null).altitudeFt.shouldBeNull()
        }

        @Test
        fun `empty string`() {
            mockAircraft(altitude = "").altitudeFt.shouldBeNull()
        }

        @Test
        fun `whitespace padded`() {
            mockAircraft(altitude = "  35000  ").altitudeFt shouldBe 35000
        }

        @Test
        fun `non-numeric string`() {
            mockAircraft(altitude = "abc").altitudeFt.shouldBeNull()
        }

        @Test
        fun `low altitude`() {
            mockAircraft(altitude = "500").altitudeFt shouldBe 500
        }

        @Test
        fun `zero`() {
            mockAircraft(altitude = "0").altitudeFt shouldBe 0
        }

        @Test
        fun `negative altitude`() {
            mockAircraft(altitude = "-100").altitudeFt shouldBe -100
        }
    }

    @Nested
    inner class IsEmergencySquawk {

        @Test
        fun `7700 is emergency`() {
            mockAircraft(squawk = "7700").isEmergencySquawk shouldBe true
        }

        @Test
        fun `7600 is emergency`() {
            mockAircraft(squawk = "7600").isEmergencySquawk shouldBe true
        }

        @Test
        fun `7500 is emergency`() {
            mockAircraft(squawk = "7500").isEmergencySquawk shouldBe true
        }

        @Test
        fun `1200 is not emergency`() {
            mockAircraft(squawk = "1200").isEmergencySquawk shouldBe false
        }

        @Test
        fun `null squawk is not emergency`() {
            mockAircraft(squawk = null).isEmergencySquawk shouldBe false
        }

        @Test
        fun `empty squawk is not emergency`() {
            mockAircraft(squawk = "").isEmergencySquawk shouldBe false
        }
    }
}
