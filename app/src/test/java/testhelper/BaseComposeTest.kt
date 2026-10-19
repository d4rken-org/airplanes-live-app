package testhelper

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import eu.darken.apl.common.debug.logging.Logging
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.logging.JUnitLogger

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
abstract class BaseComposeTest {

    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    init {
        Logging.clearAll()
        Logging.install(JUnitLogger())
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun afterClass() {
            unmockkAll()
            Logging.clearAll()
        }
    }
}
