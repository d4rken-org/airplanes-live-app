package eu.darken.apl.main.core.aircraft

import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.api.AircraftObservation
import eu.darken.apl.server.api.AircraftPosition
import eu.darken.apl.server.api.MapResponse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AircraftMappingTest {

    private val fetchedAt = Instant.ofEpochMilli(1_710_000_000_000)

    private val json = ServerModule.serverJson()

    private fun decode(record: String): Aircraft =
        json.decodeFromString<AircraftObservation>(record).toAircraft(fetchedAt)

    @Test
    fun `omitted flags default to false`() {
        val mapped = AircraftObservation(id = "3c65a3").toAircraft(fetchedAt)

        mapped.hex shouldBe "3C65A3"
        mapped.military shouldBe false
        mapped.ladd shouldBe false
        mapped.pia shouldBe false
        mapped.fetchedAt shouldBe fetchedAt
    }

    @Test
    fun `a missing position leaves the location and its age unknown`() {
        val mapped = AircraftObservation(
            id = "3c65a3",
            messageObservedAt = 1_709_999_999_000,
        ).toAircraft(fetchedAt)

        mapped.location.shouldBeNull()
        mapped.positionSeenAt.shouldBeNull()
        mapped.messageSeenAt shouldBe Instant.ofEpochMilli(1_709_999_999_000)
    }

    @Test
    fun `a position becomes a location stamped with its observation time`() {
        val mapped = AircraftObservation(
            id = "3c65a3",
            position = AircraftPosition(latitude = 50.03, longitude = 8.57, observedAt = 1_709_999_998_000),
        ).toAircraft(fetchedAt)

        mapped.location!!.latitude shouldBe 50.03
        mapped.location!!.longitude shouldBe 8.57
        mapped.location!!.time shouldBe 1_709_999_998_000
        mapped.positionSeenAt shouldBe Instant.ofEpochMilli(1_709_999_998_000)
    }

    @Test
    fun `on ground travels without a barometric altitude`() {
        val mapped = AircraftObservation(
            id = "~abcdef",
            onGround = true,
            barometricAltitudeFeet = null,
        ).toAircraft(fetchedAt)

        mapped.hex shouldBe "~ABCDEF"
        mapped.onGround shouldBe true
        mapped.altitudeFt.shouldBeNull()
    }

    @Test
    fun `units are carried over and rounded to whole numbers`() {
        val mapped = AircraftObservation(
            id = "3c65a3",
            callsign = "DLH453",
            registration = "D-AIUE",
            aircraftType = "A320",
            squawk = "1000",
            source = "adsb_icao",
            barometricAltitudeFeet = 35000.4,
            geometricAltitudeFeet = 35200.6,
            groundSpeedKnots = 450.5,
            indicatedAirspeedKnots = 280.4,
            trackDegrees = 45.5,
            trueHeadingDegrees = 46.5,
            verticalRateFeetPerMinute = -1500.6,
            outsideTemperatureCelsius = -40.4,
            military = true,
        ).toAircraft(fetchedAt)

        mapped.callsign shouldBe "DLH453"
        mapped.registration shouldBe "D-AIUE"
        mapped.airframe shouldBe "A320"
        mapped.squawk shouldBe "1000"
        mapped.source shouldBe "adsb_icao"
        mapped.altitudeFt shouldBe 35000
        mapped.geometricAltitudeFt shouldBe 35201
        mapped.groundSpeed shouldBe 450.5f
        mapped.indicatedAirSpeed shouldBe 280
        mapped.groundTrack shouldBe 45.5f
        mapped.trackheading shouldBe 46.5
        mapped.altitudeRate shouldBe -1501
        mapped.outsideTemp shouldBe -40
        mapped.military shouldBe true
    }

    @Test
    fun `an airline callsign carries the airline`() {
        decode("""{"id":"3c6681","callsign":"DLH400","airline":{"designator":"DLH","name":"Lufthansa"}}""")
            .airline shouldBe Airline(designator = "DLH", name = "Lufthansa")
        decode("""{"id":"3c6681","callsign":"DLH4AB","airline":{"designator":"DLH","name":"Lufthansa"}}""")
            .airline shouldBe Airline(designator = "DLH", name = "Lufthansa")
    }

    @Test
    fun `the airline is taken verbatim`() {
        decode("""{"id":"3c6681","callsign":"DLH400","airline":{"designator":"DLH","name":" Lufthansa Cargo "}}""")
            .airline shouldBe Airline(designator = "DLH", name = " Lufthansa Cargo ")
    }

    @Test
    fun `a record without airline has none`() {
        decode("""{"id":"a0b1c2","callsign":"N123AB"}""").airline.shouldBeNull()
        decode("""{"id":"3c4b26","callsign":"DEABC"}""").airline.shouldBeNull()
        decode("""{"id":"3c4b26"}""").airline.shouldBeNull()
    }

    @Test
    fun `an incomplete airline is no airline`() {
        decode("""{"id":"3c6681","callsign":"DLH400","airline":{"name":"Lufthansa"}}""").airline.shouldBeNull()
        decode("""{"id":"3c6681","callsign":"DLH400","airline":{"designator":"DLH"}}""").airline.shouldBeNull()
        decode("""{"id":"3c6681","callsign":"DLH400","airline":{"designator":" ","name":"Lufthansa"}}""")
            .airline.shouldBeNull()
        decode("""{"id":"3c6681","callsign":"DLH400","airline":{"designator":"DLH","name":""}}""")
            .airline.shouldBeNull()
    }

    @Test
    fun `the selected aircraft of a map answer carries the airline`() {
        val response = json.decodeFromString<MapResponse>(
            """
            {
              "serverTime": 1710000000000,
              "metadata": {"sourceTime": 1710000000000, "fetchedAt": 1710000000000, "expiresAt": 1710000005000,
                "complete": true, "stale": false},
              "aircraft": [],
              "selected": {"id": "3c6681", "callsign": "DLH400",
                "airline": {"designator": "DLH", "name": "Lufthansa"}},
              "capped": false,
              "usage": {"scope": "installation", "bucket": "viewing", "resetsAt": 1710086400000,
                "allowance": {"limit": 100, "used": 1, "reserved": 0, "remaining": 99}}
            }
            """.trimIndent()
        )

        response.selected!!.toAircraft(fetchedAt).airline shouldBe Airline(designator = "DLH", name = "Lufthansa")
    }
}
