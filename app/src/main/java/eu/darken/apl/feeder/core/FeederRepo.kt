package eu.darken.apl.feeder.core

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.feeder.core.api.FeedInfos
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederPosition
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.stats.BeastChartData
import eu.darken.apl.feeder.core.stats.BeastStatsEntity
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.feeder.core.stats.FeederStatsDatabase
import eu.darken.apl.feeder.core.stats.MlatChartData
import eu.darken.apl.feeder.core.stats.MlatStatsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeederRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val feederSettings: FeederSettings,
    private val feederEndpoint: FeederEndpoint,
    private val feederStatsDatabase: FeederStatsDatabase,
    private val networkStateProvider: NetworkStateProvider,
) {

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())
    val isRefreshing = MutableStateFlow(false)
    private val refreshLock = Mutex()
    private val configLock = Mutex()

    val feeders: Flow<Collection<Feeder>> = combine(
        refreshTrigger,
        feederStatsDatabase.beastStats.firehose(),
        feederStatsDatabase.mlatStats.firehose(),
        feederSettings.feederGroup.flow,
        feederSettings.observations.flow,
    ) { _, _, _, configGroup, observations ->

        configGroup.configs.map { config ->
            val beastLastDb = feederStatsDatabase.beastStats.getLatest(config.receiverId).firstOrNull()
            val mlatLastDb = feederStatsDatabase.mlatStats.getLatest(config.receiverId).firstOrNull()
            Feeder(
                config = config,
                beastStats = beastLastDb,
                mlatStats = mlatLastDb,
                observation = observations[config.receiverId],
            )
        }
    }

    suspend fun refresh() {
        log(TAG) { "refresh()" }
        val idsToRefresh = feeders.first().map { it.id }
        refreshStatsFor(idsToRefresh)
    }

    /**
     * Stores the feeder and everything supplied with it as one write, so a caller that gets no
     * exception has the whole feeder it asked for. Fetching its stats is a separate step, because
     * a feeder the user configured is theirs whether or not the public API answers about it.
     */
    suspend fun addFeeder(
        id: ReceiverId,
        label: String? = null,
        address: String? = null,
        position: FeederPosition? = null,
    ) {
        configLock.withLock {
            withContext(NonCancellable) {
                log(TAG) { "addFeeder($id)" }
                feederSettings.feederGroup.update { group ->
                    val oldConfigs = group.configs.toMutableSet()

                    val existing = group.configs.firstOrNull { it.receiverId == id }
                    if (existing != null) {
                        log(TAG, WARN) { "Replacing existing feeder with this ID: $existing" }
                        oldConfigs.remove(existing)
                    }

                    val added = FeederConfig.newFeeder(id).copy(
                        label = label,
                        address = address,
                        position = position,
                    )
                    group.copy(configs = oldConfigs + added)
                }
            }
        }
    }

    suspend fun removeFeeder(id: ReceiverId) = configLock.withLock {
        withContext(NonCancellable) {
            log(TAG) { "removeFeeder($id)" }
            feederSettings.feederGroup.update { group ->
                val oldConfigs = group.configs.toMutableSet()

                val toRemove = group.configs.firstOrNull { it.receiverId == id }
                if (toRemove == null) log(TAG, WARN) { "Unknown feeder: $id" }
                else oldConfigs.remove(toRemove)

                group.copy(configs = oldConfigs)
            }
            feederSettings.observations.update { it - id }

            feederStatsDatabase.apply {
                beastStats.delete(id).also {
                    log(TAG, INFO) { "Delete $it beast stats rows" }
                }
                mlatStats.delete(id).also {
                    log(TAG, INFO) { "Delete $it mlat stats rows" }
                }
            }
        }
    }

    /**
     * Fetches stats on the app scope rather than the caller's. The screen that adds a feeder is
     * gone before the answer arrives, so a fetch tied to it would be cancelled on the way out and
     * leave the new row blank until something else refreshes.
     */
    fun refreshStatsDetached(ids: Collection<ReceiverId>) {
        appScope.launch {
            try {
                refreshStatsFor(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Detached stats refresh failed for $ids: ${e.asLog()}" }
            }
        }
    }

    private suspend fun refreshStatsFor(ids: Collection<ReceiverId>) = refreshLock.withLock {
        log(TAG) { "refreshStatsfor($ids)" }

        if (isRefreshing.value) return@withLock
        isRefreshing.value = true

        try {
            val results = feederEndpoint.getFeedInfos(ids.toSet())
            val checkedAt = Instant.now()

            // Under the config lock, so a feeder removed while its fetch ran stays removed
            val stillConfigured = configLock.withLock {
                withContext(NonCancellable) {
                    val configured = feederSettings.feederGroup.value().configs.map { it.receiverId }.toSet()
                    val kept = results.filterKeys { it in configured }
                    val feedInfos = kept.mapNotNull { (id, result) -> result.getOrNull()?.let { id to it } }.toMap()
                    storeStats(feedInfos)
                    storeObservations(kept, configured, checkedAt)
                    cleanupStats()
                    kept
                }
            }

            val failures = stillConfigured
                .mapNotNull { (id, result) -> result.exceptionOrNull()?.let { id to it } }
                .toMap()
            if (failures.isNotEmpty()) throw FeederRefreshFailure(failures, stillConfigured.size)

            delay(1000)
        } finally {
            isRefreshing.value = false
        }
    }

    private suspend fun storeStats(feedInfos: Map<ReceiverId, FeedInfos>) {
        feederSettings.feederGroup.update { group ->
            val updatedConfigs = group.configs.map { config ->
                val infos = feedInfos[config.receiverId] ?: return@map config

                val mlatLabel = infos.mlat.firstOrNull()?.user?.takeIf { it.isNotEmpty() }
                val isFeeding = infos.beast.any { it.messageRate > 0 }

                config.copy(
                    label = when {
                        config.label == null && mlatLabel != null -> mlatLabel
                        else -> config.label
                    },
                    offlineCheckSnoozedAt = if (isFeeding) null else config.offlineCheckSnoozedAt
                )
            }
            group.copy(configs = updatedConfigs.toSet())
        }

        feedInfos.entries
            .flatMap { (id, infos) -> infos.beast.map { id to it } }
            .map { (id, beast) ->
                BeastStatsEntity(
                    receiverId = id,
                    positionRate = beast.positionRate,
                    positions = beast.positions,
                    messageRate = beast.messageRate,
                    bandwidth = beast.avgKBitsPerSecond,
                    connectionTime = beast.connTime,
                    latency = 100,
                )
            }
            .forEach {
                log(TAG) { "Updating beast stats : $it" }
                feederStatsDatabase.beastStats.insert(it)
            }

        feedInfos.entries
            .flatMap { (id, infos) -> infos.mlat.map { id to it } }
            .map { (id, mlat) ->
                MlatStatsEntity(
                    receiverId = id,
                    messageRate = mlat.messageRate,
                    peerCount = mlat.peerCount,
                    badSyncTimeout = mlat.badSyncTimeout,
                    outlierPercent = mlat.outlierPercent,
                )
            }
            .forEach {
                log(TAG) { "Updating mlat stats : $it" }
                feederStatsDatabase.mlatStats.insert(it)
            }
    }

    /** Runs after [storeObservations], whose seeding reads rows this may delete. */
    private suspend fun cleanupStats() {
        val lastCleanup = feederSettings.lastCleanup.value()
        if (Duration.between(lastCleanup, Instant.now()) > Duration.ofDays(1)) {
            val retentionCutoff = Instant.now().minus(Duration.ofDays(31)).toEpochMilli()
            feederStatsDatabase.beastStats.deleteBefore(retentionCutoff)
            feederStatsDatabase.mlatStats.deleteBefore(retentionCutoff)
            feederSettings.lastCleanup.value(Instant.now())
        }
    }

    /** A feeder without an observation yet starts from its newest stored row that carried messages. */
    private suspend fun storeObservations(
        results: Map<ReceiverId, Result<FeedInfos>>,
        configured: Set<ReceiverId>,
        checkedAt: Instant,
    ) {
        val known = feederSettings.observations.value().keys
        val seeds = (results.keys - known).associateWith { id ->
            FeederObservation(lastFeedingAt = feederStatsDatabase.beastStats.getLatestFeeding(id)?.receivedAt)
        }
        feederSettings.observations.update { current ->
            val updated = current.filterKeys { it in configured }.toMutableMap()
            results.forEach { (id, result) ->
                val previous = updated[id] ?: seeds[id] ?: FeederObservation()
                updated[id] = result.fold(
                    onSuccess = { previous.withAnswer(it, checkedAt) },
                    onFailure = { previous.withFailure(checkedAt) },
                )
            }
            updated
        }
    }

    suspend fun setOfflineCheckTimeout(id: ReceiverId, timeout: Duration?) {
        log(TAG) { "setOfflineCheckTimeout($id,$timeout)" }
        updateFeeder(id) {
            copy(
                offlineCheckTimeout = timeout,
                offlineCheckSnoozedAt = null,
            )
        }
    }

    suspend fun setLabel(id: ReceiverId, label: String?) {
        log(TAG) { "setLabel($id,$label" }
        updateFeeder(id) { copy(label = label) }
    }

    suspend fun setAddress(id: ReceiverId, address: String?) {
        log(TAG) { "setAddress($id,$address" }
        updateFeeder(id) { copy(address = address) }
    }

    suspend fun setPosition(id: ReceiverId, position: FeederPosition?) {
        log(TAG) { "setPosition($id,$position)" }
        updateFeeder(id) { copy(position = position) }
    }

    suspend fun setOfflineCheckSnoozedAt(id: ReceiverId, snoozedAt: Instant?) {
        log(TAG) { "setOfflineCheckSnoozedAt($id,$snoozedAt)" }
        updateFeeder(id) { copy(offlineCheckSnoozedAt = snoozedAt) }
    }

    private suspend fun updateFeeder(id: ReceiverId, update: FeederConfig.() -> FeederConfig) {
        feederSettings.feederGroup.update { group ->
            val updatedConfigs = group.configs.map { config ->
                if (config.receiverId == id) update(config) else config
            }
            group.copy(configs = updatedConfigs.toSet())
        }
    }

    suspend fun isOffline(feeder: Feeder): Boolean {
        // If internet is not available, don't consider feeders as offline
        val networkState = networkStateProvider.networkState.first()
        if (!networkState.isInternetAvailable) {
            log(TAG) { "Internet is not available, not considering feeder as offline" }
            return false
        }

        val offlineCheckTimeout = feeder.config.offlineCheckTimeout ?: return false
        if (feeder.config.offlineCheckSnoozedAt != null) return false

        return feeder.observation?.isOffline(offlineCheckTimeout, Instant.now()) ?: false
    }

    suspend fun getBeastChartData(receiverId: ReceiverId, since: Instant): BeastChartData {
        val rows = feederStatsDatabase.beastStats.getSince(receiverId, since.toEpochMilli())
        return BeastChartData(
            messageRate = rows.mapNotNull { row ->
                val v = row.messageRate
                if (v.isFinite()) ChartPoint(row.receivedAt, v) else null
            }
        )
    }

    suspend fun getMlatChartData(receiverId: ReceiverId, since: Instant): MlatChartData {
        val rows = feederStatsDatabase.mlatStats.getSince(receiverId, since.toEpochMilli())
        return MlatChartData(
            messageRate = rows.mapNotNull { row ->
                val v = row.messageRate
                if (v.isFinite()) ChartPoint(row.receivedAt, v) else null
            },
            outlierPercent = rows.mapNotNull { row ->
                val v = row.outlierPercent.toDouble().coerceIn(0.0, 100.0)
                if (v.isFinite()) ChartPoint(row.receivedAt, v) else null
            },
        )
    }

    companion object {
        private val TAG = logTag("Feeder", "Repo")
    }
}
