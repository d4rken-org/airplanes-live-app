package eu.darken.apl.feeder.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederDiscovery
import eu.darken.apl.feeder.core.FeederObservation
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.config.FeederSortMode
import eu.darken.apl.feeder.core.server.FeederHistoryRepo
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.feeder.core.stats.FeederStatsDatabase
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.toMapFeedId
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.upgrade.ui.DestinationUpgradeFeeder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import eu.darken.apl.common.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class FeederListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
    private val feederSettings: FeederSettings,
    private val feederStatsDatabase: FeederStatsDatabase,
    private val feederLinkRepo: FeederLinkRepo,
    private val feederDiscovery: FeederDiscovery,
    private val feederHistoryRepo: FeederHistoryRepo,
    upgradeRepo: UpgradeRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "List", "ViewModel"),
) {

    private val refreshTimer = callbackFlow {
        while (isActive) {
            send(Unit)
            delay(1000)
        }
        awaitClose()
    }

    /**
     * Which feeders this network can register. A network answer, so it is fetched on arrival and on
     * refresh rather than derived in [state], which recomputes every second.
     */
    private val registerableIds = MutableStateFlow<Set<ReceiverId>>(emptySet())

    /**
     * Checks run one at a time and only the newest may publish. Requests that a newer one overtakes
     * while they wait are dropped before they reach the network.
     */
    private val checkGeneration = AtomicInteger(0)
    private val checkLock = Mutex()

    /** Keyed by canonical feeder id. Loaded on show and refresh, [state] recomputes every second. */
    private val serverSparklines = MutableStateFlow<Map<String, List<ChartPoint>>>(emptyMap())
    private val serverSparklineLock = Mutex()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val sparklineData = combine(
        feederStatsDatabase.beastStats.firehose().debounce(2_000),
        feederSettings.feederGroup.flow,
    ) { _, group ->
        val since7d = Instant.now().minus(Duration.ofDays(7))
        group.configs.associate { config ->
            config.receiverId to feederRepo.getBeastChartData(config.receiverId, since7d).messageRate
        }
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val state = combine(
        refreshTimer,
        feederRepo.feeders,
        feederRepo.isRefreshing,
        feederSettings.feederSortMode.flow,
        sparklineData,
        feederLinkRepo.state,
        upgradeRepo.upgradeInfo,
        registerableIds,
        serverSparklines,
    ) { _, feeders, isRefreshing, sortMode, sparklines, linkState, upgrade, registerable, serverLines ->
        val offlineStates = feeders.associate { it.id to feederRepo.isOffline(it) }

        val sortedFeeders = when (sortMode) {
            FeederSortMode.BY_LABEL -> feeders.sortedBy { it.label }
            FeederSortMode.BY_MESSAGE_RATE -> feeders.sortedByDescending { it.beastMessageRate }
        }

        val feederItems = sortedFeeders.map { feeder ->
            FeederItem(
                feeder = feeder,
                isOffline = offlineStates[feeder.id]!!,
                beastSparkline = when (val server = feeder.server) {
                    null -> sparklines[feeder.id]
                    else -> server.feederId?.let { serverLines[it] }
                } ?: emptyList(),
            )
        }

        State(
            feeders = feederItems,
            feederCount = feederItems.size,
            isRefreshing = isRefreshing,
            header = headerOf(feeders, offlineStates),
            currentSortMode = sortMode,
            linkState = linkState,
            isPro = upgrade.isSettled && upgrade.isPro,
            registerableFeeder = feederItems.firstOrNull { it.feeder.id in registerable }?.feeder,
        )
    }.asStateFlow()

    /**
     * Whether this network can register a feeder changes with the network, so every visit asks again.
     * In server mode the visit also syncs, quietly: no spinner and no error for a sync nobody asked for.
     */
    fun onScreenShown() {
        checkRegisterable()
        vmScope.launch {
            try {
                feederRepo.syncServer("feeder-list-shown")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(tag, WARN) { "Feeder monitor sync on show failed: ${e.asLog()}" }
            }
            loadServerSparklines()
        }
    }

    fun refresh() = launch {
        log(tag) { "refresh()" }
        // Three independent sources: whichever of them fails must not skip the other two
        checkRegisterable()
        var failure: Throwable? = null
        try {
            feederRepo.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
        }
        feederLinkRepo.refresh()
        loadServerSparklines()
        failure?.let { throw FeederRefreshException.wrap(it) }
    }

    /** One feeder after the other, a feeder without a server monitor has no history to show. */
    private suspend fun loadServerSparklines() = serverSparklineLock.withLock {
        val ids = feederRepo.feeders.first()
            .mapNotNull { feeder -> feeder.server?.takeIf { it.entry != null }?.feederId }
            .distinct()
        serverSparklines.update { current -> current.filterKeys { it in ids } }
        ids.forEach { id ->
            try {
                val points = feederHistoryRepo.messageRateSparkline(id)
                serverSparklines.update { it + (id to points) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(tag, WARN) { "Feeder history of $id failed: ${e.asLog()}" }
            }
        }
    }

    /**
     * A check that did not finish says nothing about this network, so it withdraws the offer rather
     * than leaving the previous answer standing. Failures stay out of the error handler: the user
     * did not ask for this check.
     */
    private fun checkRegisterable() {
        // Taken here rather than inside the coroutine, so the order is the order of the requests
        val generation = checkGeneration.incrementAndGet()
        vmScope.launch {
            checkLock.withLock {
                if (generation != checkGeneration.get()) {
                    log(tag) { "A newer registerability check superseded this one" }
                    return@withLock
                }
                val ids = try {
                    feederDiscovery.findRegisterable().ids
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(tag, WARN) { "Registerability check failed: ${e.asLog()}" }
                    emptySet()
                }
                if (generation == checkGeneration.get()) registerableIds.value = ids
            }
        }
    }

    fun startFeeding() = launch {
        webpageTool.open(AirplanesLive.URL_START_FEEDING)
    }

    fun setSortMode(mode: FeederSortMode) = launch {
        log(tag) { "setSortMode($mode)" }
        feederSettings.feederSortMode.value(mode)
    }

    fun openFeederAction(feederId: String) {
        navTo(DestinationFeederAction(receiverId = feederId))
    }

    fun showFeedsOnMap(feederIds: Set<String>) = launch {
        log(tag) { "showFeedsOnMap($feederIds)" }
        val ids = feederIds.map { it.toMapFeedId() }.toSet()
        // Only the website knows which aircraft a feeder received
        webpageTool.open(MapOptions(feeds = ids).createUrl())
    }

    fun goToAddFeeder() {
        navTo(DestinationAddFeeder())
    }

    /** The linked feeder's id fills the form instead of asking for an id the app already has. */
    fun goToAddLinkedFeeder(feederId: ReceiverId) {
        log(tag) { "goToAddLinkedFeeder()" }
        navTo(DestinationAddFeeder(receiverId = feederId))
    }

    fun goToLinkFeeder() {
        navTo(DestinationUpgrade)
    }

    fun goToRegisterFeeder() {
        navTo(DestinationUpgradeFeeder)
    }

    fun unlinkFeeder() = launch {
        log(tag) { "unlinkFeeder()" }
        feederLinkRepo.unlink()
    }

    data class FeederItem(
        val feeder: Feeder,
        val isOffline: Boolean,
        val beastSparkline: List<ChartPoint> = emptyList(),
    )

    data class State(
        val linkState: FeederLinkRepo.FeederLinkState = FeederLinkRepo.FeederLinkState.Unknown,
        val feeders: List<FeederItem>,
        val feederCount: Int,
        val isRefreshing: Boolean = false,
        val header: Header = Header.ALL_ONLINE,
        val currentSortMode: FeederSortMode = FeederSortMode.BY_LABEL,
        val isPro: Boolean = false,
        /** The monitored feeder this network could register, if the free tier still has that to gain. */
        val registerableFeeder: Feeder? = null,
    )

    enum class Header { ALL_ONLINE, SOME_OFFLINE, SOME_UNCHECKED, SOME_NOT_FEEDING }

    companion object {
        /** "All online" only when every feeder's latest answer said so; not alerting is not enough. */
        internal fun headerOf(feeders: Collection<Feeder>, offline: Map<ReceiverId, Boolean>): Header = when {
            offline.values.any { it } -> Header.SOME_OFFLINE
            feeders.any { it.isUnchecked } -> Header.SOME_UNCHECKED
            feeders.any { it.isNotFeeding } -> Header.SOME_NOT_FEEDING
            else -> Header.ALL_ONLINE
        }

        private val Feeder.isUnchecked: Boolean
            get() = when (val server = server) {
                null -> observation == null || observation.isCheckFailing
                else -> server.status != FeederServerView.Status.ONLINE && server.status != FeederServerView.Status.OFFLINE
            }

        private val Feeder.isNotFeeding: Boolean
            get() = when (val server = server) {
                null -> observation?.status != FeederObservation.Status.FEEDING
                else -> server.status == FeederServerView.Status.OFFLINE || server.isIdle
            }
    }
}
