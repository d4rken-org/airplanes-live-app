package eu.darken.apl.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R

/** The map's own actions, at the same place with and without fullscreen. */
@Composable
internal fun MapControlRail(
    isFullscreen: Boolean,
    showAr: Boolean,
    onOptions: () -> Unit,
    onAircraftList: () -> Unit,
    onAr: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(RAIL_PADDING)) {
            RailButton(Icons.TwoTone.Tune, stringResource(R.string.map_controls_action), onOptions)
            RailButton(Icons.AutoMirrored.TwoTone.ViewSidebar, stringResource(R.string.map_sidebar_toggle_action), onAircraftList)
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

@Composable
internal fun MyLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.padding(4.dp).size(48.dp)) {
            Icon(Icons.TwoTone.MyLocation, contentDescription = stringResource(R.string.map_my_location_action))
        }
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
                stringResource(R.string.map_pinned_count, pinnedCount),
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
