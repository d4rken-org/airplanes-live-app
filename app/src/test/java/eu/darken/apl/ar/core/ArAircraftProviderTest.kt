package eu.darken.apl.ar.core

import android.location.Location
import android.os.SystemClock
import eu.darken.apl.main.core.api.AirplanesLiveApi
import eu.darken.apl.main.core.api.AirplanesLiveEndpoint
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan as intShouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

class ArAircraftProviderTest : BaseTest() {

    private val endpoint = mockk<AirplanesLiveEndpoint>()
    private val locationState = MutableStateFlow<Location?>(null)
    private val dispatcherProvider = TestDispatcherProvider()

    private val userLocation = mockk<Location> {
        every { latitude } returns 51.5
        every { longitude } returns -0.1
    }

    @BeforeEach
    fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 10_000L
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(SystemClock::class)
    }

    private fun createProvider() = ArAircraftProvider(
        locationState = locationState,
        endpoint = endpoint,
        dispatcherProvider = dispatcherProvider,
    )

    private fun mockAircraft(
        lat: Double = 52.0,
        lon: Double = -0.1,
        groundSpeed: Float? = null,
        groundTrack: Float? = null,
        altitudeRate: Int? = null,
        altitude: String? = "35000",
        seenAt: Instant = Instant.now(),
    ): AirplanesLiveApi.Aircraft = mockk(relaxed = true) {
        every { location } returns mockk {
            every { latitude } returns lat
            every { longitude } returns lon
        }
        every { this@mockk.groundSpeed } returns groundSpeed
        every { this@mockk.groundTrack } returns groundTrack
        every { this@mockk.altitudeRate } returns altitudeRate
        every { this@mockk.altitude } returns altitude
        every { this@mockk.seenAt } returns seenAt
    }

    @Test
    fun `extrapolation applied when groundSpeed and groundTrack are valid`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = 500f, groundTrack = 0f,
            seenAt = Instant.now().minusSeconds(5),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result.size shouldBe 1
        result[0].interpolatedLat shouldNotBe 52.0
        result[0].interpolatedLat shouldBeGreaterThan 52.0
    }

    @Test
    fun `fallback to raw position when groundSpeed is null`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = null, groundTrack = 0f,
            seenAt = Instant.now(),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result.size shouldBe 1
        result[0].interpolatedLat shouldBe 52.0
        result[0].interpolatedLon shouldBe -0.1
    }

    @Test
    fun `fallback to raw position when speed is NaN`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = Float.NaN, groundTrack = 90f,
            seenAt = Instant.now(),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result.size shouldBe 1
        result[0].interpolatedLat shouldBe 52.0
        result[0].interpolatedLon shouldBe -0.1
    }

    @Test
    fun `distance computed from extrapolated position`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = 500f, groundTrack = 0f,
            seenAt = Instant.now().minusSeconds(5),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        val rawDist = ScreenProjection.haversineDistanceM(51.5, -0.1, 52.0, -0.1)
        result[0].distanceM shouldNotBe rawDist
        // Extrapolated north -> further from user (user is south at 51.5)
        result[0].distanceM shouldBeGreaterThan rawDist
    }

    @Test
    fun `older seenAt produces more extrapolation`() = runTest {
        val recentAc = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = 500f, groundTrack = 0f,
            seenAt = Instant.now().minusSeconds(1),
        )
        val staleAc = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = 500f, groundTrack = 0f,
            seenAt = Instant.now().minusSeconds(10),
        )

        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(recentAc)
        locationState.value = userLocation
        val recentResult = createProvider().aircraft.first()

        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(staleAc)
        val staleResult = createProvider().aircraft.first()

        // Stale aircraft should be extrapolated further north
        staleResult[0].interpolatedLat shouldBeGreaterThan recentResult[0].interpolatedLat
    }

    @Test
    fun `altitude extrapolated when altitudeRate is present`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = 100f, groundTrack = 0f,
            altitudeRate = 2000,
            altitude = "30000",
            seenAt = Instant.now().minusSeconds(10),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result.size shouldBe 1
        // 2000 ft/min * 10s/60 = 333 ft -> 30000 + 333 = 30333
        result[0].altitudeFt!! intShouldBeGreaterThan 30000
    }

    @Test
    fun `altitude unchanged when altitudeRate is null`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = 100f, groundTrack = 0f,
            altitudeRate = null,
            altitude = "30000",
            seenAt = Instant.now().minusSeconds(5),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result[0].altitudeFt shouldBe 30000
    }

    @Test
    fun `aircraft older than 60s are dropped`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            seenAt = Instant.now().minusSeconds(61),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result.size shouldBe 0
    }

    @Test
    fun `speed out of range falls back to raw position`() = runTest {
        val ac = mockAircraft(
            lat = 52.0, lon = -0.1,
            groundSpeed = 3000f, groundTrack = 0f,
            seenAt = Instant.now().minusSeconds(5),
        )
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result[0].interpolatedLat shouldBe 52.0
    }

    @Test
    fun `aircraft with no location is filtered out`() = runTest {
        val ac = mockk<AirplanesLiveApi.Aircraft>(relaxed = true) {
            every { location } returns null
            every { seenAt } returns Instant.now()
        }
        coEvery { endpoint.getByLocation(any(), any(), any()) } returns listOf(ac)
        locationState.value = userLocation

        val result = createProvider().aircraft.first()

        result.size shouldBe 0
    }
}
