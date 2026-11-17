package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapFrames
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchLimitReachedException

sealed interface NativeMapEvents {
    data object RequestLocationPermission : NativeMapEvents
    data object LocationUnavailable : NativeMapEvents
    data class WatchAdded(val watchId: WatchId, val label: String) : NativeMapEvents
    data class WatchLimitReached(val error: WatchLimitReachedException) : NativeMapEvents

    /** The access policy has no location watches, see [eu.darken.apl.watch.core.LocationWatchNotAllowedException]. */
    data object LocationWatchNotAllowed : NativeMapEvents

    /** [camera] is on tar1090's zoom scale, with [keepZoom] only the position changes. */
    data class MoveCamera(val camera: MapOptions.Camera, val keepZoom: Boolean = false) : NativeMapEvents

    /** With [aroundHistory] the bounds stay clear of that history's date bar and timeline, [east] may pass 180. */
    data class FitBounds(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
        val aroundHistory: MapFrames.Replay? = null,
    ) : NativeMapEvents

    data class PinnedTruncated(val requested: Int, val shown: Int) : NativeMapEvents

    data class ShareAircraft(val text: String) : NativeMapEvents

    data class HistoryUnavailable(val reason: Reason) : NativeMapEvents {
        enum class Reason { NO_ACCESS, LIMIT_REACHED, UPDATE_REQUIRED, FAILED }
    }

    /** The selected aircraft was not seen in the days the history keeps. */
    data object HistoryEmpty : NativeMapEvents

    /** Lowers [selection]'s sheet to its peek, also from expanded. Moot once another selection is shown. */
    data class PeekSheet(val selection: NativeMapViewModel.Selected) : NativeMapEvents

    /** Expands [selection]'s sheet with its list at [listPosition]. Moot once another selection is shown. */
    data class ExpandSheet(
        val selection: NativeMapViewModel.Selected,
        val listPosition: SheetListPosition,
    ) : NativeMapEvents
}

/** A sheet list's first visible item and how far it is scrolled past, as the list state reports them. */
data class SheetListPosition(val index: Int, val offset: Int)
