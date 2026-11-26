package eu.darken.apl.server.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class ServerOutageTest : BaseTest() {

    private fun error(code: String, status: Int) = ServerApiException(code = code, status = status)

    @Test
    fun `the outage codes are outages`() {
        error(ServerCodes.SERVER_MAINTENANCE, 503).isServerOutage shouldBe true
        error(ServerCodes.SERVER_INCIDENT, 503).isServerOutage shouldBe true
        error(ServerCodes.SERVER_UNAVAILABLE, 503).isServerOutage shouldBe true
    }

    @Test
    fun `gateway errors without a problem body are outages`() {
        error("http_502", 502).isServerOutage shouldBe true
        error("http_503", 503).isServerOutage shouldBe true
        error("http_504", 504).isServerOutage shouldBe true
    }

    @Test
    fun `other errors are not outages`() {
        error("http_500", 500).isServerOutage shouldBe false
        error(ServerCodes.DATABASE_UNAVAILABLE, 503).isServerOutage shouldBe false
        error(ServerCodes.INVALID_REQUEST, 400).isServerOutage shouldBe false
    }

    @Test
    fun `an outage is not retried as the same request`() {
        error(ServerCodes.SERVER_MAINTENANCE, 503).isRetryableSameRequest shouldBe false
        error(ServerCodes.SERVER_INCIDENT, 503).isRetryableSameRequest shouldBe false
        error(ServerCodes.SERVER_UNAVAILABLE, 503).isRetryableSameRequest shouldBe false
    }
}
