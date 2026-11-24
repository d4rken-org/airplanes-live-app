package eu.darken.apl.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ViewSidebar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.twotone.Category
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Fullscreen
import androidx.compose.material.icons.twotone.FullscreenExit
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.ViewInAr
import androidx.compose.material.icons.twotone.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.map.core.AircraftKind
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/** The map's own actions, at the same place with and without fullscreen. */
@Composable
internal fun MapControlRail(
    isFullscreen: Boolean,
    showAr: Boolean,
    /** Names each button next to its icon, see [rememberRailIntro]. */
    showLabels: Boolean,
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
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(RAIL_PADDING),
            horizontalAlignment = Alignment.End,
        ) {
            RailButton(Icons.TwoTone.Tune, stringResource(R.string.map_controls_action), showLabels, onOptions)
            onAircraftList?.let {
                RailButton(Icons.AutoMirrored.TwoTone.ViewSidebar, stringResource(R.string.map_sidebar_toggle_action), showLabels, it)
            }
            RailButton(Icons.TwoTone.MyLocation, stringResource(R.string.map_my_location_action), showLabels, onMyLocation)
            if (showAr) RailButton(Icons.TwoTone.ViewInAr, stringResource(R.string.ar_view_action), showLabels, onAr)
            RailButton(
                if (isFullscreen) Icons.TwoTone.FullscreenExit else Icons.TwoTone.Fullscreen,
                stringResource(R.string.common_fullscreen_action),
                showLabels,
                onFullscreen,
            )
        }
    }
}

private val RAIL_BUTTON = 48.dp
private val RAIL_PADDING = 4.dp

/** How much of the map's edge [MapControlRail] covers once its labels are gone. */
internal val MapControlRailWidth = RAIL_BUTTON + RAIL_PADDING * 2

internal const val RAIL_LABEL_TAG = "map_rail_label"

@Composable
private fun RailButton(icon: ImageVector, description: String, showLabel: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = showLabel,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
        ) {
            Text(
                description,
                // The icon already carries the description, TalkBack would read it twice
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clearAndSetSemantics { testTag = RAIL_LABEL_TAG },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(modifier = Modifier.size(RAIL_BUTTON), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

private object MapRailIntro {
    private val claimed = AtomicBoolean(false)

    fun claim(): Boolean = !claimed.getAndSet(true)
}

/**
 * True for the rail's first few seconds on screen, once per process. Not saved: a restored tab or a
 * rotation would replay it.
 */
@Composable
internal fun rememberRailIntro(claim: () -> Boolean = MapRailIntro::claim): Boolean {
    var visible by remember { mutableStateOf(claim()) }
    LaunchedEffect(Unit) {
        if (!visible) return@LaunchedEffect
        delay(RAIL_INTRO_DURATION)
        visible = false
    }
    return visible
}

internal val RAIL_INTRO_DURATION = 3.seconds

/** [label] fits a chip, [explanation] is the whole sentence. */
internal data class MapCapNote(val label: String, val explanation: String)

/**
 * What currently narrows down the map, each with a way out, and [cap] while the answer was capped; nothing
 * when the map is unfiltered and complete.
 */
@Composable
internal fun MapFilterChips(
    militaryOnly: Boolean,
    /** Empty shows all kinds. */
    kinds: Set<AircraftKind>,
    isolating: Boolean,
    pinnedCount: Int,
    query: String,
    watchedOnly: Boolean,
    cap: MapCapNote?,
    onClearMilitaryOnly: () -> Unit,
    onClearKinds: () -> Unit,
    onClearIsolate: () -> Unit,
    onClearPinned: () -> Unit,
    onClearQuery: () -> Unit,
    onClearWatchedOnly: () -> Unit,
    onCapInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (militaryOnly) {
            FilterChip(
                stringResource(R.string.map_control_military),
                stringResource(R.string.map_control_military_clear_action),
                onClearMilitaryOnly,
                icon = Icons.TwoTone.Shield,
            )
        }
        if (kinds.isNotEmpty()) {
            FilterChip(
                aircraftKindsLabel(kinds),
                stringResource(R.string.map_control_kinds_clear_action),
                onClearKinds,
                icon = Icons.TwoTone.Category,
            )
        }
        if (isolating) {
            FilterChip(
                stringResource(R.string.map_control_isolate),
                stringResource(R.string.map_control_isolate_clear_action),
                onClearIsolate,
            )
        }
        if (pinnedCount > 0) {
            FilterChip(
                pluralStringResource(R.plurals.map_pinned_count, pinnedCount, pinnedCount),
                stringResource(R.string.map_pinned_clear_action),
                onClearPinned,
            )
        }
        if (query.isNotBlank()) {
            FilterChip(
                query.trim(),
                stringResource(R.string.map_sidebar_filter_clear_action),
                onClearQuery,
                icon = Icons.TwoTone.Search,
                // A pasted query would otherwise stretch over the map
                labelModifier = Modifier.widthIn(max = QUERY_CHIP_MAX_WIDTH),
                description = query.trim(),
            )
        }
        if (watchedOnly) {
            FilterChip(
                stringResource(R.string.map_sidebar_watched_filter),
                stringResource(R.string.map_sidebar_watched_filter_clear_action),
                onClearWatchedOnly,
                icon = Icons.Filled.Visibility,
            )
        }
        if (cap != null) {
            AssistChip(
                onClick = onCapInfo,
                label = { Text(cap.label) },
                modifier = Modifier.semantics { contentDescription = cap.explanation },
                leadingIcon = {
                    Icon(Icons.TwoTone.ZoomIn, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = AssistChipDefaults.assistChipElevation(elevation = 3.dp),
            )
        }
    }
}

private val QUERY_CHIP_MAX_WIDTH = 160.dp

@Composable
private fun FilterChip(
    label: String,
    clearDescription: String,
    onClear: () -> Unit,
    icon: ImageVector? = null,
    labelModifier: Modifier = Modifier,
    description: String? = null,
) {
    InputChip(
        selected = true,
        onClick = onClear,
        modifier = if (description != null) {
            Modifier.semantics {
                contentDescription = description
                onClick(label = clearDescription) {
                    onClear()
                    true
                }
            }
        } else {
            Modifier
        },
        label = { Text(label, modifier = labelModifier, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize)) }
        },
        trailingIcon = {
            Icon(
                Icons.TwoTone.Close,
                contentDescription = if (description != null) null else clearDescription,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        elevation = InputChipDefaults.inputChipElevation(elevation = 3.dp),
    )
}
