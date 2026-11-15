package eu.darken.apl.map.ui

import android.location.Location
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.flight.Airport
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.map.ui.NativeMapViewModel.RouteDisplay
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class NativeMapViewModelRouteTest : BaseTest() {

    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private val route = FlightRoute(
        callsign = "DLH123",
        origin = Airport("AAAA", null, null, null, latitude = 0.0, longitude = 0.0),
        destination = Airport("BBBB", null, null, null, latitude = 0.0, longitude = 1.0),
        seenAt = now,
    )

    private fun selected(hex: String = "3C65A3", seenAt: Instant = now) = FakeAircraft(
        hex = hex,
        callsign = "DLH123",
        onGround = false,
        location = mockk<Location> {
            every { latitude } returns 0.0
            every { longitude } returns 0.5
        },
        positionSeenAt = seenAt,
    )

    private val elevatedRoute = route.copy(destination = route.destination!!.copy(elevationFt = 100))

    /** About 2 km before the destination. */
    private fun onFinal(hex: String = "3C65A3") = FakeAircraft(
        hex = hex,
        callsign = "DLH123",
        altitudeFt = 1_200,
        onGround = false,
        location = mockk<Location> {
            every { latitude } returns 0.0
            every { longitude } returns 0.98
        },
        positionSeenAt = now,
    )

    @Test
    fun `route and progress arrive together`() {
        val display = NativeMapViewModel.routeDisplay("3C65A3", route, selected(), now)

        display.shouldBeInstanceOf<RouteDisplay.Result>()
        display.route shouldBe route
        display.progress.shouldBeInstanceOf<RouteProgress.Airborne>()
    }

    @Test
    fun `details of another aircraft place nothing`() {
        val display = NativeMapViewModel.routeDisplay("3C65A3", route, selected(hex = "ABCDEF"), now)

        display.shouldBeInstanceOf<RouteDisplay.Result>().progress shouldBe RouteProgress.Unknown
    }

    @Test
    fun `the same details age out as time passes`() {
        val details = selected()

        NativeMapViewModel.routeDisplay("3C65A3", route, details, now)
            .shouldBeInstanceOf<RouteDisplay.Result>().progress.shouldBeInstanceOf<RouteProgress.Airborne>()
        NativeMapViewModel.routeDisplay("3C65A3", route, details, now.plus(Duration.ofMinutes(5)))
            .shouldBeInstanceOf<RouteDisplay.Result>().progress shouldBe RouteProgress.Unknown
    }

    @Test
    fun `no route yet is loading`() {
        NativeMapViewModel.routeDisplay("3C65A3", null, selected(), now) shouldBe RouteDisplay.Loading("3C65A3")
    }

    @Test
    fun `the ticks expire a position no new frame replaces`() = runTest {
        val shown = mutableListOf<RouteDisplay>()
        // Details seen at the start and never updated, like a map that stopped getting answers
        backgroundScope.launch {
            NativeMapViewModel.routeDisplays("3C65A3", flowOf(route), MutableStateFlow(selected())) {
                now.plusMillis(testScheduler.currentTime)
            }.collect { shown.add(it) }
        }

        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().progress.shouldBeInstanceOf<RouteProgress.Airborne>()

        advanceTimeBy(Duration.ofMinutes(4).toMillis())
        runCurrent()
        shown.size shouldBe 1

        advanceTimeBy(Duration.ofMinutes(2).toMillis())
        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().progress shouldBe RouteProgress.Unknown
    }

    @Test
    fun `selecting the aircraft again keeps its last position until it ages out`() = runTest {
        val details = MutableStateFlow<Aircraft?>(selected())
        val shown = mutableListOf<RouteDisplay>()
        backgroundScope.launch {
            NativeMapViewModel.routeDisplays("3C65A3", flowOf(route), details) {
                now.plusMillis(testScheduler.currentTime)
            }.collect { shown.add(it) }
        }
        runCurrent()

        details.value = null
        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().progress.shouldBeInstanceOf<RouteProgress.Airborne>()

        advanceTimeBy(Duration.ofMinutes(6).toMillis())
        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().progress shouldBe RouteProgress.Unknown
    }

    @Test
    fun `the height above the airport comes from the newest details`() {
        NativeMapViewModel.routeDisplay("3C65A3", elevatedRoute, onFinal(), now, current = onFinal())
            .shouldBeInstanceOf<RouteDisplay.Result>().heightAboveAirport.shouldNotBeNull().heightFt shouldBe 1_100
    }

    @Test
    fun `details of another aircraft give no height`() {
        NativeMapViewModel.routeDisplay("3C65A3", elevatedRoute, null, now, current = onFinal(hex = "ABCDEF"))
            .shouldBeInstanceOf<RouteDisplay.Result>().heightAboveAirport.shouldBeNull()
    }

    @Test
    fun `the last details kept to place the aircraft give no height`() = runTest {
        val details = MutableStateFlow<Aircraft?>(onFinal())
        val shown = mutableListOf<RouteDisplay>()
        backgroundScope.launch {
            NativeMapViewModel.routeDisplays("3C65A3", flowOf(elevatedRoute), details) {
                now.plusMillis(testScheduler.currentTime)
            }.collect { shown.add(it) }
        }
        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().heightAboveAirport.shouldNotBeNull()

        // Selected again, the server has not answered yet
        details.value = null
        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().apply {
            progress.shouldBeInstanceOf<RouteProgress.Airborne>()
            heightAboveAirport.shouldBeNull()
        }
    }

    @Test
    fun `the ticks expire a height no new frame replaces`() = runTest {
        val shown = mutableListOf<RouteDisplay>()
        backgroundScope.launch {
            NativeMapViewModel.routeDisplays("3C65A3", flowOf(elevatedRoute), MutableStateFlow(onFinal())) {
                now.plusMillis(testScheduler.currentTime)
            }.collect { shown.add(it) }
        }
        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().heightAboveAirport.shouldNotBeNull()

        advanceTimeBy(Duration.ofMinutes(6).toMillis())
        runCurrent()
        shown.last().shouldBeInstanceOf<RouteDisplay.Result>().heightAboveAirport.shouldBeNull()
    }
}
