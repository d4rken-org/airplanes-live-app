package eu.darken.apl.feeder.core

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.api.FeedInfos
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederPosition
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.server.FeederMonitorRunner
import eu.darken.apl.feeder.core.server.FeederMonitorSyncScheduler
import eu.darken.apl.feeder.core.server.FeederServerMode
import eu.darken.apl.feeder.core.server.FeederServerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeederRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val feederSettings: FeederSettings,
    private val feederEndpoint: FeederEndpoint,
    private val serverMode: FeederServerMode,
    private val monitorRunner: FeederMonitorRunner,
    private val syncScheduler: FeederMonitorSyncScheduler,
) {

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())
    val isRefreshing = MutableStateFlow(false)
    private val refreshLock = Mutex()
    private val configLock = Mutex()

    val feeders: Flow<Collection<Feeder>> = combine(
        refreshTrigger,
        feederSettings.feederGroup.flow,
        serverMode.state,
    ) { _, configGroup, mode ->
        val server = mode.server?.takeIf { mode.isActive }

        configGroup.configs.map { config ->
            Feeder(
                config = config,
                server = server?.let { FeederServerView.of(config, it) },
            )
        }
    }

    /** Only the server checks feeders, without server monitoring there is nothing to refresh. */
    suspend fun refresh() {
        log(TAG) { "refresh()" }
        if (serverMode.current().isActive) {
            refreshServer()
        } else {
            log(TAG) { "refresh(): no server monitoring, nothing to refresh" }
        }
    }

    private suspend fun refreshServer() = refreshLock.withLock {
        if (isRefreshing.value) return@withLock
        isRefreshing.value = true
        try {
            syncServer("feeder-refresh")
        } finally {
            isRefreshing.value = false
        }
    }

    /** A foreground run of the server's feeder monitoring; outside server mode it does nothing. */
    suspend fun syncServer(reason: String) {
        log(TAG) { "syncServer($reason)" }
        val result = monitorRunner.run(reason, foreground = true)
        syncScheduler.afterRun(result)
    }

    /**
     * Stores the feeder and everything supplied with it as one write, so a caller that gets no
     * exception has the whole feeder it asked for. Filling in its label is a separate step, because
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
        }
    }

    /**
     * Fills empty labels on the app scope rather than the caller's. The screen that adds a feeder is
     * gone before the answer arrives, so a lookup tied to it would be cancelled on the way out.
     */
    fun fillLabelsDetached(ids: Collection<ReceiverId>) {
        appScope.launch {
            try {
                fillLabels(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Detached label fill failed for $ids: ${e.asLog()}" }
            }
        }
    }

    private suspend fun fillLabels(ids: Collection<ReceiverId>) {
        log(TAG) { "fillLabels($ids)" }
        val labels = feederEndpoint.getFeedInfos(ids.toSet())
            .mapNotNull { (id, result) -> result.getOrNull()?.mlatLabel?.let { id to it } }
            .toMap()
        if (labels.isEmpty()) return
        configLock.withLock {
            withContext(NonCancellable) {
                feederSettings.feederGroup.update { group ->
                    val updatedConfigs = group.configs.map { config ->
                        val label = labels[config.receiverId]
                        if (config.label == null && label != null) config.copy(label = label) else config
                    }
                    group.copy(configs = updatedConfigs.toSet())
                }
            }
        }
    }

    private val FeedInfos.mlatLabel: String?
        get() = mlat.firstOrNull()?.user?.takeIf { it.isNotEmpty() }

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

    private suspend fun updateFeeder(id: ReceiverId, update: FeederConfig.() -> FeederConfig) {
        feederSettings.feederGroup.update { group ->
            val updatedConfigs = group.configs.map { config ->
                if (config.receiverId == id) update(config) else config
            }
            group.copy(configs = updatedConfigs.toSet())
        }
    }

    /** The server decides, an unreachable device network does not make its answer wrong. */
    fun isOffline(feeder: Feeder): Boolean {
        val server = feeder.server ?: return false
        if (feeder.config.offlineCheckTimeout == null) return false
        if (feeder.config.offlineCheckSnoozedAt != null) return false
        return server.isReportedOffline
    }

    companion object {
        private val TAG = logTag("Feeder", "Repo")
    }
}
