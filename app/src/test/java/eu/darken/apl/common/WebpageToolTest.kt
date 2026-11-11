package eu.darken.apl.common

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WebpageToolTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val tool = WebpageTool(application)

    @Test
    fun `an upper case scheme is lowered and the rest kept as written`() {
        tool.open("HTTPS://Example.COM/Path?Q=A")

        val started = shadowOf(application).nextStartedActivity.shouldNotBeNull()
        started.action shouldBe Intent.ACTION_VIEW
        started.data.toString() shouldBe "https://Example.COM/Path?Q=A"
        (started.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
    }

    @Test
    fun `a lower case link is opened unchanged`() {
        tool.open("https://en.wikipedia.org/wiki/Frankfurt_Airport")

        val started = shadowOf(application).nextStartedActivity.shouldNotBeNull()
        started.action shouldBe Intent.ACTION_VIEW
        started.data.toString() shouldBe "https://en.wikipedia.org/wiki/Frankfurt_Airport"
    }
}
