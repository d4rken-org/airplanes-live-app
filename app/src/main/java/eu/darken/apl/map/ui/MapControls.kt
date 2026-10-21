package eu.darken.apl.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ViewSidebar
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Fullscreen
import androidx.compose.material.icons.twotone.FullscreenExit
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R

/** The map's own actions, at the same place with and without fullscreen. */
@Composable
internal fun MapControlRail(
    isFullscreen: Boolean,
    showAr: Boolean,
    onOptions: () -> Unit,
    /** Null while there is no list to show, e.g. during the history. */
    onAircraftList: (() -> Unit)?,
    onAr: () -> Unit,
    onFullscreen: () -> Unit,
    onMyLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        // A short map leaves less height than the rail needs, the least used buttons go last
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(RAIL_PADDING)) {
            RailButton(Icons.TwoTone.Tune, stringResource(R.string.map_controls_action), onOptions)
            onAircraftList?.let { RailButton(Icons.AutoMirrored.TwoTone.ViewSidebar, stringResource(R.string.map_sidebar_toggle_action), it) }
            RailButton(Icons.TwoTone.MyLocation, stringResource(R.string.map_my_location_action), onMyLocation)
            if (showAr) RailButton(Icons.TwoTone.ViewInAr, stringResource(R.string.ar_view_action), onAr)
            RailButton(
                if (isFullscreen) Icons.TwoTone.FullscreenExit else Icons.TwoTone.Fullscreen,
                stringResource(R.string.common_fullscreen_action),
                onFullscreen,
            )
        }
    }
}

private val RAIL_BUTTON = 48.dp
private val RAIL_PADDING = 4.dp

/** How much of the map's edge [MapControlRail] covers. */
internal val MapControlRailWidth = RAIL_BUTTON + RAIL_PADDING * 2

@Composable
private fun RailButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(RAIL_BUTTON)) {
        Icon(icon, contentDescription = description)
    }
}

/** What currently narrows down the map, each with a way out; nothing when the map is unfiltered. */
@Composable
internal fun MapFilterChips(
    militaryOnly: Boolean,
    isolating: Boolean,
    pinnedCount: Int,
    onClearMilitaryOnly: () -> Unit,
    onClearIsolate: () -> Unit,
    onClearPinned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (militaryOnly) {
            FilterChip(stringResource(R.string.map_control_military), null, onClearMilitaryOnly)
        }
        if (isolating) {
            FilterChip(stringResource(R.string.map_control_isolate), null, onClearIsolate)
        }
        if (pinnedCount > 0) {
            FilterChip(
                pluralStringResource(R.plurals.map_pinned_count, pinnedCount, pinnedCount),
                stringResource(R.string.map_pinned_clear_action),
                onClearPinned,
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, clearDescription: String?, onClear: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.TwoTone.Close,
                contentDescription = clearDescription,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        elevation = InputChipDefaults.inputChipElevation(elevation = 3.dp),
    )
}
