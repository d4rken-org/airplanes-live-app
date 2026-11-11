package eu.darken.apl.watch.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.types.LocationWatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class CreateLocationWatchViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val locationManager2: LocationManager2,
    private val creator: LocationWatchCreator,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Location", "Create", "VM"),
) {

    @Serializable
    data class Draft(
        val input: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val label: String = "",
        // Null until the slider moves, then the picked stop in meters
        val pickedRadius: Float? = null,
        val note: String = "",
        val initialized: Boolean = false,
        val labelEdited: Boolean = false,
        // Coordinates were committed before their label lookup finished
        val labelOwed: Boolean = false,
    ) {
        val isResolved: Boolean
            get() = latitude != null && longitude != null
    }

    val isAllowed = creator.isAllowed.asStateFlow(defaultValue = true)

    val maxRadiusKm = creator.maxRadiusKm.asStateFlow(defaultValue = LocationWatch.DEFAULT_MAX_RADIUS_KM)

    // Snapshot state, not a flow: the text fields fed from it need their value back synchronously while typing
    var draft: Draft by mutableStateOf(restoreDraft())
        private set

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving

    private var resolveGeneration = 0L
    private var resolveJob: Job? = null

    init {
        val restored = draft
        if (restored.labelOwed && !restored.labelEdited) {
            val lat = restored.latitude
            val lon = restored.longitude
            if (lat != null && lon != null) lookupLabel(lat, lon)
        }
    }

    fun init(latitude: Double?, longitude: Double?, label: String?, note: String?) {
        if (draft.initialized) return
        log(tag) { "init($latitude, $longitude, $label, $note)" }
        val base = Draft(note = note ?: "", initialized = true)
        when {
            latitude == null || longitude == null -> updateDraft { base }
            !label.isNullOrBlank() -> updateDraft {
                base.copy(input = "$latitude, $longitude", latitude = latitude, longitude = longitude, label = label)
            }

            else -> {
                updateDraft {
                    base.copy(input = "$latitude, $longitude", latitude = latitude, longitude = longitude, labelOwed = true)
                }
                lookupLabel(latitude, longitude)
            }
        }
    }

    fun onLocationInputChange(input: String) {
        if (input == draft.input) return
        cancelResolution()
        updateDraft { it.copy(input = input, latitude = null, longitude = null, labelOwed = false) }
    }

    fun onLabelChange(label: String) {
        updateDraft { it.copy(label = label, labelEdited = true) }
    }

    fun onRadiusChange(radiusInMeters: Float) {
        updateDraft { it.copy(pickedRadius = radiusInMeters) }
    }

    fun onNoteChange(note: String) {
        updateDraft { it.copy(note = note) }
    }

    fun useDeviceLocation() = startResolution {
        log(tag) { "useDeviceLocation()" }
        val state = locationManager2.state.first { it !is LocationManager2.State.Waiting }
        if (state is LocationManager2.State.Available) {
            val lat = state.location.latitude
            val lon = state.location.longitude
            Resolution.Found(lat, lon, creator.labelFor(lat, lon))
        } else {
            Resolution.Unchanged
        }
    }

    fun resolveInput() {
        val input = draft.input
        startResolution {
            log(tag) { "resolveInput($input)" }
            val latLon = parseLatLon(input)
            if (latLon != null) {
                Resolution.Found(latLon.first, latLon.second, creator.labelFor(latLon.first, latLon.second))
            } else {
                val location = locationManager2.fromName(input)
                if (location != null) {
                    Resolution.Found(location.latitude, location.longitude, input)
                } else {
                    Resolution.NotFound
                }
            }
        }
    }

    fun create(
        radiusInMeters: Float,
        minAltitudeFt: Int?,
        maxAltitudeFt: Int?,
    ) {
        val current = draft
        val latitude = current.latitude
        val longitude = current.longitude
        if (_isResolving.value || latitude == null || longitude == null) {
            log(tag, WARN) { "create(): location is not resolved (resolving=${_isResolving.value})" }
            return
        }
        launch {
            log(tag) {
                "create($latitude, $longitude, $radiusInMeters, $minAltitudeFt..$maxAltitudeFt, ${current.label}, ${current.note})"
            }
            creator.create(latitude, longitude, radiusInMeters, minAltitudeFt, maxAltitudeFt, current.label, current.note)
            navUp()
        }
    }

    private fun lookupLabel(latitude: Double, longitude: Double) = startResolution {
        Resolution.Found(latitude, longitude, creator.labelFor(latitude, longitude))
    }

    private sealed interface Resolution {
        data class Found(val latitude: Double, val longitude: Double, val label: String) : Resolution
        data object NotFound : Resolution
        data object Unchanged : Resolution
    }

    private fun Draft.withResolution(resolution: Resolution): Draft = when (resolution) {
        is Resolution.Found -> copy(
            latitude = resolution.latitude,
            longitude = resolution.longitude,
            label = if (labelEdited) label else resolution.label,
            labelOwed = false,
        )

        Resolution.NotFound -> copy(latitude = null, longitude = null, labelOwed = false)
        Resolution.Unchanged -> this
    }

    private fun cancelResolution() {
        resolveGeneration++
        resolveJob?.cancel()
        resolveJob = null
        _isResolving.value = false
    }

    /**
     * Runs on the main thread like every other draft write. The result is applied to the draft as it is when the
     * request finishes, and only while no newer request or edit replaced this one.
     */
    private fun startResolution(request: suspend () -> Resolution) {
        // Bumped before the cancel, so the cancelled request's cleanup already sees itself as obsolete
        val generation = ++resolveGeneration
        resolveJob?.cancel()
        _isResolving.value = true
        val context = launchErrorHandler?.let { dispatcherProvider.Main + it } ?: dispatcherProvider.Main
        resolveJob = viewModelScope.launch(context) {
            try {
                val resolution = request()
                if (generation == resolveGeneration) updateDraft { it.withResolution(resolution) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == resolveGeneration) throw e
                log(tag, WARN) { "Obsolete resolution failed: ${e.asLog()}" }
            } finally {
                if (generation == resolveGeneration) _isResolving.value = false
            }
        }
    }

    private fun updateDraft(change: (Draft) -> Draft) {
        val updated = change(draft)
        draft = updated
        handle[KEY_DRAFT] = json.encodeToString(Draft.serializer(), updated)
    }

    private fun restoreDraft(): Draft {
        val saved = handle.get<String>(KEY_DRAFT) ?: return Draft()
        return try {
            json.decodeFromString(Draft.serializer(), saved)
        } catch (e: Exception) {
            log(tag, WARN) { "Dropping unreadable draft: ${e.asLog()}" }
            Draft()
        }
    }

    private fun parseLatLon(input: String): Pair<Double, Double>? {
        val parts = input.split(",").map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    companion object {
        internal const val KEY_DRAFT = "draft"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
