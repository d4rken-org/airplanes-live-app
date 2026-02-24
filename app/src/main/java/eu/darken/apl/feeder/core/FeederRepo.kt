package eu.darken.apl.feeder.core

import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.network.NetworkStateProvider
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederPosition
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.stats.BeastChartData
import eu.darken.apl.feeder.core.stats.BeastStatsEntity
import eu.darken.apl.feeder.core.stats.ChartPoint
import eu.darken.apl.feeder.core.stats.FeederStatsDatabase
import eu.darken.apl.feeder.core.stats.MlatChartData
import eu.darken.apl.feeder.core.stats.MlatStatsEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
    ) { _, latestBeast, latestMlat, configGroup ->

        configGroup.configs.map { config ->
            val beastLastDb = feederStatsDatabase.beastStats.getLatest(config.receiverId).firstOrNull()
            val mlatLastDb = feederStatsDatabase.mlatStats.getLatest(config.receiverId).firstOrNull()
            Feeder(
                config = config,
                beastStats = beastLastDb,
                mlatStats = mlatLastDb,
            )
        }
    }

    suspend fun refresh() {
        log(TAG) { "refresh()" }
        val idsToRefresh = feeders.first().map { it.id }
        refreshStatsFor(idsToRefresh)
    }

    suspend fun addFeeder(id: ReceiverId) {
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

                    group.copy(configs = oldConfigs + FeederConfig(receiverId = id))
                }
            }
        }

        refreshStatsFor(setOf(id))
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

    private suspend fun refreshStatsFor(ids: Collection<ReceiverId>) = refreshLock.withLock {
        log(TAG) { "refreshStatsfor($ids)" }

        if (isRefreshing.value) return@withLock
        isRefreshing.value = true

        try {
            val feedInfos = feederEndpoint.getFeedInfos(ids.toSet())

            feederSettings.feederGroup.update { group ->
                val updatedConfigs = group.configs.map { config ->
                    val infos = feedInfos[config.receiverId] ?: return@map config

                    val mlatLabel = infos.mlat.firstOrNull()?.user?.takeIf { it.isNotEmpty() }

                    config.copy(
                        label = when {
                            config.label == null && mlatLabel != null -> mlatLabel
                            else -> config.label
                        },
                        offlineCheckSnoozedAt = if (infos.beast.any()) null else config.offlineCheckSnoozedAt
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

            feederSettings.lastUpdate.value(Instant.now())

            val lastCleanup = feederSettings.lastCleanup.value()
            if (Duration.between(lastCleanup, Instant.now()) > Duration.ofDays(1)) {
                val retentionCutoff = Instant.now().minus(Duration.ofDays(31)).toEpochMilli()
                feederStatsDatabase.beastStats.deleteBefore(retentionCutoff)
                feederStatsDatabase.mlatStats.deleteBefore(retentionCutoff)
                feederSettings.lastCleanup.value(Instant.now())
            }

            delay(1000)
        } finally {
            isRefreshing.value = false
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

        // If offline check is not enabled for this feeder, it's not considered offline
        val offlineCheckTimeout = feeder.config.offlineCheckTimeout
        if (offlineCheckTimeout == null) {
            return false
        }

        // If the feeder has never been seen, it's not considered offline
        val lastSeen = feeder.lastSeen
        if (lastSeen == null) {
            return false
        }

        // If the feeder is snoozed, it's not considered offline
        if (feeder.config.offlineCheckSnoozedAt != null) {
            return false
        }

        // Check if the time since the last update is not greater than the offlineCheckTimeout
        val timeSinceUpdate = Duration.between(feederSettings.lastUpdate.value(), Instant.now())
        if (timeSinceUpdate > offlineCheckTimeout) {
            return false
        }

        // Check if the time since the feeder was last seen is greater than the offlineCheckTimeout
        return Duration.between(lastSeen, Instant.now()) > offlineCheckTimeout
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
