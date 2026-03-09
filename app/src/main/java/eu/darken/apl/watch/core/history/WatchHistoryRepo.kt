package eu.darken.apl.watch.core.history

import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.history.WatchCheckDao
import eu.darken.apl.watch.core.db.history.WatchCheckEntity
import kotlinx.coroutines.flow.Flow
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

    suspend fun addCheck(watchId: WatchId, aircraftCount: Int, seenHexes: Set<AircraftHex>? = null) {
        log(TAG) { "addCheck($watchId, $aircraftCount, seenHexes=${seenHexes?.size})" }
        val entity = WatchCheckEntity(
            watchId = watchId,
            aircraftcount = aircraftCount,
            seenHexes = seenHexes?.takeIf { it.isNotEmpty() }?.joinToString(","),
        )
        watchCheckDao.insert(entity)
    }

    suspend fun getCountChartData(watchId: WatchId, since: Instant): WatchCountChartData {
        val rows = watchCheckDao.getChartDataSince(watchId, since)
        return WatchCountChartData(rows.map { ChartPoint(it.checkedAt, it.aircraftCount.toDouble()) })
    }

    suspend fun getActivityData(watchId: WatchId, since: Instant): WatchActivityData {
        val rows = watchCheckDao.getChartDataSince(watchId, since)
        return WatchActivityData(rows.map { WatchActivityCheck(it.checkedAt, it.aircraftCount) })
    }

    suspend fun getSparklineDataBatch(
        watchIds: Set<WatchId>,
        since: Instant,
    ): Map<WatchId, List<WatchCheckDao.WatchCheckChartRow>> {
        return watchIds
            .chunked(500)
            .flatMap { batch -> watchCheckDao.getChartDataSince(batch.toSet(), since) }
            .groupBy { it.watchId }
    }

    suspend fun cleanupOldChecks() {
        val cutoff = Instant.now().minus(Duration.ofDays(31))
        val deleted = watchCheckDao.deleteBefore(cutoff)
        log(TAG) { "cleanupOldChecks() deleted $deleted" }
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
        internal val TAG = logTag("Watch", "History", "Repo")
        private val HEX_PATTERN = Regex("[0-9A-Fa-f]+")
    }
}
