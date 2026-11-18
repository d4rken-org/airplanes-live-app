package eu.darken.apl.feeder.ui.actions

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.server.FeederProblemCategory
import eu.darken.apl.feeder.core.server.problemCategories
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class FeederProblemNotificationsTest : BaseComposeTest() {

    private val toggled = mutableListOf<Pair<FeederProblemCategory, Boolean>>()

    private fun setSection(categories: Set<FeederProblemCategory>) = composeRule.setContent {
        PreviewWrapper {
            FeederProblemNotificationsSection(
                categories = categories,
                onToggle = { category, enabled -> toggled += category to enabled },
            )
        }
    }

    @Test
    fun `a new feeder's categories show message rate and connection on and MLAT off`() {
        setSection(FeederConfig.newFeeder(RECEIVER_ID).problemCategories)

        composeRule.onNodeWithText(MESSAGE_RATE).assertIsOn()
        composeRule.onNodeWithText(MLAT).assertIsOff()
        composeRule.onNodeWithText(CONNECTION).assertIsOn()
    }

    @Test
    fun `tapping an off switch reports its category switched on`() {
        setSection(setOf(FeederProblemCategory.MESSAGE_RATE, FeederProblemCategory.CONNECTION))

        composeRule.onNodeWithText(MLAT).performClick()

        toggled shouldBe listOf(FeederProblemCategory.MLAT to true)
    }

    @Test
    fun `tapping an on switch reports its category switched off`() {
        setSection(setOf(FeederProblemCategory.MESSAGE_RATE, FeederProblemCategory.CONNECTION))

        composeRule.onNodeWithText(CONNECTION).performClick()

        toggled shouldBe listOf(FeederProblemCategory.CONNECTION to false)
    }

    companion object {
        private const val RECEIVER_ID = "0a1b2c3d-0000-4000-8000-000000000001"
        private const val MESSAGE_RATE = "Notify about message rate drops"
        private const val MLAT = "Notify about MLAT problems"
        private const val CONNECTION = "Notify about connection problems"
    }
}
