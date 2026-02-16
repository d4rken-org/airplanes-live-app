package eu.darken.apl.watch.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.BottomNavBar
import eu.darken.apl.common.compose.LoadingBox
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import java.time.Instant

@Composable
fun WatchListScreenHost(
    vm: WatchListViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)

    state?.let {
        WatchListScreen(
            state = it,
            onRefresh = vm::refresh,
            onAddWatch = vm::showAddWatchOptions,
            onSettings = { vm.navTo(eu.darken.apl.main.ui.settings.DestinationSettingsIndex) },
            onWatchClick = { item -> vm.openWatchDetails(item.status.id) },
            onThumbnailClick = vm::openThumbnail,
            onAircraftTap = vm::showAircraftOnMap,
            onShowSquawkInSearch = { status ->
                vm.showSquawkInSearch((status as SquawkWatch.Status).squawk)
            },
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchListScreen(
    state: WatchListViewModel.State,
    onRefresh: () -> Unit,
    onAddWatch: (WatchListViewModel.WatchType) -> Unit,
    onSettings: () -> Unit,
    onWatchClick: (WatchListViewModel.WatchItem) -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
    onAircraftTap: (Aircraft) -> Unit,
    onShowSquawkInSearch: (status: eu.darken.apl.watch.core.types.Watch.Status) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.watch_list_page_label))
                        Text(
                            text = pluralStringResource(R.plurals.watch_list_yours_x_active_msg, state.items.size, state.items.size),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_add_action))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = { BottomNavBar(selectedTab = 2) },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (state.items.isEmpty() && !state.isRefreshing) {
                EmptyWatchContent(
                    onAddWatch = { showAddDialog = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = state.items,
                        key = { it.status.id },
                    ) { item ->
                        when (item) {
                            is WatchListViewModel.WatchItem.Single -> SingleWatchItem(
                                item = item,
                                onClick = { onWatchClick(item) },
                                onThumbnailClick = onThumbnailClick,
                            )

                            is WatchListViewModel.WatchItem.Multi -> MultiWatchItem(
                                item = item,
                                onClick = { onWatchClick(item) },
                                onThumbnailClick = onThumbnailClick,
                                onAircraftTap = onAircraftTap,
                                onShowMore = { onShowSquawkInSearch(item.status) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.watch_list_add_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            onAddWatch(WatchListViewModel.WatchType.FLIGHT)
                            showAddDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.watch_list_add_watch_type_label_flight),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        onClick = {
                            onAddWatch(WatchListViewModel.WatchType.AIRCRAFT)
                            showAddDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.watch_list_add_watch_type_label_aircraft),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        onClick = {
                            onAddWatch(WatchListViewModel.WatchType.SQUAWK)
                            showAddDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.watch_list_add_watch_type_label_squawk),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun EmptyWatchContent(
    onAddWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.watch_list_list_addnew_msg),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FilledTonalButton(onClick = onAddWatch) {
            Text(stringResource(R.string.common_add_action))
        }
    }
}

@Composable
private fun SingleWatchItem(
    item: WatchListViewModel.WatchItem.Single,
    onClick: () -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
) {
    val status = item.status
    val aircraft = item.aircraft

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header row: icon + title + last seen
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        when (status) {
                            is AircraftWatch.Status -> R.drawable.ic_hexagon_multiple_24
                            is FlightWatch.Status -> R.drawable.ic_bullhorn_24
                            else -> R.drawable.ic_hexagon_multiple_24
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (status) {
                                is AircraftWatch.Status -> aircraft?.registration ?: "?"
                                is FlightWatch.Status -> status.callsign.uppercase()
                                else -> "?"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when (status) {
                                is AircraftWatch.Status -> "| ${status.hex.uppercase()}"
                                is FlightWatch.Status -> "| #${aircraft?.hex?.uppercase() ?: "?"}"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = when (status) {
                            is AircraftWatch.Status -> stringResource(R.string.watch_list_item_aircraft_subtitle)
                            is FlightWatch.Status -> stringResource(R.string.watch_list_item_flight_subtitle)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Notification ribbon indicator
                if (status.watch.isNotificationEnabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_alarm_bell_24),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Last triggered time
                LastTriggeredText(status)
            }

            // Extra info (message type)
            aircraft?.messageTypeLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Thumbnail + info grid
            if (aircraft != null || status is AircraftWatch.Status) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    PlanespottersThumbnail(
                        query = aircraft?.let {
                            AircraftThumbnailQuery(hex = it.hex, registration = it.registration)
                        } ?: (status as? AircraftWatch.Status)?.let {
                            AircraftThumbnailQuery(hex = it.hex)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        onImageClick = onThumbnailClick,
                    )

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        InfoRow(
                            label = aircraft?.callsign ?: "?",
                        )
                        InfoRow(
                            label = aircraft?.squawk ?: "?",
                            isAlert = aircraft?.squawk?.startsWith("7") == true,
                        )
                        val distanceText = if (item.ourLocation != null && aircraft?.location != null) {
                            val distanceInMeter = item.ourLocation.distanceTo(aircraft.location!!)
                            "${(distanceInMeter / 1000).toInt()} km"
                        } else "?"
                        InfoRow(label = distanceText)
                        InfoRow(label = aircraft?.description ?: "?")
                    }
                }
            }

            // Note
            if (status.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MultiWatchItem(
    item: WatchListViewModel.WatchItem.Multi,
    onClick: () -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
    onAircraftTap: (Aircraft) -> Unit,
    onShowMore: () -> Unit,
) {
    val status = item.status as SquawkWatch.Status

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_router_wireless_24),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = status.squawk.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )

                LastTriggeredText(status)
            }

            // Tracked aircraft sub-list (top 5)
            val trackedSorted = remember(status.tracked) {
                status.tracked
                    .map { ac ->
                        val distance = if (item.ourLocation != null && ac.location != null) {
                            item.ourLocation.distanceTo(ac.location!!)
                        } else null
                        ac to distance
                    }
                    .sortedBy { it.second }
                    .take(5)
            }

            if (trackedSorted.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                trackedSorted.forEach { (ac, _) ->
                    SquawkAircraftRow(
                        aircraft = ac,
                        onClick = { onAircraftTap(ac) },
                        onThumbnailClick = onThumbnailClick,
                    )
                }

                if (status.tracked.size > 5) {
                    TextButton(onClick = onShowMore) {
                        Text(
                            text = "${status.tracked.size} items (${stringResource(R.string.watch_list_show_all_action)})",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Note
            if (status.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SquawkAircraftRow(
    aircraft: Aircraft,
    onClick: () -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanespottersThumbnail(
            query = AircraftThumbnailQuery(hex = aircraft.hex, registration = aircraft.registration),
            modifier = Modifier.size(width = 80.dp, height = 54.dp),
            onImageClick = onThumbnailClick,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = aircraft.callsign ?: "?",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = aircraft.registration ?: "?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = aircraft.airframe ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LastTriggeredText(status: eu.darken.apl.watch.core.types.Watch.Status) {
    val lastPing = status.tracked.maxOfOrNull { it.seenAt } ?: status.lastHit?.checkAt
    val color = when {
        status.tracked.isNotEmpty() -> MaterialTheme.colorScheme.primary
        status.lastHit != null -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        text = lastPing?.let {
            DateUtils.getRelativeTimeSpanString(
                it.toEpochMilli(),
                Instant.now().toEpochMilli(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
        } ?: stringResource(R.string.watch_list_spotted_never_label),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun InfoRow(
    label: String,
    isAlert: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
