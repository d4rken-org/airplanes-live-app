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
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.config.FeederSortMode
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.feeder.core.stats.FeederStatsDatabase
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.toMapFeedId
import eu.darken.apl.map.ui.DestinationMap
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

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val sparklineData = combine(
        feederStatsDatabase.beastStats.firehose().debounce(2_000),
        feederSettings.feederGroup.flow,
    ) { _, group ->
        val since7d = Instant.now().minus(Duration.ofDays(7))
        group.configs.associate { config ->
            config.receiverId to feederRepo.getBeastChartData(config.receiverId, since7d).messageRate
        }
    }.stateIn(vmScope, SharingStarted.Eagerly, emptyMap())

    init {
        checkRegisterable()
    }

    val state = combine(
        refreshTimer,
        feederRepo.feeders,
        feederRepo.isRefreshing,
        feederSettings.feederSortMode.flow,
        sparklineData,
        feederLinkRepo.state,
        upgradeRepo.upgradeInfo,
        registerableIds,
    ) { _, feeders, isRefreshing, sortMode, sparklines, linkState, upgrade, registerable ->
        val offlineStates = feeders.associate { it.id to feederRepo.isOffline(it) }

        val sortedFeeders = when (sortMode) {
            FeederSortMode.BY_LABEL -> feeders.sortedBy { it.label }
            FeederSortMode.BY_MESSAGE_RATE -> feeders.sortedByDescending { it.beastMessageRate }
        }

        val feederItems = sortedFeeders.map { feeder ->
            FeederItem(
                feeder = feeder,
                isOffline = offlineStates[feeder.id]!!,
                beastSparkline = sparklines[feeder.id] ?: emptyList(),
            )
        }

        State(
            feeders = feederItems,
            feederCount = feederItems.size,
            isRefreshing = isRefreshing,
            hasOfflineFeeders = offlineStates.values.any { it },
            currentSortMode = sortMode,
            linkState = linkState,
            isPro = upgrade.isSettled && upgrade.isPro,
            registerableFeeder = feederItems.firstOrNull { it.feeder.id in registerable }?.feeder,
        )
    }.asStateFlow()

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
        failure?.let { throw it }
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
        navTo(DestinationMap(mapOptions = MapOptions(feeds = ids)))
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
        val hasOfflineFeeders: Boolean = false,
        val currentSortMode: FeederSortMode = FeederSortMode.BY_LABEL,
        val isPro: Boolean = false,
        /** The monitored feeder this network could register, if the free tier still has that to gain. */
        val registerableFeeder: Feeder? = null,
    )
}
