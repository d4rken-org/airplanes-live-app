package eu.darken.apl.map.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
import kotlin.math.max
import kotlin.math.min
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
    val currentRoute = (routeDisplay as? NativeMapViewModel.RouteDisplay.Result)
        ?.takeIf { it.hex == state?.selectedHex }
        ?.route

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

                    is NativeMapEvents.ShareAircraft -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, event.text)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.common_share_action)))
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

    val selectedHex = state?.selectedHex
    var lastDetails by remember { mutableStateOf<MapAircraftDetails?>(null) }
    val freshDetails = state?.details
    // While the sheet slides away it keeps showing the aircraft it showed, and selecting the same
    // aircraft again keeps the full details until the server answers again
    val details = when {
        freshDetails == null -> lastDetails
        freshDetails.isPartial && lastDetails?.let { !it.isPartial && it.hex == freshDetails.hex } == true -> lastDetails
        else -> freshDetails
    }
    LaunchedEffect(details) {
        if (details != null) lastDetails = details
    }
    val detailsLoading = details?.isPartial == true && state?.detailsLoading == true
    val sheetListState = rememberLazyListState()
    BackHandler(enabled = selectedHex != null && sheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.partialExpand() }
    }
    // Until the row is measured, about the height of its pills
    var floatingRowHeight by remember { mutableIntStateOf(with(density) { 40.dp.roundToPx() }) }

    // The sheet's peek is the glance at rest, measured by the sheet content itself
    var sheetLayoutHeight by remember { mutableIntStateOf(0) }
    var glancePeekPx by remember { mutableIntStateOf(0) }
    val minPeekPx = with(density) { 180.dp.roundToPx() }
    val peekPx = when {
        glancePeekPx == 0 || sheetLayoutHeight == 0 -> minPeekPx
        // Large fonts on a short screen must still leave room for the map
        else -> min(glancePeekPx, max(minPeekPx, (sheetLayoutHeight * 0.6f).roundToInt()))
    }
    // The peek grows while the details load, each change cancels the running settle, so every change settles again
    LaunchedEffect(selectedHex, peekPx) {
        if (selectedHex == null) {
            sheetState.hide()
            lastDetails = null
        } else if (sheetState.targetValue != SheetValue.Expanded) {
            // The sheet needs its content laid out before it can peek
            withFrameNanos { }
            sheetState.partialExpand()
        }
    }
    val sheetProgress: () -> Float = {
        val range = sheetLayoutHeight - peekPx
        val offset = runCatching { sheetState.requireOffset() }.getOrNull()
        when {
            offset == null || range <= 0 -> if (sheetState.currentValue == SheetValue.Expanded) 1f else 0f
            else -> (1f - offset / range).coerceIn(0f, 1f)
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
            // The map runs under the cutout in fullscreen and under side navigation bars in landscape, the overlays must not
            val avoidCutout = isFullscreen && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
            val overlayInsets = if (avoidCutout) {
                WindowInsets.displayCutout
            } else {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            }
            val cutoutSafe = Modifier.windowInsetsPadding(overlayInsets)
            val cutoutTop = if (avoidCutout) WindowInsets.displayCutout.getTop(density) else 0
            val layoutDirection = LocalLayoutDirection.current
            val overlayEndPx = if (layoutDirection == LayoutDirection.Ltr) {
                overlayInsets.getRight(density, layoutDirection)
            } else {
                overlayInsets.getLeft(density, layoutDirection)
            }
            val showFloatingActions = selectedHex != null && details != null && !isSidebarOpen
            // My location rides on the sheet, above the floating actions while they are shown
            val floatingActionsSpace = if (showFloatingActions && sheetState.targetValue != SheetValue.Expanded) {
                floatingRowHeight + with(density) { 8.dp.roundToPx() }
            } else {
                0
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { sheetLayoutHeight = it.height },
            ) {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetContent = {
                        val shown = details ?: return@BottomSheetScaffold
                        // A blocked request explains more than the aircraft missing from older answers
                        val note = state?.takeIf { shown.isPartial && !detailsLoading }?.let { current ->
                            unavailableText(current)
                                ?: stringResource(R.string.watch_list_item_no_current_data_msg).takeIf { current.selectedMissing }
                        }
                        AircraftDetailsSheetContent(
                            details = shown,
                            loading = detailsLoading,
                            note = note,
                            route = currentRoute,
                            progress = sheetProgress,
                            topInset = cutoutTop,
                            maxPhotoHeight = (sheetLayoutHeight * 0.45f).roundToInt(),
                            onCollapsedHeight = { glancePeekPx = it },
                            listState = sheetListState,
                            onShowInSearch = vm::showInSearch,
                            onAddWatch = vm::addWatch,
                            onPhotoClick = { link ->
                                if (sheetState.currentValue == SheetValue.Expanded) {
                                    vm.onOpenUrl(link)
                                } else {
                                    scope.launch { sheetState.expand() }
                                }
                            },
                            modifier = Modifier.windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Horizontal)),
                        )
                    },
                    sheetPeekHeight = with(density) { peekPx.toDp() },
                    sheetShape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
                    sheetDragHandle = null,
                    // A drag would cancel the hide that follows a deselection and leave the sheet up
                    sheetSwipeEnabled = selectedHex != null,
                    snackbarHost = {
                        // Snackbars appear right above the resting sheet, where the floating actions are
                        val lift = if (showFloatingActions && sheetState.currentValue == SheetValue.PartiallyExpanded) {
                            with(density) { (floatingRowHeight + 8.dp.roundToPx()).toDp() }
                        } else {
                            0.dp
                        }
                        SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = lift))
                    },
                    modifier = Modifier.fillMaxSize(),
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

                        var mapSize by remember { mutableStateOf(IntSize.Zero) }
                        val layoutDirection = LocalLayoutDirection.current
                        val insetTopPx = overlayInsets.getTop(density)
                        val insetEndPx = if (layoutDirection == LayoutDirection.Ltr) {
                            overlayInsets.getRight(density, layoutDirection)
                        } else {
                            overlayInsets.getLeft(density, layoutDirection)
                        }
                        val sheetTop = runCatching { sheetState.requireOffset() }.getOrNull()
                        // The sheet is centered and has a maximum width, on wide screens the end corner stays free
                        val sheetCoversEnd = with(density) {
                            val sheetWidth = minOf(mapSize.width, BottomSheetDefaults.SheetMaxWidth.roundToPx())
                            (mapSize.width - sheetWidth) / 2 < insetEndPx + (16.dp + MapControlRailWidth).roundToPx()
                        }
                        val freeBottomPx = if (sheetCoversEnd && sheetTop != null) {
                            (sheetTop - floatingActionsSpace).coerceAtMost(mapSize.height.toFloat())
                        } else {
                            mapSize.height.toFloat()
                        }
                        val railRoom = with(density) { (freeBottomPx - insetTopPx).toDp() } - 16.dp
                        // Too little height for a rail above the location button puts that button into the rail
                        val measured = mapSize != IntSize.Zero
                        val locationInRail = measured && railRoom - LOCATION_BUTTON_SPACE < MapControlRailWidth
                        val railMaxHeight = when {
                            !measured -> Dp.Unspecified
                            locationInRail -> (railRoom - 16.dp).coerceAtLeast(0.dp)
                            else -> railRoom - LOCATION_BUTTON_SPACE
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { mapSize = it }
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
                                onMyLocation = if (locationInRail) {
                                    { vm.goToMyLocation() }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .heightIn(max = railMaxHeight),
                            )

                            current?.let { MapStatus(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) }

                            // Rides on the aircraft sheet's top edge, an expanded sheet leaves no map to locate on
                            if (!locationInRail && sheetState.targetValue != SheetValue.Expanded) {
                                MyLocationButton(
                                    onClick = { vm.goToMyLocation() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .offset { IntOffset(0, -(mapSize.height - freeBottomPx).roundToInt()) },
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

                // Above the whole scaffold, so they can sit on the sheet's photo as well as above the sheet
                if (showFloatingActions) {
                    val shown = details
                    SheetFloatingActions(
                        sheetTop = { runCatching { sheetState.requireOffset() }.getOrNull() },
                        contentScroll = {
                            if (sheetListState.firstVisibleItemIndex == 0) sheetListState.firstVisibleItemScrollOffset else Int.MAX_VALUE / 2
                        },
                        progress = sheetProgress,
                        safeTop = cutoutTop,
                        sheetMaxWidth = with(density) { BottomSheetDefaults.SheetMaxWidth.roundToPx() },
                        endInset = overlayEndPx,
                        onRowHeight = { floatingRowHeight = it },
                        follow = state?.toggles?.follow == true,
                        onToggleFollow = { vm.toggleFollow() },
                        onShare = { vm.shareAircraft(shown) },
                        onClose = { vm.deselect() },
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

// The location button, its margin and a gap to the rail above it
private val LOCATION_BUTTON_SPACE = MapControlRailWidth + 16.dp + 8.dp

/** Why no answers arrive or positions are aging, null while the map is up to date. */
@Composable
private fun unavailableText(state: NativeMapViewModel.State): String? = when {
    state.waiting is AircraftRepo.ViewingState.Reason.Exhausted -> stringResource(R.string.map_status_allowance_used_up)
    state.waiting is AircraftRepo.ViewingState.Reason.Restricted ||
            state.waiting is AircraftRepo.ViewingState.Reason.Revoked ||
            state.waiting is AircraftRepo.ViewingState.Reason.Offline -> stringResource(R.string.map_status_unavailable)

    state.offline -> stringResource(R.string.map_status_connection_problem)
    else -> null
}

/** Why the map shows fewer aircraft than exist, or none at all. */
@Composable
private fun MapStatus(state: NativeMapViewModel.State, modifier: Modifier = Modifier) {
    val text = unavailableText(state) ?: when {
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
