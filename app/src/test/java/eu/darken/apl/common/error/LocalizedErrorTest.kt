package eu.darken.apl.common.error

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LocalizedErrorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `an app update refusal explains the update instead of naming the exception`() {
        val error = ServerApiException(code = ServerCodes.APP_UPDATE_REQUIRED, status = 403).localized(context)

        error.label.get(context) shouldBe context.getString(R.string.update_prompt_required_title)
        error.description.get(context) shouldBe context.getString(R.string.update_required_inline_msg)
    }
}
