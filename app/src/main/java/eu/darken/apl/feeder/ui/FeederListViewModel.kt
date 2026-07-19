package eu.darken.apl.feeder.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.account.ui.DestinationAccount
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.config.FeederSettings
import eu.darken.apl.feeder.core.config.FeederSortMode
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.feeder.core.stats.FeederChartWindow
import eu.darken.apl.feeder.core.stats.FeederMetricFamily
import eu.darken.apl.feeder.core.stats.FeederStatsRepo
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.toMapFeedId
import eu.darken.apl.map.ui.DestinationMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.util.Collections
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class FeederListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
    private val feederSettings: FeederSettings,
    private val feederStatsRepo: FeederStatsRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "List", "ViewModel"),
) {

    init {
        // Pull fresh data on entry (no-op when logged out).
        launch { feederRepo.refresh() }
    }

    // Decorative row sparklines: loaded lazily per visible row, failures swallowed (a broken
    // sparkline must not raise the global error UI). Failed IDs leave the requested set so the
    // next list visit or pull-to-refresh retries them.
    private val sparklines = MutableStateFlow<Map<String, List<ChartPoint>>>(emptyMap())
    private val sparklineRequested: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val sparklineGeneration = MutableStateFlow(0)

    fun loadSparkline(feederId: String) = launch {
        if (!sparklineRequested.add(feederId)) return@launch
        try {
            val points = feederStatsRepo.getDetail(feederId)
                .history[FeederChartWindow.H24]
                ?.chartPoints(FeederMetricFamily.Feed.WIRE_KEY, "messages_per_sec")
                ?: emptyList()
            sparklines.update { it + (feederId to points) }
        } catch (e: Exception) {
            log(tag, WARN) { "Sparkline load failed for $feederId: ${e.asLog()}" }
            sparklineRequested.remove(feederId)
        }
    }

    private val sparklineState = combine(sparklines, sparklineGeneration) { lines, generation ->
        lines to generation
    }

    val state = combine(
        feederRepo.isLoggedIn,
        feederRepo.feeders,
        feederRepo.isRefreshing,
        feederSettings.feederSortMode.flow,
        sparklineState,
    ) { isLoggedIn, feeders, isRefreshing, sortMode, (sparklines, sparklineGeneration) ->
        val sorted = when (sortMode) {
            FeederSortMode.BY_LABEL -> feeders.sortedBy { it.label.lowercase() }
            FeederSortMode.BY_STATUS -> feeders.sortedWith(compareBy({ it.status.ordinal }, { it.label.lowercase() }))
            FeederSortMode.BY_LAST_SEEN -> feeders.sortedByDescending { it.lastSeen ?: Instant.EPOCH }
        }
        State(
            isLoggedIn = isLoggedIn,
            feeders = sorted,
            feederCount = sorted.size,
            isRefreshing = isRefreshing,
            hasOfflineFeeders = sorted.any { it.status == FeederStatus.INACTIVE },
            currentSortMode = sortMode,
            sparklines = sparklines,
            sparklineGeneration = sparklineGeneration,
        )
    }.asStateFlow()

    fun refresh() = launch {
        log(tag) { "refresh()" }
        // Visible rows re-request their sparkline (keyed on the generation) with fresh data.
        sparklineRequested.clear()
        sparklineGeneration.update { it + 1 }
        feederRepo.refresh()
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

    fun goToLogin() {
        navTo(DestinationAccount)
    }

    fun claimFeeders() = launch {
        webpageTool.open(AirplanesLive.URL_START_FEEDING)
    }

    data class State(
        val isLoggedIn: Boolean,
        val feeders: List<Feeder>,
        val feederCount: Int,
        val isRefreshing: Boolean = false,
        val hasOfflineFeeders: Boolean = false,
        val currentSortMode: FeederSortMode = FeederSortMode.BY_LABEL,
        val sparklines: Map<String, List<ChartPoint>> = emptyMap(),
        val sparklineGeneration: Int = 0,
    )
}
