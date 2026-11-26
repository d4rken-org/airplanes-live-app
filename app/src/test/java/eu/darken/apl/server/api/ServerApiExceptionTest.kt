package eu.darken.apl.server.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.server.status.formatServerStatusUntil
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ServerApiExceptionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun description(code: String, status: Int): String =
        ServerApiException(code = code, status = status).getLocalizedError(context).description.get(context)

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun outageDescription(code: String, until: Instant?): String =
        ServerApiException(code = code, status = 503, until = until).getLocalizedError(context).description.get(context)

    // Days ahead, so the text has the date either way and the clock moving on between two calls can't change it
    private val laterUntil: Instant = Instant.now().plus(Duration.ofDays(3))

    private fun untilText(until: Instant): String = formatServerStatusUntil(
        until = until,
        now = Instant.now(),
        zone = ZoneId.systemDefault(),
        locale = context.resources.configuration.locales[0],
    )!!

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
    fun `maintenance with an expected end names it`() {
        outageDescription(ServerCodes.SERVER_MAINTENANCE, laterUntil) shouldBe
            string(R.string.server_status_maintenance_until_msg_x, untilText(laterUntil))
    }

    @Test
    fun `maintenance without an expected end is back shortly`() {
        outageDescription(ServerCodes.SERVER_MAINTENANCE, null) shouldBe string(R.string.server_status_maintenance_msg)
    }

    @Test
    fun `maintenance past its expected end no longer names it`() {
        outageDescription(ServerCodes.SERVER_MAINTENANCE, Instant.now().minus(Duration.ofHours(1))) shouldBe
            string(R.string.server_status_maintenance_msg)
    }

    @Test
    fun `an incident with an expected end names it`() {
        outageDescription(ServerCodes.SERVER_INCIDENT, laterUntil) shouldBe
            string(R.string.server_status_incident_until_msg_x, untilText(laterUntil))
    }

    @Test
    fun `an incident without an expected end is being worked on`() {
        outageDescription(ServerCodes.SERVER_INCIDENT, null) shouldBe string(R.string.server_status_incident_msg)
    }

    @Test
    fun `an unavailable server is having trouble`() {
        outageDescription(ServerCodes.SERVER_UNAVAILABLE, null) shouldBe string(R.string.server_error_unavailable_msg)
    }

    @Test
    fun `outages keep the server error title`() {
        ServerApiException(code = ServerCodes.SERVER_MAINTENANCE, status = 503).getLocalizedError(context)
            .label.get(context) shouldBe string(R.string.server_error_title)
    }

    @Test
    fun `every server error has the server error title`() {
        ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429).getLocalizedError(context)
            .label.get(context) shouldBe string(R.string.server_error_title)
    }
}
