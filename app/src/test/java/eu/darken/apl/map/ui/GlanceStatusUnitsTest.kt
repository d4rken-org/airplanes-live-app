package eu.darken.apl.map.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnit
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.UnitFormatter
import eu.darken.apl.map.core.MapAircraftDetails
import org.junit.Test
import testhelper.BaseComposeTest
import java.util.Locale

class GlanceStatusUnitsTest : BaseComposeTest() {

    // A paused history sends no new details, a unit change alone has to update the labels
    @Test
    fun `changing the units relabels unchanged details`() {
        val details = MapAircraftDetails(
            hex = "4CA2B1",
            isHistorical = true,
            altitudeFt = 35_000,
            vertRateFpm = 1_200,
            groundSpeedKt = 450f,
        )
        val units = mutableStateOf(UnitFormatter(AircraftUnits.AVIATION, DistanceUnit.KILOMETERS, Locale.US))

        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalUnitFormatter provides units.value) {
                    GlanceStatus(details = details, route = null)
                }
            }
        }

        composeRule.onNodeWithText("35,000 ft").assertExists()
        composeRule.onNodeWithText("450 kt").assertExists()
        composeRule.onNodeWithText("+1.2k fpm").assertExists()

        units.value = UnitFormatter(AircraftUnits.METRIC, DistanceUnit.KILOMETERS, Locale.US)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("10,670 m").assertExists()
        composeRule.onNodeWithText("833 km/h").assertExists()
        composeRule.onNodeWithText("+6.1 m/s").assertExists()
        composeRule.onNodeWithText("35,000 ft").assertDoesNotExist()
    }
}
