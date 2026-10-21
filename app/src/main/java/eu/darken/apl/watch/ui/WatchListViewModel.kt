package eu.darken.apl.watch.ui

import android.location.Location
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.isLiveAt
import eu.darken.apl.main.core.findByCallsign
import eu.darken.apl.main.core.findByHex
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchLimit
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.alerts.WatchMonitor
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.history.WatchHistoryRepo
import eu.darken.apl.watch.core.server.WatchServerMode
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.flow.combine
import eu.darken.apl.watch.core.WatchSettings
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val serverClock: ServerClock,
    private val serverMode: WatchServerMode,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Watch", "List", "ViewModel"),
) {

    private val screenOpenLock = Mutex()

    /** Until the server's own Retry-After has passed, further ticks would only be rejected again. */
    private var quotaBlockedUntil: Instant? = null

    /** Ages and "live" labels move on between checks, without spending one. */
    private val clockTicks = flow {
        while (true) {
            emit(Unit)
            delay(CLOCK_TICK.toMillis())
        }
    }

    val state = combine(
        watchRepo.status,
        locationManager2.state,
        watchRepo.isRefreshing,
        historyRepo.activityByWatch(ACTIVITY_WINDOW),
        historyRepo.historyLastSeen,
        watchSettings.watchSortMode.flow,
        accessRepo.state,
        watchRepo.limit,
        clockTicks,
        serverMode.state,
    ) { alerts, locationState, isRefreshing, activity, historyLastSeen, sortMode, access, watchLimit, _, mode ->
        val ourLocation = (locationState as? LocationManager2.State.Available)?.location
        val now = serverClock.now()
        fun Watch.Status.liveCount(): Int = tracked.count { it.isLiveAt(now) }
        fun Watch.Status.lastSeenAt(): Instant? = listOfNotNull(lastSeenAt, historyLastSeen[id]).maxOrNull()

        val sorted = when (sortMode) {
            WatchSortMode.BY_NOTE -> alerts.sortedWith(
                compareBy<Watch.Status> { it.note.isBlank() }
                    .thenBy { it.note }
                    .thenByDescending { it.watch.addedAt }
            )

            WatchSortMode.BY_LAST_SEEN -> alerts.sortedWith(
                compareBy<Watch.Status> { it.lastSeenAt() == null }
                    .thenByDescending { it.lastSeenAt() }
                    .thenByDescending { it.watch.addedAt }
            )

            WatchSortMode.BY_CREATED -> alerts.sortedByDescending { it.watch.addedAt }
        }

        val items = sorted
            .map { alert ->
                when (alert) {
                    is AircraftWatch.Status -> {
                        val aircraft = alert.tracked.firstOrNull() ?: aircraftRepo.findByHex(alert.hex)
                        WatchItem.Single(
                            status = alert,
                            aircraft = aircraft,
                            registration = aircraft?.registration ?: alert.watch.lastRegistration,
                            ourLocation = ourLocation,
                            liveCount = alert.liveCount(),
                            activity = activity[alert.id],
                            historyLastSeenAt = historyLastSeen[alert.id],
                            serverLabel = WatchServerLabel.of(alert, mode.isActive),
                        )
                    }

                    is FlightWatch.Status -> {
                        val aircraft = alert.tracked.firstOrNull() ?: aircraftRepo.findByCallsign(alert.callsign)
                        WatchItem.Single(
                            status = alert,
                            aircraft = aircraft,
                            registration = aircraft?.registration,
                            ourLocation = ourLocation,
                            liveCount = alert.liveCount(),
                            activity = activity[alert.id],
                            historyLastSeenAt = historyLastSeen[alert.id],
                            serverLabel = WatchServerLabel.of(alert, mode.isActive),
                        )
                    }

                    is SquawkWatch.Status -> WatchItem.Multi(
                        status = alert,
                        ourLocation = ourLocation,
                        liveCount = alert.liveCount(),
                        activity = activity[alert.id],
                        historyLastSeenAt = historyLastSeen[alert.id],
                        serverLabel = WatchServerLabel.of(alert, mode.isActive),
                    )

                    is LocationWatch.Status -> WatchItem.Multi(
                        status = alert,
                        ourLocation = ourLocation,
                        liveCount = alert.liveCount(),
                        activity = activity[alert.id],
                        historyLastSeenAt = historyLastSeen[alert.id],
                        serverLabel = WatchServerLabel.of(alert, mode.isActive),
                    )
                }
            }
        State(
            items = items,
            now = now,
            isRefreshing = isRefreshing,
            currentSortMode = sortMode,
            // The server's watch list does not spend the allowance, only the watches the app still checks do
            isAllowanceExhausted = access?.takeIf { it.showsAllowances }?.usage?.watch?.let { it.remaining <= 0 } == true &&
                    (!mode.isActive || alerts.any { mode.isLegacy(it.watch) }),
            // A reset that already passed is waiting for fresh counters, not a time to promise
            allowanceResetsAt = access?.watchResetsAt()?.takeIf { it > now },
            gatedWatchTypes = WatchType.entries
                .filter { type -> access?.allowsWatchType(type.serverType) == false }
                .toSet(),
            watchLimit = watchLimit,
            overLimitBanner = OverLimitBanner.of(mode.isActive, mode.access, alerts),
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
        // Held through the check, so an overlapping open sees the result of the first one
        screenOpenLock.withLock {
            // Server time both sides, so a wrong device clock cannot age this
            val age = Duration.between(watchSettings.lastCheck.value(), serverClock.now())
            if (age < WatchSettings.FOREGROUND_CHECK_MAX_AGE) {
                log(tag) { "Last check was ${age.toMinutes()}min ago, not checking" }
                return@launch
            }
            check(WatchMonitor.Trigger.SCREEN_OPEN)
        }
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
        val exhausted = remaining != null && remaining <= 0
        val serverModeActive = serverMode.current().isActive
        if (exhausted && !serverModeActive) {
            log(tag) { "The watch allowance is used up, skipping this tick" }
            return
        }
        quotaBlockedUntil?.let { until ->
            if (Instant.now() < until) {
                log(tag) { "The server asked to wait until $until, skipping this tick" }
                return
            }
        }
        check(WatchMonitor.Trigger.FOREGROUND, legacy = !exhausted)
    }

    private suspend fun check(trigger: WatchMonitor.Trigger, legacy: Boolean = true) {
        watchRepo.isRefreshing.value = true
        try {
            watchMonitor.check(trigger, legacy)
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

    companion object {
        val ACTIVITY_WINDOW: Duration = Duration.ofDays(7)
        private val CLOCK_TICK: Duration = Duration.ofSeconds(30)
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

    /** A squawk's matches are a search away, a location's are shown on the map. */
    fun showAllTracked(status: Watch.Status) {
        when (status) {
            is SquawkWatch.Status -> navTo(
                DestinationSearch(targetSquawks = listOf(status.squawk))
            )

            else -> navTo(DestinationMap(mapOptions = MapOptions.focusAircraft(status.tracked)))
        }
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

    sealed interface WatchItem {
        val status: Watch.Status

        /** Hour by hour, from the app's own checks and the server's history. */
        val activity: WatchActivityTimeline?

        /** The latest sighting in the server's history, which the app's own checks may have missed. */
        val historyLastSeenAt: Instant?

        /** Tracked aircraft heard from recently enough to count as live, see [isLiveAt]. */
        val liveCount: Int

        val serverLabel: WatchServerLabel?

        data class Single(
            override val status: Watch.Status,
            val aircraft: Aircraft?,
            val ourLocation: Location?,
            override val activity: WatchActivityTimeline? = null,
            override val historyLastSeenAt: Instant? = null,
            /** The current one when known, else the last one an aircraft watch's checks saw. */
            val registration: Registration? = aircraft?.registration,
            override val liveCount: Int = 0,
            override val serverLabel: WatchServerLabel? = null,
        ) : WatchItem

        data class Multi(
            override val status: Watch.Status,
            val ourLocation: Location?,
            override val activity: WatchActivityTimeline? = null,
            override val historyLastSeenAt: Instant? = null,
            override val liveCount: Int = 0,
            override val serverLabel: WatchServerLabel? = null,
        ) : WatchItem
    }

    data class State(
        val items: List<WatchItem>,
        /** Server time, the clock observations are stamped with. */
        val now: Instant = Instant.now(),
        val isRefreshing: Boolean = false,
        val currentSortMode: WatchSortMode = WatchSortMode.BY_NOTE,
        val isAllowanceExhausted: Boolean = false,
        val allowanceResetsAt: Instant? = null,
        /** Watch types the current tier does not evaluate, the list offers them behind the upgrade. */
        val gatedWatchTypes: Set<WatchType> = emptySet(),
        val watchLimit: WatchLimit = WatchLimit(count = 0, max = null),
        val overLimitBanner: OverLimitBanner? = null,
    )
}

/** The watch counter can still be in the period before another bucket's rollover moved the shared one. */
private fun AccessState.watchResetsAt(): Instant =
    bucketPeriods[AccessRepo.BUCKET_WATCH]?.let(Instant::ofEpochMilli) ?: resetsAt

private val Watch.Status.lastSeenAt: Instant?
    get() = tracked.mapNotNull { it.messageSeenAt }.maxOrNull() ?: lastHit?.checkAt
