package eu.darken.apl.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.map.core.MapSidebarData
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MapSidebar(
    visible: Boolean,
    sidebarData: MapSidebarData?,
    activeSort: MapSidebarData.SortField?,
    sortAscending: Boolean,
    onSortToggle: (MapSidebarData.SortField) -> Unit,
    onClose: () -> Unit,
    onAircraftClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onClose),
            )

            // Sidebar panel
            SidebarPanel(
                sidebarData = sidebarData,
                activeSort = activeSort,
                sortAscending = sortAscending,
                onSortToggle = onSortToggle,
                onClose = onClose,
                onAircraftClick = onAircraftClick,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun SidebarPanel(
    sidebarData: MapSidebarData?,
    activeSort: MapSidebarData.SortField?,
    sortAscending: Boolean,
    onSortToggle: (MapSidebarData.SortField) -> Unit,
    onClose: () -> Unit,
    onAircraftClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = minOf(screenWidth * 0.75f, 320.dp)
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { panelWidth.toPx() * 0.4f }
    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> dragOffset = maxOf(0f, dragOffset + delta) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = panelWidth)
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    scope.launch {
                        if (dragOffset > dismissThresholdPx || velocity > 500f) {
                            onClose()
                        }
                        dragOffset = 0f
                    }
                },
            ),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            SidebarHeader(
                sidebarData = sidebarData,
                onClose = onClose,
            )

            // Sort chips
            SortChipRow(
                activeSort = activeSort,
                sortAscending = sortAscending,
                onSortToggle = onSortToggle,
            )

            HorizontalDivider()

            // Aircraft list
            val aircraft = sidebarData?.aircraft.orEmpty()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = aircraft, key = { it.hex }) { ac ->
                    SidebarAircraftRow(
                        aircraft = ac,
                        onClick = { onAircraftClick(ac.hex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarHeader(
    sidebarData: MapSidebarData?,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (sidebarData != null) {
                Text(
                    text = stringResource(R.string.map_sidebar_total_x, sidebarData.totalAircraft)
                            + " \u00b7 "
                            + stringResource(R.string.map_sidebar_on_screen_x, sidebarData.onScreen),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.TwoTone.Close,
                contentDescription = stringResource(R.string.map_sidebar_close_action),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SortChipRow(
    activeSort: MapSidebarData.SortField?,
    sortAscending: Boolean,
    onSortToggle: (MapSidebarData.SortField) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MapSidebarData.SortField.entries.forEach { field ->
            val isSelected = activeSort == field
            FilterChip(
                selected = isSelected,
                onClick = { onSortToggle(field) },
                label = {
                    val arrow = if (isSelected) {
                        if (sortAscending) " \u25B2" else " \u25BC"
                    } else ""
                    Text(
                        text = stringResource(field.labelRes) + arrow,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun SidebarAircraftRow(
    aircraft: MapSidebarData.SidebarAircraft,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Line 1: callsign/hex + squawk + country
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = aircraft.callsign?.takeIf { it.isNotBlank() } ?: aircraft.hex,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!aircraft.squawk.isNullOrBlank()) {
                Text(
                    text = aircraft.squawk,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (!aircraft.country.isNullOrBlank()) {
                Text(
                    text = aircraft.country,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                )
            }
        }

        // Line 2: type · altitude · speed
        val parts = listOfNotNull(
            aircraft.icaoType,
            aircraft.altitude,
            aircraft.speed,
        )
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString(" \u00b7 "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
