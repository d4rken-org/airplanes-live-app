package eu.darken.apl.feeder.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.Feeder
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import eu.darken.apl.common.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class FeederListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
    private val feederSettings: FeederSettings,
    private val feederStatsDatabase: FeederStatsDatabase,
    private val feederLinkRepo: FeederLinkRepo,
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

    val state = combine(
        refreshTimer,
        feederRepo.feeders,
        feederRepo.isRefreshing,
        feederSettings.feederSortMode.flow,
        sparklineData,
        feederLinkRepo.state,
    ) { _, feeders, isRefreshing, sortMode, sparklines, linkState ->
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
        )
    }.asStateFlow()

    fun refresh() = launch {
        log(tag) { "refresh()" }
        // The link state is independent of the public feeder stats, a failure there must not skip it
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

    fun goToLinkFeeder() {
        navTo(DestinationLinkFeeder)
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
    )
}
