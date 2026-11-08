package eu.darken.apl.watch.core.history

import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.db.WatchDatabase
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

    /** Emits at every UTC hour boundary, so a window that ends now moves on with the clock. */
    private val hourTicks: Flow<Instant> = flow {
        while (true) {
            val now = Instant.now()
            emit(now)
            delay(Duration.between(now, WatchActivityTimeline.hourOf(now).plus(Duration.ofHours(1))).toMillis() + 1)
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
     * One point per server hour with the most aircraft any of its rollups counted. A server hour
     * without any evaluated minute is left out, it has no count to plot.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun countChart(watchId: WatchId, window: Duration): Flow<WatchCountChartData> = hourTicks.flatMapLatest { now ->
        val since = now.minus(window)
        combine(
            database.server.rollupsSince(watchId, since),
            serverRevisionOf(watchId),
        ) { rollups, server ->
            val hourPoints = rollups
                .filter { it.isOf(server) }
                .filter { it.aircraftCount > 0 || it.coverage != WatchActivityTimeline.COVERAGE_NONE }
                .groupBy { it.hour }
                .map { (hour, rows) -> ChartPoint(hour, rows.maxOf { it.aircraftCount }.toDouble()) }
            WatchCountChartData(hourPoints.sortedBy { it.timestamp })
        }
    }

    /**
     * The server's history, hour by hour from [since] through the hour that contains [asOf]. It does
     * not move on with the clock, the caller decides when the range changes.
     */
    fun activity(watchId: WatchId, since: Instant, asOf: Instant): Flow<WatchActivityTimeline> = combine(
        database.server.rollupsSince(watchId, WatchActivityTimeline.hourOf(since)),
        serverRevisionOf(watchId),
    ) { rollups, server ->
        WatchActivityTimeline.build(since, asOf, rollups, server.serverWatchId, server.revision)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun activityByWatch(window: Duration): Flow<Map<WatchId, WatchActivityTimeline>> = hourTicks.flatMapLatest { now ->
        val since = now.minus(window)
        combine(
            database.server.allRollupsSince(WatchActivityTimeline.hourOf(since)),
            database.server.allStates(),
            database.definitionRevisions,
        ) { rollups, states, definitionRevisions ->
            val serverByWatch = states.associate { it.watchId to it.toServerRevision(definitionRevisions[it.watchId]) }
            rollups
                .filter { rollup -> serverByWatch[rollup.watchId]?.let { rollup.isOf(it) } == true }
                .groupBy { it.watchId }
                .mapValues { (watchId, watchRollups) ->
                    val server = serverByWatch[watchId]
                    WatchActivityTimeline.build(since, now, watchRollups, server?.serverWatchId, server?.revision)
                }
        }
    }

    /** The latest sighting the server knows of for the definition it evaluates now, per watch. */
    val historyLastSeen: Flow<Map<WatchId, Instant>> = combine(
        database.server.allStates(),
        database.server.rollupsLastSeen(),
        database.definitionRevisions,
    ) { states, rollups, definitionRevisions ->
        val serverByWatch = states.associate { it.watchId to it.toServerRevision(definitionRevisions[it.watchId]) }
        val fromRollups = rollups
            .filter { serverByWatch[it.watchId] == ServerRevision(it.serverWatchId, it.revision) }
            .map { it.watchId to it.lastSeenAt }
        val fromStates = states
            .filter { it.isCurrent(definitionRevisions[it.watchId]) }
            .mapNotNull { state -> state.lastSeenAt?.let { state.watchId to it } }
        (fromStates + fromRollups)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, seen) -> seen.max() }
    }
}
