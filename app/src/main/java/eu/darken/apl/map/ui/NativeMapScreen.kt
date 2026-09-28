package eu.darken.apl.map.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.OpenInNew
import androidx.compose.material.icons.automirrored.twotone.ViewSidebar
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Fullscreen
import androidx.compose.material.icons.twotone.FullscreenExit
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.ViewInAr
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.compose.BottomNavBar
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.upgrade.ui.TierChip
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private val TAG = logTag("Map", "Native", "Screen")

@Composable
fun NativeMapScreenHost(
    mapOptions: MapOptions? = null,
    vm: NativeMapViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(mapOptions) { vm.init(mapOptions) }

    // Collections stop while the screen is not started, which lets the server polling stop too
    val state by vm.state.collectAsStateWithLifecycle()
    val startCamera by vm.initialCamera.collectAsStateWithLifecycle()
    val sidebarData by vm.sidebarData.collectAsStateWithLifecycle()
    val isSidebarOpen by vm.isSidebarOpen.collectAsStateWithLifecycle()
    val sidebarSort by vm.sidebarSort.collectAsStateWithLifecycle()
    val sidebarSortAscending by vm.sidebarSortAscending.collectAsStateWithLifecycle()
    val shapes by vm.shapes.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val controller = remember { NativeMapController() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        log(TAG) { "locationPermissionLauncher: $isGranted" }
        if (isGranted) {
            vm.goToMyLocation()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.map_my_location_permission_required))
            }
        }
    }

    val routeDisplay by vm.routeDisplay.collectAsStateWithLifecycle(initialValue = null)
    val currentRoute = (routeDisplay as? NativeMapViewModel.RouteDisplay.Result)?.route

    LaunchedEffect(Unit) {
        vm.events
            .onEach { event ->
                when (event) {
                    NativeMapEvents.RequestLocationPermission -> {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    NativeMapEvents.LocationUnavailable -> scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.map_my_location_unavailable))
                    }

                    is NativeMapEvents.WatchAdded -> {
                        val ac = event.watch.tracked.firstOrNull()
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.watch_item_x_added, ac?.registration ?: ac?.hex)
                        )
                    }

                    is NativeMapEvents.MoveCamera -> controller.moveTo(event.camera, event.keepZoom)

                    is NativeMapEvents.FitBounds -> controller.fitBounds(
                        event.south,
                        event.west,
                        event.north,
                        event.east,
                        paddingPx = with(density) { 64.dp.roundToPx() },
                    )

                    is NativeMapEvents.PinnedTruncated -> scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.map_pinned_truncated, event.shown, event.requested)
                        )
                    }
                }
            }
            .launchIn(this)
    }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsExpanded by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
        snackbarHostState = snackbarHostState,
    )

    // A new selection has no details until its first answer, the sheet keeps the previous ones meanwhile
    val selectedHex = state?.selectedHex
    var details by remember { mutableStateOf<MapAircraftDetails?>(null) }
    val freshDetails = state?.details
    LaunchedEffect(freshDetails) {
        if (freshDetails != null) details = freshDetails
    }
    LaunchedEffect(selectedHex, details != null) {
        when {
            selectedHex == null -> {
                sheetState.hide()
                details = null
            }

            details != null -> sheetState.partialExpand()
        }
    }
    // A shown sheet is only hidden for a selection when the user swipes it away, which ends the selection
    var sheetShown by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue != SheetValue.Hidden) {
            sheetShown = true
            return@LaunchedEffect
        }
        if (sheetShown && vm.state.value?.selectedHex != null) vm.deselect()
        sheetShown = false
    }

    MapFullscreenEffect(isFullscreen)

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(stringResource(R.string.app_name))
                                TierChip(
                                    isPro = state?.isPro == true,
                                    onClick = { vm.goUpgrade() },
                                )
                            }
                            Text(
                                text = state?.tagline ?: stringResource(R.string.map_page_label),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.goToMyLocation() }) {
                            Icon(Icons.TwoTone.MyLocation, contentDescription = stringResource(R.string.map_my_location_action))
                        }
                        IconButton(onClick = { vm.reset() }) {
                            Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.common_reset_action))
                        }
                        IconButton(onClick = { vm.goToSettings() }) {
                            Icon(Icons.TwoTone.Settings, contentDescription = stringResource(R.string.label_settings))
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = when {
            !isFullscreen -> aplContentWindowInsets(hasBottomNav = true)
            // Pre-R the immersive effect can't hide the system bars, so keep their inset
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R -> aplContentWindowInsets(hasBottomNav = false)
            else -> WindowInsets(0, 0, 0, 0)
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            val sheetCornerRadius by animateDpAsState(
                targetValue = if (sheetState.currentValue == SheetValue.Expanded) 0.dp else 28.dp,
                label = "sheetCornerRadius",
            )
            // In fullscreen the map runs under the cutout, the overlays must not
            val cutoutSafe = if (isFullscreen && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Modifier.windowInsetsPadding(WindowInsets.displayCutout)
            } else {
                Modifier
            }

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetContent = {
                    details?.let { shown ->
                        AircraftDetailsSheetContent(
                            details = shown,
                            route = currentRoute,
                            onClose = { vm.deselect() },
                            onCopyLink = { hex ->
                                vm.copyLink(hex)
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.map_aircraft_details_link_copied))
                                }
                            },
                            onShowInSearch = vm::showInSearch,
                            onAddWatch = vm::addWatch,
                            onThumbnailClick = vm::onOpenUrl,
                            modifier = if (sheetState.currentValue == SheetValue.Expanded) cutoutSafe else Modifier,
                        )
                    }
                },
                sheetPeekHeight = 180.dp,
                sheetShape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
                sheetDragHandle = null,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.weight(1f),
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val camera = startCamera
                    val current = state
                    if (camera != null && current != null) {
                        NativeMapView(
                            controller = controller,
                            styleUrl = current.style.styleUrl(darkTheme),
                            startCamera = camera,
                            frames = vm.frames,
                            shapes = shapes,
                            labels = current.toggles.labels,
                            follow = current.toggles.follow,
                            myLocation = current.myLocation,
                            onCameraIdle = vm::onCameraIdle,
                            onAircraftTapped = vm::onAircraftTapped,
                            onMapTapped = vm::onMapTapped,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().then(cutoutSafe)) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalIconButton(
                                onClick = { isFullscreen = !isFullscreen },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    if (isFullscreen) Icons.TwoTone.FullscreenExit else Icons.TwoTone.Fullscreen,
                                    contentDescription = stringResource(R.string.common_fullscreen_action),
                                )
                            }
                            if (isFullscreen) {
                                FilledTonalIconButton(
                                    onClick = { vm.goToMyLocation() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.TwoTone.MyLocation, contentDescription = stringResource(R.string.map_my_location_action))
                                }
                            }
                            if (vm.hasRotationSensor) {
                                FilledTonalIconButton(
                                    onClick = { vm.goToAr() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.TwoTone.ViewInAr, contentDescription = stringResource(R.string.ar_view_action))
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box {
                                FilledTonalIconButton(
                                    onClick = { controlsExpanded = true },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.TwoTone.Tune, contentDescription = stringResource(R.string.map_controls_action))
                                }
                                val toggles = current?.toggles ?: NativeMapViewModel.Toggles()
                                val hasSelection = current?.selectedHex != null
                                DropdownMenu(
                                    expanded = controlsExpanded,
                                    onDismissRequest = { controlsExpanded = false },
                                ) {
                                    ControlItem(R.string.map_control_labels, toggles.labels) { vm.toggleLabels() }
                                    ControlItem(R.string.map_control_all_tracks, toggles.allTracks) { vm.toggleAllTracks() }
                                    ControlItem(R.string.map_control_military, toggles.militaryOnly) { vm.toggleMilitaryOnly() }
                                    ControlItem(R.string.map_control_multiselect, toggles.multiSelect) { vm.toggleMultiSelect() }
                                    ControlItem(
                                        R.string.map_control_isolate,
                                        toggles.isolate,
                                        enabled = hasSelection || (current?.pinnedCount ?: 0) > 0,
                                    ) { vm.toggleIsolate() }
                                    ControlItem(R.string.map_control_follow, toggles.follow, enabled = hasSelection) {
                                        vm.toggleFollow()
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.map_open_website_action)) },
                                        onClick = {
                                            controlsExpanded = false
                                            vm.openWebsite()
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.TwoTone.OpenInNew, contentDescription = null) },
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = { vm.toggleSidebar() },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.TwoTone.ViewSidebar,
                                    contentDescription = stringResource(R.string.map_sidebar_toggle_action),
                                )
                            }
                        }

                        current?.let { MapStatus(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) }
                    }

                    MapSidebar(
                        visible = isSidebarOpen,
                        sidebarData = sidebarData,
                        activeSort = sidebarSort,
                        sortAscending = sidebarSortAscending,
                        onSortToggle = { vm.toggleSort(it) },
                        onClose = { vm.closeSidebar() },
                        onAircraftClick = { hex -> vm.onSidebarAircraftClicked(hex) },
                        panelModifier = cutoutSafe,
                    )
                }
            }

            if (!isFullscreen) {
                BottomNavBar(selectedTab = 0)
            }
        }
    }
}

@Composable
private fun ControlItem(
    @StringRes labelRes: Int,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = onClick,
        leadingIcon = if (active) {
            { Icon(Icons.TwoTone.Check, contentDescription = null) }
        } else {
            null
        },
        enabled = enabled,
    )
}

/** Why the map shows fewer aircraft than exist, or none at all. */
@Composable
private fun MapStatus(state: NativeMapViewModel.State, modifier: Modifier = Modifier) {
    val text = when {
        state.waiting is AircraftRepo.ViewingState.Reason.Exhausted -> stringResource(R.string.map_status_allowance_used_up)
        state.waiting is AircraftRepo.ViewingState.Reason.Restricted ||
                state.waiting is AircraftRepo.ViewingState.Reason.Revoked ||
                state.waiting is AircraftRepo.ViewingState.Reason.Offline -> stringResource(R.string.map_status_unavailable)

        state.offline -> stringResource(R.string.map_status_connection_problem)
        state.capped -> state.totalMatching
            ?.let { stringResource(R.string.map_status_capped, state.shownCount, it) }
            ?: stringResource(R.string.map_status_capped_unknown_total, state.shownCount)

        else -> return
    }
    Box(
        modifier = modifier
            .widthIn(max = 360.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
