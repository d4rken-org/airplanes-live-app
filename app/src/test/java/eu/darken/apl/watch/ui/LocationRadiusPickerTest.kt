package eu.darken.apl.watch.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import org.junit.Test
import testhelper.BaseComposeTest

class LocationRadiusPickerTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val radiusLabel get() = context.getString(R.string.watch_location_radius_label)
    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    private val texts = mutableMapOf<Float, String>()

    private fun setPicker(stops: List<Float>, radiusInMeters: Float) = composeRule.setContent {
        PreviewWrapper {
            (stops + radiusInMeters).forEach { texts[it] = radiusText(it) }
            LocationRadiusPicker(stops = stops, radiusInMeters = radiusInMeters, onRadiusChange = {})
        }
    }

    @Test
    fun `the slider has the first and last stop at its ends`() {
        setPicker(listOf(2_000f, 25_000f, 250_000f), radiusInMeters = 25_000f)

        composeRule.onNodeWithText(radiusLabel).assertExists()
        composeRule.onAllNodesWithText(texts.getValue(25_000f)).assertCountEquals(1)
        composeRule.onAllNodesWithText(texts.getValue(2_000f)).assertCountEquals(1)
        composeRule.onAllNodesWithText(texts.getValue(250_000f)).assertCountEquals(1)
        composeRule.onAllNodes(slider).assertCountEquals(1)
    }

    @Test
    fun `no stops shows only the radius`() {
        setPicker(emptyList(), radiusInMeters = 25_000f)

        composeRule.onNodeWithText(radiusLabel).assertExists()
        composeRule.onAllNodesWithText(texts.getValue(25_000f)).assertCountEquals(1)
        composeRule.onAllNodes(slider).assertCountEquals(0)
    }

    @Test
    fun `a single stop shows only the radius`() {
        setPicker(listOf(2_000f), radiusInMeters = 2_000f)

        composeRule.onNodeWithText(radiusLabel).assertExists()
        composeRule.onAllNodesWithText(texts.getValue(2_000f)).assertCountEquals(1)
        composeRule.onAllNodes(slider).assertCountEquals(0)
    }
}
