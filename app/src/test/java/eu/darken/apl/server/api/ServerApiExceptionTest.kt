package eu.darken.apl.server.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ServerApiExceptionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun description(code: String, status: Int): String =
        ServerApiException(code = code, status = status).getLocalizedError(context).description.get(context)

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    @Test
    fun `a revoked installation says so`() {
        description(ServerCodes.INSTALLATION_REVOKED, 403) shouldBe string(R.string.server_error_revoked_msg)
    }

    @Test
    fun `a revoked token is not a revoked installation`() {
        description(ServerCodes.REVOKED, 401).apply {
            this shouldNotBe string(R.string.server_error_revoked_msg)
            this shouldBe string(R.string.server_error_generic_msg_x, ServerCodes.REVOKED)
        }
        description(ServerCodes.REVOKED, 503) shouldBe string(R.string.server_error_unavailable_msg)
    }

    @Test
    fun `a restricted installation`() {
        description(ServerCodes.INSTALLATION_RESTRICTED, 403) shouldBe string(R.string.upgrade_restricted_msg)
    }

    @Test
    fun `a feature outside the tier`() {
        description(ServerCodes.TIER_RESTRICTED, 200) shouldBe string(R.string.server_error_tier_restricted_msg)
    }

    @Test
    fun `a used up allowance`() {
        description(ServerCodes.DAILY_ALLOWANCE_EXHAUSTED, 429) shouldBe string(R.string.server_error_allowance_exhausted_msg)
        description(ServerCodes.BUDGET_EXHAUSTED, 200) shouldBe string(R.string.server_error_allowance_exhausted_msg)
    }

    @Test
    fun `a rate limit`() {
        description(ServerCodes.INSTALLATION_RATE_EXCEEDED, 429) shouldBe string(R.string.server_error_rate_limited_msg)
        description(ServerCodes.WATCH_MUTATION_BUDGET_EXCEEDED, 429) shouldBe string(R.string.server_error_rate_limited_msg)
    }

    @Test
    fun `the stored watch limit`() {
        description(ServerCodes.STORED_WATCH_LIMIT, 409) shouldBe string(R.string.watch_limit_stored_reached_unnumbered_msg)
    }

    @Test
    fun `a feeder link refusal`() {
        description(ServerCodes.FEEDER_NETWORK_MISMATCH, 403) shouldBe string(R.string.feeder_link_error_network_mismatch)
    }

    @Test
    fun `a known outage code`() {
        description(ServerCodes.UPSTREAM_UNAVAILABLE, 502) shouldBe string(R.string.server_error_unavailable_msg)
    }

    @Test
    fun `an unknown code is an outage when the status is a server error`() {
        description("http_503", 503) shouldBe string(R.string.server_error_unavailable_msg)
    }

    @Test
    fun `an unknown code with a client error status is named`() {
        description("http_404", 404) shouldBe string(R.string.server_error_generic_msg_x, "http_404")
    }

    @Test
    fun `every server error has the server error title`() {
        ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429).getLocalizedError(context)
            .label.get(context) shouldBe string(R.string.server_error_title)
    }
}
