package eu.darken.apl.feeder.ui.detail

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.ClipboardHelper
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.monitor.FeederMuteController
import eu.darken.apl.feeder.core.stats.FeederChartWindow
import eu.darken.apl.feeder.core.stats.FeederDetail
import eu.darken.apl.feeder.core.stats.FeederStatsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class FeederDetailViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val feederStatsRepo: FeederStatsRepo,
    private val muteController: FeederMuteController,
    private val clipboardHelper: ClipboardHelper,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "Detail", "ViewModel"),
) {

    sealed interface DetailState {
        data object Loading : DetailState
        data class Ready(val detail: FeederDetail, val isRefreshing: Boolean = false) : DetailState
        data class Error(val error: Throwable) : DetailState
    }

    private var feederId: ReceiverId = ""
    private val window = MutableStateFlow(FeederChartWindow.H24)
    private val detailState = MutableStateFlow<DetailState>(DetailState.Loading)

    // Serializes initial load, pull-to-refresh, and auto-refresh so their Loading/Ready state
    // transitions can't interleave. Manual paths wait their turn; auto-refresh skips when busy.
    private val refreshLock = Mutex()

    fun init(receiverId: ReceiverId) {
        if (feederId == receiverId) return
        feederId = receiverId

        // Confirmed removal only: the feeder was present in the account's list and then vanished.
        // A cold start (empty cache while login/refresh settles) must NOT navigate away.
        var wasPresent = false
        feederRepo.feeders
            .onEach { feeders ->
                val present = feeders.any { it.id == feederId }
                if (present) {
                    wasPresent = true
                } else if (wasPresent) {
                    log(tag) { "Feeder $feederId is no longer owned" }
                    navUp()
                }
            }
            .launchInViewModel()

        feederRepo.isLoggedIn
            .filter { !it }
            .take(1)
            .onEach {
                log(tag) { "Logged out, leaving feeder detail" }
                navUp()
            }
            .launchInViewModel()

        launch {
            refreshLock.withLock {
                // Cold start (e.g. notification deep link): the header needs the list entry too.
                if (feederRepo.feeders.first().none { it.id == feederId }) {
                    refreshFeederList()
                }
                loadDetailInner(silent = false)
            }
        }
    }

    val state = combine(
        feederRepo.feeders,
        detailState,
        window,
        muteController.mutedFeeders,
    ) { feeders, detail, window, muted ->
        State(
            feeder = feeders.singleOrNull { it.id == feederId },
            detail = detail,
            window = window,
            isMuted = feederId in muted,
        )
    }.asStateFlow()

    fun refresh() = launch {
        log(tag) { "refresh()" }
        // The spinner has to show while we wait for the lock and the list request, not just
        // once the detail request starts.
        setRefreshIndicator()
        refreshLock.withLock {
            // An auto-refresh cycle that held the lock has overwritten the state meanwhile.
            setRefreshIndicator()
            refreshFeederList()
            loadDetailInner(silent = false)
        }
    }

    fun autoRefresh() = launch {
        if (!refreshLock.tryLock()) {
            log(tag) { "autoRefresh(): refresh already in flight, skipping" }
            return@launch
        }
        try {
            log(tag) { "autoRefresh()" }
            refreshFeederList()
            loadDetailInner(silent = true)
        } finally {
            refreshLock.unlock()
        }
    }

    fun setWindow(window: FeederChartWindow) {
        this.window.value = window
    }

    fun setNotificationsEnabled(enabled: Boolean) = launch {
        muteController.setMuted(feederId, !enabled)
    }

    fun copyFeederId() = launch {
        // Full UUID on purpose: the visible short id is just the URL-facing suffix.
        clipboardHelper.copyToClipboard(feederId)
    }

    /** Keep the header (status/last seen from the list endpoint) as fresh as the detail data. */
    private suspend fun refreshFeederList() {
        try {
            feederRepo.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(tag, WARN) { "List refresh failed: ${e.asLog()}" }
        }
    }

    private fun setRefreshIndicator() {
        detailState.value = when (val current = detailState.value) {
            is DetailState.Ready -> current.copy(isRefreshing = true)
            else -> DetailState.Loading
        }
    }

    private suspend fun loadDetailInner(silent: Boolean) {
        if (!silent) setRefreshIndicator()
        try {
            val detail = feederStatsRepo.getDetail(feederId, forceRefresh = true)
            // A silent cycle must not clear a spinner a queued manual refresh has already shown.
            val keepSpinner = silent && (detailState.value as? DetailState.Ready)?.isRefreshing == true
            detailState.value = DetailState.Ready(detail, isRefreshing = keepSpinner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 404) {
                // The server confirmed this feeder isn't ours (removed/transferred).
                log(tag, WARN) { "Feeder $feederId not found, leaving detail" }
                navUp()
                return
            }
            log(tag, WARN) { "Detail load failed (silent=$silent): ${e.asLog()}" }
            detailState.value = when (val current = detailState.value) {
                // Keep showing the last good data; the refresh spinner just stops.
                is DetailState.Ready -> current.copy(isRefreshing = if (silent) current.isRefreshing else false)
                else -> if (silent) current else DetailState.Error(e)
            }
        }
    }

    data class State(
        val feeder: Feeder?,
        val detail: DetailState,
        val window: FeederChartWindow,
        val isMuted: Boolean,
    )
}
