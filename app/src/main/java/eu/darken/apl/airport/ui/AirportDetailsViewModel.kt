package eu.darken.apl.airport.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.toMapOptions
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.map.ui.DestinationMap
import javax.inject.Inject

@HiltViewModel
class AirportDetailsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Airport", "Details", "ViewModel"),
) {

    fun showOnMap(airport: Airport) {
        log(tag) { "showOnMap(${airport.id})" }
        navTo(DestinationMap(mapOptions = airport.toMapOptions()))
    }
}
