package eu.darken.apl.watch.ui

import android.location.Location
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.findByCallsign
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.alerts.WatchMonitor
import eu.darken.apl.watch.core.history.WatchActivityCheck
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.flow.combine
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import java.time.Duration
import eu.darken.apl.watch.core.WatchSortMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class WatchListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val watchRepo: WatchRepo,
    private val watchMonitor: WatchMonitor,
    private val webpageTool: WebpageTool,
    private val locationManager2: LocationManager2,
    private val aircraftRepo: AircraftRepo,
    private val historyRepo: WatchHistoryRepo,
    private val watchSettings: WatchSettings,
    private val accessRepo: AccessRepo,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Watch", "List", "ViewModel"),
) {

    private val screenOpened = MutableStateFlow(false)

    /** Until the server's own Retry-After has passed, further ticks would only be rejected again. */
    private var quotaBlockedUntil: Instant? = null

    private val sparklineCache = MutableStateFlow<Map<WatchId, WatchSparklineData>>(emptyMap())

    init {
        // Initial load of all sparklines
        launch {
            loadAllSparklines()
        }

        // Incremental updates when new checks arrive
        historyRepo.firehose
            .mapNotNull { it }
            .onEach { check ->
                val since7d = Instant.now().minus(Duration.ofDays(7))
                val watches = watchRepo.watches.first()
                val watch = watches.find { it.id == check.watchId } ?: return@onEach
                val data = loadSparkline(check.watchId, watch, since7d)
                sparklineCache.update { it + (check.watchId to data) }
            }
            .launchInViewModel()
    }

    private suspend fun loadAllSparklines() {
        val watches = watchRepo.watches.first()
        if (watches.isEmpty()) return

        val since7d = Instant.now().minus(Duration.ofDays(7))
        val allIds = watches.map { it.id }.toSet()
        val batchRows = historyRepo.getSparklineDataBatch(allIds, since7d)

        val result = watches.associate { watch ->
            val rows = batchRows[watch.id] ?: emptyList()
            val data = when (watch) {
                is AircraftWatch, is FlightWatch -> WatchSparklineData.Activity(
                    rows.map { WatchActivityCheck(it.checkedAt, it.aircraftCount) }
                )
                is SquawkWatch, is LocationWatch -> WatchSparklineData.Count(
                    rows.map { ChartPoint(it.checkedAt, it.aircraftCount.toDouble()) }
                )
            }
            watch.id to data
        }
        sparklineCache.update { it + result }
    }

    private suspend fun loadSparkline(watchId: WatchId, watch: Watch, since: Instant): WatchSparklineData {
        return when (watch) {
            is AircraftWatch, is FlightWatch -> {
                val data = historyRepo.getActivityData(watchId, since)
                WatchSparklineData.Activity(data.checks)
            }
            is SquawkWatch, is LocationWatch -> {
                val data = historyRepo.getCountChartData(watchId, since)
                WatchSparklineData.Count(data.counts)
            }
        }
    }

    val state = combine(
        watchRepo.status,
        locationManager2.state,
        watchRepo.isRefreshing,
        sparklineCache,
        watchSettings.watchSortMode.flow,
        accessRepo.state,
    ) { alerts, locationState, isRefreshing, sparklines, sortMode, access ->
        val ourLocation = (locationState as? LocationManager2.State.Available)?.location

        val sorted = when (sortMode) {
            WatchSortMode.BY_NOTE -> alerts.sortedWith(
                compareBy<Watch.Status> { it.note.isBlank() }
                    .thenBy { it.note }
                    .thenByDescending { it.watch.addedAt }
            )

            WatchSortMode.BY_LAST_SEEN -> alerts.sortedWith(
                compareBy<Watch.Status> { it.lastSeenAt == null }
                    .thenByDescending { it.lastSeenAt }
                    .thenByDescending { it.watch.addedAt }
            )

            WatchSortMode.BY_CREATED -> alerts.sortedByDescending { it.watch.addedAt }
        }

        val items = sorted
            .map { alert ->
                when (alert) {
                    is AircraftWatch.Status -> {
                        val aircraft = aircraftRepo.findByHex(alert.hex)
                        WatchItem.Single(
                            status = alert,
                            aircraft = aircraft,
                            ourLocation = ourLocation,
                            sparkline = sparklines[alert.id] as? WatchSparklineData.Activity,
                        )
                    }

                    is FlightWatch.Status -> {
                        val aircraft = aircraftRepo.findByCallsign(alert.callsign)
                        WatchItem.Single(
                            status = alert,
                            aircraft = aircraft,
                            ourLocation = ourLocation,
                            sparkline = sparklines[alert.id] as? WatchSparklineData.Activity,
                        )
                    }

                    is SquawkWatch.Status -> WatchItem.Multi(
                        status = alert,
                        ourLocation = ourLocation,
                        sparkline = sparklines[alert.id] as? WatchSparklineData.Count,
                    )

                    is LocationWatch.Status -> WatchItem.Multi(
                        status = alert,
                        ourLocation = ourLocation,
                        sparkline = sparklines[alert.id] as? WatchSparklineData.Count,
                    )
                }
            }
        State(
            items = items,
            isRefreshing = isRefreshing,
            currentSortMode = sortMode,
            allowance = access?.takeIf { it.showsAllowances }?.usage?.watch,
            allowanceResetsAt = access?.resetsAt,
            gatedWatchTypes = WatchType.entries
                .filter { type -> access?.allowsWatchType(type.serverType) == false }
                .toSet(),
        )
    }.asStateFlow()

    fun setSortMode(mode: WatchSortMode) = launch {
        log(tag) { "setSortMode($mode)" }
        watchSettings.watchSortMode.value(mode)
    }

    /** Pull to refresh always spends an evaluation, the user asked for it. */
    fun refresh() = launch {
        log(tag) { "refresh()" }
        check(WatchMonitor.Trigger.MANUAL)
    }

    /** Opening the list only checks when the last result is old enough to be worth an evaluation. */
    fun onScreenOpened() = launch {
        if (screenOpened.value) return@launch
        screenOpened.value = true
        val age = Duration.between(watchSettings.lastCheck.value(), Instant.now())
        if (age < WatchSettings.FOREGROUND_CHECK_MAX_AGE) {
            log(tag) { "Last check was ${age.toMinutes()}min ago, not checking" }
            return@launch
        }
        check(WatchMonitor.Trigger.SCREEN_OPEN)
    }

    /**
     * A repeating check for upgraded users, for as long as the list is actually on screen. The
     * caller ties this to the host's lifecycle: composition outlives the visible screen, and each
     * tick spends one evaluation per watch out of an allowance shared by every linked installation.
     */
    suspend fun pollWhileVisible() {
        upgradeRepo.upgradeInfo
            .map { it.isSettled && it.isPro }
            .distinctUntilChanged()
            .flatMapLatest { isPro ->
                if (!isPro) emptyFlow() else flow {
                    while (true) {
                        delay(WatchSettings.PRO_FOREGROUND_CHECK_INTERVAL.toMillis())
                        tick()
                        emit(Unit)
                    }
                }
            }
            .collect { }
    }

    private suspend fun tick() {
        if (watchRepo.isRefreshing.value) {
            log(tag) { "A check is still running, skipping this tick" }
            return
        }
        val remaining = accessRepo.state.value?.usage?.watch?.remaining
        if (remaining != null && remaining <= 0) {
            log(tag) { "The watch allowance is used up, skipping this tick" }
            return
        }
        quotaBlockedUntil?.let { until ->
            if (Instant.now() < until) {
                log(tag) { "The server asked to wait until $until, skipping this tick" }
                return
            }
        }
        check(WatchMonitor.Trigger.FOREGROUND)
    }

    private suspend fun check(trigger: WatchMonitor.Trigger) {
        watchRepo.isRefreshing.value = true
        try {
            watchMonitor.check(trigger)
            quotaBlockedUntil = null
        } catch (e: ServerApiException) {
            if (e.code == ServerCodes.QUOTA_EXCEEDED) {
                val wait = e.retryAfterSeconds ?: WatchSettings.PRO_FOREGROUND_CHECK_INTERVAL.seconds
                quotaBlockedUntil = Instant.now().plusSeconds(wait)
            }
            log(tag, WARN) { "Check failed: ${e.message}" }
        } catch (e: Exception) {
            log(tag, WARN) { "Check failed: ${e.message}" }
        } finally {
            watchRepo.isRefreshing.value = false
        }
    }

    fun goUpgrade() = navTo(DestinationUpgrade)

    fun openWatchDetails(watchId: String) {
        navTo(DestinationWatchDetails(watchId = watchId))
    }

    fun openThumbnail(meta: PlanespottersMeta) = launch {
        webpageTool.open(meta.link)
    }

    fun showAircraftDetails(aircraft: Aircraft) {
        navTo(DestinationSearchAction(hex = aircraft.hex))
    }

    fun showSquawkInSearch(squawk: String) {
        navTo(DestinationSearch(targetSquawks = listOf(squawk)))
    }

    fun deleteSelected(ids: Set<WatchId>) = launch {
        if (ids.isEmpty()) return@launch
        log(tag) { "deleteSelected(${ids.size} items)" }
        watchRepo.deleteBatch(ids)
    }

    fun showAddWatchOptions(type: WatchType) {
        when (type) {
            WatchType.FLIGHT -> navTo(DestinationCreateFlightWatch())
            WatchType.AIRCRAFT -> navTo(DestinationCreateAircraftWatch())
            WatchType.SQUAWK -> navTo(DestinationCreateSquawkWatch())
            WatchType.LOCATION -> navTo(DestinationCreateLocationWatch())
        }
    }

    /** [serverType] is the type name the server's tier policy lists in `watchTypes`. */
    enum class WatchType(val serverType: String) {
        FLIGHT("callsign"),
        AIRCRAFT("hex"),
        SQUAWK("squawk"),
        LOCATION("location"),
        ;
    }

    sealed interface WatchSparklineData {
        data class Count(val points: List<ChartPoint>) : WatchSparklineData
        data class Activity(val checks: List<WatchActivityCheck>) : WatchSparklineData
    }

    sealed interface WatchItem {
        val status: Watch.Status

        data class Single(
            override val status: Watch.Status,
            val aircraft: Aircraft?,
            val ourLocation: Location?,
            val sparkline: WatchSparklineData.Activity? = null,
        ) : WatchItem

        data class Multi(
            override val status: Watch.Status,
            val ourLocation: Location?,
            val sparkline: WatchSparklineData.Count? = null,
        ) : WatchItem
    }

    data class State(
        val items: List<WatchItem>,
        val isRefreshing: Boolean = false,
        val currentSortMode: WatchSortMode = WatchSortMode.BY_NOTE,
        val allowance: Allowance? = null,
        val allowanceResetsAt: Instant? = null,
        /** Watch types the current tier does not evaluate, the list offers them behind the upgrade. */
        val gatedWatchTypes: Set<WatchType> = emptySet(),
    )
}

private val Watch.Status.lastSeenAt: Instant?
    get() = tracked.mapNotNull { it.messageSeenAt }.maxOrNull() ?: lastHit?.checkAt
