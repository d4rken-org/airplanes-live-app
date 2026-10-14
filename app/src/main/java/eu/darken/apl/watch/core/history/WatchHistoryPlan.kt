package eu.darken.apl.watch.core.history

import eu.darken.apl.server.api.HistoryWatchOutcome
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.min

/** The decisions of the history backfill that need no database or network. */
object WatchHistoryPlan {

    val RETENTION: Duration = Duration.ofDays(7)
    val TOP_UP_INTERVAL: Duration = Duration.ofHours(3)
    private val BACKOFF_BASE: Duration = Duration.ofMinutes(15)

    fun hourOf(at: Instant): Instant = at.truncatedTo(ChronoUnit.HOURS)

    /** The server keeps the last 7 days from the start of the hour, up to the end of the current one. */
    fun retainedWindow(now: Instant): Pair<Instant, Instant> {
        val current = hourOf(now)
        return current.minus(RETENTION) to current.plus(Duration.ofHours(1))
    }

    /**
     * Where a new evaluation starts: the first retained hour that is missing or still provisional.
     * The current hour is never final yet, so there always is one.
     */
    fun evaluationStart(now: Instant, cached: List<WatchHistoryHourEntity>): Instant {
        val (from, until) = retainedWindow(now)
        val finalHours = cached.filter { it.isFinal }.map { it.hour }.toSet()
        var hour = from
        while (hour < until) {
            if (hour !in finalHours) return hour
            hour = hour.plus(Duration.ofHours(1))
        }
        return hourOf(now)
    }

    /**
     * WATCH units history may spend now: what is left after the periodic checks still due before the
     * reset. Foreground and manual checks are not reserved for.
     */
    fun spendable(
        remaining: Int,
        watchCount: Int,
        now: Instant,
        resetsAt: Instant,
        checkInterval: Duration,
    ): Int {
        val untilReset = Duration.between(now, resetsAt).coerceAtLeast(Duration.ZERO)
        val checksDue = ceil(untilReset.toMillis().toDouble() / checkInterval.toMillis().coerceAtLeast(1)).toInt()
        return (remaining - watchCount * checksDue).coerceAtLeast(0)
    }

    fun backoff(step: Int): Duration {
        val factor = 1L shl min(step, 10)
        return minOf(BACKOFF_BASE.multipliedBy(factor), TOP_UP_INTERVAL)
    }

    fun hours(watchId: String, outcome: HistoryWatchOutcome): List<WatchHistoryHourEntity> = outcome.hours.map {
        WatchHistoryHourEntity(
            watchId = watchId,
            hour = Instant.ofEpochMilli(it.hour),
            aircraftCount = it.aircraftCount,
            coverage = it.coverage,
            isFinal = it.isFinal,
        )
    }

    /**
     * The watch's sync state after one outcome. [cursorJson] is the outcome's continuation, encoded,
     * and [from]/[until] the range the request was sent with, which a continuation must keep.
     * [tier] is the tier the request was built under, null when that is unknown (a recovered answer).
     */
    fun next(
        current: WatchHistorySyncEntity,
        outcome: HistoryWatchOutcome,
        cursorJson: String?,
        from: Instant,
        until: Instant,
        now: Instant,
        tier: String?,
        allowanceResetsAt: Instant,
    ): WatchHistorySyncEntity {
        val base = current.copy(updatedAt = now)
        if (outcome.status == STATUS_ANSWERED) {
            val seenAt = outcome.lastSeenAt?.let { Instant.ofEpochMilli(it) }
            val newer = seenAt != null && (current.lastSeenAt == null || seenAt > current.lastSeenAt)
            val answered = base.copy(
                evaluationBasis = outcome.evaluationBasis ?: current.evaluationBasis,
                lastSeenAt = if (newer) seenAt else current.lastSeenAt,
                lastSeenAircraftId = if (newer) outcome.lastSeenAircraftId else current.lastSeenAircraftId,
                restrictedTier = null,
                backoffStep = 0,
            )
            return if (cursorJson != null) {
                answered.copy(windowFrom = from, windowUntil = until, cursorJson = cursorJson, nextEligibleAt = now)
            } else {
                answered.copy(
                    windowFrom = null,
                    windowUntil = null,
                    cursorJson = null,
                    lastPassAt = now,
                    nextEligibleAt = now.plus(TOP_UP_INTERVAL),
                )
            }
        }
        return when (outcome.error?.code) {
            // A refusal under an unknown tier must not block a tier that may allow the watch
            ServerCodes.TIER_RESTRICTED -> if (tier != null) {
                base.copy(restrictedTier = tier)
            } else {
                base.copy(nextEligibleAt = now.plus(TOP_UP_INTERVAL))
            }
            ServerCodes.BUDGET_EXHAUSTED -> base.copy(
                nextEligibleAt = now.plus(backoff(current.backoffStep)),
                backoffStep = current.backoffStep + 1,
            )
            ServerCodes.DAILY_ALLOWANCE_EXHAUSTED -> base.copy(nextEligibleAt = maxOf(allowanceResetsAt, now))
            // The answer belonged to another allowance owner; asking again under a new id is free of that
            ServerCodes.ACCESS_CHANGED -> base.copy(nextEligibleAt = now)
            // Something the server cannot evaluate, a cursor included: start over, but not right away
            else -> base.copy(
                windowFrom = null,
                windowUntil = null,
                cursorJson = null,
                nextEligibleAt = now.plus(TOP_UP_INTERVAL),
            )
        }
    }

    const val STATUS_ANSWERED = "answered"
}
