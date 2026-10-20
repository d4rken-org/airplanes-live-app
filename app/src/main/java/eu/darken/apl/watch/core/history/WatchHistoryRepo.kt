package eu.darken.apl.watch.core.history

import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
import eu.darken.apl.watch.core.db.history.WatchCheckDao
import eu.darken.apl.watch.core.db.history.WatchCheckEntity
import eu.darken.apl.watch.core.db.server.WatchServerRollupEntity
import eu.darken.apl.watch.core.db.server.WatchServerStateEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private data class ServerRevision(val serverWatchId: String?, val revision: Long?)

    /** False while the server evaluates another definition than the local one, its evidence is of that one. */
    private fun WatchServerStateEntity.isCurrent(definitionRevision: Long?) =
        ackedDefinitionRevision != null && ackedDefinitionRevision == definitionRevision

    private fun WatchServerStateEntity?.toServerRevision(definitionRevision: Long?): ServerRevision {
        val current = this?.takeIf { it.isCurrent(definitionRevision) }
        return ServerRevision(current?.serverWatchId, current?.serverRevision)
    }

    private fun WatchServerRollupEntity.isOf(server: ServerRevision) =
        serverWatchId == server.serverWatchId && revision == server.revision

    /** Only the server watch and revision a watch maps to now, see [WatchActivityTimeline.mergeHours]. */
    private fun serverRevisionOf(watchId: WatchId): Flow<ServerRevision> = combine(
        database.server.state(watchId),
        database.definitionRevisions.map { it[watchId] }.distinctUntilChanged(),
    ) { state, definitionRevision -> state.toServerRevision(definitionRevision) }
        .distinctUntilChanged()

    /**
     * The app's checks, and one point per server hour with the most aircraft any of its rollups counted.
     * A server hour without any evaluated minute is left out, it has no count to plot.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun countChart(watchId: WatchId, window: Duration): Flow<WatchCountChartData> = hourTicks.flatMapLatest { now ->
        val since = now.minus(window)
        combine(
            watchCheckDao.chartDataSinceFlow(watchId, since),
            database.server.rollupsSince(watchId, since),
            serverRevisionOf(watchId),
        ) { checks, rollups, server ->
            val checkPoints = checks.map { ChartPoint(it.checkedAt, it.aircraftCount.toDouble()) }
            val hourPoints = rollups
                .filter { it.isOf(server) }
                .filter { it.aircraftCount > 0 || it.coverage != WatchActivityTimeline.COVERAGE_NONE }
                .groupBy { it.hour }
                .map { (hour, rows) -> ChartPoint(hour, rows.maxOf { it.aircraftCount }.toDouble()) }
            WatchCountChartData((checkPoints + hourPoints).sortedBy { it.timestamp })
        }
    }

    /**
     * The app's own checks and the server's history, hour by hour from [since] through the hour that
     * contains [asOf]. It does not move on with the clock, the caller decides when the range changes.
     */
    fun activity(watchId: WatchId, since: Instant, asOf: Instant): Flow<WatchActivityTimeline> = combine(
        watchCheckDao.chartDataSinceFlow(watchId, since),
        database.history.hoursSince(watchId, WatchHistoryPlan.hourOf(since)),
        database.server.rollupsSince(watchId, WatchHistoryPlan.hourOf(since)),
        serverRevisionOf(watchId),
    ) { checks, hours, rollups, server ->
        WatchActivityTimeline.build(
            since,
            asOf,
            checks.map { WatchActivityCheck(it.checkedAt, it.aircraftCount) },
            hours,
            rollups,
            server.serverWatchId,
            server.revision,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun activityByWatch(window: Duration): Flow<Map<WatchId, WatchActivityTimeline>> = hourTicks.flatMapLatest { now ->
        val since = now.minus(window)
        combine(
            watchCheckDao.chartDataSinceFlow(since),
            database.history.allHoursSince(WatchHistoryPlan.hourOf(since)),
            database.server.allRollupsSince(WatchHistoryPlan.hourOf(since)),
            database.server.allStates(),
            database.definitionRevisions,
        ) { checks, hours, rollups, states, definitionRevisions ->
            val checksByWatch = checks.groupBy { it.watchId }
            val hoursByWatch = hours.groupBy { it.watchId }
            val serverByWatch = states.associate { it.watchId to it.toServerRevision(definitionRevisions[it.watchId]) }
            val rollupsByWatch = rollups
                .filter { rollup -> serverByWatch[rollup.watchId]?.let { rollup.isOf(it) } == true }
                .groupBy { it.watchId }
            (checksByWatch.keys + hoursByWatch.keys + rollupsByWatch.keys).associateWith { watchId ->
                val server = serverByWatch[watchId]
                WatchActivityTimeline.build(
                    since,
                    now,
                    checksByWatch[watchId].orEmpty().map { WatchActivityCheck(it.checkedAt, it.aircraftCount) },
                    hoursByWatch[watchId].orEmpty(),
                    rollupsByWatch[watchId].orEmpty(),
                    server?.serverWatchId,
                    server?.revision,
                )
            }
        }
    }

    /** The latest sighting the server knows of, per watch. */
    val historyLastSeen: Flow<Map<WatchId, Instant>> = combine(
        database.history.allSync(),
        database.server.allStates(),
        database.server.rollupsLastSeen(),
        database.definitionRevisions,
    ) { syncs, states, rollups, definitionRevisions ->
        val serverByWatch = states.associate { it.watchId to it.toServerRevision(definitionRevisions[it.watchId]) }
        val fromRollups = rollups
            .filter { serverByWatch[it.watchId] == ServerRevision(it.serverWatchId, it.revision) }
            .map { it.watchId to it.lastSeenAt }
        val fromHistory = syncs.mapNotNull { sync -> sync.lastSeenAt?.let { sync.watchId to it } }
        val fromStates = states
            .filter { it.isCurrent(definitionRevisions[it.watchId]) }
            .mapNotNull { state -> state.lastSeenAt?.let { state.watchId to it } }
        (fromHistory + fromStates + fromRollups)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, seen) -> seen.max() }
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
