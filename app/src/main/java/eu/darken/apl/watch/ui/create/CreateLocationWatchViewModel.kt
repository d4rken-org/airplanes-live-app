package eu.darken.apl.watch.ui.create

import android.location.Location
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.types.LocationWatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class CreateLocationWatchViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val locationManager2: LocationManager2,
    private val creator: LocationWatchCreator,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Location", "Create", "VM"),
) {

    val isAllowed = creator.isAllowed.asStateFlow(defaultValue = true)

    val maxRadiusKm = creator.maxRadiusKm.asStateFlow(defaultValue = LocationWatch.DEFAULT_MAX_RADIUS_KM)

    val resolvedLocation = MutableStateFlow<Location?>(null)
    val resolvedLabel = MutableStateFlow<String?>(null)
    val isResolving = MutableStateFlow(false)

    fun useDeviceLocation() = launch {
        log(tag) { "useDeviceLocation()" }
        isResolving.value = true
        try {
            val state = locationManager2.state.first { it !is LocationManager2.State.Waiting }
            if (state is LocationManager2.State.Available) {
                resolvedLocation.value = state.location
                resolvedLabel.value = creator.labelFor(state.location.latitude, state.location.longitude)
            }
        } finally {
            isResolving.value = false
        }
    }

    fun resolveInput(input: String) = launch {
        log(tag) { "resolveInput($input)" }
        isResolving.value = true
        try {
            val latLon = parseLatLon(input)
            if (latLon != null) {
                val location = Location("manual").apply {
                    latitude = latLon.first
                    longitude = latLon.second
                }
                resolvedLocation.value = location
                resolvedLabel.value = creator.labelFor(latLon.first, latLon.second)
            } else {
                val location = locationManager2.fromName(input)
                if (location != null) {
                    resolvedLocation.value = location
                    resolvedLabel.value = input
                } else {
                    resolvedLocation.value = null
                    resolvedLabel.value = null
                }
            }
        } finally {
            isResolving.value = false
        }
    }

    fun create(latitude: Double, longitude: Double, radiusInMeters: Float, label: String, note: String) = launch {
        log(tag) { "create($latitude, $longitude, $radiusInMeters, $label, $note)" }
        creator.create(latitude, longitude, radiusInMeters, label, note)
        navUp()
    }

    private fun parseLatLon(input: String): Pair<Double, Double>? {
        val parts = input.split(",").map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }
}
