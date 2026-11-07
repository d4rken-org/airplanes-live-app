package eu.darken.apl.map.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.AircraftKind
import eu.darken.apl.map.core.MapLabelField
import eu.darken.apl.map.core.MapLabelFields
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapOptionsSheetTest : BaseComposeTest() {

    private val toggled = mutableListOf<MapLabelField>()
    private val toggledKinds = mutableListOf<AircraftKind>()
    private var showAllKindsClicks = 0

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
                onToggleAircraftKind = { toggledKinds.add(it) },
                onShowAllAircraftKinds = { showAllKindsClicks++ },
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

        // Injected touches don't reach rows inside the sheet's dialog window under Robolectric.
        // OnClick runs the row's own click handler, the one a tap triggers.
        composeRule.onNodeWithText("Altitude").performSemanticsAction(SemanticsActions.OnClick)

        toggled shouldBe listOf(MapLabelField.ALTITUDE)
    }

    private fun kindChip(kind: AircraftKind) = composeRule.onNodeWithTag(aircraftKindTag(kind))

    private fun allKindsChip() = composeRule.onNodeWithTag(AIRCRAFT_KINDS_ALL_TAG)

    @Test
    fun `without chosen kinds all kinds are shown`() {
        setSheet(NativeMapViewModel.Toggles())

        allKindsChip().assertIsSelected()
        AircraftKind.entries.forEach { kindChip(it).assertIsNotSelected() }
    }

    @Test
    fun `a chosen kind is selected instead of all kinds`() {
        setSheet(NativeMapViewModel.Toggles(kinds = setOf(AircraftKind.HELICOPTER)))

        kindChip(AircraftKind.HELICOPTER).assertIsSelected()
        allKindsChip().assertIsNotSelected()
        kindChip(AircraftKind.PLANE).assertIsNotSelected()
    }

    @Test
    fun `tapping a kind toggles it`() {
        setSheet(NativeMapViewModel.Toggles())

        kindChip(AircraftKind.GLIDER).performSemanticsAction(SemanticsActions.OnClick)

        toggledKinds shouldBe listOf(AircraftKind.GLIDER)
        showAllKindsClicks shouldBe 0
    }

    @Test
    fun `tapping all kinds shows all kinds`() {
        setSheet(NativeMapViewModel.Toggles(kinds = setOf(AircraftKind.HELICOPTER)))

        allKindsChip().performSemanticsAction(SemanticsActions.OnClick)

        showAllKindsClicks shouldBe 1
        toggledKinds shouldBe emptyList()
    }
}
