package eu.darken.apl.ar.ui

import eu.darken.apl.main.core.AircraftRepo.ViewingState
import eu.darken.apl.server.api.ServerApiException
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.IOException

class ArViewModelStaleTest : BaseTest() {

    private fun snapshot(error: Throwable?) = ViewingState.Snapshot(value = mockk(relaxed = true), error = error)

    @Test
    fun `a failed poll leaves the positions aging`() {
        ArViewModel.isStale(snapshot(IOException("timeout"))) shouldBe true
        ArViewModel.isStale(snapshot(ServerApiException(code = "internal", status = 500))) shouldBe true
    }

    @Test
    fun `a rate limit does not`() {
        ArViewModel.isStale(snapshot(ServerApiException(code = "rate_limited", status = 429))) shouldBe false
    }

    @Test
    fun `a successful poll or no snapshot is not stale`() {
        ArViewModel.isStale(snapshot(null)) shouldBe false
        ArViewModel.isStale(ViewingState.Waiting(ViewingState.Reason.Offline)) shouldBe false
        ArViewModel.isStale(null) shouldBe false
    }
}
