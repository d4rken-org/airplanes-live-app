package eu.darken.apl.main.core.aircraft

import eu.darken.apl.common.compose.preview.FakeAircraft
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class AircraftExtensionsTest : BaseTest() {

    @Nested
    inner class AltitudeLabel {

        @Test
        fun `altitude in feet`() {
            FakeAircraft(altitudeFt = 35000, onGround = false).altitudeLabel shouldBe "35000"
        }

        @Test
        fun `on ground wins over a reported altitude`() {
            FakeAircraft(altitudeFt = 0, onGround = true).altitudeLabel shouldBe "ground"
        }

        @Test
        fun `unknown altitude`() {
            FakeAircraft(altitudeFt = null, onGround = null).altitudeLabel shouldBe "?"
        }
    }

    @Nested
    inner class MessageTypeLabel {

        @Test
        fun `mlat source`() {
            FakeAircraft(source = "mlat").messageTypeLabel shouldBe "MLAT"
        }

        @Test
        fun `adsb sources`() {
            FakeAircraft(source = "adsb_icao").messageTypeLabel shouldBe "ADS-B"
        }

        @Test
        fun `mode s source`() {
            FakeAircraft(source = "mode_s").messageTypeLabel shouldBe "MODE-S"
        }

        @Test
        fun `unknown source`() {
            FakeAircraft(source = null).messageTypeLabel shouldBe "Other"
        }
    }

    @Nested
    inner class IsEmergencySquawk {

        @Test
        fun `7700 is emergency`() {
            FakeAircraft(squawk = "7700").isEmergencySquawk shouldBe true
        }

        @Test
        fun `7600 is emergency`() {
            FakeAircraft(squawk = "7600").isEmergencySquawk shouldBe true
        }

        @Test
        fun `7500 is emergency`() {
            FakeAircraft(squawk = "7500").isEmergencySquawk shouldBe true
        }

        @Test
        fun `1200 is not emergency`() {
            FakeAircraft(squawk = "1200").isEmergencySquawk shouldBe false
        }

        @Test
        fun `other codes starting with 7 are not emergency`() {
            FakeAircraft(squawk = "7160").isEmergencySquawk shouldBe false
            FakeAircraft(squawk = "7000").isEmergencySquawk shouldBe false
        }

        @Test
        fun `null squawk is not emergency`() {
            FakeAircraft(squawk = null).isEmergencySquawk shouldBe false
        }

        @Test
        fun `empty squawk is not emergency`() {
            FakeAircraft(squawk = "").isEmergencySquawk shouldBe false
        }
    }

    @Nested
    inner class IsLiveAt {
        private val now = Instant.parse("2026-10-14T12:00:00Z")

        @Test
        fun `seen just now`() {
            FakeAircraft(messageSeenAt = now.minusSeconds(10)).isLiveAt(now) shouldBe true
        }

        @Test
        fun `seen exactly five minutes ago is no longer live`() {
            FakeAircraft(messageSeenAt = now.minus(Duration.ofMinutes(5))).isLiveAt(now) shouldBe false
        }

        @Test
        fun `a timestamp ahead of the clock counts as now`() {
            FakeAircraft(messageSeenAt = now.plusSeconds(30)).isLiveAt(now) shouldBe true
        }

        @Test
        fun `never seen`() {
            FakeAircraft(messageSeenAt = null).isLiveAt(now) shouldBe false
        }
    }
}
