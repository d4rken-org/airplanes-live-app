package eu.darken.apl.feeder.ui

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederObservation
import eu.darken.apl.feeder.core.FeederObservation.Status
import eu.darken.apl.feeder.core.server.FeederServerState
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.feeder.ui.FeederListViewModel.Header
import eu.darken.apl.feeder.ui.preview.mockFeeder
import eu.darken.apl.server.api.FeederMonitorEntry
import eu.darken.apl.server.api.FeederMonitorLatest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class FeederListHeaderTest : BaseTest() {

    private val now = Instant.parse("2026-11-01T00:00:00Z")

    private fun feeder(id: String, observation: FeederObservation?) =
        mockFeeder(id = id).copy(observation = observation)

    private fun answered(status: Status) = FeederObservation(checkedAt = now, status = status)

    private fun entry(
        status: String = "active",
        health: String = "online",
        pauseReason: String? = null,
        unknownReason: String? = null,
        rate: Double? = 5.0,
        lastSampleAt: Long? = null,
        lastSuccessAt: Long? = null,
        lastConnectedAt: Long? = null,
    ) = FeederMonitorEntry(
        feederId = "f",
        monitorId = "m",
        offlineAfterMinutes = 60,
        status = status,
        pauseReason = pauseReason,
        createdAt = 0,
        updatedAt = 0,
        health = health,
        unknownReason = unknownReason,
        lastSampleAt = lastSampleAt,
        lastSuccessAt = lastSuccessAt,
        lastConnectedAt = lastConnectedAt,
        latest = FeederMonitorLatest(msgsPerSecond = rate),
        eventSeq = 0,
    )

    private fun served(
        id: String,
        entry: FeederMonitorEntry?,
        refusal: FeederServerState.Refusal? = null,
        feederId: String? = id,
    ) = mockFeeder(id = id).copy(server = FeederServerView(feederId = feederId, entry = entry, refusal = refusal))

    private fun serverHeader(vararg feeders: Feeder, offline: Set<String> = emptySet()) =
        FeederListViewModel.headerOf(feeders.toList(), feeders.associate { it.id to (it.id in offline) })

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

    @Test
    fun `server mode is all online when every monitor is online with messages`() {
        serverHeader(served("a", entry()), served("b", entry())) shouldBe Header.ALL_ONLINE
    }

    @Test
    fun `server mode offline alert wins`() {
        serverHeader(
            served("a", entry(health = "offline")),
            served("b", null),
            offline = setOf("a"),
        ) shouldBe Header.SOME_OFFLINE
    }

    @Test
    fun `server mode paused or refused or unknown is unchecked`() {
        val online = served("ok", entry())
        serverHeader(online, served("a", entry(status = "paused", pauseReason = "lease"))) shouldBe Header.SOME_UNCHECKED
        serverHeader(online, served("a", entry(status = "paused", pauseReason = "over_limit"))) shouldBe Header.SOME_UNCHECKED
        serverHeader(online, served("a", null, refusal = FeederServerState.Refusal.SERVER_FULL)) shouldBe Header.SOME_UNCHECKED
        serverHeader(online, served("a", null, feederId = null)) shouldBe Header.SOME_UNCHECKED
        serverHeader(online, served("a", entry(health = "unknown", unknownReason = "upstream"))) shouldBe Header.SOME_UNCHECKED
        // Unchecked wins over not feeding
        serverHeader(
            served("b", entry(health = "offline")),
            served("a", entry(health = "unknown", unknownReason = "upstream")),
        ) shouldBe Header.SOME_UNCHECKED
    }

    private fun pending(lastSampleAt: Long? = null, lastSuccessAt: Long? = null, lastConnectedAt: Long? = null) =
        entry(
            health = "unknown",
            unknownReason = "pending",
            lastSampleAt = lastSampleAt,
            lastSuccessAt = lastSuccessAt,
            lastConnectedAt = lastConnectedAt,
        )

    @Test
    fun `server mode feeders without a result yet are waiting`() {
        val online = served("ok", entry())
        // Not checked yet
        serverHeader(online, served("a", pending())) shouldBe Header.SOME_WAITING
        // Checked, no data yet
        serverHeader(online, served("a", pending(lastSampleAt = 10, lastSuccessAt = 10))) shouldBe Header.SOME_WAITING
        // Connected at the latest success, no verdict stored yet
        serverHeader(
            online,
            served("a", pending(lastSampleAt = 10, lastSuccessAt = 10, lastConnectedAt = 10)),
        ) shouldBe Header.SOME_WAITING
        // No monitor yet
        serverHeader(online, served("a", null)) shouldBe Header.SOME_WAITING
    }

    @Test
    fun `server mode checks that never succeeded are unchecked`() {
        serverHeader(served("ok", entry()), served("a", pending(lastSampleAt = 10))) shouldBe Header.SOME_UNCHECKED
    }

    @Test
    fun `server mode unchecked wins over waiting`() {
        serverHeader(served("a", pending(lastSampleAt = 10)), served("b", pending())) shouldBe Header.SOME_UNCHECKED
    }

    @Test
    fun `server mode waiting wins over not feeding`() {
        serverHeader(served("a", entry(health = "offline")), served("b", pending())) shouldBe Header.SOME_WAITING
    }

    @Test
    fun `server mode offline without alert or online without messages is not feeding`() {
        val online = served("ok", entry())
        serverHeader(online, served("a", entry(health = "offline"))) shouldBe Header.SOME_NOT_FEEDING
        serverHeader(online, served("a", entry(rate = 0.0))) shouldBe Header.SOME_NOT_FEEDING
    }
}
