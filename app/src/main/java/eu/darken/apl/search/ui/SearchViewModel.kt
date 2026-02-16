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
import eu.darken.apl.common.flow.throttleLatest
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchSettings
import eu.darken.apl.search.ui.actions.DestinationSearchAction
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.DestinationCreateAircraftWatch
import eu.darken.apl.watch.ui.DestinationWatchDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val searchRepo: SearchRepo,
    private val webpageTool: WebpageTool,
    private val locationManager2: LocationManager2,
    private val settings: SearchSettings,
    watchRepo: WatchRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Search", "ViewModel"),
) {

    private var targetHexes: Set<AircraftHex>? = null
    private var targetSquawks: Set<SquawkCode>? = null
    private var initialized = false

    val events = SingleEventFlow<SearchEvents>()

    private val currentInput = MutableStateFlow<Input?>(null)

    private val searchTrigger = MutableStateFlow(UUID.randomUUID())
    private val currentSearch: Flow<SearchRepo.Result?> = combine(
        searchTrigger,
        currentInput.filterNotNull(),
    ) { _, input ->
        val terms = input.raw.split(",").map { it.trim() }.toSet()
        when (input.mode) {
            State.Mode.ALL -> SearchQuery.All(terms)
            State.Mode.HEX -> SearchQuery.Hex(terms)
            State.Mode.CALLSIGN -> SearchQuery.Callsign(terms)
            State.Mode.REGISTRATION -> SearchQuery.Registration(terms)
            State.Mode.SQUAWK -> SearchQuery.Squawk(terms)
            State.Mode.AIRFRAME -> SearchQuery.Airframe(terms)
            State.Mode.INTERESTING -> SearchQuery.Interesting(
                military = terms.contains("military"),
                ladd = terms.contains("ladd"),
                pia = terms.contains("pia"),
            )

            State.Mode.POSITION -> {
                var location = input.rawMeta as? Location
                if (location == null && input.raw.isNotBlank()) {
                    location = locationManager2.fromName(input.raw.trim())
                }
                if (location != null) {
                    SearchQuery.Position(location)
                } else {
                    SearchQuery.Position()
                }
            }
        }.also { log(tag) { "Mapped raw query: '$input' to $it" } }
    }
        .debounce(300)
        .map { searchRepo.liveSearch(it) }
        .flatMapLatest { it }
        .replayingShare(viewModelScope)

    fun init(
        targetHexes: List<String>? = null,
        targetSquawks: List<String>? = null,
        targetCallsigns: List<String>? = null,
    ) {
        if (initialized) return
        initialized = true

        this.targetHexes = targetHexes?.toSet()
        this.targetSquawks = targetSquawks?.toSet()

        log(tag, INFO) { "init: targetHexes=${this.targetHexes}, targetSquawks=${this.targetSquawks}" }

        launch {
            if (currentInput.value != null) return@launch

            when {
                this@SearchViewModel.targetHexes != null -> {
                    currentInput.value = Input(State.Mode.HEX, raw = this@SearchViewModel.targetHexes!!.joinToString(","))
                }

                this@SearchViewModel.targetSquawks != null -> {
                    currentInput.value = Input(State.Mode.SQUAWK, raw = this@SearchViewModel.targetSquawks!!.joinToString(","))
                }

                targetCallsigns != null -> {
                    currentInput.value = Input(State.Mode.CALLSIGN, raw = targetCallsigns.joinToString(","))
                }

                else -> {
                    updateMode(settings.inputLastMode.value())
                }
            }
        }
    }

    private val errorShownForSearch = MutableStateFlow<Set<Throwable>>(emptySet())

    val state = combine(
        currentInput.filterNotNull(),
        currentSearch.throttleLatest(500),
        watchRepo.watches,
        settings.searchLocationDismissed.flow,
        locationManager2.state,
        errorShownForSearch,
    ) { input, result, alerts, locationDismissed, locationState, shownErrors ->
        if (result != null && !result.searching && result.errors.isNotEmpty()) {
            val newError = result.errors.firstOrNull { it !in shownErrors }
            if (newError != null) {
                errorShownForSearch.value = shownErrors + newError
                events.tryEmit(SearchEvents.SearchError(newError))
            }
        }

        val items = mutableListOf<SearchItem>()

        if (!locationDismissed && (locationState as? LocationManager2.State.Unavailable)?.isPermissionIssue == true) {
            items.add(SearchItem.LocationPrompt)
        }

        if (result?.aircraft != null) {
            if (result.searching) {
                items.add(SearchItem.Searching(aircraftCount = result.aircraft.size))
            } else if (result.aircraft.isEmpty()) {
                items.add(SearchItem.NoResults)
            } else {
                items.add(SearchItem.Summary(aircraftCount = result.aircraft.size))
            }
        }

        result?.aircraft
            ?.map { ac ->
                SearchItem.AircraftResult(
                    aircraft = ac,
                    watch = alerts.filterIsInstance<AircraftWatch>().firstOrNull { it.matches(ac) },
                    distanceInMeter = if (locationState is LocationManager2.State.Available && ac.location != null) {
                        locationState.location.distanceTo(ac.location!!)
                    } else {
                        null
                    },
                )
            }
            ?.sortedBy { it.distanceInMeter ?: Float.MAX_VALUE }
            ?.run { items.addAll(this) }

        State(
            input = input,
            isSearching = result?.searching ?: false,
            items = items,
        )
    }.catch { }.asStateFlow()

    fun search(input: Input) {
        log(tag) { "search($input)" }
        errorShownForSearch.value = emptySet()
        if (currentInput.value == input) {
            searchTrigger.value = UUID.randomUUID()
        } else {
            currentInput.value = input
        }
    }

    fun updateSearchText(raw: String) = launch {
        log(tag) { "updateSearchText($raw)" }
        val oldInput = currentInput.value ?: Input()
        val newInput = when (oldInput.mode) {
            State.Mode.ALL -> {
                settings.inputLastAll.value(raw)
                Input(oldInput.mode, raw = raw)
            }

            State.Mode.HEX -> {
                settings.inputLastHex.value(raw)
                Input(oldInput.mode, raw = raw)
            }

            State.Mode.CALLSIGN -> {
                settings.inputLastCallsign.value(raw)
                Input(oldInput.mode, raw = raw)
            }

            State.Mode.REGISTRATION -> {
                settings.inputLastRegistration.value(raw)
                Input(oldInput.mode, raw = raw)
            }

            State.Mode.SQUAWK -> {
                settings.inputLastSquawk.value(raw)
                Input(oldInput.mode, raw = raw)
            }

            State.Mode.AIRFRAME -> {
                settings.inputLastAirframe.value(raw)
                Input(oldInput.mode, raw = raw)
            }

            State.Mode.INTERESTING -> {
                settings.inputLastInteresting.value(raw)
                Input(State.Mode.INTERESTING, raw = raw)
            }

            State.Mode.POSITION -> {
                settings.inputLastPosition.value(raw)
                Input(
                    oldInput.mode,
                    raw = raw,
                    rawMeta = raw.trim().takeIf { it.isNotBlank() }?.let { locationManager2.fromName(it) },
                )
            }
        }

        log(tag) { "updateSearchText(): $oldInput -> $newInput " }
        search(newInput)
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
        search(newInput)
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
        search(input)
    }

    sealed interface SearchItem {
        data object LocationPrompt : SearchItem
        data class Searching(val aircraftCount: Int) : SearchItem
        data object NoResults : SearchItem
        data class Summary(val aircraftCount: Int) : SearchItem
        data class AircraftResult(
            val aircraft: Aircraft,
            val watch: Watch?,
            val distanceInMeter: Float?,
        ) : SearchItem
    }

    data class State(
        val input: Input,
        val items: List<SearchItem>,
        val isSearching: Boolean = false,
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
}
