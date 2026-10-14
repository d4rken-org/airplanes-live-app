package eu.darken.apl.watch.core.history

import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.history.WatchCheckDao
import eu.darken.apl.watch.core.db.history.WatchCheckEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHistoryRepo @Inject constructor(
    private val database: WatchDatabase,
) {

    private val watchCheckDao: WatchCheckDao
        get() = database.checks

    val firehose: Flow<WatchCheck?> = watchCheckDao.firehose().map {
        if (it == null) return@map null
        it.toAlertCheck()
    }

    suspend fun getLastCheck(watchId: WatchId): WatchCheck? {
        return watchCheckDao.getLastCheck(watchId)?.toAlertCheck()
    }

    suspend fun getLastHit(watchId: WatchId): WatchCheck? {
        return watchCheckDao.getLastHit(watchId)?.toAlertCheck()
    }

    /** Emits at every UTC hour boundary, so a window that ends now moves on with the clock. */
    private val hourTicks: Flow<Instant> = flow {
        while (true) {
            val now = Instant.now()
            emit(now)
            delay(Duration.between(now, WatchHistoryPlan.hourOf(now).plus(Duration.ofHours(1))).toMillis() + 1)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun countChart(watchId: WatchId, window: Duration): Flow<WatchCountChartData> = hourTicks.flatMapLatest { now ->
        watchCheckDao.chartDataSinceFlow(watchId, now.minus(window)).map { rows ->
            WatchCountChartData(rows.map { ChartPoint(it.checkedAt, it.aircraftCount.toDouble()) })
        }
    }

    /**
     * The app's own checks and the server's history, hour by hour from [since] through the hour that
     * contains [asOf]. It does not move on with the clock, the caller decides when the range changes.
     */
    fun activity(watchId: WatchId, since: Instant, asOf: Instant): Flow<WatchActivityTimeline> = combine(
        watchCheckDao.chartDataSinceFlow(watchId, since),
        database.history.hoursSince(watchId, WatchHistoryPlan.hourOf(since)),
    ) { checks, hours ->
        WatchActivityTimeline.build(since, asOf, checks.map { WatchActivityCheck(it.checkedAt, it.aircraftCount) }, hours)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun activityByWatch(window: Duration): Flow<Map<WatchId, WatchActivityTimeline>> = hourTicks.flatMapLatest { now ->
        val since = now.minus(window)
        combine(
            watchCheckDao.chartDataSinceFlow(since),
            database.history.allHoursSince(WatchHistoryPlan.hourOf(since)),
        ) { checks, hours ->
            val checksByWatch = checks.groupBy { it.watchId }
            val hoursByWatch = hours.groupBy { it.watchId }
            (checksByWatch.keys + hoursByWatch.keys).associateWith { watchId ->
                WatchActivityTimeline.build(
                    since,
                    now,
                    checksByWatch[watchId].orEmpty().map { WatchActivityCheck(it.checkedAt, it.aircraftCount) },
                    hoursByWatch[watchId].orEmpty(),
                )
            }
        }
    }

    /** The latest sighting the server's history knows of, per watch. */
    val historyLastSeen: Flow<Map<WatchId, Instant>> = database.history.allSync().map { syncs ->
        syncs.mapNotNull { sync -> sync.lastSeenAt?.let { sync.watchId to it } }.toMap()
    }

    suspend fun cleanupOldChecks() {
        val now = Instant.now()
        val cutoff = now.minus(RETENTION)
        val deleted = watchCheckDao.deleteBefore(cutoff)
        log(TAG) { "cleanupOldChecks() deleted $deleted" }
        // Applied markers outlive the longest a pending history answer is kept for replay
        database.cleanupHistory(hoursBefore = cutoff, appliedBefore = now.minus(Duration.ofHours(48)))
    }

    private fun WatchCheckEntity.toAlertCheck() = WatchCheck(
        watchId = this.watchId,
        checkAt = this.checkedAt,
        aircraftCount = this.aircraftcount,
        seenHexes = this.seenHexes
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.filter { it.matches(HEX_PATTERN) }
            ?.toSet()
            ?: emptySet(),
    )

    companion object {
        val RETENTION: Duration = Duration.ofDays(31)
        internal val TAG = logTag("Watch", "History", "Repo")
        private val HEX_PATTERN = Regex("~?[0-9A-Fa-f]+")
    }
}
