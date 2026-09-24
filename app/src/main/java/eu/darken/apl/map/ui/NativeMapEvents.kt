package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.watch.core.types.AircraftWatch

sealed interface NativeMapEvents {
    data object RequestLocationPermission : NativeMapEvents
    data object LocationUnavailable : NativeMapEvents
    data class WatchAdded(val watch: AircraftWatch.Status) : NativeMapEvents

    /** [camera] is on tar1090's zoom scale, with [keepZoom] only the position changes. */
    data class MoveCamera(val camera: MapOptions.Camera, val keepZoom: Boolean = false) : NativeMapEvents

    data class FitBounds(val south: Double, val west: Double, val north: Double, val east: Double) : NativeMapEvents

    data class PinnedTruncated(val requested: Int, val shown: Int) : NativeMapEvents
}
