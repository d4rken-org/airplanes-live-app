package eu.darken.apl.map.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.watch.ui.AltitudeBand
import eu.darken.apl.watch.ui.AltitudeBandInput
import eu.darken.apl.watch.ui.AltitudeBandUnit
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapAreaEditorTest : BaseComposeTest() {

    private val created = mutableListOf<Float>()
    private val bands = mutableListOf<AltitudeBandInput>()
    private var deleted = 0

    private fun setEditor(draft: NativeMapViewModel.AreaDraft, radiusInMeters: Float? = 25_000f, maxRadiusKm: Int = 250) =
        composeRule.setContent {
            PreviewWrapper {
                MapAreaEditor(
                    draft = draft,
                    stops = listOf(2_000f, 25_000f, 250_000f),
                    radiusInMeters = radiusInMeters,
                    maxRadiusKm = maxRadiusKm,
                    onLabelChange = {},
                    onRadiusChange = {},
                    onBandChange = { bands.add(it) },
                    onCancel = {},
                    onCreate = { created.add(it) },
                    onDelete = { deleted++ },
                )
            }
        }

    private fun draft(label: String = "Aachen, DE", saving: Boolean = false) = NativeMapViewModel.AreaDraft(
        session = 1,
        id = 2,
        latitude = 50.77,
        longitude = 6.08,
        label = label,
        saving = saving,
    )

    private fun original(
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        minAltitudeFt: Int? = null,
        maxAltitudeFt: Int? = null,
    ) = NativeMapViewModel.AreaDraft.Original(AreaCircle(latitude, longitude, radiusMeters), minAltitudeFt, maxAltitudeFt)

    @Test
    fun `add creates the watch with the radius shown`() {
        setEditor(draft())

        composeRule.onNodeWithText("Add").performScrollTo().assertIsEnabled().performClick()

        created shouldBe listOf(25_000f)
    }

    @Test
    fun `no label, no watch`() {
        setEditor(draft(label = " "))

        composeRule.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `a radius above the limit can't be added`() {
        setEditor(draft(), radiusInMeters = 250_000f, maxRadiusKm = 25)

        composeRule.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `an edited area keeps a radius above today's limit until it moves`() {
        setEditor(draft().copy(editing = "l1", original = original(50.77, 6.08, 250_000f)), radiusInMeters = 250_000f, maxRadiusKm = 25)

        composeRule.onNodeWithText("Change the watched area").assertExists()
        composeRule.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()

        created shouldBe listOf(250_000f)
    }

    @Test
    fun `an edited area's radius moved above the limit can't be saved`() {
        setEditor(draft().copy(editing = "l1", original = original(50.77, 6.08, 250_000f)), radiusInMeters = 100_000f, maxRadiusKm = 25)

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `an edited area's new label alone keeps its history`() {
        setEditor(draft().copy(editing = "l1", original = original(50.77, 6.08, 250_000f)), radiusInMeters = 250_000f)

        composeRule.onNodeWithText(RESET_WARNING).assertDoesNotExist()
    }

    @Test
    fun `an edited area that moves warns that its history starts over`() {
        setEditor(draft().copy(editing = "l1", original = original(51.0, 7.0, 25_000f)), radiusInMeters = 25_000f)

        composeRule.onNodeWithText(RESET_WARNING).assertExists()
    }

    @Test
    fun `a new area's altitude fields start empty and report what is typed`() {
        setEditor(draft())

        composeRule.onNodeWithText("Max altitude").assertExists()
        composeRule.onNodeWithText("Min altitude").performTextInput("1000")

        bands.last().band shouldBe AltitudeBand(1_000, null)
    }

    @Test
    fun `an edited area's altitude fields start from its band`() {
        setEditor(draft().copy(editing = "l1", original = original(50.77, 6.08, 25_000f, 1_000, 10_000)))

        composeRule.onNodeWithText("1000").assertExists()
        composeRule.onNodeWithText("10000").assertExists()
        composeRule.onNodeWithText(RESET_WARNING).assertDoesNotExist()
    }

    @Test
    fun `an invalid altitude range can't be added`() {
        setEditor(draft().copy(bandInput = AltitudeBandInput.empty(AltitudeBandUnit.FEET).withMinText("-5")))

        composeRule.onNodeWithText("Can't be below 0").assertExists()
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `an edited area's new altitude range warns that its history starts over`() {
        val band = AltitudeBandInput.of(AltitudeBand(1_000, 10_000), AltitudeBandUnit.FEET).withMaxText("20000")
        setEditor(
            draft().copy(editing = "l1", original = original(50.77, 6.08, 25_000f, 1_000, 10_000), bandInput = band)
        )

        composeRule.onNodeWithText(RESET_WARNING).assertExists()
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `an edited area is removed only after confirming`() {
        setEditor(draft().copy(editing = "l1", original = original(50.77, 6.08, 25_000f)))

        composeRule.onNodeWithText("Remove").performScrollTo().performClick()
        deleted shouldBe 0
        composeRule.onNodeWithText("Delete this watch?").assertExists()
        composeRule.onAllNodesWithText("Remove").onLast().performClick()

        deleted shouldBe 1
    }

    @Test
    fun `a new area has no remove action`() {
        setEditor(draft())

        composeRule.onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun `saving holds every input`() {
        setEditor(draft(saving = true))

        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.onNodeWithText("Label").assertIsNotEnabled()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsNotEnabled()
    }

    companion object {
        private const val RESET_WARNING = "Changing the area or the altitude range starts this watch's history over."
    }
}
