package eu.darken.apl.search.ui

import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.search.ui.SearchViewModel.Freshness
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class SearchFreshnessTest : BaseTest() {

    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun freshness(messageSeenAt: Instant?, positionSeenAt: Instant?) = SearchViewModel.freshness(
        FakeAircraft(messageSeenAt = messageSeenAt, positionSeenAt = positionSeenAt),
        now,
    )

    @Test
    fun `live while the position is at most 15 seconds old`() {
        freshness(now, now.minusSeconds(15)) shouldBe Freshness.LIVE
        freshness(now, now.minusSeconds(16)) shouldBe Freshness.RECENT
    }

    @Test
    fun `a position ahead of the clock is live`() {
        freshness(now, now.plusSeconds(30)) shouldBe Freshness.LIVE
    }

    @Test
    fun `a recent message does not make an old position live`() {
        freshness(now, now.minus(Duration.ofMinutes(3))) shouldBe Freshness.RECENT
    }

    @Test
    fun `without a position the message is aged but never live`() {
        freshness(now, null) shouldBe Freshness.RECENT
        freshness(now.minus(Duration.ofHours(2)), null) shouldBe Freshness.STALE
    }

    @Test
    fun `aged by the position, not the message`() {
        freshness(now, now.minus(Duration.ofHours(2))) shouldBe Freshness.STALE
        freshness(now, now.minus(Duration.ofHours(24))) shouldBe Freshness.OLD
    }

    @Test
    fun `never seen`() {
        freshness(null, null) shouldBe Freshness.UNKNOWN
    }
}
