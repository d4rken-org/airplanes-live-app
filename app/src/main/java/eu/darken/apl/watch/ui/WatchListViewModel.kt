package eu.darken.apl.watch.ui

import android.location.Location
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.common.coroutine.DispatcherProvider
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import java.time.Duration
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
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Watch", "List", "ViewModel"),
) {

    private val refreshTimer = callbackFlow {
        while (isActive) {
            refresh()
            send(Unit)
            delay(60 * 1000)
        }
        awaitClose()
    }

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
        refreshTimer,
        watchRepo.status,
        locationManager2.state,
        watchRepo.isRefreshing,
        sparklineCache,
    ) { _, alerts, locationState, isRefreshing, sparklines ->
        val ourLocation = (locationState as? LocationManager2.State.Available)?.location

        val items = alerts
            .sortedByDescending { it.watch.addedAt }
            .sortedBy { alert ->
                alert.note.takeIf { it.isNotBlank() } ?: "ZZZZ"
            }
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
        )
    }.asStateFlow()

    fun refresh() = launch {
        log(tag) { "refresh()" }
        watchMonitor.check()
    }

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

    fun showAddWatchOptions(type: WatchType) {
        when (type) {
            WatchType.FLIGHT -> navTo(DestinationCreateFlightWatch())
            WatchType.AIRCRAFT -> navTo(DestinationCreateAircraftWatch())
            WatchType.SQUAWK -> navTo(DestinationCreateSquawkWatch())
            WatchType.LOCATION -> navTo(DestinationCreateLocationWatch())
        }
    }

    enum class WatchType { FLIGHT, AIRCRAFT, SQUAWK, LOCATION }

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
    )
}
