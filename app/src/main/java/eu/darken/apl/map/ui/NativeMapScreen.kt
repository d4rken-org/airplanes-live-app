package eu.darken.apl.map.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.compose.HideBottomNavBar
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
import kotlin.math.roundToInt

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
    HideBottomNavBar(hidden = isFullscreen)
    var optionsOpen by rememberSaveable { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
        snackbarHostState = snackbarHostState,
    )

    // The sheet keeps its content while it hides after a deselection, a new selection shows a
    // placeholder until its first answer brings details
    val selectedHex = state?.selectedHex
    var sheetHex by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<MapAircraftDetails?>(null) }
    val freshDetails = state?.details
    LaunchedEffect(freshDetails) {
        if (freshDetails != null) details = freshDetails
    }
    LaunchedEffect(selectedHex) {
        if (selectedHex == null) {
            sheetState.hide()
            sheetHex = null
            details = null
        } else {
            sheetHex = selectedHex
            // The sheet needs its content laid out before it can peek
            withFrameNanos { }
            sheetState.partialExpand()
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
                    val hex = sheetHex ?: return@BottomSheetScaffold
                    val follow = state?.toggles?.follow == true
                    val shown = details?.takeIf { it.hex == hex }
                    if (shown == null) {
                        AircraftPendingSheetContent(
                            hex = hex,
                            follow = follow,
                            onToggleFollow = { vm.toggleFollow() },
                            onClose = { vm.deselect() },
                        )
                    } else {
                        AircraftDetailsSheetContent(
                            details = shown,
                            route = currentRoute,
                            follow = follow,
                            onToggleFollow = { vm.toggleFollow() },
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
                            militaryHighlight = current.toggles.militaryHighlight,
                            follow = current.toggles.follow,
                            myLocation = current.myLocation,
                            onCameraIdle = vm::onCameraIdle,
                            onAircraftTapped = vm::onAircraftTapped,
                            onMapTapped = vm::onMapTapped,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    var mapHeightPx by remember { mutableIntStateOf(0) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { mapHeightPx = it.height }
                            .then(cutoutSafe)
                    ) {
                        current?.let {
                            MapFilterChips(
                                militaryOnly = it.toggles.militaryOnly,
                                isolating = it.isolating,
                                pinnedCount = it.pinnedCount,
                                onClearMilitaryOnly = { vm.toggleMilitaryOnly() },
                                onClearIsolate = { vm.toggleIsolate() },
                                onClearPinned = { vm.clearPinned() },
                                // Keeps clear of the rail
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp + MapControlRailWidth + 8.dp),
                            )
                        }

                        MapControlRail(
                            isFullscreen = isFullscreen,
                            showAr = vm.hasRotationSensor,
                            onOptions = {
                                vm.closeSidebar()
                                optionsOpen = true
                            },
                            onAircraftList = { vm.toggleSidebar() },
                            onAr = { vm.goToAr() },
                            onFullscreen = { isFullscreen = !isFullscreen },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                        )

                        current?.let { MapStatus(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) }

                        // Rides on the aircraft sheet's top edge, an expanded sheet leaves no map to locate on
                        if (sheetState.targetValue != SheetValue.Expanded) {
                            MyLocationButton(
                                onClick = { vm.goToMyLocation() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .offset {
                                        val sheetTop = runCatching { sheetState.requireOffset() }.getOrNull()
                                        val lift = sheetTop?.let { (mapHeightPx - it).coerceAtLeast(0f) } ?: 0f
                                        IntOffset(0, -lift.roundToInt())
                                    },
                            )
                        }
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
        }
    }

    if (optionsOpen) {
        val current = state
        MapOptionsSheet(
            toggles = current?.toggles ?: NativeMapViewModel.Toggles(),
            canIsolate = current?.selectedHex != null || (current?.pinnedCount ?: 0) > 0,
            onToggleLabels = { vm.toggleLabels() },
            onToggleAllTracks = { vm.toggleAllTracks() },
            onToggleMilitaryHighlight = { vm.toggleMilitaryHighlight() },
            onToggleMilitaryOnly = { vm.toggleMilitaryOnly() },
            onToggleIsolate = { vm.toggleIsolate() },
            onToggleMultiSelect = { vm.toggleMultiSelect() },
            onOpenWebsite = {
                optionsOpen = false
                vm.openWebsite()
            },
            onResetMap = {
                optionsOpen = false
                vm.reset()
            },
            onDismiss = { optionsOpen = false },
        )
    }
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
