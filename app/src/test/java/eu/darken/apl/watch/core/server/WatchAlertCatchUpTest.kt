package eu.darken.apl.watch.core.server

import eu.darken.apl.watch.core.server.WatchAlertCatchUp.Candidate
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class WatchAlertCatchUpTest : BaseTest() {

    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val gap = WatchAlertCatchUp.expectedGap(Duration.ofMinutes(60))

    private fun at(minutesBeforeNow: Long) = now.minus(Duration.ofMinutes(minutesBeforeNow))

    @Test
    fun `the expected gap is the interval plus the worker flex and a grace period`() {
        gap shouldBe Duration.ofMinutes(85)
        WatchAlertCatchUp.expectedGap(Duration.ofMinutes(15)) shouldBe Duration.ofMinutes(40)
    }

    @Test
    fun `after a poll within the gap every match alerts on its own, however old`() {
        val candidates = listOf(Candidate(1, at(600)), Candidate(2, at(1)))

        val result = WatchAlertCatchUp.classify(candidates, lastPollAt = at(85), now = now, expectedGap = gap, individualSoFar = 0)

        result.individual shouldBe candidates
        result.summarized.shouldBeEmpty()
    }

    @Test
    fun `without a poll since the list was read every match alerts on its own`() {
        val candidates = listOf(Candidate(1, at(600)))

        val result = WatchAlertCatchUp.classify(candidates, lastPollAt = null, now = now, expectedGap = gap, individualSoFar = 0)

        result.individual shouldBe candidates
        result.summarized.shouldBeEmpty()
    }

    @Test
    fun `after a long gap matches older than the gap are summarized and recent ones alert`() {
        val old = Candidate(1, at(86))
        val edge = Candidate(2, at(85))
        val recent = Candidate(3, at(5))

        val result = WatchAlertCatchUp.classify(
            listOf(recent, old, edge),
            lastPollAt = at(86),
            now = now,
            expectedGap = gap,
            individualSoFar = 0,
        )

        result.individual shouldBe listOf(edge, recent)
        result.summarized shouldBe listOf(old)
    }

    @Test
    fun `at most five matches of a run alert on their own, in event order`() {
        val candidates = (1L..7L).map { Candidate(it, at(10)) }

        val result = WatchAlertCatchUp.classify(candidates, lastPollAt = at(10), now = now, expectedGap = gap, individualSoFar = 0)

        result.individual shouldBe candidates.take(5)
        result.summarized shouldBe candidates.drop(5)
    }

    @Test
    fun `earlier pages of the run use up the budget`() {
        val candidates = (5L..7L).map { Candidate(it, at(10)) }

        val result = WatchAlertCatchUp.classify(candidates, lastPollAt = at(10), now = now, expectedGap = gap, individualSoFar = 4)

        result.individual shouldBe candidates.take(1)
        result.summarized shouldBe candidates.drop(1)

        val spent = WatchAlertCatchUp.classify(candidates, lastPollAt = at(10), now = now, expectedGap = gap, individualSoFar = 5)
        spent.individual.shouldBeEmpty()
        spent.summarized shouldBe candidates
    }
}
