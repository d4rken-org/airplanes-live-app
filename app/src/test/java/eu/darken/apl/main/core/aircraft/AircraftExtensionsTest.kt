package eu.darken.apl.main.core.aircraft

import android.location.Location
import eu.darken.apl.common.compose.preview.FakeAircraft
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class AircraftExtensionsTest : BaseTest() {

    @Nested
    inner class SourceType {

        @Test
        fun `mlat source`() {
            FakeAircraft(source = "mlat").sourceType shouldBe AircraftSourceType.MLAT
        }

        @Test
        fun `adsb sources`() {
            FakeAircraft(source = "adsb_icao").sourceType shouldBe AircraftSourceType.ADSB
        }

        @Test
        fun `mode s source`() {
            FakeAircraft(source = "mode_s").sourceType shouldBe AircraftSourceType.MODE_S
        }

        @Test
        fun `unknown source`() {
            FakeAircraft(source = null).sourceType shouldBe AircraftSourceType.OTHER
        }
    }

    @Nested
    inner class SourceMeaning {

        @Test
        fun `mlat source`() {
            aircraftSourceMeaning("mlat") shouldBe AircraftSourceMeaning.MLAT
        }

        @Test
        fun `adsb sources`() {
            aircraftSourceMeaning("adsb_icao") shouldBe AircraftSourceMeaning.ADSB
            aircraftSourceMeaning("adsb_icao_nt") shouldBe AircraftSourceMeaning.ADSB
            aircraftSourceMeaning("adsb_other") shouldBe AircraftSourceMeaning.ADSB
        }

        @Test
        fun `mode s source`() {
            aircraftSourceMeaning("mode_s") shouldBe AircraftSourceMeaning.MODE_S
        }

        @Test
        fun `tisb sources`() {
            aircraftSourceMeaning("tisb_icao") shouldBe AircraftSourceMeaning.TISB
            aircraftSourceMeaning("tisb_trackfile") shouldBe AircraftSourceMeaning.TISB
            aircraftSourceMeaning("tisb_other") shouldBe AircraftSourceMeaning.TISB
        }

        @Test
        fun `adsr sources`() {
            aircraftSourceMeaning("adsr_icao") shouldBe AircraftSourceMeaning.ADSR
            aircraftSourceMeaning("adsr_other") shouldBe AircraftSourceMeaning.ADSR
        }

        @Test
        fun `adsc source`() {
            aircraftSourceMeaning("adsc") shouldBe AircraftSourceMeaning.ADSC
        }

        @Test
        fun `other and unknown sources`() {
            aircraftSourceMeaning("other") shouldBe AircraftSourceMeaning.OTHER
            aircraftSourceMeaning("something_new") shouldBe AircraftSourceMeaning.OTHER
            aircraftSourceMeaning(null) shouldBe AircraftSourceMeaning.OTHER
        }

        @Test
        fun `meaning agrees with the shown source type`() {
            mapOf(
                "mlat" to (AircraftSourceType.MLAT to AircraftSourceMeaning.MLAT),
                "adsb_icao" to (AircraftSourceType.ADSB to AircraftSourceMeaning.ADSB),
                "mode_s" to (AircraftSourceType.MODE_S to AircraftSourceMeaning.MODE_S),
                "other" to (AircraftSourceType.OTHER to AircraftSourceMeaning.OTHER),
            ).forEach { (source, expected) ->
                val (type, meaning) = expected
                FakeAircraft(source = source).sourceType shouldBe type
                aircraftSourceMeaning(source) shouldBe meaning
            }
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

    @Nested
    inner class CurrentLocation {
        private val messageSeenAt = Instant.parse("2026-10-14T12:00:00Z")
        private val location = mockk<Location>()

        private fun aircraft(
            positionSeenAt: Instant?,
            messageSeenAt: Instant? = this.messageSeenAt,
            location: Location? = this.location,
        ) = FakeAircraft(location = location, messageSeenAt = messageSeenAt, positionSeenAt = positionSeenAt)

        @Test
        fun `position from the same moment as the message`() {
            aircraft(positionSeenAt = messageSeenAt).currentLocation shouldBeSameInstanceAs location
        }

        @Test
        fun `position more than five minutes older than the message`() {
            aircraft(positionSeenAt = messageSeenAt.minus(Duration.ofMinutes(5)).minusSeconds(1))
                .currentLocation.shouldBeNull()
        }

        @Test
        fun `position exactly five minutes older than the message`() {
            aircraft(positionSeenAt = messageSeenAt.minus(Duration.ofMinutes(5)))
                .currentLocation shouldBeSameInstanceAs location
        }

        @Test
        fun `unknown position time`() {
            aircraft(positionSeenAt = null).currentLocation shouldBeSameInstanceAs location
        }

        @Test
        fun `unknown message time`() {
            aircraft(positionSeenAt = messageSeenAt.minus(Duration.ofHours(2)), messageSeenAt = null)
                .currentLocation shouldBeSameInstanceAs location
        }

        @Test
        fun `no position`() {
            aircraft(positionSeenAt = messageSeenAt, location = null).currentLocation.shouldBeNull()
        }
    }
}
