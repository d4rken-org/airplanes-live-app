package eu.darken.apl.common.flight

import android.location.Location
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Aircraft
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class RouteProgressTest : BaseTest() {

    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun airport(icao: String, lat: Double?, lon: Double?) = Airport(
        icaoCode = icao, iataCode = null, name = null, countryName = null, latitude = lat, longitude = lon,
    )

    /** Along the equator, one degree of longitude is about 111 km. */
    private fun route(
        originLat: Double? = 0.0, originLon: Double? = 0.0,
        destLat: Double? = 0.0, destLon: Double? = 1.0,
    ) = FlightRoute(
        callsign = "DLH123",
        origin = airport("AAAA", originLat, originLon),
        destination = airport("BBBB", destLat, destLon),
        seenAt = now,
    )

    private fun aircraft(
        lat: Double,
        lon: Double,
        onGround: Boolean? = false,
        seenAt: Instant? = now.minusSeconds(5),
        callsign: String? = "DLH123",
    ): Aircraft = FakeAircraft(
        callsign = callsign,
        onGround = onGround,
        location = mockk<Location> {
            every { latitude } returns lat
            every { longitude } returns lon
        },
        positionSeenAt = seenAt,
    )

    private fun RouteProgress.fraction(): Float = shouldBeInstanceOf<RouteProgress.Airborne>().fraction

    @Test
    fun `midway along the route`() {
        route().progressOf(aircraft(0.0, 0.5), now).fraction() shouldBe (0.5f plusOrMinus 0.001f)
    }

    @Test
    fun `departure turn just behind origin shows at the start`() {
        // 3 km behind, the allowance is at least 15 km
        route().progressOf(aircraft(0.0, -0.03), now).fraction() shouldBe 0f
    }

    @Test
    fun `25 km behind origin is not on this route`() {
        route().progressOf(aircraft(0.0, -0.225), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `downwind just past destination shows at the end`() {
        route().progressOf(aircraft(0.0, 1.03), now).fraction() shouldBe 1f
    }

    @Test
    fun `downwind leg 10 km past a short route's destination shows at the end`() {
        route().progressOf(aircraft(0.0, 1.09), now).fraction() shouldBe 1f
    }

    @Test
    fun `long haul allows at most 20 km past the destination`() {
        // 5% of this 6,700 km route would be 333 km
        route(destLon = 60.0).progressOf(aircraft(0.0, 60.3), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `far past destination is not on this route`() {
        route().progressOf(aircraft(0.0, 1.3), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `sideways within 50 km still places the aircraft`() {
        route().progressOf(aircraft(0.3, 0.5), now).fraction() shouldBe (0.5f plusOrMinus 0.01f)
    }

    @Test
    fun `sideways beyond the allowance is not on this route`() {
        route().progressOf(aircraft(0.6, 0.5), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `long haul allows a wide offset`() {
        // About 6,700 km route, 556 km off its line
        route(destLon = 60.0).progressOf(aircraft(5.0, 30.0), now).fraction() shouldBe (0.5f plusOrMinus 0.01f)
    }

    @Test
    fun `route across the antimeridian`() {
        route(originLon = 170.0, destLon = -170.0).progressOf(aircraft(0.0, -179.0), now).fraction() shouldBe
            (0.55f plusOrMinus 0.001f)
    }

    @Test
    fun `route over the pole`() {
        route(originLat = 80.0, originLon = 0.0, destLat = 80.0, destLon = 180.0)
            .progressOf(aircraft(90.0, 0.0), now).fraction() shouldBe (0.5f plusOrMinus 0.001f)
    }

    @Test
    fun `airports too close together have no usable line`() {
        route(destLon = 0.0).progressOf(aircraft(0.0, 0.0), now) shouldBe RouteProgress.Unknown
        route(destLon = 0.05).progressOf(aircraft(0.0, 0.02), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `near-antipodal airports have no single line`() {
        route(destLon = 179.95).progressOf(aircraft(0.0, 90.0), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `on the ground at origin`() {
        route().progressOf(aircraft(0.0, 0.01, onGround = true), now) shouldBe RouteProgress.AtOrigin
    }

    @Test
    fun `on the ground at destination`() {
        route().progressOf(aircraft(0.0, 0.99, onGround = true), now) shouldBe RouteProgress.AtDestination
    }

    @Test
    fun `on the ground away from both airports`() {
        route().progressOf(aircraft(0.0, 0.5, onGround = true), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `on the ground within reach of both airports`() {
        // About 12 km apart, the midpoint is within 15 km of either
        route(destLon = 0.108).progressOf(aircraft(0.0, 0.054, onGround = true), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `airborne next to an airport is still in flight`() {
        route().progressOf(aircraft(0.0, 0.01, onGround = false), now).fraction() shouldBe (0.01f plusOrMinus 0.001f)
    }

    @Test
    fun `unknown ground state is not guessed`() {
        route().progressOf(aircraft(0.0, 0.5, onGround = null), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `position at the live limit is too old`() {
        val seenAt = now.minus(Duration.ofMinutes(5))
        route().progressOf(aircraft(0.0, 0.5, seenAt = seenAt), now) shouldBe RouteProgress.Unknown
        route().progressOf(aircraft(0.0, 0.5, seenAt = seenAt.plusSeconds(1)), now)
            .shouldBeInstanceOf<RouteProgress.Airborne>()
    }

    @Test
    fun `slightly future timestamp is tolerated, far future is not`() {
        route().progressOf(aircraft(0.0, 0.5, seenAt = now.plusSeconds(30)), now)
            .shouldBeInstanceOf<RouteProgress.Airborne>()
        route().progressOf(aircraft(0.0, 0.5, seenAt = now.plus(Duration.ofMinutes(10))), now) shouldBe
            RouteProgress.Unknown
    }

    @Test
    fun `no position timestamp`() {
        route().progressOf(aircraft(0.0, 0.5, seenAt = null), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `route of another callsign`() {
        route().progressOf(aircraft(0.0, 0.5, callsign = "DLH124"), now) shouldBe RouteProgress.Unknown
        route().progressOf(aircraft(0.0, 0.5, callsign = null), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `callsign padding does not matter`() {
        route().progressOf(aircraft(0.0, 0.5, callsign = "DLH123  "), now).shouldBeInstanceOf<RouteProgress.Airborne>()
    }

    @Test
    fun `airport without coordinates`() {
        route(destLat = null, destLon = null).progressOf(aircraft(0.0, 0.5), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `no aircraft or no position`() {
        route().progressOf(null, now) shouldBe RouteProgress.Unknown
        route().progressOf(FakeAircraft(location = null, positionSeenAt = now), now) shouldBe RouteProgress.Unknown
    }

    @Test
    fun `invalid coordinates are not placed`() {
        route().progressOf(aircraft(95.0, 0.5), now) shouldBe RouteProgress.Unknown
        route().progressOf(aircraft(Double.NaN, 0.5), now) shouldBe RouteProgress.Unknown
        route().progressOf(aircraft(0.0, Double.POSITIVE_INFINITY), now) shouldBe RouteProgress.Unknown
        route(destLon = 200.0).progressOf(aircraft(0.0, 0.5), now) shouldBe RouteProgress.Unknown
    }
}
