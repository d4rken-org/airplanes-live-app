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

    private fun setFields(onInputChange: (AltitudeBandInput) -> Unit = { input = it }) = composeRule.setContent {
        PreviewWrapper {
            LocationAltitudeBandFields(input = input, onInputChange = onInputChange)
        }
    }

    @Test
    fun `a negative altitude shows its error and is invalid`() {
        setFields()

        composeRule.onNode(minField).performTextInput("-5")

        composeRule.onNodeWithText("Can't be below 0").assertExists()
        composeRule.onNodeWithText(anyAltitude).assertDoesNotExist()
        composeRule.runOnIdle { input.isValid shouldBe false }
    }

    @Test
    fun `a valid band shows no error`() {
        setFields()

        composeRule.onNode(minField).performTextInput("1000")
        composeRule.onNode(maxField).performTextInput("10000")

        composeRule.onNodeWithText("Below the min altitude").assertDoesNotExist()
        composeRule.runOnIdle { input.band shouldBe AltitudeBand(1_000, 10_000) }
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

        composeRule.runOnIdle { input.band shouldBe AltitudeBand(1_000, null) }
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
        composeRule.runOnIdle { input.band shouldBe AltitudeBand(2_000, 30_000) }
    }

    @Test
    fun `a late echo of an earlier edit keeps the newer text`() {
        val reported = mutableListOf<AltitudeBandInput>()
        // Holds back the echo, so the test decides when each reported text comes back
        setFields { reported.add(it) }

        composeRule.onNode(minField).performTextInput("1")
        composeRule.onNode(minField).performTextInput("2")
        composeRule.runOnIdle { reported.map { it.min.text } shouldBe listOf("1", "12") }

        composeRule.runOnIdle { input = reported[0] }
        composeRule.waitForIdle()
        composeRule.onNode(minField).assert(hasText("12"))

        composeRule.runOnIdle { input = reported[1] }
        composeRule.waitForIdle()
        composeRule.onNode(minField).assert(hasText("12"))
        composeRule.runOnIdle { reported.size shouldBe 2 }
    }

    @Test
    fun `an outside change after pending edits replaces the field`() {
        val reported = mutableListOf<AltitudeBandInput>()
        setFields { reported.add(it) }

        composeRule.onNode(minField).performTextInput("1")
        composeRule.onNode(minField).performTextInput("2")
        composeRule.runOnIdle { input = AltitudeBandInput.of(AltitudeBand(5_000, null), AltitudeBandUnit.FEET) }
        composeRule.waitForIdle()

        composeRule.onNode(minField).assert(hasText("5000"))
        composeRule.runOnIdle { reported.map { it.min.text } shouldBe listOf("1", "12") }
    }
}
