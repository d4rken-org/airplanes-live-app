package eu.darken.apl.feeder.ui

import eu.darken.apl.feeder.core.FeederObservation
import eu.darken.apl.feeder.core.FeederObservation.Status
import eu.darken.apl.feeder.ui.FeederListViewModel.Header
import eu.darken.apl.feeder.ui.preview.mockFeeder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class FeederListHeaderTest : BaseTest() {

    private val now = Instant.parse("2026-11-01T00:00:00Z")

    private fun feeder(id: String, observation: FeederObservation?) =
        mockFeeder(id = id).copy(observation = observation)

    private fun answered(status: Status) = FeederObservation(checkedAt = now, status = status)

    @Test
    fun `all online only when every feeder's latest answer was feeding`() {
        val feeders = listOf(feeder("a", answered(Status.FEEDING)), feeder("b", answered(Status.FEEDING)))

        FeederListViewModel.headerOf(feeders, mapOf("a" to false, "b" to false)) shouldBe Header.ALL_ONLINE
    }

    @Test
    fun `an inactive feeder below the offline timeout is not online`() {
        val feeders = listOf(feeder("a", answered(Status.FEEDING)), feeder("b", answered(Status.NOT_SEEN)))

        FeederListViewModel.headerOf(feeders, mapOf("a" to false, "b" to false)) shouldBe Header.SOME_NOT_FEEDING
    }

    @Test
    fun `a failing or missing check is unchecked`() {
        val failing = listOf(feeder("a", answered(Status.FEEDING).withFailure(now.plusSeconds(60))))
        val missing = listOf(feeder("a", null))

        FeederListViewModel.headerOf(failing, mapOf("a" to false)) shouldBe Header.SOME_UNCHECKED
        FeederListViewModel.headerOf(missing, mapOf("a" to false)) shouldBe Header.SOME_UNCHECKED
    }

    @Test
    fun `offline wins over everything else`() {
        val feeders = listOf(feeder("a", answered(Status.NOT_SEEN)), feeder("b", null))

        FeederListViewModel.headerOf(feeders, mapOf("a" to true, "b" to false)) shouldBe Header.SOME_OFFLINE
    }
}
