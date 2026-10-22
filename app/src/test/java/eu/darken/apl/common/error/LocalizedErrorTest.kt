package eu.darken.apl.common.error

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

    @Test
    fun `a known server code gets its own message`() {
        val error = ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429).localized(context)

        error.description.get(context) shouldBe context.getString(R.string.server_error_rate_limited_msg)
    }

    @Test
    fun `an unknown server code is named in the generic message`() {
        val error = ServerApiException(code = "http_418", status = 418).localized(context)

        error.description.get(context) shouldBe context.getString(R.string.server_error_generic_msg_x, "http_418")
    }

    @Test
    fun `an unknown exception keeps its message but not its class name`() {
        val error = IllegalStateException("disk on fire").localized(context)

        error.label.get(context) shouldBe context.getString(R.string.common_error_label)
        error.description.get(context) shouldContain "disk on fire"
        error.description.get(context) shouldNotContain "IllegalStateException"
    }
}
