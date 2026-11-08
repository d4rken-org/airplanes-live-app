package eu.darken.apl.watch.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class LocationAltitudeBandFieldsTest : BaseComposeTest() {

    private var input by mutableStateOf(AltitudeBandInput.empty(AltitudeBandUnit.FEET))

    private fun setFields() = composeRule.setContent {
        PreviewWrapper {
            LocationAltitudeBandFields(input = input, onInputChange = { input = it })
        }
    }

    @Test
    fun `a negative altitude shows its error and is invalid`() {
        setFields()

        composeRule.onNodeWithText("Min altitude").performTextInput("-5")

        composeRule.onNodeWithText("Can't be below 0").assertExists()
        input.isValid shouldBe false
    }

    @Test
    fun `a valid band shows no error`() {
        setFields()

        composeRule.onNodeWithText("Min altitude").performTextInput("1000")
        composeRule.onNodeWithText("Max altitude").performTextInput("10000")

        composeRule.onNodeWithText("Below the min altitude").assertDoesNotExist()
        input.band shouldBe AltitudeBand(1_000, 10_000)
    }
}
