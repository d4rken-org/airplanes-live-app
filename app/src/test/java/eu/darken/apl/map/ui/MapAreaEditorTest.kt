package eu.darken.apl.map.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapAreaEditorTest : BaseComposeTest() {

    private val created = mutableListOf<Float>()

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
                    onCancel = {},
                    onCreate = { created.add(it) },
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

    @Test
    fun `add creates the watch with the radius shown`() {
        setEditor(draft())

        composeRule.onNodeWithText("Add").assertIsEnabled().performClick()

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
    fun `saving holds every input`() {
        setEditor(draft(saving = true))

        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.onNodeWithText("Label").assertIsNotEnabled()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsNotEnabled()
    }
}
