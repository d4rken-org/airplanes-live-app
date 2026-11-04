package eu.darken.apl.feeder.core.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import testhelper.BaseTest
import java.io.IOException

class FeederEndpointTest : BaseTest() {

    @Test
    fun `an ID the API refuses leaves the others their results`() = runTest {
        val refused = HttpException(Response.error<Any>(400, "".toResponseBody(null)))

        val results = FeederEndpoint.fetchEach(listOf("a", "b", "c")) { id ->
            if (id == "b") throw refused else "infos-$id"
        }

        results["a"] shouldBe Result.success("infos-a")
        results["b"] shouldBe Result.failure(refused)
        results["c"] shouldBe Result.success("infos-c")
    }

    @Test
    fun `a network failure fails the remaining IDs without asking for them`() = runTest {
        val timeout = IOException("timeout")
        val asked = mutableListOf<String>()

        val results = FeederEndpoint.fetchEach(listOf("a", "b", "c")) { id ->
            asked += id
            if (id == "b") throw timeout else "infos-$id"
        }

        asked shouldBe listOf("a", "b")
        results["a"] shouldBe Result.success("infos-a")
        results["b"] shouldBe Result.failure(timeout)
        results["c"] shouldBe Result.failure(timeout)
    }

    @Test
    fun `cancellation is not swallowed as a failed ID`() = runTest {
        shouldThrow<CancellationException> {
            FeederEndpoint.fetchEach(listOf("a")) { throw CancellationException("gone") }
        }
    }
}
