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
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.toMapFeedId
import eu.darken.apl.map.ui.DestinationMap
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class FeederListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val webpageTool: WebpageTool,
    private val feederSettings: FeederSettings,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "List", "ViewModel"),
) {

    init {
        // Pull fresh data on entry (no-op when logged out).
        launch { feederRepo.refresh() }
    }

    val state = combine(
        feederRepo.isLoggedIn,
        feederRepo.feeders,
        feederRepo.isRefreshing,
        feederSettings.feederSortMode.flow,
    ) { isLoggedIn, feeders, isRefreshing, sortMode ->
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
        )
    }.asStateFlow()

    fun refresh() = launch {
        log(tag) { "refresh()" }
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
    )
}
