package eu.darken.apl.common.flight

import android.location.Location
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnit
import eu.darken.apl.common.units.Quantity
import eu.darken.apl.common.units.UnitFormatter
import eu.darken.apl.common.units.UnitSymbol
import eu.darken.apl.main.core.aircraft.Aircraft
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant
import java.util.Locale

class HeightAboveAirportTest : BaseTest() {

    private val now = Instant.parse("2026-11-15T12:00:00Z")

    private val munich = Airport(
        icaoCode = "EDDM",
        iataCode = "MUC",
        name = "Munich Airport",
        countryName = "Germany",
        latitude = MUC_LAT,
        longitude = MUC_LON,
        elevationFt = 1_487,
    )
    private val hamburg = Airport(
        icaoCode = "EDDH",
        iataCode = "HAM",
        name = "Hamburg Airport",
        countryName = "Germany",
        latitude = 53.6304,
        longitude = 9.98823,
        elevationFt = 53,
    )

    private fun route(origin: Airport? = hamburg, destination: Airport? = munich) = FlightRoute(
        callsign = "DLH2AB",
        origin = origin,
        destination = destination,
        seenAt = now,
    )

    /** North of Munich by [nm] nautical miles. */
    private fun aircraft(
        nm: Double = 2.0,
        altitudeFt: Int? = 2_600,
        geometricAltitudeFt: Int? = null,
        onGround: Boolean? = false,
        callsign: String? = "DLH2AB",
        seenAt: Instant? = now.minusSeconds(5),
    ): Aircraft = FakeAircraft(
        callsign = callsign,
        altitudeFt = altitudeFt,
        geometricAltitudeFt = geometricAltitudeFt,
        onGround = onGround,
        location = mockk<Location> {
            every { latitude } returns MUC_LAT + nm * DEGREES_PER_NM
            every { longitude } returns MUC_LON
        },
        positionSeenAt = seenAt,
    )

    @Test
    fun `on short final into Munich`() {
        val height = route().heightAboveAirport(aircraft(), now).shouldNotBeNull()

        height.airport shouldBe munich
        height.heightFt shouldBe 1_113
        height.aboutFieldLevel shouldBe false
    }

    @Test
    fun `near the origin it is the origin`() {
        route(origin = munich, destination = hamburg).heightAboveAirport(aircraft(), now)
            .shouldNotBeNull().airport shouldBe munich
    }

    @Test
    fun `a GPS-only aircraft shows nothing`() {
        route().heightAboveAirport(aircraft(altitudeFt = null, geometricAltitudeFt = 2_600), now).shouldBeNull()
    }

    @Test
    fun `on the ground shows nothing`() {
        route().heightAboveAirport(aircraft(nm = 0.5, altitudeFt = 1_500, onGround = true), now).shouldBeNull()
    }

    @Test
    fun `11 nm away shows nothing`() {
        route().heightAboveAirport(aircraft(nm = 11.0), now).shouldBeNull()
    }

    @Test
    fun `9 nm away still shows`() {
        route().heightAboveAirport(aircraft(nm = 9.0), now).shouldNotBeNull()
    }

    @Test
    fun `5,600 ft above shows nothing`() {
        route().heightAboveAirport(aircraft(altitudeFt = 1_487 + 5_600), now).shouldBeNull()
    }

    @Test
    fun `5,000 ft above still shows`() {
        route().heightAboveAirport(aircraft(altitudeFt = 1_487 + 5_000), now).shouldNotBeNull().heightFt shouldBe 5_000
    }

    @Test
    fun `unknown elevation shows nothing`() {
        route(destination = munich.copy(elevationFt = null)).heightAboveAirport(aircraft(), now).shouldBeNull()
    }

    @Test
    fun `an airport without a position shows nothing`() {
        route(destination = munich.copy(latitude = null)).heightAboveAirport(aircraft(), now).shouldBeNull()
    }

    @Test
    fun `below 100 ft is about field level`() {
        route().heightAboveAirport(aircraft(nm = 0.5, altitudeFt = 1_550), now)
            .shouldNotBeNull().aboutFieldLevel shouldBe true
    }

    @Test
    fun `below the field elevation is about field level`() {
        val height = route().heightAboveAirport(aircraft(nm = 0.5, altitudeFt = 1_400), now).shouldNotBeNull()

        height.heightFt shouldBe -87
        height.aboutFieldLevel shouldBe true
    }

    @Test
    fun `100 ft above is a height`() {
        route().heightAboveAirport(aircraft(nm = 0.5, altitudeFt = 1_587), now)
            .shouldNotBeNull().aboutFieldLevel shouldBe false
    }

    @Test
    fun `another callsign with the old route shows nothing`() {
        route().heightAboveAirport(aircraft(callsign = "DLH7XY"), now).shouldBeNull()
    }

    @Test
    fun `a callsign with padding matches`() {
        route().heightAboveAirport(aircraft(callsign = "DLH2AB  "), now).shouldNotBeNull()
    }

    @Test
    fun `no aircraft shows nothing`() {
        route().heightAboveAirport(null, now).shouldBeNull()
    }

    @Test
    fun `a position older than live shows nothing`() {
        route().heightAboveAirport(aircraft(seenAt = now.minus(Duration.ofMinutes(5))), now).shouldBeNull()
    }

    @Test
    fun `a position 2 min in the future shows nothing`() {
        route().heightAboveAirport(aircraft(seenAt = now.plus(Duration.ofMinutes(2))), now).shouldBeNull()
    }

    @Test
    fun `an unknown position time shows nothing`() {
        route().heightAboveAirport(aircraft(seenAt = null), now).shouldBeNull()
    }

    @Test
    fun `with both airports near it is the nearer one`() {
        // 5 nm further north than the aircraft, Munich is 2 nm south of it
        val nearby = Airport(
            icaoCode = "XXXX",
            iataCode = null,
            name = "Nearby Field",
            countryName = null,
            latitude = MUC_LAT + 7.0 * DEGREES_PER_NM,
            longitude = MUC_LON,
            elevationFt = 1_600,
        )

        route(origin = nearby, destination = munich).heightAboveAirport(aircraft(), now)
            .shouldNotBeNull().airport shouldBe munich
        route(origin = munich, destination = nearby).heightAboveAirport(aircraft(), now)
            .shouldNotBeNull().airport shouldBe munich
        route(origin = nearby, destination = munich).heightAboveAirport(aircraft(nm = 6.0), now)
            .shouldNotBeNull().airport shouldBe nearby
    }

    @Test
    fun `feet round to 50 ft`() {
        val units = UnitFormatter(AircraftUnits.AVIATION, DistanceUnit.KILOMETERS, Locale.US)

        units.heightAboveField(1_113) shouldBe Quantity("1,100", UnitSymbol.FEET)
        units.heightAboveField(1_130) shouldBe Quantity("1,150", UnitSymbol.FEET)
    }

    @Test
    fun `metric rounds to 10 m and not from rounded feet`() {
        val units = UnitFormatter(AircraftUnits.METRIC, DistanceUnit.KILOMETERS, Locale.US)

        // 1,130 ft is 344 m, from 1,150 ft it would be 350 m
        units.heightAboveField(1_130) shouldBe Quantity("340", UnitSymbol.METERS)
    }

    private companion object {
        const val MUC_LAT = 48.3538
        const val MUC_LON = 11.7861

        /** Along a meridian, on a 6,371 km earth. */
        const val DEGREES_PER_NM = 1_852.0 / 111_194.9
    }
}
