package eu.darken.apl.map.ui

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest

class MapTopBarTest : BaseComposeTest() {

    private var settingsClicks = 0
    private var tierClicks = 0

    private val settingsLabel: String
        get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.label_settings)

    private fun setBar() = composeRule.setContent {
        PreviewWrapper {
            MapTopBar(
                isPro = false,
                tagline = "Somewhere, a plane is turning left",
                onTierClick = { tierClicks++ },
                onSettingsClick = { settingsClicks++ },
            )
        }
    }

    @Test
    fun `the settings gear is a button`() {
        setBar()

        composeRule.onNodeWithContentDescription(settingsLabel).assertHasClickAction()
    }

    @Test
    fun `the settings gear opens only the settings`() {
        setBar()

        composeRule.onNodeWithContentDescription(settingsLabel).performClick()

        settingsClicks shouldBe 1
        tierClicks shouldBe 0
    }
}
