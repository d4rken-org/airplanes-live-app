package eu.darken.apl.feeder.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.feeder.core.FeederRefreshFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FeederRefreshExceptionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    private fun description(error: Throwable): String =
        (FeederRefreshException.wrap(error) as FeederRefreshException).getLocalizedError(context).description.get(context)

    @Test
    fun `network and HTTP failures are wrapped with their cause`() {
        val io = IOException("reset")
        FeederRefreshException.wrap(io).apply {
            shouldBeInstanceOf<FeederRefreshException>()
            cause shouldBeSameInstanceAs io
        }
        val http = httpError(503)
        FeederRefreshException.wrap(http).apply {
            shouldBeInstanceOf<FeederRefreshException>()
            cause shouldBeSameInstanceAs http
        }
    }

    @Test
    fun `anything else passes through`() {
        val error = IllegalStateException("bug")
        FeederRefreshException.wrap(error) shouldBeSameInstanceAs error
    }

    @Test
    fun `an HTTP failure names its status code`() {
        description(httpError(503)) shouldBe context.getString(R.string.feeder_refresh_failed_http_x, 503)
        description(httpError(503)) shouldContain "503"
    }

    @Test
    fun `other failures get the generic refresh message`() {
        val expected = context.getString(R.string.feeder_refresh_failed_msg)
        description(UnknownHostException("example.com")) shouldBe expected
        description(SocketTimeoutException("timeout")) shouldBe expected
        description(IOException("reset")) shouldBe expected
    }

    @Test
    fun `some feeders failing says how many and that the rest updated`() {
        val failure = FeederRefreshFailure(failed = mapOf("b" to httpError(400)), total = 3)

        description(failure) shouldBe context.getString(R.string.feeder_refresh_failed_some_x_of_y, 1, 3)
    }

    @Test
    fun `every feeder failing reads like the failure itself`() {
        val io = IOException("reset")
        val failure = FeederRefreshFailure(failed = mapOf("a" to io), total = 1)

        FeederRefreshException.wrap(failure).cause shouldBeSameInstanceAs io
        description(failure) shouldBe context.getString(R.string.feeder_refresh_failed_msg)
    }

    @Test
    fun `the title stays the refresh title`() {
        (FeederRefreshException.wrap(IOException()) as FeederRefreshException).getLocalizedError(context)
            .label.get(context) shouldBe context.getString(R.string.feeder_refresh_failed_title)
    }
}
