package eu.darken.apl.watch.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Campaign
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Hexagon
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.NotificationsActive
import androidx.compose.material.icons.twotone.Pin
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material.icons.twotone.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.main.core.aircraft.isLiveAt
import eu.darken.apl.R
import eu.darken.apl.common.compose.LoadingBox
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.settings.UpgradeChip
import eu.darken.apl.upgrade.ui.UpgradeBanner
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.isEmergencySquawk
import eu.darken.apl.watch.core.types.Watch
import eu.darken.apl.watch.core.WatchSortMode
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.FlightWatch
import eu.darken.apl.watch.core.types.LocationWatch
import eu.darken.apl.watch.core.types.SquawkWatch
import eu.darken.apl.watch.core.types.WatchCheckOutcome
import eu.darken.apl.watch.ui.chart.ActivityHeatStrip
import eu.darken.apl.watch.ui.preview.mockAircraftWatchStatus
import eu.darken.apl.watch.ui.preview.mockFlightWatchStatus
import eu.darken.apl.watch.ui.preview.mockSquawkWatchStatus
import java.time.Duration
import java.time.Instant

@Composable
fun WatchListScreenHost(
    vm: WatchListViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    // The state carries a clock that ticks while collected, a stopped screen must not keep it going
    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(Unit) { vm.onScreenOpened() }

    // The repeating check follows the visible screen, not the composition, which survives a Home press
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVisible by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isVisible = true
                Lifecycle.Event.ON_STOP -> isVisible = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isVisible) {
        if (isVisible) vm.pollWhileVisible()
    }

    state?.let {
        WatchListScreen(
            state = it,
            onRefresh = vm::refresh,
            onAddWatch = vm::showAddWatchOptions,
            onWatchClick = { item -> vm.openWatchDetails(item.status.id) },
            onThumbnailClick = vm::openThumbnail,
            onAircraftTap = vm::showAircraftDetails,
            onShowAllTracked = vm::showAllTracked,
            onDeleteSelected = vm::deleteSelected,
            onSortModeSelected = vm::setSortMode,
            onUpgrade = vm::goUpgrade,
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox()
    }
}

@Composable
fun WatchListScreen(
    state: WatchListViewModel.State,
    onRefresh: () -> Unit,
    onAddWatch: (WatchListViewModel.WatchType) -> Unit,
    onWatchClick: (WatchListViewModel.WatchItem) -> Unit,
    onThumbnailClick: (PlanespottersMeta) -> Unit,
    onAircraftTap: (Aircraft) -> Unit,
    onShowAllTracked: (status: Watch.Status) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
    onSortModeSelected: (WatchSortMode) -> Unit,
    onUpgrade: () -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    val currentItemIds = remember(state.items) { state.items.map { it.status.id }.toSet() }
    val effectiveSelectedIds = selectedIds.intersect(currentItemIds)

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(hasBottomNav = true),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.TwoTone.Close, contentDescription = stringResource(R.string.common_cancel_action))
                        }
                    },
                    title = {
                        Text(
                            pluralStringResource(
                                R.plurals.watch_list_selected_x_items_msg,
                                effectiveSelectedIds.size,
                                effectiveSelectedIds.size,
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = { selectedIds = currentItemIds }) {
                            Icon(Icons.TwoTone.SelectAll, contentDescription = stringResource(R.string.common_list_select_all_action))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.watch_list_delete_selected_action))
                        }
                    },
                )
            } else {
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
                            Icon(Icons.TwoTone.Add, contentDescription = stringResource(R.string.common_add_action))
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.TwoTone.SortByAlpha, contentDescription = stringResource(R.string.watch_list_sort_action))
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_sort_mode_by_note)) },
                                    onClick = {
                                        onSortModeSelected(WatchSortMode.BY_NOTE)
                                        sortMenuExpanded = false
                                    },
                                    leadingIcon = if (state.currentSortMode == WatchSortMode.BY_NOTE) {
                                        { Icon(Icons.TwoTone.Check, contentDescription = null) }
                                    } else null,
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_sort_mode_by_last_seen)) },
                                    onClick = {
                                        onSortModeSelected(WatchSortMode.BY_LAST_SEEN)
                                        sortMenuExpanded = false
                                    },
                                    leadingIcon = if (state.currentSortMode == WatchSortMode.BY_LAST_SEEN) {
                                        { Icon(Icons.TwoTone.Check, contentDescription = null) }
                                    } else null,
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_sort_mode_by_created)) },
                                    onClick = {
                                        onSortModeSelected(WatchSortMode.BY_CREATED)
                                        sortMenuExpanded = false
                                    },
                                    leadingIcon = if (state.currentSortMode == WatchSortMode.BY_CREATED) {
                                        { Icon(Icons.TwoTone.Check, contentDescription = null) }
                                    } else null,
                                )
                            }
                        }
                    },
                )
            }
        },
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
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val gridColumns = (maxWidth / 350.dp).toInt().coerceIn(1, 3)
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    items(
                        items = state.items,
                        key = { it.status.id },
                        span = { item ->
                            if (item is WatchListViewModel.WatchItem.Multi) {
                                StaggeredGridItemSpan.FullLine
                            } else {
                                StaggeredGridItemSpan.SingleLane
                            }
                        },
                    ) { item ->
                        val isSelected = item.status.id in effectiveSelectedIds
                        when (item) {
                            is WatchListViewModel.WatchItem.Single -> SingleWatchItem(
                                item = item,
                                now = state.now,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (item.status.id in selectedIds) {
                                            selectedIds - item.status.id
                                        } else {
                                            selectedIds + item.status.id
                                        }
                                    } else {
                                        onWatchClick(item)
                                    }
                                },
                                onLongClick = {
                                    selectedIds = if (item.status.id in selectedIds) {
                                        selectedIds - item.status.id
                                    } else {
                                        selectedIds + item.status.id
                                    }
                                },
                                onThumbnailClick = if (isSelectionMode) null else onThumbnailClick,
                            )

                            is WatchListViewModel.WatchItem.Multi -> MultiWatchItem(
                                item = item,
                                now = state.now,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (item.status.id in selectedIds) {
                                            selectedIds - item.status.id
                                        } else {
                                            selectedIds + item.status.id
                                        }
                                    } else {
                                        onWatchClick(item)
                                    }
                                },
                                onLongClick = {
                                    selectedIds = if (item.status.id in selectedIds) {
                                        selectedIds - item.status.id
                                    } else {
                                        selectedIds + item.status.id
                                    }
                                },
                                onThumbnailClick = if (isSelectionMode) null else onThumbnailClick,
                                onAircraftTap = if (isSelectionMode) null else onAircraftTap,
                                onShowMore = if (isSelectionMode) null else {{ onShowAllTracked(item.status) }},
                            )
                        }
                    }

                    state.allowance?.let { allowance ->
                        item(span = StaggeredGridItemSpan.FullLine) {
                            UpgradeBanner(
                                title = stringResource(
                                    R.string.watch_banner_remaining_title,
                                    allowance.remaining,
                                    allowance.limit,
                                ),
                                body = stringResource(R.string.watch_banner_remaining_msg),
                                onClick = onUpgrade,
                            )
                        }
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
                    WatchListViewModel.WatchType.entries.forEach { type ->
                        AddWatchTypeItem(
                            label = stringResource(
                                when (type) {
                                    WatchListViewModel.WatchType.FLIGHT ->
                                        R.string.watch_list_add_watch_type_label_flight

                                    WatchListViewModel.WatchType.AIRCRAFT ->
                                        R.string.watch_list_add_watch_type_label_aircraft

                                    WatchListViewModel.WatchType.SQUAWK ->
                                        R.string.watch_list_add_watch_type_label_squawk

                                    WatchListViewModel.WatchType.LOCATION ->
                                        R.string.watch_list_add_watch_type_label_location
                                }
                            ),
                            requiresUpgrade = type in state.gatedWatchTypes,
                            onClick = {
                                onAddWatch(type)
                                showAddDialog = false
                            },
                            onUpgrade = {
                                onUpgrade()
                                showAddDialog = false
                            },
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.watch_list_remove_confirmation_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.watch_list_remove_selected_confirmation_message,
                        effectiveSelectedIds.size,
                        effectiveSelectedIds.size,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSelected(effectiveSelectedIds)
                    selectedIds = emptySet()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.watch_list_remove_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun AddWatchTypeItem(
    label: String,
    requiresUpgrade: Boolean,
    onClick: () -> Unit,
    onUpgrade: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        if (requiresUpgrade) UpgradeChip(onClick = onUpgrade)
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
private fun WatchCard(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable ColumnScope.(secondaryColor: Color) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { if (isSelectionMode) selected = isSelected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        // The fixed grey is made for the plain card, on the selected tint it would all but vanish
        val secondaryColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        content(secondaryColor)
    }
}

@Composable
private fun SingleWatchItem(
    item: WatchListViewModel.WatchItem.Single,
    now: Instant,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onThumbnailClick: ((PlanespottersMeta) -> Unit)?,
) {
    val status = item.status
    val aircraft = item.aircraft

    // A watched aircraft is known by its photo even while it is not flying
    val photoQuery = when {
        status is AircraftWatch.Status -> AircraftThumbnailQuery(hex = status.hex, registration = item.registration)
        aircraft != null -> AircraftThumbnailQuery(hex = aircraft.hex, registration = aircraft.registration)
        else -> null
    }

    WatchCard(
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) { secondaryColor ->
        // At least the photo's 3:2 height, taller when the text needs it; the photo then crops to fill.
        // Measured from the text itself: a wrapped header is missing from intrinsic heights.
        Box(
            modifier = if (photoQuery != null) Modifier.heightIn(min = WATCH_PHOTO_WIDTH / 1.5f) else Modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (photoQuery != null) WATCH_PHOTO_WIDTH + 12.dp else 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                WatchHeader(
                    icon = when (status) {
                        is FlightWatch.Status -> Icons.TwoTone.Campaign
                        else -> Icons.TwoTone.Hexagon
                    },
                    typeLabel = when (status) {
                        is FlightWatch.Status -> stringResource(R.string.watch_list_item_flight_subtitle)
                        else -> stringResource(R.string.watch_list_item_aircraft_subtitle)
                    },
                    title = when (status) {
                        is AircraftWatch.Status -> item.registration ?: "#${status.hex.uppercase()}"
                        is FlightWatch.Status -> status.callsign.uppercase()
                        else -> "?"
                    },
                    item = item,
                    now = now,
                    isSelected = isSelected,
                    secondaryColor = secondaryColor,
                )

                if (status.note.isNotBlank()) WatchNote(status.note)

                val identity = when (status) {
                    // Without a registration the title already is the hex
                    is AircraftWatch.Status -> listOfNotNull(
                        "#${status.hex.uppercase()}".takeIf { item.registration != null },
                        aircraft?.callsign?.takeIf { it.isNotBlank() },
                    )

                    else -> listOfNotNull(aircraft?.registration, aircraft?.hex?.let { "#${it.uppercase()}" })
                }
                if (identity.isNotEmpty()) {
                    DetailText(identity.joinToString(" · "), secondaryColor)
                }

                if (aircraft != null) {
                    aircraft.description?.let { DetailText(it, secondaryColor) }
                    val distance = if (item.ourLocation != null && aircraft.location != null) {
                        "${(item.ourLocation.distanceTo(aircraft.location!!) / 1000).toInt()} km"
                    } else null
                    SquawkAndDistance(aircraft, distance, secondaryColor)
                }
            }

            if (photoQuery != null) {
                Box(Modifier.matchParentSize()) {
                    // The card's corners clip the photo, it runs edge to edge
                    PlanespottersThumbnail(
                        query = photoQuery,
                        modifier = Modifier
                            .width(WATCH_PHOTO_WIDTH)
                            .fillMaxHeight(),
                        shape = RectangleShape,
                        aspectRatio = null,
                        onImageClick = onThumbnailClick,
                        onImageLongClick = onLongClick,
                    )
                }
            }
        }

        ActivityFooter(item, secondaryColor)
    }
}

@Composable
private fun MultiWatchItem(
    item: WatchListViewModel.WatchItem.Multi,
    now: Instant,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onThumbnailClick: ((PlanespottersMeta) -> Unit)?,
    onAircraftTap: ((Aircraft) -> Unit)?,
    onShowMore: (() -> Unit)?,
) {
    val status = item.status

    WatchCard(
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) { secondaryColor ->
        val hasFooter = item.activity?.hasData == true
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (hasFooter) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            WatchHeader(
                icon = when (status) {
                    is LocationWatch.Status -> Icons.TwoTone.MyLocation
                    else -> Icons.TwoTone.Pin
                },
                typeLabel = when (status) {
                    is LocationWatch.Status -> stringResource(R.string.watch_list_add_watch_type_label_location)
                    else -> stringResource(R.string.watch_list_item_squawk_subtitle)
                },
                title = when (status) {
                    is SquawkWatch.Status -> stringResource(R.string.watch_list_item_squawk_title, status.squawk.uppercase())
                    is LocationWatch.Status -> status.label
                    else -> "?"
                },
                item = item,
                now = now,
                isSelected = isSelected,
                secondaryColor = secondaryColor,
            )

            if (status is LocationWatch.Status) {
                val km = (status.radiusInMeters / 1000).toInt()
                DetailText(pluralStringResource(R.plurals.watch_list_item_location_subtitle, km, km), secondaryColor)
            }

            if (status.note.isNotBlank()) WatchNote(status.note)

            val trackedSorted = remember(status.tracked, now, item.ourLocation) {
                status.tracked.sortedWith(
                    compareBy<Aircraft> { !it.isLiveAt(now) }.thenBy { ac ->
                        if (item.ourLocation != null && ac.location != null) {
                            item.ourLocation.distanceTo(ac.location!!)
                        } else {
                            Float.MAX_VALUE
                        }
                    }
                )
            }

            if (trackedSorted.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    trackedSorted.take(MULTI_ROWS_SHOWN).forEach { ac ->
                        TrackedAircraftRow(
                            aircraft = ac,
                            secondaryColor = secondaryColor,
                            onClick = onAircraftTap?.let { tap -> { tap(ac) } },
                            onLongClick = onLongClick,
                            onThumbnailClick = onThumbnailClick,
                        )
                    }
                }
                if (status.tracked.size > MULTI_ROWS_SHOWN && onShowMore != null) {
                    TextButton(onClick = onShowMore) {
                        Text(
                            text = when (status) {
                                is LocationWatch.Status -> stringResource(R.string.common_show_on_map_action)
                                else -> pluralStringResource(
                                    R.plurals.watch_list_show_all_x_action,
                                    status.tracked.size,
                                    status.tracked.size,
                                )
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else if (status.watch.lastCheckOutcome == WatchCheckOutcome.ABSENT) {
                DetailText(stringResource(R.string.watch_list_item_none_at_last_check_msg), secondaryColor)
            }
        }

        ActivityFooter(item, secondaryColor)
    }
}

/** Title and status share a line while they fit, a long title or large font pushes the status below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchHeader(
    icon: ImageVector,
    typeLabel: String,
    title: String,
    item: WatchListViewModel.WatchItem,
    now: Instant,
    isSelected: Boolean,
    secondaryColor: Color,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // The gap sits on the title's side, so a status pushed onto the next line starts flush
        Row(
            modifier = Modifier.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isSelected) Icons.TwoTone.CheckCircle else icon,
                contentDescription = typeLabel,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else secondaryColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.status.watch.isNotificationEnabled) {
                Icon(
                    imageVector = Icons.TwoTone.NotificationsActive,
                    contentDescription = stringResource(R.string.watch_details_enable_notifications_label),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
            }
            WatchStatusLabel(item, now, secondaryColor)
        }
    }
}

@Composable
private fun WatchStatusLabel(
    item: WatchListViewModel.WatchItem,
    now: Instant,
    secondaryColor: Color,
) {
    val status = item.status
    val unresolved = when (status.watch.lastCheckOutcome) {
        WatchCheckOutcome.RESTRICTED -> stringResource(R.string.watch_state_restricted)
        WatchCheckOutcome.INCONCLUSIVE -> stringResource(R.string.watch_state_inconclusive)
        WatchCheckOutcome.EXHAUSTED -> stringResource(R.string.watch_state_exhausted)
        else -> null
    }
    if (unresolved != null) {
        Text(
            text = unresolved,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        return
    }

    if (item.liveCount > 0) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = when (item) {
                    is WatchListViewModel.WatchItem.Multi -> pluralStringResource(
                        R.plurals.watch_list_status_x_live,
                        item.liveCount,
                        item.liveCount,
                    )

                    else -> stringResource(R.string.watch_list_status_live)
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        return
    }

    // The server's history can know of a sighting none of the app's own checks caught
    val lastPing = status.tracked.mapNotNull { it.messageSeenAt }.maxOrNull()
        ?: listOfNotNull(status.lastHit?.checkAt, item.historyLastSeenAt).maxOrNull()
    Text(
        text = lastPing?.let {
            DateUtils.getRelativeTimeSpanString(
                it.toEpochMilli(),
                now.toEpochMilli(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
        } ?: stringResource(R.string.watch_list_status_not_seen_yet),
        style = MaterialTheme.typography.labelMedium,
        color = secondaryColor,
    )
}

@Composable
private fun WatchNote(note: String) {
    Text(
        text = note,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SquawkAndDistance(aircraft: Aircraft, distance: String?, color: Color) {
    val squawk = aircraft.squawk
    if (squawk == null && distance == null) return
    val style = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (squawk != null) {
            Text(stringResource(R.string.common_squawk_label) + " ", style = style, color = color)
            if (aircraft.isEmergencySquawk) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(squawk, style = style, modifier = Modifier.padding(horizontal = 4.dp))
                }
            } else {
                Text(squawk, style = style, color = color)
            }
        }
        if (squawk != null && distance != null) Text(" · ", style = style, color = color)
        if (distance != null) Text(distance, style = style, color = color)
    }
}

@Composable
private fun TrackedAircraftRow(
    aircraft: Aircraft,
    secondaryColor: Color,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit,
    onThumbnailClick: ((PlanespottersMeta) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanespottersThumbnail(
            query = AircraftThumbnailQuery(hex = aircraft.hex, registration = aircraft.registration),
            modifier = Modifier.size(width = 54.dp, height = 36.dp),
            onImageClick = onThumbnailClick,
            onImageLongClick = onLongClick,
        )
        Spacer(Modifier.width(8.dp))
        val name = aircraft.callsign?.takeIf { it.isNotBlank() }
            ?: aircraft.registration
            ?: "#${aircraft.hex.uppercase()}"
        val rest = listOfNotNull(
            aircraft.registration?.takeIf { it != name },
            aircraft.description ?: aircraft.airframe,
        )
        Text(
            text = buildAnnotatedString {
                append(name)
                if (rest.isNotEmpty()) {
                    withStyle(SpanStyle(color = secondaryColor)) { append(rest.joinToString(" · ", prefix = " · ")) }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivityFooter(item: WatchListViewModel.WatchItem, secondaryColor: Color) {
    val timeline = item.activity?.takeIf { it.hasData } ?: return
    val description = stringResource(R.string.watch_chart_activity_7d_label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.watch_chart_activity_start_short_label),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor,
        )
        ActivityHeatStrip(
            timeline = timeline,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .height(8.dp),
        )
        Text(
            text = stringResource(R.string.watch_chart_activity_end_short_label),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor,
        )
    }
}

private val WATCH_PHOTO_WIDTH = 132.dp
private const val MULTI_ROWS_SHOWN = 4

@Preview2
@Composable
private fun EmptyWatchContentPreview() {
    PreviewWrapper {
        EmptyWatchContent(onAddWatch = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Preview2
@Composable
private fun SingleAircraftWatchItemPreview() {
    PreviewWrapper {
        SingleWatchItem(
            item = WatchListViewModel.WatchItem.Single(
                status = mockAircraftWatchStatus(aircraft = FakeAircraft(), note = "Spotted at EDDF last spring"),
                aircraft = FakeAircraft(),
                ourLocation = null,
                activity = previewTimeline { it % 9 == 0 },
                liveCount = 1,
            ),
            now = Instant.now(),
            onClick = {},
            onThumbnailClick = {},
        )
    }
}

@Preview2
@Composable
private fun SingleAircraftWatchItemNoAircraftPreview() {
    PreviewWrapper {
        SingleWatchItem(
            item = WatchListViewModel.WatchItem.Single(
                status = mockAircraftWatchStatus(aircraft = null),
                aircraft = null,
                ourLocation = null,
            ),
            now = Instant.now(),
            onClick = {},
            onThumbnailClick = {},
        )
    }
}

@Preview2
@Composable
private fun SingleFlightWatchItemPreview() {
    PreviewWrapper {
        SingleWatchItem(
            item = WatchListViewModel.WatchItem.Single(
                status = mockFlightWatchStatus(callsign = "BAW123"),
                aircraft = null,
                ourLocation = null,
                activity = previewTimeline { it % 13 < 2 },
            ),
            now = Instant.now(),
            onClick = {},
            onThumbnailClick = {},
        )
    }
}

@Preview2
@Composable
private fun MultiWatchItemPreview() {
    PreviewWrapper {
        MultiWatchItem(
            item = WatchListViewModel.WatchItem.Multi(
                status = mockSquawkWatchStatus(
                    aircraft = (1..3).map { FakeAircraft(hex = "AC${it}000") }.toSet(),
                ),
                ourLocation = null,
                activity = previewTimeline { it % 5 == 0 },
                liveCount = 3,
            ),
            now = Instant.now(),
            onClick = {},
            onThumbnailClick = {},
            onAircraftTap = {},
            onShowMore = {},
        )
    }
}

/** A week of hours, the first day unknown, then [active] hours seen and the others checked quiet. */
private fun previewTimeline(active: (Int) -> Boolean): WatchActivityTimeline {
    val cells = (0 until 7 * 24 + 1).map { hour ->
        when {
            hour < 24 -> WatchActivityTimeline.Cell.UNKNOWN
            active(hour) -> WatchActivityTimeline.Cell.ACTIVE
            hour % 2 == 0 -> WatchActivityTimeline.Cell.INACTIVE
            else -> WatchActivityTimeline.Cell.SAMPLED_QUIET
        }
    }
    return WatchActivityTimeline(Instant.now().minus(Duration.ofDays(7)), cells)
}
