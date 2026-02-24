package eu.darken.apl.feeder.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.LocalFireDepartment
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.NotificationsActive
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.BottomNavBar
import eu.darken.apl.common.compose.LoadingBox
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.config.FeederSortMode
import eu.darken.apl.feeder.ui.chart.BeastSparkline
import eu.darken.apl.feeder.ui.preview.mockFeeder
import java.time.Instant

@Composable
fun FeederListScreenHost(
    vm: FeederListViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)

    state?.let {
        FeederListScreen(
            state = it,
            onRefresh = vm::refresh,
            onAddFeeder = vm::goToAddFeeder,
            onSettings = { vm.navTo(eu.darken.apl.main.ui.settings.DestinationSettingsIndex) },
            onFeederClick = { feeder -> vm.openFeederAction(feeder.feeder.id) },
            onSortModeSelected = vm::setSortMode,
            onShowOnMap = vm::showFeedsOnMap,
            onStartFeeding = vm::startFeeding,
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox()
    }
}

@Composable
fun FeederListScreen(
    state: FeederListViewModel.State,
    onRefresh: () -> Unit,
    onAddFeeder: () -> Unit,
    onSettings: () -> Unit,
    onFeederClick: (FeederListViewModel.FeederItem) -> Unit,
    onSortModeSelected: (FeederSortMode) -> Unit,
    onShowOnMap: (Set<String>) -> Unit,
    onStartFeeding: () -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(hasBottomNav = true),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            onShowOnMap(selectedIds)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.TwoTone.Map, contentDescription = stringResource(R.string.common_show_on_map_action))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.feeder_page_label))
                            Text(
                                text = pluralStringResource(R.plurals.feeder_yours_x_active_msg, state.feederCount, state.feederCount),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onAddFeeder) {
                            Icon(Icons.TwoTone.Add, contentDescription = stringResource(R.string.common_add_action))
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.TwoTone.Settings, contentDescription = null)
                        }
                    },
                )
            }
        },
        bottomBar = { BottomNavBar(selectedTab = 3) },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (state.feeders.isEmpty() && !state.isRefreshing) {
                EmptyFeederContent(
                    onAddFeeder = onAddFeeder,
                    onStartFeeding = onStartFeeding,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Header
                    item(key = "header") {
                        FeederHeaderItem(
                            hasOfflineFeeders = state.hasOfflineFeeders,
                            currentSortMode = state.currentSortMode,
                            onSortModeSelected = onSortModeSelected,
                        )
                    }

                    // Feeder items
                    items(
                        items = state.feeders,
                        key = { it.feeder.id },
                    ) { item ->
                        FeederItem(
                            item = item,
                            isSelected = item.feeder.id in selectedIds,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (item.feeder.id in selectedIds) {
                                        selectedIds - item.feeder.id
                                    } else {
                                        selectedIds + item.feeder.id
                                    }
                                } else {
                                    onFeederClick(item)
                                }
                            },
                            onLongClick = {
                                selectedIds = if (item.feeder.id in selectedIds) {
                                    selectedIds - item.feeder.id
                                } else {
                                    selectedIds + item.feeder.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFeederContent(
    onAddFeeder: () -> Unit,
    onStartFeeding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feeder_startfeeding_msg),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FilledTonalButton(onClick = onStartFeeding) {
            Text(stringResource(R.string.common_start_feeding_action))
        }
        TextButton(onClick = onAddFeeder, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.common_add_action))
        }
    }
}

@Composable
private fun FeederHeaderItem(
    hasOfflineFeeders: Boolean,
    currentSortMode: FeederSortMode,
    onSortModeSelected: (FeederSortMode) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (hasOfflineFeeders) {
                stringResource(R.string.feeder_status_header_some_offline)
            } else {
                stringResource(R.string.feeder_status_header_all_online)
            },
            color = if (hasOfflineFeeders) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )

        Box {
            TextButton(onClick = { sortMenuExpanded = true }) {
                Text(
                    text = when (currentSortMode) {
                        FeederSortMode.BY_LABEL -> stringResource(R.string.feeder_sort_mode_by_label)
                        FeederSortMode.BY_MESSAGE_RATE -> stringResource(R.string.feeder_sort_mode_by_message_rate)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeder_sort_mode_by_label)) },
                    onClick = {
                        onSortModeSelected(FeederSortMode.BY_LABEL)
                        sortMenuExpanded = false
                    },
                    leadingIcon = if (currentSortMode == FeederSortMode.BY_LABEL) {
                        { Icon(Icons.TwoTone.Check, contentDescription = null) }
                    } else null,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeder_sort_mode_by_message_rate)) },
                    onClick = {
                        onSortModeSelected(FeederSortMode.BY_MESSAGE_RATE)
                        sortMenuExpanded = false
                    },
                    leadingIcon = if (currentSortMode == FeederSortMode.BY_MESSAGE_RATE) {
                        { Icon(Icons.TwoTone.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun FeederItem(
    item: FeederListViewModel.FeederItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val feeder = item.feeder
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = when {
            isSelected -> androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            item.isOffline -> androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            )
            else -> androidx.compose.material3.CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Feeder name
                Text(
                    text = feeder.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Last seen
                Text(
                    text = feeder.lastSeen?.let {
                        DateUtils.getRelativeTimeSpanString(
                            it.toEpochMilli(),
                            Instant.now().toEpochMilli(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ).toString()
                    } ?: "?",
                    style = MaterialTheme.typography.bodySmall,
                )

                // Beast stats
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "BEAST",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = feeder.beastStats?.messageRate?.let { "%.1f MSG/s".format(it) } ?: "? MSG/s",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = feeder.beastStats?.bandwidth?.let { "%.1f KBit/s".format(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                if (item.beastSparkline.size >= 2) {
                    BeastSparkline(
                        data = item.beastSparkline,
                        lineColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(32.dp),
                    )
                }

                // MLAT stats
                if (feeder.mlatStats != null) {
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = "MLAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "%.1f MSG/s".format(feeder.mlatStats.messageRate),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = buildString {
                                append(stringResource(R.string.feeder_mlat_outliers_format, feeder.mlatStats.outlierPercent))
                                append(" ")
                                append(pluralStringResource(R.plurals.feeder_mlat_peers_count, feeder.mlatStats.peerCount, feeder.mlatStats.peerCount))
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // Monitor icon
            Spacer(Modifier.width(8.dp))
            AnimatedVisibility(visible = feeder.config.offlineCheckTimeout != null) {
                Icon(
                    imageVector = if (item.isOffline) Icons.TwoTone.LocalFireDepartment else Icons.TwoTone.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (item.isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun FeederItemOnlinePreview() {
    PreviewWrapper {
        FeederItem(
            item = FeederListViewModel.FeederItem(
                feeder = mockFeeder(label = "Home Feeder"),
                isOffline = false,
            ),
            isSelected = false,
            onClick = {},
            onLongClick = {},
        )
    }
}

@Preview2
@Composable
private fun FeederItemOfflinePreview() {
    PreviewWrapper {
        FeederItem(
            item = FeederListViewModel.FeederItem(
                feeder = mockFeeder(label = "Remote Feeder"),
                isOffline = true,
            ),
            isSelected = false,
            onClick = {},
            onLongClick = {},
        )
    }
}

@Preview2
@Composable
private fun FeederHeaderAllOnlinePreview() {
    PreviewWrapper {
        FeederHeaderItem(
            hasOfflineFeeders = false,
            currentSortMode = FeederSortMode.BY_LABEL,
            onSortModeSelected = {},
        )
    }
}

@Preview2
@Composable
private fun FeederHeaderWithOfflinePreview() {
    PreviewWrapper {
        FeederHeaderItem(
            hasOfflineFeeders = true,
            currentSortMode = FeederSortMode.BY_LABEL,
            onSortModeSelected = {},
        )
    }
}
