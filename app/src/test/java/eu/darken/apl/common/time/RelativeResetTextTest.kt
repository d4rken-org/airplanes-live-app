package eu.darken.apl.common.time

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class RelativeResetTextTest {

    // Years away from the device clock, a text following System.currentTimeMillis() would be far off
    private val serverNow = Instant.parse("2020-01-01T00:00:00Z")

    private fun text(secondsAhead: Long) =
        relativeResetText(serverNow.plusSeconds(secondsAhead), serverNow.toEpochMilli(), Locale.US)

    @Test
    fun `hours relative to the server's now, lowercase for the middle of a sentence`() {
        text(5 * 3600) shouldBe "in 5 hours"
    }

    @Test
    fun `under an hour is minutes`() {
        text(45 * 60) shouldBe "in 45 minutes"
    }

    @Test
    fun `a day or more is days`() {
        text(2 * 86400) shouldBe "in 2 days"
    }

    @Test
    fun `a reset already past is still ahead by a minute`() {
        text(-3600) shouldBe "in 1 minute"
    }
}
