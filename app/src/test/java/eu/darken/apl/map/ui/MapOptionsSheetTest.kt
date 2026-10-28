package eu.darken.apl.map.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.MapLabelField
import eu.darken.apl.map.core.MapLabelFields
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapOptionsSheetTest : BaseComposeTest() {

    private val toggled = mutableListOf<MapLabelField>()

    private fun setSheet(toggles: NativeMapViewModel.Toggles) = composeRule.setContent {
        PreviewWrapper {
            MapOptionsSheet(
                toggles = toggles,
                canIsolate = false,
                onToggleLabels = {},
                onToggleLabelField = { toggled.add(it) },
                onToggleAllTracks = {},
                onToggleMilitaryHighlight = {},
                onToggleWatchHighlight = {},
                onToggleMilitaryOnly = {},
                onToggleIsolate = {},
                onToggleMultiSelect = {},
                onOpenWebsite = {},
                onResetMap = {},
                onDismiss = {},
            )
        }
    }

    private fun fieldSwitch(field: MapLabelField) =
        composeRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(labelFieldTag(field))))

    @Test
    fun `the only enabled field can not be switched off`() {
        setSheet(NativeMapViewModel.Toggles())

        fieldSwitch(MapLabelField.CALLSIGN).assertIsOn().assertIsNotEnabled()
        fieldSwitch(MapLabelField.TYPE).assertIsEnabled()
        fieldSwitch(MapLabelField.ALTITUDE).assertIsEnabled()
        fieldSwitch(MapLabelField.SPEED).assertIsEnabled()
    }

    @Test
    fun `with two fields enabled both can be switched off`() {
        setSheet(NativeMapViewModel.Toggles(labelFields = MapLabelFields(callsign = true, type = true)))

        fieldSwitch(MapLabelField.CALLSIGN).assertIsEnabled()
        fieldSwitch(MapLabelField.TYPE).assertIsEnabled()
    }

    @Test
    fun `fields stay switchable while labels are off`() {
        setSheet(NativeMapViewModel.Toggles(labels = false))

        fieldSwitch(MapLabelField.CALLSIGN).assertIsNotEnabled()
        fieldSwitch(MapLabelField.TYPE).assertIsEnabled()
        fieldSwitch(MapLabelField.ALTITUDE).assertIsEnabled()
        fieldSwitch(MapLabelField.SPEED).assertIsEnabled()
    }

    @Test
    fun `tapping a field toggles it`() {
        setSheet(NativeMapViewModel.Toggles())

        composeRule.onNodeWithText("Altitude").performScrollTo().performClick()

        toggled shouldBe listOf(MapLabelField.ALTITUDE)
    }
}
