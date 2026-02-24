package eu.darken.apl.map.ui

import eu.darken.apl.watch.core.types.AircraftWatch

sealed interface MapEvents {

    data object RequestLocationPermission : MapEvents
    data class CenterOnLocation(val lat: Double, val lon: Double) : MapEvents
    data object LocationUnavailable : MapEvents
    data class WatchAdded(val watch: AircraftWatch.Status) : MapEvents
    data class SelectAircraftOnMap(val hex: String) : MapEvents
    data object ReloadMap : MapEvents

}