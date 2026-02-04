package eu.darken.apl.search.ui

import android.location.Location
import androidx.lifecycle.SavedStateHandle
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
import eu.darken.apl.common.navigation.navArgs
import eu.darken.apl.common.uix.ViewModel3
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.map.core.AirplanesLive
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.search.core.SearchQuery
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.search.core.SearchSettings
import eu.darken.apl.search.ui.items.AircraftResultVH
import eu.darken.apl.search.ui.items.LocationPromptVH
import eu.darken.apl.search.ui.items.NoAircraftVH
import eu.darken.apl.search.ui.items.SearchingAircraftVH
import eu.darken.apl.search.ui.items.SummaryVH
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
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
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val searchRepo: SearchRepo,
    private val webpageTool: WebpageTool,
    private val locationManager2: LocationManager2,
    private val settings: SearchSettings,
    watchRepo: WatchRepo,
) : ViewModel3(
    dispatcherProvider,
    tag = logTag("Search", "ViewModel"),
) {

    private val args by handle.navArgs<SearchFragmentArgs>()
    private val targetHexes: Set<AircraftHex>?
        get() = args.targetHexes?.toSet()
    private val targetSquawks: Set<SquawkCode>?
        get() = args.targetSquawks?.toSet()


    init {
        log(tag, INFO) { "targetHexes=$targetHexes, targetSquawks=$targetSquawks" }
    }

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

    init {
        log(tag) { "init with handle: $handle" }
        launch {
            if (currentInput.value != null) return@launch

            when {
                targetHexes != null -> {
                    currentInput.value = Input(State.Mode.HEX, raw = targetHexes!!.joinToString(","))
                }

                targetSquawks != null -> {
                    currentInput.value = Input(State.Mode.SQUAWK, raw = targetSquawks!!.joinToString(","))
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

        val items = mutableListOf<SearchAdapter.Item>()

        if (!locationDismissed && (locationState as? LocationManager2.State.Unavailable)?.isPermissionIssue == true) {
            LocationPromptVH.Item(
                locationState,
                onGrant = {
                    events.emitBlocking(SearchEvents.RequestLocationPermission)
                },
                onDismiss = {
                    settings.searchLocationDismissed.valueBlocking = true
                }
            ).run { items.add(this) }
        }

        if (result?.aircraft != null) {
            if (result.searching) {
                items.add(SearchingAircraftVH.Item(input, result.aircraft.size))
            } else if (result.aircraft.isEmpty()) {
                NoAircraftVH.Item(
                    input,
                    onStartFeeding = {
                        webpageTool.open(AirplanesLive.URL_START_FEEDING)
                    }
                ).run { items.add(this) }
            } else {
                items.add(SummaryVH.Item(result.aircraft.size))
            }
        }

        result?.aircraft
            ?.map { ac ->
                AircraftResultVH.Item(
                    aircraft = ac,
                    watch = alerts.filterIsInstance<AircraftWatch>().firstOrNull { it.matches(ac) },
                    distanceInMeter = if (locationState is LocationManager2.State.Available && ac.location != null) {
                        locationState.location.distanceTo(ac.location!!)
                    } else {
                        null
                    },
                    onTap = {
                        SearchFragmentDirections.actionSearchToSearchAction(
                            hex = ac.hex,
                        ).navigate()
                    },
                    onThumbnail = { launch { webpageTool.open(it.link) } },
                    onWatch = {
                        SearchFragmentDirections.actionSearchToWatchlistDetailsFragment(it.id).navigate()
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
        val oldInput = currentInput.value ?: Input()
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
        log(tag) { "updateMode(): $oldInput -> $newInput" }
        search(newInput)
    }

    fun showOnMap(items: Collection<SearchAdapter.Item>) {
        log(tag) { "showOnMap(${items.size} items)" }
        val acs = items.filterIsInstance<AircraftResultVH.Item>().map { it.aircraft }
        if (acs.isEmpty()) return
        SearchFragmentDirections.actionSearchToMap(
            mapOptions = MapOptions.focusAircraft(acs.toSet())
        ).navigate()
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

        val symbols = DecimalFormatSymbols(Locale.US) // Ensure the use of period as decimal separator
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


    data class State(
        val input: Input,
        val items: List<SearchAdapter.Item>,
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
