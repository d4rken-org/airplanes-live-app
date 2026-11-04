package eu.darken.apl.map.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.darken.apl.R
import eu.darken.apl.map.core.AircraftKind

@get:StringRes
internal val AircraftKind.labelRes: Int
    get() = when (this) {
        AircraftKind.PLANE -> R.string.map_kind_plane
        AircraftKind.HELICOPTER -> R.string.map_kind_helicopter
        AircraftKind.GLIDER -> R.string.map_kind_glider
        AircraftKind.BALLOON -> R.string.map_kind_balloon
        AircraftKind.DRONE -> R.string.map_kind_drone
        AircraftKind.GROUND -> R.string.map_kind_ground
        AircraftKind.UNKNOWN -> R.string.map_kind_unknown
    }

/** A single kind by its name, several by their number. */
@Composable
internal fun aircraftKindsLabel(kinds: Set<AircraftKind>): String = when (kinds.size) {
    1 -> stringResource(kinds.single().labelRes)
    else -> pluralStringResource(R.plurals.map_control_kinds_x, kinds.size, kinds.size)
}
