package eu.darken.apl.watch.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AirplanemodeActive
import androidx.compose.material.icons.twotone.Dialpad
import androidx.compose.material.icons.twotone.Radar
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.apl.R
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.ui.WatchListViewModel.WatchType

val WatchType.icon: ImageVector
    get() = when (this) {
        WatchType.FLIGHT -> Icons.TwoTone.Tag
        WatchType.AIRCRAFT -> Icons.TwoTone.AirplanemodeActive
        WatchType.SQUAWK -> Icons.TwoTone.Dialpad
        WatchType.LOCATION -> Icons.TwoTone.Radar
    }

@get:StringRes
val WatchType.shortLabelRes: Int
    get() = when (this) {
        WatchType.FLIGHT -> R.string.watch_type_flight_label
        WatchType.AIRCRAFT -> R.string.watch_type_aircraft_label
        WatchType.SQUAWK -> R.string.watch_type_squawk_label
        WatchType.LOCATION -> R.string.watch_type_location_label
    }

val Watch.Status.type: WatchType
    get() = when (this) {
        is FlightWatch.Status -> WatchType.FLIGHT
        is AircraftWatch.Status -> WatchType.AIRCRAFT
        is SquawkWatch.Status -> WatchType.SQUAWK
        is LocationWatch.Status -> WatchType.LOCATION
    }
