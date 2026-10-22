package eu.darken.apl.common.flight.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.flight.RouteProgress
import org.junit.Test
import testhelper.BaseComposeTest

class RouteTrackTest : BaseComposeTest() {

    private var progress by mutableStateOf<RouteProgress>(RouteProgress.Unknown)

    private fun setTrack() = composeRule.setContent {
        PreviewWrapper {
            RouteTrack(originLabel = "FRA", destinationLabel = "LHR", routeKey = "EDDF-EGLL", progress = progress)
        }
    }

    @Test
    fun `describes where the aircraft is as it moves along`() {
        setTrack()
        composeRule.onNodeWithContentDescription("Flight from FRA to LHR, position unknown").assertExists()

        progress = RouteProgress.AtOrigin
        composeRule.onNodeWithContentDescription("On the ground at FRA").assertExists()

        progress = RouteProgress.Airborne(0.4f)
        composeRule.onNodeWithContentDescription("In flight from FRA to LHR").assertExists()

        progress = RouteProgress.AtDestination
        composeRule.onNodeWithContentDescription("Arrived at LHR").assertExists()
    }

    @Test
    fun `an unknown end is left out of the description`() {
        composeRule.setContent {
            PreviewWrapper {
                RouteTrack(originLabel = "FRA", destinationLabel = null, routeKey = "EDDF", progress = RouteProgress.Unknown)
            }
        }
        composeRule.onNodeWithContentDescription("Flight from FRA, destination unknown").assertExists()
    }
}
