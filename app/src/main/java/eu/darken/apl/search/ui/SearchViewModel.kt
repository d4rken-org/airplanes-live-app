package eu.darken.apl.search.ui

import android.location.Location
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.uix.ScreenVisits
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapTraffic
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.search.core.buildSearchQuery
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerApiException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val searchRepo: SearchRepo,
    private val webpageTool: WebpageTool,
    private val locationManager2: LocationManager2,
    private val settings: SearchSettings,
    watchRepo: WatchRepo,
    private val accessRepo: AccessRepo,
    private val serverClock: ServerClock,
    private val searchRequests: SearchRequests,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Search", "ViewModel"),
) {

    private var initialized = false

    val events = SingleEventFlow<SearchEvents>()

    private val nearbyAwaitsPermission = AtomicBoolean(false)

    private val currentInput = MutableStateFlow<SearchInput?>(null)
    private val shown = MutableStateFlow<ShownResult?>(null)
    private val searching = MutableStateFlow(false)

    /** Only the newest submit may publish, a cleared or replaced search must not reappear. */
    private val latestGeneration = AtomicInteger()

    /** Serializes input changes, the start of a submit and every publish, see [latestGeneration]. */
    private val inputLock = Mutex()

    private val revisions = AtomicInteger()
    private val claimedError = AtomicReference<Throwable?>(null)

    @Volatile private var screen = ScreenState()

    /** Typed but not submitted, kept while the screen is not shown. */
    @Volatile var draft: Draft? = null
        private set

    private val revision: Int
        get() = shown.value?.revision ?: 0

    private val visits = ScreenVisits()

    fun onScreenShown() = visits.shown()

    fun onScreenHidden() = visits.hidden()

    fun init() {
        if (initialized) return
        initialized = true

        launch {
            val requested = searchRequests.take()
            if (requested != null) {
                searchFor(requested)
            } else {
                inputLock.withLock {
                    if (currentInput.value == null) currentInput.value = settings.lastInput.value()
                }
            }
            searchRequests.consumeEach { searchFor(it) }
        }
    }

    private suspend fun searchFor(target: SearchTarget) {
        log(tag, INFO) { "searchFor($target)" }
        val targets = target.terms ?: return
        // A search opened for specific aircraft is not what the user typed, so it isn't remembered
        val input = SearchInput(text = targets.joinToString(", "))
        val generation = inputLock.withLock {
            resetResults()
            draft = null
            currentInput.value = input
            latestGeneration.incrementAndGet()
        }
        submit(input, generation, visits.current())
    }

    val state = combine(
        currentInput.filterNotNull(),
        shown,
        searching,
        watchRepo.watches,
        settings.searchLocationDismissed.flow,
        locationManager2.state,
        accessRepo.state,
    ) { input, shown, searching, alerts, locationDismissed, locationState, access ->
        val result = shown?.result
        val error = result?.error?.takeIf {
            it !is java.net.UnknownHostException &&
                    it !is java.net.SocketTimeoutException &&
                    it !is java.net.ConnectException &&
                    // The update notice explains a refused app
                    (it as? ServerApiException)?.code != ServerCodes.APP_UPDATE_REQUIRED
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
                SearchItem.AircraftResult(
                    aircraft = ac,
                    watch = alerts.filterIsInstance<AircraftWatch>().firstOrNull { it.matches(ac) },
                    distanceInMeter = ac.location?.let { distanceOrigin?.distanceTo(it) },
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
            resultRevision = shown?.revision ?: 0,
            error = error?.let { SearchError(it, result.charged) },
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
    private fun submit(input: SearchInput, generation: Int, visit: Int?) = launch {
        log(tag) { "submit($input)" }
        if (input.isEmpty) return@launch
        ifCurrent(generation) { searching.value = true }
        try {
            val origin = if (input.nearby) {
                resolveNearbyLocation(input, generation, visit) ?: run {
                    ifCurrent(generation) { shown.value = null }
                    return@launch
                }
            } else {
                null
            }
            // A lookup that outlived its search must not charge for it
            if (!ifCurrent(generation) {}) return@launch
            withContext(dispatcherProvider.Default) { search(input, origin, generation) }
        } finally {
            ifCurrent(generation) { searching.value = false }
        }
    }

    /** Failures become the shown result's error. */
    private suspend fun search(input: SearchInput, origin: Location?, generation: Int) {
        val query = buildSearchQuery(input)
        val result = try {
            if (origin != null) {
                searchRepo.nearby(
                    latitude = origin.latitude,
                    longitude = origin.longitude,
                    radiusNm = DEFAULT_NEARBY_RADIUS_NM,
                    filter = query,
                )
            } else {
                searchRepo.search(query)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(tag, WARN) { "search($input) failed: ${e.asLog()}" }
            SearchRepo.SearchResult(
                query = query,
                error = e,
                charged = if (origin != null) SearchRepo.Charged.VIEWING else SearchRepo.Charged.SEARCH,
            )
        }
        // A newer submit owns the screen, this answer belongs to an input no longer shown
        ifCurrent(generation) { publish(result, origin) }
    }

    /** Call with [inputLock] held. */
    private fun publish(result: SearchRepo.SearchResult, origin: Location?) {
        shown.value = ShownResult(result, origin, revision = revisions.incrementAndGet())
    }

    /** Call with [inputLock] held. */
    private fun resetResults() {
        latestGeneration.incrementAndGet()
        shown.value = null
        searching.value = false
    }

    /** Checked and written under the lock the resets take, so a reset can't slip in between. */
    private suspend fun ifCurrent(generation: Int, block: () -> Unit): Boolean = withContext(NonCancellable) {
        inputLock.withLock {
            (generation == latestGeneration.get()).also { if (it) block() }
        }
    }

    /** Only for the search still shown, in the visit that started it. */
    private suspend fun feedback(event: SearchEvents, generation: Int, visit: Int?): Boolean {
        if (!visits.isCurrent(visit) || !ifCurrent(generation) {}) return false
        events.tryEmit(event)
        return true
    }

    private suspend fun resolveNearbyLocation(input: SearchInput, generation: Int, visit: Int?): Location? {
        input.place?.let { place ->
            if (!locationManager2.canGeocode) {
                feedback(SearchEvents.PlaceSearchUnavailable, generation, visit)
                return null
            }
            return locationManager2.fromName(place).also {
                if (it == null) feedback(SearchEvents.PlaceNotFound(place), generation, visit)
            }
        }

        val locationState = withTimeoutOrNull(2000) {
            locationManager2.state.filter { it !is LocationManager2.State.Waiting }.first()
        }
        if (locationState is LocationManager2.State.Available) return locationState.location

        log(tag) { "Device location unavailable: $locationState" }
        if ((locationState as? LocationManager2.State.Unavailable)?.isPermissionIssue == true) {
            // A prompt that is never shown must not turn the prompt card's grant into a search
            if (feedback(SearchEvents.RequestLocationPermission, generation, visit)) nearbyAwaitsPermission.set(true)
        } else {
            feedback(SearchEvents.LocationUnavailable, generation, visit)
        }
        return null
    }

    /** Only picks up a nearby search that asked for the permission, not the prompt card's grant. */
    fun onLocationPermissionResult(granted: Boolean) {
        val visit = visits.current()
        launch { onLocationPermissionResult(granted, visit) }
    }

    private suspend fun onLocationPermissionResult(granted: Boolean, visit: Int?) {
        if (!nearbyAwaitsPermission.getAndSet(false)) return
        if (!granted) {
            if (visits.isCurrent(visit)) events.tryEmit(SearchEvents.LocationUnavailable)
            return
        }
        // The location state notices the grant on its own schedule
        withTimeoutOrNull(2000) {
            locationManager2.state.first { (it as? LocationManager2.State.Unavailable)?.isPermissionIssue != true }
        }
        val (input, generation) = inputLock.withLock {
            val input = currentInput.value?.takeIf { it.nearby } ?: return
            input to latestGeneration.incrementAndGet()
        }
        submit(input, generation, visit)
    }

    /** The keyboard action and the search button submit what the input field currently holds. */
    fun submitCurrent(text: String) {
        val visit = visits.current()
        launch {
            val (input, generation) = inputLock.withLock {
                draft = null
                val input = updateInputLocked { it.copy(text = text) }
                // Nothing to search for, a search still running for the previous input keeps the screen
                if (input.isEmpty) return@launch
                input to latestGeneration.incrementAndGet()
            }
            log(tag) { "submitCurrent(): $input" }
            submit(input, generation, visit)
        }
    }

    /** An empty field shows no results, a search still running for the old text must not land either. */
    fun clearSearch() = launch {
        nearbyAwaitsPermission.set(false)
        inputLock.withLock {
            resetResults()
            updateInputLocked { it.copy(text = "") }
        }
    }

    fun updateDraft(base: String, text: String) {
        draft = Draft(base = base, text = text)
    }

    /** What the screen saved while showing [revision], fresh state once a newer result replaced it. */
    fun screenState(revision: Int): ScreenState = screen.takeIf { it.revision == revision } ?: ScreenState(revision)

    fun saveSelection(revision: Int, selection: Set<String>) {
        saveScreenState(revision) { it.copy(selection = selection) }
    }

    fun saveGridPosition(revision: Int, position: GridPosition) {
        saveScreenState(revision) { it.copy(gridPosition = position) }
    }

    /** Ignored for a result that is no longer shown, e.g. a screen disposed after a newer search landed. */
    @Synchronized
    private fun saveScreenState(revision: Int, change: (ScreenState) -> ScreenState) {
        if (revision != this.revision) return
        screen = change(screenState(revision))
    }

    /** True for the first caller only, so each error is shown once, also when the screen comes back to it. */
    fun claimError(error: Throwable): Boolean = claimedError.getAndSet(error) !== error

    fun serverNowMillis(): Long = serverClock.now().toEpochMilli()

    /** Any of the chip's categories counts as on, so switching it off clears all of them. */
    fun toggleCategoryChip(chip: CategoryChip) = launch {
        updateInput {
            val isOn = it.categories.any { c -> c in chip.categories }
            it.copy(categories = if (isOn) it.categories - chip.categories else it.categories + chip.categories)
        }
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
        updateInputLocked(change)
    }

    private suspend fun updateInputLocked(change: (SearchInput) -> SearchInput): SearchInput {
        val newInput = currentInput.updateAndGet { change(it ?: SearchInput()) }!!
        settings.lastInput.value(newInput)
        log(tag) { "updateInput(): $newInput" }
        return newInput
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

    /** LADD and PIA both mean an owner asked not to be tracked, few users care which program it was. */
    enum class CategoryChip(val categories: Set<SearchCategory>) {
        MILITARY(setOf(SearchCategory.MILITARY)),
        PRIVACY(setOf(SearchCategory.LADD, SearchCategory.PIA)),
        ;
    }

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
            val cacheOnly: Boolean = false,
        ) : SearchItem
    }

    data class State(
        val input: SearchInput,
        val items: List<SearchItem>,
        val isSearching: Boolean = false,
        /** Server time, the reference the allowance reset is rendered against. */
        val nowMillis: Long = System.currentTimeMillis(),
        /** Changes with every published result, the screen's scroll and selection belong to one of them. */
        val resultRevision: Int = 0,
        val error: SearchError? = null,
    )

    data class SearchError(
        val error: Throwable,
        val charged: SearchRepo.Charged,
    )

    /** A nearby result measures distances from the spot that was searched, not from the device. */
    private data class ShownResult(
        val result: SearchRepo.SearchResult,
        val origin: Location? = null,
        val revision: Int = 0,
    )

    data class ScreenState(
        val revision: Int = 0,
        val selection: Set<String> = emptySet(),
        val gridPosition: GridPosition = GridPosition(),
    )

    data class GridPosition(val index: Int = 0, val offset: Int = 0)

    /** Typed but not submitted, only valid while the input still holds [base]. */
    data class Draft(val base: String, val text: String)

    companion object {
        private const val DEFAULT_NEARBY_RADIUS_NM = 25.0

        /** Its position's time, the last message for an aircraft without one. */
        fun seenAt(aircraft: Aircraft): Instant? = aircraft.positionSeenAt ?: aircraft.messageSeenAt

        /** [now] is server time. */
        fun isLive(aircraft: Aircraft, now: Instant): Boolean {
            val positionSeenAt = aircraft.positionSeenAt ?: return false
            return Duration.between(positionSeenAt, now) <= LIVE_POSITION_WITHIN
        }

        internal val LIVE_POSITION_WITHIN = Duration.ofMillis((MapTraffic.EXTRAPOLATE_AGE_SEC * 1000).toLong())
    }
}
