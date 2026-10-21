package eu.darken.apl.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.twotone.ArrowDownward
import androidx.compose.material.icons.twotone.ArrowDropDown
import androidx.compose.material.icons.twotone.ArrowUpward
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.map.core.AircraftShapes
import eu.darken.apl.map.core.MapSidebarData
import eu.darken.apl.map.core.MapSidebarData.SidebarAircraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TAG = logTag("Map", "Sidebar")

/** Width of the list where it stays beside the map, and the most it takes as an overlay. */
internal val MapListWidth = 360.dp

/** What the list does when its controls are used. */
internal class MapListActions(
    val onClose: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onToggleWatchedOnly: () -> Unit,
    val onSort: (MapSidebarData.SortField) -> Unit,
    val onClearFilters: () -> Unit,
    val onAircraftClick: (AircraftHex) -> Unit,
)

/** The list over the map, sliding in from the end edge. */
@Composable
internal fun MapSidebar(
    visible: Boolean,
    /** Read inside the shown panel only, so its updates don't recompose the caller. */
    sidebarData: State<MapSidebarData?>,
    controls: MapSidebarData.Controls,
    shapes: AircraftShapes?,
    selectedHex: AircraftHex?,
    actions: MapListActions,
    modifier: Modifier = Modifier,
    // The scrim must stay full-screen to catch all dismiss taps, so display-cutout insets
    // supplied by the caller are applied to the sliding panel only.
    panelModifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier,
    ) {
        // A closed list has no data, it slides away with what it showed. Opened again once it is gone, it starts without it.
        val lastShown = remember { arrayOfNulls<MapSidebarData>(1) }
        val data = sidebarData.value?.also { lastShown[0] = it } ?: lastShown[0]

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                    .clickable(onClick = actions.onClose),
            )

            // Room on the start side to tap out of it, also on narrow phones
            val panelWidth = minOf(maxWidth * 0.85f, MapListWidth)
            val dismissThresholdPx = with(LocalDensity.current) { panelWidth.toPx() * 0.4f }
            val scope = rememberCoroutineScope()
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val dragState = rememberDraggableState { delta -> dragOffset = maxOf(0f, dragOffset + delta) }

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth)
                    .offset { IntOffset(dragOffset.roundToInt(), 0) }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity ->
                            scope.launch {
                                if (dragOffset > dismissThresholdPx || velocity > 500f) actions.onClose()
                                dragOffset = 0f
                            }
                        },
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) {
                MapAircraftList(
                    data = data,
                    controls = controls,
                    shapes = shapes,
                    selectedHex = selectedHex,
                    actions = actions,
                    modifier = panelModifier,
                )
            }
        }
    }
}

/** The list as a pane of its own beside the map. */
@Composable
internal fun MapListPane(
    sidebarData: State<MapSidebarData?>,
    controls: MapSidebarData.Controls,
    shapes: AircraftShapes?,
    selectedHex: AircraftHex?,
    actions: MapListActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(MapListWidth),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        MapAircraftList(
            data = sidebarData.value,
            controls = controls,
            shapes = shapes,
            selectedHex = selectedHex,
            actions = actions,
        )
    }
}

/** A row of the list, [gone] once its aircraft left the matches while the rows were held. */
internal data class ListRow(val aircraft: SidebarAircraft, val gone: Boolean = false)

/**
 * The rows to show for [matches]. While [held] the rows keep their places and take new values by
 * hex; a row whose aircraft left stays as [ListRow.gone], aircraft that are new wait until it ends.
 *
 * Held rows [A, B, C] with matches [C, D, A] become [A, B gone, C].
 */
internal fun listRows(shown: List<ListRow>, matches: List<SidebarAircraft>, held: Boolean): List<ListRow> {
    if (!held) return matches.take(MapSidebarData.ROW_LIMIT).map { ListRow(it) }
    val latest = matches.associateBy { it.hex }
    return shown.map { row -> latest[row.aircraft.hex]?.let { ListRow(it) } ?: row.copy(gone = true) }
}

/** Whether releasing [shown] would change which rows there are, or their order. */
internal fun hasHeldChanges(shown: List<ListRow>, matches: List<SidebarAircraft>): Boolean {
    val next = matches.take(MapSidebarData.ROW_LIMIT)
    return shown.size != next.size || shown.indices.any { shown[it].gone || shown[it].aircraft.hex != next[it].hex }
}

@Composable
internal fun MapAircraftList(
    data: MapSidebarData?,
    controls: MapSidebarData.Controls,
    shapes: AircraftShapes?,
    selectedHex: AircraftHex?,
    actions: MapListActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var touching by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(touching, listState.isScrollInProgress) {
        if (touching || listState.isScrollInProgress) {
            held = true
        } else if (held) {
            delay(HOLD_AFTER_INTERACTION_MS)
            held = false
        }
    }
    // Kept here while typing, the list catches up with it
    var query by remember { mutableStateOf(controls.query) }
    val clearFilters = {
        query = ""
        actions.onClearFilters()
    }

    var rows by remember { mutableStateOf(emptyList<ListRow>()) }
    var rowsControls by remember { mutableStateOf<MapSidebarData.Controls?>(null) }
    val matches = data?.matches
    val dataControls = data?.controls
    LaunchedEffect(matches, held, dataControls) {
        if (matches == null) return@LaunchedEffect
        // Asked for, so shown right away and from the top, also while the rows are held
        val asked = dataControls != rowsControls
        rowsControls = dataControls
        if (held && !asked) {
            rows = listRows(rows, matches, held = true)
            return@LaunchedEffect
        }
        // Keyed rows would otherwise keep the first visible aircraft in view wherever it moved to, e.g. the end
        // of a reversed sort
        listState.requestScrollToItem(
            index = if (asked) 0 else listState.firstVisibleItemIndex,
            scrollOffset = if (asked) 0 else listState.firstVisibleItemScrollOffset,
        )
        rows = listRows(rows, matches, held = false)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // E.g. a phone in landscape, fixed controls would leave no room for a single row
        val compact = maxHeight < COMPACT_LIST_HEIGHT
        val header = @Composable {
            ListHeader(data = data, onClose = actions.onClose)
            ListControls(
                query = query,
                onQueryChange = {
                    query = it
                    actions.onQueryChange(it)
                },
                controls = controls,
                actions = actions,
            )
            HorizontalDivider()
        }

        // Once per opening, and only before the list was scrolled by hand
        LaunchedEffect(Unit) {
            val hex = selectedHex ?: return@LaunchedEffect
            val index = snapshotFlow { rows }.first { it.isNotEmpty() }.indexOfFirst { it.aircraft.hex == hex }
            if (index > 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                listState.scrollToItem(index + if (compact) 1 else 0)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (!compact) header()

            val silhouettes = rememberSilhouettes(shapes)
            val focusManager = LocalFocusManager.current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Watches the press without taking it from the rows
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                touching = true
                                try {
                                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                } finally {
                                    touching = false
                                }
                            }
                        },
                ) {
                    if (compact) item(key = HEADER_KEY) { Column { header() } }
                    // Centered in the list, below the scrolling controls on short panels
                    fun LazyItemScope.stateBox() = if (compact) Modifier.fillParentMaxWidth() else Modifier.fillParentMaxSize()
                    when {
                        data == null -> item(key = STATE_KEY) {
                            Box(modifier = stateBox().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        rows.isEmpty() && matches.isNullOrEmpty() -> item(key = STATE_KEY) {
                            Box(modifier = stateBox(), contentAlignment = Alignment.Center) {
                                EmptyList(controls = controls, onClearFilters = clearFilters)
                            }
                        }

                        else -> items(items = rows, key = { it.aircraft.hex }) { row ->
                            AircraftRow(
                                row = row,
                                selected = row.aircraft.hex == selectedHex,
                                silhouettes = silhouettes,
                                onClick = {
                                    // Beside the map the list stays open, the keyboard would cover the selection's sheet
                                    focusManager.clearFocus()
                                    actions.onAircraftClick(row.aircraft.hex)
                                },
                            )
                        }
                    }
                    if (compact && data != null) item(key = FOOTER_KEY) { ListFooter(data = data) }
                }

                // Over the rows, taking room would move the rows it says are held
                if (held && matches != null && hasHeldChanges(rows, matches)) {
                    Text(
                        text = stringResource(R.string.map_sidebar_paused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            if (!compact && data != null) ListFooter(data = data)
        }
    }
}

@Composable
private fun ListHeader(data: MapSidebarData?, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.map_sidebar_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (data != null) {
                val inView = pluralStringResource(R.plurals.map_sidebar_in_view_x, data.inView, data.inView)
                Text(
                    text = if (data.controls.isFiltering) {
                        pluralStringResource(R.plurals.map_sidebar_matches_x, data.matches.size, data.matches.size) +
                                " · " + inView
                    } else {
                        inView
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val mapFilter = when (data.mapFilter) {
                    MapSidebarData.MapFilter.MILITARY_ONLY -> stringResource(R.string.map_control_military)
                    MapSidebarData.MapFilter.ISOLATED -> stringResource(R.string.map_control_isolate)
                    null -> null
                }
                if (mapFilter != null) {
                    Text(
                        text = stringResource(R.string.map_sidebar_map_filter_x, mapFilter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.TwoTone.Close, contentDescription = stringResource(R.string.map_sidebar_close_action))
        }
    }
}

@Composable
private fun ListControls(
    query: String,
    onQueryChange: (String) -> Unit,
    controls: MapSidebarData.Controls,
    actions: MapListActions,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.map_sidebar_filter_hint)) },
        leadingIcon = { Icon(Icons.TwoTone.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.TwoTone.Close, contentDescription = stringResource(R.string.map_sidebar_filter_clear_action))
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = controls.watchedOnly,
            onClick = actions.onToggleWatchedOnly,
            label = { Text(stringResource(R.string.map_sidebar_watched_filter)) },
            leadingIcon = {
                Icon(
                    if (controls.watchedOnly) Icons.Filled.Visibility else Icons.TwoTone.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        SortMenu(controls = controls, onSort = actions.onSort)
    }
}

@Composable
private fun SortMenu(
    controls: MapSidebarData.Controls,
    onSort: (MapSidebarData.SortField) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(controls.sort.labelRes)
    val description = stringResource(
        if (controls.ascending) R.string.map_sidebar_sort_ascending_description else R.string.map_sidebar_sort_descending_description,
        label,
    )
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(label, maxLines = 1)
            Icon(
                if (controls.ascending) Icons.TwoTone.ArrowUpward else Icons.TwoTone.ArrowDownward,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp),
            )
            Icon(Icons.TwoTone.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapSidebarData.SortField.entries.forEach { field ->
                val active = field == controls.sort
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes)) },
                    leadingIcon = {
                        if (active) Icon(Icons.TwoTone.Check, contentDescription = null)
                    },
                    trailingIcon = {
                        // The current field again reverses it, the arrow shows which way it goes then
                        if (active) {
                            Icon(
                                if (controls.ascending) Icons.TwoTone.ArrowDownward else Icons.TwoTone.ArrowUpward,
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSort(field)
                    },
                )
            }
        }
    }
}

@Composable
private fun AircraftRow(
    row: ListRow,
    selected: Boolean,
    silhouettes: Silhouettes,
    onClick: () -> Unit,
) {
    val aircraft = row.aircraft
    val goneDescription = stringResource(R.string.map_sidebar_gone_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(enabled = !row.gone, onClick = onClick)
            .then(if (row.gone) Modifier.semantics { contentDescription = goneDescription } else Modifier)
            .alpha(if (row.gone) 0.45f else 1f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AircraftTile(aircraft = aircraft, gone = row.gone, silhouettes = silhouettes)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = aircraft.callsign ?: aircraft.hex,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (aircraft.watched) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.map_sidebar_watched_description),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(14.dp),
                    )
                }
            }
            val secondary = listOfNotNull(
                aircraft.icaoType,
                aircraft.countryCode?.let { flagEmoji(it) },
                aircraft.distanceMeters?.let { "${(it / 1000).roundToInt()} km" },
            )
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when {
                    aircraft.onGround -> "ground"
                    else -> aircraft.altitudeFt?.let { "%,d ft".format(it) } ?: UNKNOWN_VALUE
                },
                style = MaterialTheme.typography.bodyMedium.merge(TABULAR),
                maxLines = 1,
            )
            Text(
                text = aircraft.speedKnots?.let { "$it kts" } ?: UNKNOWN_VALUE,
                style = MaterialTheme.typography.bodySmall.merge(TABULAR),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The aircraft's silhouette on its altitude colour, as on the map, with the map's military badge. */
@Composable
private fun AircraftTile(
    aircraft: SidebarAircraft,
    gone: Boolean,
    silhouettes: Silhouettes,
) {
    val altitude = if (aircraft.onGround) GROUND_ALTITUDE else aircraft.altitudeFt ?: GROUND_ALTITUDE
    val color = if (gone) Color(altitudeColor(GROUND_ALTITUDE)) else Color(altitudeColor(altitude))
    Box(modifier = Modifier.size(TILE_SIZE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            val silhouette = aircraft.shape?.shape?.let { silhouettes[it] }
            if (silhouette != null) {
                Image(
                    bitmap = silhouette,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(SILHOUETTE_COLOR),
                    modifier = Modifier.size(SILHOUETTE_SIZE),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_map_aircraft),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(SILHOUETTE_COLOR),
                    modifier = Modifier.size(SILHOUETTE_SIZE),
                )
            }
        }
        if (aircraft.military) {
            Image(
                painter = painterResource(R.drawable.ic_map_military_badge),
                contentDescription = stringResource(R.string.map_sidebar_military_description),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(16.dp),
            )
        }
    }
}

/** Silhouettes by shape name, each drawn the first time a row needs it; null where drawing failed. */
private class Silhouettes(private val shapes: AircraftShapes?, private val density: Float) {
    private val drawn = HashMap<String, ImageBitmap?>()

    operator fun get(name: String): ImageBitmap? {
        // A stored null is a drawing that failed, getOrPut would try it again every time
        if (name in drawn) return drawn[name]
        val bitmap = shapes?.shapes?.get(name)?.let { shape ->
            try {
                renderShapeSilhouette(shape, density).asImageBitmap()
            } catch (e: RuntimeException) {
                log(TAG) { "Shape $name could not be drawn: $e" }
                null
            }
        }
        drawn[name] = bitmap
        return bitmap
    }
}

@Composable
private fun rememberSilhouettes(shapes: AircraftShapes?): Silhouettes {
    val density = LocalDensity.current.density
    return remember(shapes, density) { Silhouettes(shapes, density) }
}

@Composable
private fun EmptyList(
    controls: MapSidebarData.Controls,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                controls.query.isNotBlank() -> stringResource(R.string.map_sidebar_empty_query_x, controls.query.trim())
                controls.watchedOnly -> stringResource(R.string.map_sidebar_empty_watched)
                else -> stringResource(R.string.map_sidebar_empty_in_view)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (controls.isFiltering) {
            TextButton(onClick = onClearFilters) {
                Text(stringResource(R.string.map_sidebar_clear_filters_action))
            }
        }
    }
}

@Composable
private fun ListFooter(data: MapSidebarData) {
    val notes = listOfNotNull(
        stringResource(R.string.map_sidebar_limit_x, MapSidebarData.ROW_LIMIT, data.matches.size)
            .takeIf { data.matches.size > MapSidebarData.ROW_LIMIT },
        stringResource(R.string.map_sidebar_server_limited).takeIf { data.serverLimited },
    )
    if (notes.isEmpty()) return
    HorizontalDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        notes.forEach {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The flag of an ISO 3166 alpha-2 country, from its regional indicator symbols. */
private fun flagEmoji(countryCode: String): String? {
    if (countryCode.length != 2 || !countryCode.all { it.isLetter() }) return null
    return countryCode.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

private const val SCRIM_ALPHA = 0.32f
private const val HEADER_KEY = "list-header"
private const val STATE_KEY = "list-state"
private const val FOOTER_KEY = "list-footer"
private val COMPACT_LIST_HEIGHT = 480.dp
private const val HOLD_AFTER_INTERACTION_MS = 1_000L
private const val UNKNOWN_VALUE = "–"
private val TILE_SIZE = 40.dp
private val SILHOUETTE_SIZE = 28.dp
private val SILHOUETTE_COLOR = Color(0xDE000000)
private val TABULAR = TextStyle(fontFeatureSettings = "tnum")
