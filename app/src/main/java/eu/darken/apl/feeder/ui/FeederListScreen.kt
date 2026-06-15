package eu.darken.apl.feeder.ui

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.config.FeederSortMode
import eu.darken.apl.feeder.ui.preview.mockFeeder
import java.time.Instant

@Composable
fun FeederListScreenHost(
    vm: FeederListViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)

    // Ask for notification permission once we have feeders to monitor (Android 13+).
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result not acted upon; monitoring degrades gracefully */ }
    LaunchedEffect(state?.isLoggedIn) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state?.isLoggedIn == true) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    state?.let {
        FeederListScreen(
            state = it,
            onRefresh = vm::refresh,
            onSettings = { vm.navTo(eu.darken.apl.main.ui.settings.DestinationSettingsIndex) },
            onFeederClick = { feeder -> vm.openFeederAction(feeder.id) },
            onSortModeSelected = vm::setSortMode,
            onShowOnMap = vm::showFeedsOnMap,
            onLogin = vm::goToLogin,
            onClaimFeeders = vm::claimFeeders,
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox()
    }
}

@Composable
fun FeederListScreen(
    state: FeederListViewModel.State,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onFeederClick: (Feeder) -> Unit,
    onSortModeSelected: (FeederSortMode) -> Unit,
    onShowOnMap: (Set<String>) -> Unit,
    onLogin: () -> Unit,
    onClaimFeeders: () -> Unit,
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
                            if (state.isLoggedIn) {
                                Text(
                                    text = pluralStringResource(R.plurals.feeder_yours_x_active_msg, state.feederCount, state.feederCount),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.TwoTone.Settings, contentDescription = null)
                        }
                    },
                )
            }
        },
        bottomBar = { BottomNavBar(selectedTab = 3) },
    ) { contentPadding ->
        when {
            !state.isLoggedIn -> LoggedOutContent(
                onLogin = onLogin,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )

            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                if (state.feeders.isEmpty() && !state.isRefreshing) {
                    EmptyFeederContent(
                        onClaimFeeders = onClaimFeeders,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val gridColumns = (maxWidth / 350.dp).toInt().coerceIn(1, 3)
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(gridColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                                FeederHeaderItem(
                                    hasOfflineFeeders = state.hasOfflineFeeders,
                                    currentSortMode = state.currentSortMode,
                                    onSortModeSelected = onSortModeSelected,
                                )
                            }

                            items(
                                items = state.feeders,
                                key = { it.id },
                            ) { feeder ->
                                FeederItem(
                                    feeder = feeder,
                                    isSelected = feeder.id in selectedIds,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (feeder.id in selectedIds) selectedIds - feeder.id else selectedIds + feeder.id
                                        } else {
                                            onFeederClick(feeder)
                                        }
                                    },
                                    onLongClick = {
                                        selectedIds = if (feeder.id in selectedIds) selectedIds - feeder.id else selectedIds + feeder.id
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggedOutContent(
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feeder_login_required_msg),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FilledTonalButton(onClick = onLogin) {
            Text(stringResource(R.string.account_login_action))
        }
    }
}

@Composable
private fun EmptyFeederContent(
    onClaimFeeders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feeder_empty_claim_msg),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FilledTonalButton(onClick = onClaimFeeders) {
            Text(stringResource(R.string.feeder_claim_action))
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
            .padding(vertical = 8.dp),
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
                    text = stringResource(currentSortMode.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
            ) {
                FeederSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.labelRes())) },
                        onClick = {
                            onSortModeSelected(mode)
                            sortMenuExpanded = false
                        },
                        leadingIcon = if (currentSortMode == mode) {
                            { Icon(Icons.TwoTone.Check, contentDescription = null) }
                        } else null,
                    )
                }
            }
        }
    }
}

private fun FeederSortMode.labelRes(): Int = when (this) {
    FeederSortMode.BY_LABEL -> R.string.feeder_sort_mode_by_label
    FeederSortMode.BY_STATUS -> R.string.feeder_sort_mode_by_status
    FeederSortMode.BY_LAST_SEEN -> R.string.feeder_sort_mode_by_last_seen
}

private fun FeederStatus.labelRes(): Int = when (this) {
    FeederStatus.ACTIVE -> R.string.feeder_status_active
    FeederStatus.INACTIVE -> R.string.feeder_status_inactive
    FeederStatus.DATA_BLOCKED -> R.string.feeder_status_data_blocked
    FeederStatus.UNKNOWN -> R.string.feeder_status_unknown
}

@Composable
private fun FeederItem(
    feeder: Feeder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isOffline = feeder.status == FeederStatus.INACTIVE
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = when {
            isSelected -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            isOffline -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            else -> CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feeder.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(feeder.status.labelRes()) + (feeder.country?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (feeder.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = feeder.lastSeen?.let {
                        stringResource(
                            R.string.feeder_last_seen_label,
                            DateUtils.getRelativeTimeSpanString(
                                it.toEpochMilli(),
                                Instant.now().toEpochMilli(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString(),
                        )
                    } ?: stringResource(R.string.feeder_last_seen_never),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (feeder.isOnline) Icons.TwoTone.SettingsInputAntenna else Icons.TwoTone.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview2
@Composable
private fun FeederItemPreview() {
    PreviewWrapper {
        FeederItem(
            feeder = mockFeeder(label = "Home Feeder", status = FeederStatus.ACTIVE),
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
            feeder = mockFeeder(label = "Remote Feeder", status = FeederStatus.INACTIVE),
            isSelected = false,
            onClick = {},
            onLongClick = {},
        )
    }
}
