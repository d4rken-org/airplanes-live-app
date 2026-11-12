package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.server.FeederServerView.Status
import eu.darken.apl.server.api.FeederMonitorEntry
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederServerViewTest : BaseTest() {

    private val created = 1_000_000L

    private fun entry(
        health: String = "unknown",
        unknownReason: String? = "pending",
        updatedAt: Long = created,
        lastSampleAt: Long? = null,
        lastSuccessAt: Long? = null,
        lastConnectedAt: Long? = null,
    ) = FeederMonitorEntry(
        feederId = "f",
        monitorId = "m",
        offlineAfterMinutes = 60,
        status = "active",
        createdAt = created,
        updatedAt = updatedAt,
        health = health,
        unknownReason = unknownReason,
        lastSampleAt = lastSampleAt,
        lastSuccessAt = lastSuccessAt,
        lastConnectedAt = lastConnectedAt,
        eventSeq = 0,
    )

    private fun statusOf(entry: FeederMonitorEntry?) = FeederServerView(feederId = "f", entry = entry).status

    @Test
    fun `never sampled is not checked yet`() {
        statusOf(entry()) shouldBe Status.NOT_CHECKED_YET
    }

    @Test
    fun `samples only from before this monitor are not checked yet`() {
        statusOf(entry(lastSampleAt = created - 10, lastSuccessAt = created - 10)) shouldBe Status.NOT_CHECKED_YET
    }

    @Test
    fun `a successful check that never saw the feeder connected is checked without data`() {
        statusOf(entry(lastSampleAt = created + 10, lastSuccessAt = created + 10)) shouldBe Status.CHECKED_NO_DATA
    }

    @Test
    fun `a feeder last connected before the latest success is checked without data`() {
        statusOf(
            entry(lastSampleAt = created + 10, lastSuccessAt = created + 10, lastConnectedAt = created - 10)
        ) shouldBe Status.CHECKED_NO_DATA
    }

    @Test
    fun `sampled but never succeeded is checks failing`() {
        statusOf(entry(lastSampleAt = created)) shouldBe Status.CHECKS_FAILING
        statusOf(entry(lastSampleAt = created + 10, lastSuccessAt = created - 10)) shouldBe Status.CHECKS_FAILING
    }

    @Test
    fun `a failure after an earlier successful not connected check is checked without data`() {
        statusOf(entry(lastSampleAt = created + 20, lastSuccessAt = created + 10)) shouldBe Status.CHECKED_NO_DATA
    }

    @Test
    fun `a threshold edit does not move the reference`() {
        statusOf(
            entry(updatedAt = created + 100, lastSampleAt = created + 10, lastSuccessAt = created + 10)
        ) shouldBe Status.CHECKED_NO_DATA
    }

    @Test
    fun `online health is online`() {
        statusOf(
            entry(health = "online", unknownReason = null, lastSampleAt = created + 10, lastSuccessAt = created + 10)
        ) shouldBe Status.ONLINE
    }

    @Test
    fun `connected at the latest success without a verdict is waiting`() {
        statusOf(
            entry(lastSampleAt = created + 10, lastSuccessAt = created + 10, lastConnectedAt = created + 10)
        ) shouldBe Status.WAITING
    }

    @Test
    fun `no entry and no refusal is waiting`() {
        statusOf(null) shouldBe Status.WAITING
    }

    @Test
    fun `an unrecognised unknown reason is waiting`() {
        statusOf(entry(unknownReason = "something_new")) shouldBe Status.WAITING
    }
}
