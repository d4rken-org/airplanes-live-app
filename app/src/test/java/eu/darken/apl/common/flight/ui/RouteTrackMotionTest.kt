package eu.darken.apl.common.flight.ui

import eu.darken.apl.common.flight.RouteProgress
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class RouteTrackMotionTest : BaseTest() {

    private val route = listOf("EDDF", "EGLL", "DLH123")
    private val otherRoute = listOf("EGLL", "EDDF", "DLH124")

    @Test
    fun `targets per progress`() {
        RouteProgress.AtOrigin.trackTarget() shouldBe TrackTarget(0f, dimmed = false)
        RouteProgress.AtDestination.trackTarget() shouldBe TrackTarget(1f, dimmed = false)
        RouteProgress.Airborne(0.3f).trackTarget() shouldBe TrackTarget(0.3f, dimmed = false)
        RouteProgress.Unknown.trackTarget() shouldBe TrackTarget(0.5f, dimmed = true)
    }

    @Test
    fun `airborne updates on the same route glide`() {
        glides(
            TrackState(route, RouteProgress.Airborne(0.3f)),
            TrackState(route, RouteProgress.Airborne(0.35f)),
        ) shouldBe true
    }

    @Test
    fun `a new route jumps`() {
        glides(
            TrackState(route, RouteProgress.Airborne(0.3f)),
            TrackState(otherRoute, RouteProgress.Airborne(0.8f)),
        ) shouldBe false
    }

    @Test
    fun `gaining or losing the position jumps`() {
        glides(TrackState(route, RouteProgress.Unknown), TrackState(route, RouteProgress.Airborne(0.3f))) shouldBe false
        glides(TrackState(route, RouteProgress.Airborne(0.3f)), TrackState(route, RouteProgress.Unknown)) shouldBe false
    }

    @Test
    fun `taking off and landing jump`() {
        glides(TrackState(route, RouteProgress.AtOrigin), TrackState(route, RouteProgress.Airborne(0f))) shouldBe false
        glides(TrackState(route, RouteProgress.Airborne(1f)), TrackState(route, RouteProgress.AtDestination)) shouldBe false
    }
}
