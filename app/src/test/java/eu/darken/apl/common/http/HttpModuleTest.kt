package eu.darken.apl.common.http

import eu.darken.apl.common.BuildConfigWrap
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.concurrent.TimeUnit

class HttpModuleTest : BaseTest() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpModule: HttpModule

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        httpModule = HttpModule()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `userAgent returns correct user-agent string`() {
        val expectedUserAgent =
            "${BuildConfigWrap.APPLICATION_ID}/${BuildConfigWrap.VERSION_NAME} " +
                "(Android null; null; +https://github.com/d4rken-org/airplanes-live-app)"
        httpModule.userAgent() shouldBe expectedUserAgent
    }

    @Test
    fun `userAgent includes a contact url`() {
        // planespotters.net returns HTTP 403 unless the User-Agent carries a contact URL or email
        httpModule.userAgent() shouldContain "+https://github.com/d4rken-org/airplanes-live-app"
    }

    @Test
    fun `baseHttpClient sets correct user-agent header`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/").toString())
            .build()
        httpModule.baseHttpClient().newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        recordedRequest?.getHeader("User-Agent") shouldBe httpModule.userAgent()
    }
}
