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
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.upgrade.ui.DestinationUpgrade
import eu.darken.apl.main.core.query.TermOutcome
import eu.darken.apl.search.core.buildSearchQuery
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerCodes
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.util.Locale
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

    private var targetHexes: Set<AircraftHex>? = null
    private var targetSquawks: Set<SquawkCode>? = null
    private var targetCallsigns: Set<String>? = null
    private var initialized = false

    val events = SingleEventFlow<SearchEvents>()

    private val currentInput = MutableStateFlow<Input?>(null)

    private val currentResult = MutableStateFlow<SearchRepo.SearchResult?>(null)
    private val isSearching = MutableStateFlow(false)

    fun init(
        targetHexes: List<String>? = null,
        targetSquawks: List<String>? = null,
        targetCallsigns: List<String>? = null,
    ) {
        if (initialized) return
        initialized = true

        this.targetHexes = targetHexes?.toSet()
        this.targetSquawks = targetSquawks?.toSet()
        this.targetCallsigns = targetCallsigns?.toSet()

        log(tag, INFO) { "init: targetHexes=${this.targetHexes}, targetSquawks=${this.targetSquawks}, targetCallsigns=${this.targetCallsigns}" }

        launch {
            if (currentInput.value != null) return@launch

            when {
                this@SearchViewModel.targetHexes != null -> {
                    currentInput.value =
                        Input(State.Mode.HEX, raw = this@SearchViewModel.targetHexes!!.joinToString(","))
                }

                this@SearchViewModel.targetSquawks != null -> {
                    currentInput.value =
                        Input(State.Mode.SQUAWK, raw = this@SearchViewModel.targetSquawks!!.joinToString(","))
                }

                this@SearchViewModel.targetCallsigns != null -> {
                    currentInput.value = Input(State.Mode.CALLSIGN, raw = this@SearchViewModel.targetCallsigns!!.joinToString(","))
                }

                else -> {
                    updateMode(settings.inputLastMode.value())
                    return@launch
                }
            }
            currentInput.value?.let { submit(it) }
        }
    }

    private val errorShownForSearch = MutableStateFlow<Set<Throwable>>(emptySet())

    val state = combine(
        currentInput.filterNotNull(),
        currentResult,
        isSearching,
        watchRepo.watches,
        settings.searchLocationDismissed.flow,
        locationManager2.state,
        accessRepo.state,
    ) { input, result, searching, alerts, locationDismissed, locationState, access ->
        val error = result?.error
        if (error != null && error !in errorShownForSearch.value) {
            errorShownForSearch.value = errorShownForSearch.value + error
            val isNetworkError = error is java.net.UnknownHostException ||
                    error is java.net.SocketTimeoutException ||
                    error is java.net.ConnectException
            if (!isNetworkError) events.tryEmit(SearchEvents.SearchError(error))
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
        } else if (result != null) {
            if (result.aircraft.isEmpty()) {
                // Terms the server rejected were never evaluated, absence would be a claim we can't make
                if (result.terms.any { it.outcome is TermOutcome.Answered }) items.add(SearchItem.NoResults)
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
                    distanceInMeter = if (locationState is LocationManager2.State.Available && ac.location != null) {
                        locationState.location.distanceTo(ac.location!!)
                    } else {
                        null
                    },
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
            val charged = result?.charged ?: SearchRepo.Charged.SEARCH
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

    /** Deliberate submit, every term is charged against the daily allowance. */
    fun search(input: Input) = launch {
        submit(input)
    }

    private suspend fun submit(input: Input) {
        log(tag) { "submit($input)" }
        errorShownForSearch.value = emptySet()
        currentInput.value = input
        isSearching.value = true
        try {
            currentResult.value = when (input.mode) {
                State.Mode.POSITION -> {
                    val location = input.rawMeta as? Location
                        ?: input.raw.trim().takeIf { it.isNotBlank() }?.let { locationManager2.fromName(it) }
                    when (location) {
                        null -> null
                        else -> searchRepo.nearby(location.latitude, location.longitude, DEFAULT_NEARBY_RADIUS_NM)
                    }
                }

                else -> searchRepo.search(buildSearchQuery(input.mode, input.raw))
            }
        } finally {
            isSearching.value = false
        }
    }

    /** The keyboard action and the search button submit what the input field currently holds. */
    fun submitCurrent() = launch {
        val current = currentInput.value ?: Input()
        log(tag) { "submitCurrent(): $current" }
        submit(remember(current.mode, current.raw))
    }

    fun updateSearchText(raw: String) {
        log(tag) { "updateSearchText($raw)" }
        val mode = currentInput.value?.mode ?: Input().mode
        // Published before the slower persistence, a submit right after must see the new text
        currentInput.value = Input(mode, raw = raw)
        launch { currentInput.value = remember(mode, raw) }
    }

    /** Keeps the text as the last one used for [mode] and resolves what the mode needs on top. */
    private suspend fun remember(mode: State.Mode, raw: String): Input {
        val newInput = when (mode) {
            State.Mode.ALL -> {
                settings.inputLastAll.value(raw)
                Input(mode, raw = raw)
            }

            State.Mode.HEX -> {
                settings.inputLastHex.value(raw)
                Input(mode, raw = raw)
            }

            State.Mode.CALLSIGN -> {
                settings.inputLastCallsign.value(raw)
                Input(mode, raw = raw)
            }

            State.Mode.REGISTRATION -> {
                settings.inputLastRegistration.value(raw)
                Input(mode, raw = raw)
            }

            State.Mode.SQUAWK -> {
                settings.inputLastSquawk.value(raw)
                Input(mode, raw = raw)
            }

            State.Mode.AIRFRAME -> {
                settings.inputLastAirframe.value(raw)
                Input(mode, raw = raw)
            }

            State.Mode.INTERESTING -> {
                settings.inputLastInteresting.value(raw)
                Input(State.Mode.INTERESTING, raw = raw)
            }

            State.Mode.POSITION -> {
                settings.inputLastPosition.value(raw)
                Input(
                    mode,
                    raw = raw,
                    rawMeta = raw.trim().takeIf { it.isNotBlank() }?.let { locationManager2.fromName(it) },
                )
            }
        }

        log(tag) { "remember($mode, $raw): $newInput" }
        return newInput
    }

    fun updateMode(mode: State.Mode) = launch {
        log(tag) { "updateMode($mode)" }
        val newInput = when (mode) {
            State.Mode.ALL -> Input(mode, raw = settings.inputLastAll.value())
            State.Mode.REGISTRATION -> Input(mode, raw = settings.inputLastRegistration.value())
            State.Mode.HEX -> Input(mode, raw = settings.inputLastHex.value())
            State.Mode.CALLSIGN -> Input(mode, raw = settings.inputLastCallsign.value())
            State.Mode.AIRFRAME -> Input(mode, raw = settings.inputLastAirframe.value())
            State.Mode.SQUAWK -> Input(mode, raw = settings.inputLastSquawk.value())
            State.Mode.INTERESTING -> Input(mode, raw = settings.inputLastInteresting.value())
            State.Mode.POSITION -> Input(mode, raw = settings.inputLastPosition.value())
        }
        log(tag) { "updateMode(): -> $newInput" }
        settings.inputLastMode.value(mode)
        currentInput.value = newInput
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

    fun searchPositionHome() = launch {
        log(tag) { "searchPositionHome()" }
        val locationState = withTimeoutOrNull(2000) {
            locationManager2.state
                .filter { it !is LocationManager2.State.Waiting }
                .first()
        }

        if (locationState !is LocationManager2.State.Available) {
            log(tag) { "Location unavailable" }
            return@launch
        }

        val location = locationState.location

        val symbols = DecimalFormatSymbols(Locale.US)
        val formatter = DecimalFormat("#.##", symbols)
        val roundedLat = formatter.format(location.latitude).toDouble()
        val roundedLon = formatter.format(location.longitude).toDouble()
        val altText = "${roundedLat},${roundedLon}"
        val address = locationManager2.toName(location)
        val input = Input(
            State.Mode.POSITION,
            raw = address?.let { "${it.locality}, ${it.countryName}" } ?: altText,
            rawMeta = location,
        )
        settings.inputLastPosition.value(input.raw)
        submit(input)
    }

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
        val input: Input,
        val items: List<SearchItem>,
        val isSearching: Boolean = false,
        /** Server time, the reference the relative ages in the list are rendered against. */
        val nowMillis: Long = System.currentTimeMillis(),
    ) {
        @Serializable
        enum class Mode {
            @SerialName("ALL") ALL,
            @SerialName("HEX") HEX,
            @SerialName("CALLSIGN") CALLSIGN,
            @SerialName("REGISTRATION") REGISTRATION,
            @SerialName("SQUAWK") SQUAWK,
            @SerialName("AIRFRAME") AIRFRAME,
            @SerialName("INTERESTING") INTERESTING,
            @SerialName("POSITION") POSITION,
            ;
        }
    }

    data class Input(
        val mode: State.Mode = State.Mode.INTERESTING,
        val raw: String = "military, pia, ladd",
        val rawMeta: Any? = null,
    )

    companion object {
        private const val DEFAULT_NEARBY_RADIUS_NM = 25.0
    }
}
