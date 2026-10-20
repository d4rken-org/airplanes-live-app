package eu.darken.apl.watch.core.server

import java.time.Duration
import java.time.Instant

/**
 * Decides which new matches of one events poll run are alerted on their own and which only count
 * towards the run's summary.
 *
 * Example with an expected gap of 85 min, polled at 12:00 after a last poll at 09:00, 4 of 5 individual
 * alerts already used by earlier pages of the run:
 * - observed 10:00: summary, older than 10:35
 * - observed 11:50: individual
 * - observed 11:55: summary, the run's budget is used up
 */
object WatchAlertCatchUp {

    /** The periodic worker's flex window. */
    val WORKER_FLEX: Duration = Duration.ofMinutes(10)
    val GRACE: Duration = Duration.ofMinutes(15)
    const val MAX_INDIVIDUAL_PER_RUN = 5

    fun expectedGap(interval: Duration): Duration = interval.plus(WORKER_FLEX).plus(GRACE)

    data class Candidate(val seq: Long, val observedAt: Instant)

    data class Classification(
        val individual: List<Candidate>,
        val summarized: List<Candidate>,
    )

    /**
     * [lastPollAt] is the last poll run that read up to the newest event, null when none did since the
     * list was read. [individualSoFar] counts the individual alerts earlier pages of this run produced.
     */
    fun classify(
        candidates: List<Candidate>,
        lastPollAt: Instant?,
        now: Instant,
        expectedGap: Duration,
        individualSoFar: Int,
    ): Classification {
        val caughtUp = lastPollAt == null || !lastPollAt.plus(expectedGap).isBefore(now)
        val cutoff = now.minus(expectedGap)
        var budget = (MAX_INDIVIDUAL_PER_RUN - individualSoFar).coerceAtLeast(0)
        val individual = mutableListOf<Candidate>()
        val summarized = mutableListOf<Candidate>()
        candidates.sortedBy { it.seq }.forEach { candidate ->
            val late = !caughtUp && candidate.observedAt.isBefore(cutoff)
            if (!late && budget > 0) {
                individual += candidate
                budget--
            } else {
                summarized += candidate
            }
        }
        return Classification(individual = individual, summarized = summarized)
    }
}
