package eu.darken.apl.map.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.map.core.MapLabelField
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.ui.NativeMapViewModel.OverlayOption
import eu.darken.apl.server.api.OverlayDto
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapOptionsSheetTest : BaseComposeTest() {

    private val toggled = mutableListOf<MapLabelField>()
    private var labelsClicks = 0
    private val toggledOverlays = mutableListOf<String>()
    private var upgradeClicks = 0

    private fun setSheet(
        toggles: NativeMapViewModel.Toggles,
        overlays: List<OverlayOption> = emptyList(),
    ) = composeRule.setContent {
        PreviewWrapper {
            MapOptionsSheet(
                toggles = toggles,
                overlays = overlays,
                onToggleLabels = { labelsClicks++ },
                onToggleLabelField = { toggled.add(it) },
                onToggleAllTracks = {},
                onToggleMilitaryHighlight = {},
                onToggleWatchHighlight = {},
                onToggleOverlay = { toggledOverlays.add(it) },
                onUpgrade = { upgradeClicks++ },
                onToggleMultiSelect = {},
                onOpenWebsite = {},
                onResetMap = {},
                onDismiss = {},
            )
        }
    }

    private fun fieldSwitch(field: MapLabelField) =
        composeRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(labelFieldTag(field))))

    private fun labelsSwitch() = composeRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(LABELS_SWITCH_TAG)))

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
    fun `fields can not be switched while labels are off`() {
        setSheet(NativeMapViewModel.Toggles(labels = false))

        labelsSwitch().assertIsEnabled()
        MapLabelField.entries.forEach { fieldSwitch(it).assertIsNotEnabled() }
    }

    @Test
    fun `fields that could be switched off are disabled while labels are off`() {
        setSheet(
            NativeMapViewModel.Toggles(
                labels = false,
                labelFields = MapLabelFields(callsign = true, type = true),
            ),
        )

        fieldSwitch(MapLabelField.CALLSIGN).assertIsNotEnabled()
        fieldSwitch(MapLabelField.TYPE).assertIsNotEnabled()
    }

    @Test
    fun `tapping show labels toggles labels`() {
        setSheet(NativeMapViewModel.Toggles())

        composeRule.onNodeWithTag(LABELS_SWITCH_TAG).performSemanticsAction(SemanticsActions.OnClick)

        labelsClicks shouldBe 1
        toggled shouldBe emptyList()
    }

    @Test
    fun `tapping a field toggles it`() {
        setSheet(NativeMapViewModel.Toggles())

        // Injected touches don't reach rows inside the sheet's dialog window under Robolectric.
        // OnClick runs the row's own click handler, the one a tap triggers.
        composeRule.onNodeWithText("Altitude").performSemanticsAction(SemanticsActions.OnClick)

        toggled shouldBe listOf(MapLabelField.ALTITUDE)
    }

    @Test
    fun `the sheet has no filters`() {
        setSheet(NativeMapViewModel.Toggles())

        composeRule.onNodeWithText("Filter").assertDoesNotExist()
        composeRule.onNodeWithText("Military only").assertDoesNotExist()
        composeRule.onNodeWithText("Aircraft kinds").assertDoesNotExist()
        composeRule.onNodeWithText("Only selected & pinned").assertDoesNotExist()
    }

    private fun overlay(
        id: String,
        title: String = id,
        checked: Boolean = false,
        locked: Boolean = false,
        available: Boolean = true,
    ) = OverlayOption(
        id = id,
        title = title,
        coverage = listOf("United States"),
        checked = checked,
        locked = locked,
        available = available,
    )

    private fun overlayRow(id: String) = composeRule.onNodeWithTag(overlayTag(id))

    private fun overlaySwitch(id: String) =
        composeRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(overlayTag(id))))

    @Test
    fun `without overlays there is no overlay section`() {
        setSheet(NativeMapViewModel.Toggles())

        composeRule.onNodeWithText("Overlays").assertDoesNotExist()
    }

    @Test
    fun `tapping a locked overlay opens the upgrade instead of switching it`() {
        setSheet(NativeMapViewModel.Toggles(), listOf(overlay(OverlayDto.ID_RADAR, locked = true)))

        overlayRow(OverlayDto.ID_RADAR).performSemanticsAction(SemanticsActions.OnClick)
        overlaySwitch(OverlayDto.ID_RADAR).assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)

        upgradeClicks shouldBe 2
        toggledOverlays shouldBe emptyList()
    }

    @Test
    fun `a free tier switches airspace while the locked overlays open the upgrade`() {
        setSheet(
            NativeMapViewModel.Toggles(),
            listOf(
                overlay(OverlayDto.ID_RADAR, locked = true),
                overlay(OverlayDto.ID_AIRSPACE),
                overlay(OverlayDto.ID_MILITARY, locked = true),
            ),
        )

        overlayRow(OverlayDto.ID_AIRSPACE).performSemanticsAction(SemanticsActions.OnClick)
        overlayRow(OverlayDto.ID_MILITARY).performSemanticsAction(SemanticsActions.OnClick)

        toggledOverlays shouldBe listOf(OverlayDto.ID_AIRSPACE)
        upgradeClicks shouldBe 1
    }

    @Test
    fun `an overlay without data can not be switched`() {
        setSheet(NativeMapViewModel.Toggles(), listOf(overlay(OverlayDto.ID_RESTRICTIONS, checked = true, available = false)))

        overlaySwitch(OverlayDto.ID_RESTRICTIONS).assertIsNotEnabled()
        overlayRow(OverlayDto.ID_RESTRICTIONS).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        toggledOverlays shouldBe emptyList()
        upgradeClicks shouldBe 0
    }

    @Test
    fun `tapping an available overlay switches it`() {
        setSheet(NativeMapViewModel.Toggles(), listOf(overlay(OverlayDto.ID_MILITARY)))

        overlayRow(OverlayDto.ID_MILITARY).performSemanticsAction(SemanticsActions.OnClick)

        toggledOverlays shouldBe listOf(OverlayDto.ID_MILITARY)
        upgradeClicks shouldBe 0
    }

    @Test
    fun `an overlay the app doesn't know shows the server's title`() {
        setSheet(NativeMapViewModel.Toggles(), listOf(overlay("volcanic_ash", title = "Volcanic ash")))

        composeRule.onNodeWithText("Volcanic ash").assertExists()
    }
}
