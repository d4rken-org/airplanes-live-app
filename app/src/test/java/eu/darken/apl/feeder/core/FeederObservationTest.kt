package eu.darken.apl.feeder.core

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.FeederObservation.Status
import eu.darken.apl.feeder.core.api.FeedInfos
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant
import java.util.UUID

class FeederObservationTest : BaseTest() {

    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val timeout = Duration.ofHours(48)

    private fun beast(rate: Double) = FeedInfos.Beast(
        uuid = UUID.randomUUID(),
        avgKBitsPerSecond = 1.0,
        connTime = 10,
        messageRate = rate,
        positionRate = 0.0,
        recentRtt = 0,
        positions = 0,
    )

    private fun infos(vararg rates: Double) = FeedInfos(beast = rates.map { beast(it) }, mlat = emptyList())

    @Test
    fun `any client with messages means feeding`() {
        FeederObservation().withAnswer(infos(0.0, 12.5), t0).status shouldBe Status.FEEDING
    }

    @Test
    fun `clients without messages mean idle, no clients mean not seen`() {
        FeederObservation().withAnswer(infos(0.0, 0.0), t0).status shouldBe Status.IDLE
        FeederObservation().withAnswer(infos(), t0).status shouldBe Status.NOT_SEEN
    }

    @Test
    fun `an empty answer after an idle one is not seen`() {
        FeederObservation()
            .withAnswer(infos(0.0), t0)
            .withAnswer(infos(), t0.plusSeconds(60))
            .status shouldBe Status.NOT_SEEN
    }

    @Test
    fun `last feeding time survives inactive answers, inactive start is the first one`() {
        val fed = FeederObservation().withAnswer(infos(5.0), t0)
        val later = fed
            .withAnswer(infos(), t0.plusSeconds(3600))
            .withAnswer(infos(0.0), t0.plusSeconds(7200))

        later.lastFeedingAt shouldBe t0
        later.inactiveSince shouldBe t0.plusSeconds(3600)
    }

    @Test
    fun `feeding again clears the inactive start`() {
        FeederObservation()
            .withAnswer(infos(), t0)
            .withAnswer(infos(5.0), t0.plusSeconds(60))
            .inactiveSince shouldBe null
    }

    @Test
    fun `a failure keeps the last answer but marks the check failing`() {
        val answered = FeederObservation().withAnswer(infos(5.0), t0)
        val failed = answered.withFailure(t0.plusSeconds(60))

        failed.isCheckFailing shouldBe true
        failed.status shouldBe Status.FEEDING
        failed.lastFeedingAt shouldBe t0

        failed.withAnswer(infos(5.0), t0.plusSeconds(120)).isCheckFailing shouldBe false
    }

    @Test
    fun `offline once the last feeding is older than the timeout`() {
        val obs = FeederObservation()
            .withAnswer(infos(5.0), t0)
            .withAnswer(infos(), t0 + timeout + Duration.ofHours(1))

        obs.isOffline(timeout, t0 + timeout + Duration.ofHours(1)) shouldBe true
        obs.isOffline(timeout, t0 + timeout - Duration.ofHours(1)) shouldBe false
    }

    @Test
    fun `idle counts as offline like not seen`() {
        val obs = FeederObservation()
            .withAnswer(infos(5.0), t0)
            .withAnswer(infos(0.0), t0 + timeout + Duration.ofHours(1))

        obs.isOffline(timeout, t0 + timeout + Duration.ofHours(1)) shouldBe true
    }

    @Test
    fun `a failing check is never offline, even after a recent inactive answer`() {
        val now = t0 + timeout + Duration.ofHours(2)
        val obs = FeederObservation()
            .withAnswer(infos(5.0), t0)
            .withAnswer(infos(), now.minusSeconds(3600))
            .withFailure(now)

        obs.isOffline(timeout, now) shouldBe false
    }

    @Test
    fun `an answer older than the timeout is too old to alert on`() {
        val obs = FeederObservation()
            .withAnswer(infos(5.0), t0)
            .withAnswer(infos(), t0.plusSeconds(60))

        obs.isOffline(timeout, t0 + timeout + timeout) shouldBe false
    }

    @Test
    fun `a feeder never seen feeding counts from its first inactive answer`() {
        val first = FeederObservation().withAnswer(infos(), t0)
        val now = t0 + timeout + Duration.ofHours(1)
        val obs = first.withAnswer(infos(), now)

        obs.isOffline(timeout, now) shouldBe true
        obs.isOffline(timeout, t0 + timeout - Duration.ofHours(1)) shouldBe false
    }

    @Test
    fun `a seeded feeding time is the baseline before any answer`() {
        val seeded = FeederObservation(lastFeedingAt = t0)
        val now = t0 + timeout + Duration.ofHours(1)

        seeded.isOffline(timeout, now) shouldBe false
        seeded.withAnswer(infos(), now).isOffline(timeout, now) shouldBe true
    }

    @Test
    fun `stored observations decode`() {
        val json = SerializationModule().json()
        val serializer = MapSerializer(String.serializer(), FeederObservation.serializer())
        val stored = """
            {"abc":{"checkedAt":"2026-11-01T00:00:00Z","status":"not_seen","failedAt":"2026-11-01T01:00:00Z",
            "lastFeedingAt":"2026-10-31T00:00:00Z","inactiveSince":"2026-10-31T06:00:00Z"}}
        """.trimIndent()

        json.decodeFromString(serializer, stored) shouldBe mapOf(
            "abc" to FeederObservation(
                checkedAt = t0,
                status = Status.NOT_SEEN,
                failedAt = Instant.parse("2026-11-01T01:00:00Z"),
                lastFeedingAt = Instant.parse("2026-10-31T00:00:00Z"),
                inactiveSince = Instant.parse("2026-10-31T06:00:00Z"),
            )
        )
    }

    @Test
    fun `observations round trip`() {
        val json = SerializationModule().json()
        val serializer = MapSerializer(String.serializer(), FeederObservation.serializer())
        val value = mapOf(
            "a" to FeederObservation().withAnswer(infos(3.0), t0),
            "b" to FeederObservation().withFailure(t0),
            "c" to FeederObservation(),
        )

        json.decodeFromString(serializer, json.encodeToString(serializer, value)) shouldBe value
    }
}
