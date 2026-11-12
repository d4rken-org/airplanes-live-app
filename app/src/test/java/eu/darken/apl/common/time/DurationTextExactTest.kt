package eu.darken.apl.common.time

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DurationTextExactTest {

    @Test
    fun `under an hour is minutes`() {
        durationTextExact(Duration.ofMinutes(30), Locale.US) shouldBe "30 minutes"
    }

    @Test
    fun `whole hours are hours`() {
        durationTextExact(Duration.ofMinutes(2880), Locale.US) shouldBe "48 hours"
    }

    @Test
    fun `hours with a remainder keep the minutes`() {
        val text = durationTextExact(Duration.ofMinutes(90), Locale.US)
        text shouldContain "1 hour"
        text shouldContain "30 minutes"
    }
}
