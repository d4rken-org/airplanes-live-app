package eu.darken.apl.search.ui

import eu.darken.apl.common.compose.preview.FakeAircraft
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class SearchFreshnessTest : BaseTest() {

    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun isLive(messageSeenAt: Instant?, positionSeenAt: Instant?) = SearchViewModel.isLive(
        FakeAircraft(messageSeenAt = messageSeenAt, positionSeenAt = positionSeenAt),
        now,
    )

    private fun seenAt(messageSeenAt: Instant?, positionSeenAt: Instant?) = SearchViewModel.seenAt(
        FakeAircraft(messageSeenAt = messageSeenAt, positionSeenAt = positionSeenAt),
    )

    @Test
    fun `live while the position is at most 15 seconds old`() {
        isLive(now, now.minusSeconds(15)) shouldBe true
        isLive(now, now.minusSeconds(16)) shouldBe false
    }

    @Test
    fun `a position ahead of the clock is live`() {
        isLive(now, now.plusSeconds(30)) shouldBe true
    }

    @Test
    fun `a recent message does not make an old position live`() {
        isLive(now, now.minus(Duration.ofMinutes(3))) shouldBe false
    }

    @Test
    fun `without a position never live`() {
        isLive(now, null) shouldBe false
    }

    @Test
    fun `seen at the position's time, the message's without one`() {
        val position = now.minus(Duration.ofHours(2))
        seenAt(now, position) shouldBe position
        seenAt(now, null) shouldBe now
        seenAt(null, null) shouldBe null
    }
}
