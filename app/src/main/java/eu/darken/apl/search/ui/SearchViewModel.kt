package eu.darken.apl.search.ui

import android.location.Location
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.search.core.buildSearchQuery
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.search.core.SearchCategory
import eu.darken.apl.search.core.SearchInput
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchSettings
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.server.ServerClock
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val searchRepo: SearchRepo,
    private val webpageTool: WebpageTool,
    private val locationManager2: LocationManager2,
    private val settings: SearchSettings,
    watchRepo: WatchRepo,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Search", "ViewModel"),
) {

    private var initialized = false

    val events = SingleEventFlow<SearchEvents>()

    private val currentInput = MutableStateFlow<SearchInput?>(null)

    private val currentResult = MutableStateFlow<ShownResult?>(null)
    private val isSearching = MutableStateFlow(false)
    private val submitGeneration = AtomicInteger()
    private val inputLock = Mutex()
    private val nearbyAwaitsPermission = AtomicBoolean(false)

    fun init(
        targetHexes: List<String>? = null,
        targetSquawks: List<String>? = null,
        targetCallsigns: List<String>? = null,
    ) {
        if (initialized) return
        initialized = true

        log(tag, INFO) { "init: targetHexes=$targetHexes, targetSquawks=$targetSquawks, targetCallsigns=$targetCallsigns" }

        launch {
            if (currentInput.value != null) return@launch

            val targets = targetHexes ?: targetSquawks ?: targetCallsigns
            if (targets == null) {
                currentInput.value = settings.lastInput.value()
                return@launch
            }
            // A search opened for specific aircraft is not what the user typed, so it isn't remembered
            val input = SearchInput(text = targets.joinToString(" "))
            currentInput.value = input
            submit(input)
        }
    }

    @Volatile private var lastShownError: Throwable? = null

    val state = combine(
        currentInput.filterNotNull(),
        currentResult,
        isSearching,
        watchRepo.watches,
        settings.searchLocationDismissed.flow,
        locationManager2.state,
        accessRepo.state,
    ) { input, shown, searching, alerts, locationDismissed, locationState, access ->
        val result = shown?.result
        val error = result?.error
        if (error != null && error !== lastShownError) {
            lastShownError = error
            val isNetworkError = error is java.net.UnknownHostException ||
                    error is java.net.SocketTimeoutException ||
                    error is java.net.ConnectException
            if (!isNetworkError) events.tryEmit(SearchEvents.SearchError(error, result?.charged ?: SearchRepo.Charged.SEARCH))
        }

        val items = mutableListOf<SearchItem>()

        if (!locationDismissed && (locationState as? LocationManager2.State.Unavailable)?.isPermissionIssue == true) {
            items.add(SearchItem.LocationPrompt)
        }

        val answered = result?.terms?.mapNotNull { it.outcome as? TermOutcome.Answered } ?: emptyList()
        // Only a lone term can speak for the whole query: per-term totals overlap, and the aircraft
        // list is deduplicated across terms and padded with cached matches for the unresolved ones
        val loneCapped = answered.singleOrNull()?.takeIf { it.capped }
        val isCapped = answered.any { it.capped }

        if (searching) {
            items.add(SearchItem.Searching(aircraftCount = result?.aircraft?.size ?: 0))
        } else if (result == null) {
            items.add(SearchItem.Hint)
        } else {
            if (result.aircraft.isEmpty()) {
                // Only a full answer for every term can claim nothing matched: a rejected term was
                // never evaluated, and a capped or incomplete answer may miss matches
                val answeredInFull = result.terms.isNotEmpty() && result.terms.all {
                    (it.outcome as? TermOutcome.Answered)?.let { a -> a.complete && !a.capped } == true
                }
                if (answeredInFull) items.add(SearchItem.NoResults)
            } else {
                items.add(
                    SearchItem.Summary(
                        aircraftCount = result.aircraft.size,
                        cacheOnlyCount = result.cacheOnly.size,
                        // A capped answer returned a sample, the count the user asked for is how
                        // many matched
                        totalMatching = loneCapped?.totalMatching,
                    )
                )
            }
        }

        val showsBanner = access?.showsAllowances == true
        result?.terms?.mapNotNull { it.toStatusItem(access?.resetsAt, showsBanner) }
            ?.let { items.addAll(it) }

        val serverNow = serverClock.now()
        val distanceOrigin = shown?.origin ?: (locationState as? LocationManager2.State.Available)?.location
        val cacheOnlyHexes = result?.cacheOnly?.map { it.hex }?.toSet() ?: emptySet()
        result?.aircraft
            ?.map { ac ->
                // Observation timestamps are server time, the device clock may be off by hours
                val age = ac.messageSeenAt?.let { Duration.between(it, serverNow).coerceAtLeast(Duration.ZERO) }
                val freshness = when {
                    age == null -> Freshness.OLD
                    age < Duration.ofMinutes(5) -> Freshness.LIVE
                    age < Duration.ofHours(1) -> Freshness.RECENT
                    age < Duration.ofHours(24) -> Freshness.STALE
                    else -> Freshness.OLD
                }
                SearchItem.AircraftResult(
                    aircraft = ac,
                    watch = alerts.filterIsInstance<AircraftWatch>().firstOrNull { it.matches(ac) },
                    distanceInMeter = ac.location?.let { distanceOrigin?.distanceTo(it) },
                    freshness = freshness,
                    cacheOnly = ac.hex in cacheOnlyHexes,
                )
            }
            ?.sortedBy { it.distanceInMeter ?: Float.MAX_VALUE }
            ?.run { items.addAll(this) }

        // Closes the list rather than heading it: the numbers only matter once you have read the results
        if (!searching) {
            // Nearby spends the viewing allowance, not the search one. Taken from the query rather
            // than the response, so a failed call still reports the allowance it would have spent
            val charged = result?.charged ?: if (input.nearby) SearchRepo.Charged.VIEWING else SearchRepo.Charged.SEARCH
            val allowance = when (charged) {
                SearchRepo.Charged.VIEWING -> access?.usage?.viewing
                SearchRepo.Charged.SEARCH -> access?.usage?.search
            }
            access?.takeIf { it.showsAllowances }?.let { allowance }?.let { allowance ->
                items.add(
                    SearchItem.UpgradeBanner(
                        charged = charged,
                        state = when {
                            // Pairing the row count with the term's total would compare two
                            // different sets, the rows include cached matches the answer never had
                            isCapped -> loneCapped?.totalMatching
                                ?.let { BannerState.Capped(shown = loneCapped.aircraft.size, total = it) }
                                ?: BannerState.CappedUnknown

                            allowance.remaining <= 0 -> BannerState.Exhausted(access.resetsAt)
                            else -> BannerState.Remaining(
                                remaining = allowance.remaining,
                                limit = allowance.limit,
                            )
                        }
                    )
                )
            }
        }

        State(
            input = input,
            isSearching = searching,
            items = items,
            nowMillis = serverNow.toEpochMilli(),
        )
    }.catch { e -> log(tag, eu.darken.apl.common.debug.logging.Logging.Priority.ERROR) { "State flow failed: ${e.message}" } }.asStateFlow()

    /**
     * [showsBanner] is what decides who explains a capped answer. The banner only exists on the free
     * tier, so without it the per-term line has to, or a result that was capped before an upgrade
     * would keep showing ten rows under a headline counting thousands.
     */
    private fun SearchRepo.TermResult.toStatusItem(
        resetsAt: Instant?,
        showsBanner: Boolean,
    ): SearchItem.TermStatus? {
        val termState = when (val outcome = outcome) {
            is TermOutcome.Rejected -> when (outcome.code) {
                ServerCodes.DAILY_ALLOWANCE_EXHAUSTED -> TermState.Exhausted(resetsAt)
                ServerCodes.TIER_RESTRICTED -> TermState.Restricted
                ServerCodes.RESULT_EXPIRED, ServerCodes.ACCESS_CHANGED -> TermState.Expired
                else -> TermState.Invalid
            }

            is TermOutcome.Answered -> when {
                outcome.capped -> if (showsBanner) return null else TermState.Capped(outcome.totalMatching)
                snapshot?.complete == false -> TermState.Incomplete
                snapshot?.stale == true -> TermState.Stale
                else -> return null
            }
        }
        return SearchItem.TermStatus(term = term.id, state = termState)
    }

    /** Deliberate submit only, every term is charged against the daily allowance. */
    private suspend fun submit(input: SearchInput) {
        log(tag) { "submit($input)" }
        if (input.isEmpty) return
        val generation = submitGeneration.incrementAndGet()
        isSearching.value = true
        try {
            val query = buildSearchQuery(input)
            val shown = if (input.nearby) {
                resolveNearbyLocation(input)?.let { location ->
                    val result = searchRepo.nearby(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusNm = DEFAULT_NEARBY_RADIUS_NM,
                        filter = query,
                    )
                    ShownResult(result, origin = location)
                }
            } else {
                ShownResult(searchRepo.search(query))
            }
            // A newer submit owns the screen, this answer belongs to an input no longer shown
            if (generation == submitGeneration.get()) currentResult.value = shown
        } finally {
            if (generation == submitGeneration.get()) isSearching.value = false
        }
    }

    private suspend fun resolveNearbyLocation(input: SearchInput): Location? {
        input.place?.let { place ->
            if (!locationManager2.canGeocode) {
                events.tryEmit(SearchEvents.PlaceSearchUnavailable)
                return null
            }
            return locationManager2.fromName(place).also {
                if (it == null) events.tryEmit(SearchEvents.PlaceNotFound(place))
            }
        }

        val locationState = withTimeoutOrNull(2000) {
            locationManager2.state.filter { it !is LocationManager2.State.Waiting }.first()
        }
        if (locationState is LocationManager2.State.Available) return locationState.location

        log(tag) { "Device location unavailable: $locationState" }
        val event = if ((locationState as? LocationManager2.State.Unavailable)?.isPermissionIssue == true) {
            nearbyAwaitsPermission.set(true)
            SearchEvents.RequestLocationPermission
        } else {
            SearchEvents.LocationUnavailable
        }
        events.tryEmit(event)
        return null
    }

    /** Only picks up a nearby search that asked for the permission, not the prompt card's grant. */
    fun onLocationPermissionResult(granted: Boolean) = launch {
        if (!nearbyAwaitsPermission.getAndSet(false)) return@launch
        if (!granted) {
            events.tryEmit(SearchEvents.LocationUnavailable)
            return@launch
        }
        // The location state notices the grant on its own schedule
        withTimeoutOrNull(2000) {
            locationManager2.state.first { (it as? LocationManager2.State.Unavailable)?.isPermissionIssue != true }
        }
        currentInput.value?.takeIf { it.nearby }?.let { submit(it) }
    }

    /** The keyboard action and the search button submit what the input field currently holds. */
    fun submitCurrent(text: String) = launch {
        val input = updateInput { it.copy(text = text) }
        log(tag) { "submitCurrent(): $input" }
        submit(input)
    }

    fun updateText(text: String) = launch {
        updateInput { it.copy(text = text) }
    }

    fun toggleCategory(category: SearchCategory) = launch {
        updateInput { it.copy(categories = if (category in it.categories) it.categories - category else it.categories + category) }
    }

    fun toggleNearby() = launch {
        updateInput { it.copy(nearby = !it.nearby) }
    }

    /** Null, or blank, looks around the device again. */
    fun setNearbyPlace(place: String?) = launch {
        updateInput { it.copy(nearby = true, place = place?.trim()?.takeIf { p -> p.isNotBlank() }) }
    }

    /** Serialized, so the stored input is written in the same order the screen changed. */
    private suspend fun updateInput(change: (SearchInput) -> SearchInput): SearchInput = inputLock.withLock {
        val newInput = currentInput.updateAndGet { change(it ?: SearchInput()) }!!
        settings.lastInput.value(newInput)
        log(tag) { "updateInput(): $newInput" }
        newInput
    }

    fun openAircraftAction(hex: AircraftHex) {
        navTo(DestinationSearchAction(hex = hex))
    }

    fun openThumbnail(link: String) = launch {
        webpageTool.open(link)
    }

    fun openWatch(watch: Watch) {
        navTo(DestinationWatchDetails(watchId = watch.id))
    }

    fun showOnMap(aircraft: Collection<Aircraft>) {
        log(tag) { "showOnMap(${aircraft.size} items)" }
        if (aircraft.isEmpty()) return
        navTo(DestinationMap(mapOptions = MapOptions.focusAircraft(aircraft.toSet())))
    }

    fun requestLocationPermission() {
        events.emitBlocking(SearchEvents.RequestLocationPermission)
    }

    fun dismissLocationPrompt() {
        settings.searchLocationDismissed.valueBlocking = true
    }

    fun startFeeding() = launch {
        webpageTool.open(AirplanesLive.URL_START_FEEDING)
    }

    fun goUpgrade() = navTo(DestinationUpgrade)

    /** A nearby result measures distances from the spot that was searched, not from the device. */
    private data class ShownResult(
        val result: SearchRepo.SearchResult,
        val origin: Location? = null,
    )

    enum class Freshness { LIVE, RECENT, STALE, OLD }

    sealed interface TermState {
        data class Capped(val totalMatching: Int?) : TermState
        data class Exhausted(val resetsAt: Instant?) : TermState
        data object Invalid : TermState
        data object Restricted : TermState
        data object Expired : TermState
        data object Incomplete : TermState
        data object Stale : TermState
    }

    /** What the free tier is doing to this list, as the list's closing row. */
    sealed interface BannerState {
        /** Both counts come from the same answered term, or neither is shown. */
        data class Capped(val shown: Int, val total: Int) : BannerState
        data object CappedUnknown : BannerState
        data class Exhausted(val resetsAt: Instant?) : BannerState
        data class Remaining(val remaining: Int, val limit: Int) : BannerState
    }

    sealed interface SearchItem {
        data object LocationPrompt : SearchItem
        data object Hint : SearchItem
        data class Searching(val aircraftCount: Int) : SearchItem
        data object NoResults : SearchItem
        data class Summary(
            val aircraftCount: Int,
            val cacheOnlyCount: Int = 0,
            val totalMatching: Int? = null,
        ) : SearchItem

        data class TermStatus(val term: String, val state: TermState) : SearchItem
        data class UpgradeBanner(
            val state: BannerState,
            /** Which allowance the numbers belong to, so the row can name it correctly. */
            val charged: SearchRepo.Charged = SearchRepo.Charged.SEARCH,
        ) : SearchItem
        data class AircraftResult(
            val aircraft: Aircraft,
            val watch: Watch?,
            val distanceInMeter: Float?,
            val freshness: Freshness = Freshness.LIVE,
            val cacheOnly: Boolean = false,
        ) : SearchItem
    }

    data class State(
        val input: SearchInput,
        val items: List<SearchItem>,
        val isSearching: Boolean = false,
        /** Server time, the reference the relative ages in the list are rendered against. */
        val nowMillis: Long = System.currentTimeMillis(),
    )

    companion object {
        private const val DEFAULT_NEARBY_RADIUS_NM = 25.0
    }
}
