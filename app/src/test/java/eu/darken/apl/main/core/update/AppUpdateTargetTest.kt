package eu.darken.apl.main.core.update

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.WebpageTool
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppUpdateTargetTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val webpageTool = mockk<WebpageTool>(relaxed = true)

    private fun target(flavor: BuildConfigWrap.Flavor) = AppUpdateTarget(
        context = application,
        webpageTool = webpageTool,
        flavor = flavor,
        applicationId = APP_ID,
    )

    @Test
    fun `foss opens the latest github release`() {
        target(BuildConfigWrap.Flavor.FOSS).open()

        verify(exactly = 1) { webpageTool.open("https://github.com/d4rken-org/airplanes-live-app/releases/latest") }
        shadowOf(application).nextStartedActivity.shouldBeNull()
    }

    @Test
    fun `gplay opens the store app`() {
        target(BuildConfigWrap.Flavor.GPLAY).open()

        val started = shadowOf(application).nextStartedActivity.shouldNotBeNull()
        started.action shouldBe Intent.ACTION_VIEW
        started.data.toString() shouldBe "market://details?id=$APP_ID"
        (started.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
        verify(exactly = 0) { webpageTool.open(any()) }
    }

    @Test
    fun `gplay without a store app opens the store website`() {
        // No activity resolves market:// in the test environment, this makes starting one fail like on a device
        shadowOf(application).checkActivities(true)

        target(BuildConfigWrap.Flavor.GPLAY).open()

        verify(exactly = 1) { webpageTool.open("https://play.google.com/store/apps/details?id=$APP_ID") }
        shadowOf(application).nextStartedActivity.shouldBeNull()
    }

    companion object {
        private const val APP_ID = "eu.darken.apl"
    }
}
