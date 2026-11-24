package eu.darken.apl.watch.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class LocationAltitudeBandFieldsTest : BaseComposeTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val minField get() = hasSetTextAction() and hasText(context.getString(R.string.watch_location_altitude_min_label))
    private val maxField get() = hasSetTextAction() and hasText(context.getString(R.string.watch_location_altitude_max_label))
    private val anyAltitude get() = context.getString(R.string.watch_location_altitude_any)

    private var input by mutableStateOf(AltitudeBandInput.empty(AltitudeBandUnit.FEET))

    private fun setFields() = composeRule.setContent {
        PreviewWrapper {
            LocationAltitudeBandFields(input = input, onInputChange = { input = it })
        }
    }

    @Test
    fun `a negative altitude shows its error and is invalid`() {
        setFields()

        composeRule.onNode(minField).performTextInput("-5")

        composeRule.onNodeWithText("Can't be below 0").assertExists()
        composeRule.onNodeWithText(anyAltitude).assertDoesNotExist()
        input.isValid shouldBe false
    }

    @Test
    fun `a valid band shows no error`() {
        setFields()

        composeRule.onNode(minField).performTextInput("1000")
        composeRule.onNode(maxField).performTextInput("10000")

        composeRule.onNodeWithText("Below the min altitude").assertDoesNotExist()
        input.band shouldBe AltitudeBand(1_000, 10_000)
    }

    @Test
    fun `empty fields show no limit and sum up as any altitude`() {
        setFields()

        composeRule.onAllNodes(hasSetTextAction() and hasText(context.getString(R.string.watch_location_altitude_no_limit)))
            .assertCountEquals(2)
        composeRule.onAllNodesWithText(anyAltitude).assertCountEquals(1)
    }

    @Test
    fun `a typed band sums up capitalized`() {
        setFields()

        composeRule.onNode(minField).performTextInput("1000")

        input.band shouldBe AltitudeBand(1_000, null)
        composeRule.onNodeWithText(anyAltitude).assertDoesNotExist()
        composeRule.onNode(hasText("Above ", substring = true) and !hasSetTextAction()).assertExists()
    }

    @Test
    fun `an input replaced from outside shows its text`() {
        setFields()

        composeRule.onNode(minField).performTextInput("1000")
        composeRule.runOnIdle { input = AltitudeBandInput.of(AltitudeBand(2_000, 30_000), AltitudeBandUnit.FEET) }
        composeRule.waitForIdle()

        composeRule.onNode(minField).assert(hasText("2000"))
        composeRule.onNode(maxField).assert(hasText("30000"))
        input.band shouldBe AltitudeBand(2_000, 30_000)
    }
}
