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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
import eu.darken.apl.common.compose.Mascot
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.upgrade.ui.TierChip
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val TAG = logTag("Map", "Native", "Screen")

@Composable
fun NativeMapScreenHost(
    vm: NativeMapViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(Unit) { vm.init() }
    DisposableEffect(vm) {
        vm.onScreenShown()
        onDispose { vm.onScreenHidden() }
    }

    // Collections stop while the screen is not started, which lets the server polling stop too
    val fullState = vm.state.collectAsStateWithLifecycle()
    // Only the status and the sheet read the rest, the screen itself skips its changes
    val state by remember { derivedStateOf { fullState.value?.let { ScreenState(it) } } }
    val startCamera by vm.initialCamera.collectAsStateWithLifecycle()
    val sidebarData = vm.sidebarData.collectAsStateWithLifecycle()
    val isSidebarOpen by vm.isSidebarOpen.collectAsStateWithLifecycle()
    val sidebarSort by vm.sidebarSort.collectAsStateWithLifecycle()
    val sidebarSortAscending by vm.sidebarSortAscending.collectAsStateWithLifecycle()
    val shapes by vm.shapes.collectAsStateWithLifecycle()
    // Playback moves the cursor every frame, only the history's own overlays read it, see [HistoryShown]
    val historyView = vm.historyView.collectAsStateWithLifecycle()
    val viewingAttempt = vm.viewingAttempt.collectAsStateWithLifecycle()
    val watchedHexes by vm.watchedHexes.collectAsStateWithLifecycle()
    val inHistory by remember { derivedStateOf { historyView.value != null } }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Replaced by the next tap instead of queueing behind it
    var capSnackbar by remember { mutableStateOf<Job?>(null) }
    val watchSnackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val controller = remember { NativeMapController() }
    val historyLayout = remember { HistoryLayout() }
    val currentLayoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )

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

    // Its progress changes with the answers, only the sheet reads it
    val routeDisplay = vm.routeDisplay.collectAsStateWithLifecycle(initialValue = null)

    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(Unit) {
        // A newer watch message replaces the last one, whether it is shown or still waiting
        var watchSnackbar: Job? = null
        fun showWatchSnackbar(block: suspend () -> Unit) {
            watchSnackbar?.cancel()
            watchSnackbarHostState.currentSnackbarData?.dismiss()
            watchSnackbar = scope.launch { block() }
        }

        vm.events
            .onEach { event ->
                when (event) {
                    NativeMapEvents.RequestLocationPermission -> {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    NativeMapEvents.LocationUnavailable -> scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.map_my_location_unavailable))
                    }

                    is NativeMapEvents.WatchAdded -> showWatchSnackbar {
                        // Shorter than SnackbarDuration.Short, an Undo only matters right after the tap
                        val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
                            UNDO_TIMEOUT_MS,
                            containsIcons = false,
                            containsText = true,
                            containsControls = true,
                        ) ?: UNDO_TIMEOUT_MS
                        val result = withTimeoutOrNull(timeout) {
                            watchSnackbarHostState.showSnackbar(
                                message = context.getString(R.string.watch_item_x_added, event.label),
                                actionLabel = context.getString(R.string.common_undo_action),
                                duration = SnackbarDuration.Indefinite,
                            )
                        }
                        if (result == SnackbarResult.ActionPerformed) vm.undoWatch(event.watchId)
                    }

                    is NativeMapEvents.WatchLimitReached -> showWatchSnackbar {
                        val result = watchSnackbarHostState.showSnackbar(
                            message = event.error.getLocalizedError(context).description.get(context),
                            actionLabel = context.getString(R.string.common_upgrade_action),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.goUpgrade()
                    }

                    is NativeMapEvents.MoveCamera -> {
                        historyLayout.fit?.cancel()
                        controller.moveTo(event.camera, event.keepZoom)
                    }

                    is NativeMapEvents.FitBounds -> if (event.aroundHistory != null) {
                        historyLayout.fit?.cancel()
                        historyLayout.fit = scope.launch {
                            // Once the date bar, the timeline and the resting sheet are laid out
                            withTimeoutOrNull(HISTORY_FIT_WAIT) {
                                snapshotFlow {
                                    historyLayout.ready && sheetState.currentValue == SheetValue.PartiallyExpanded &&
                                            sheetState.targetValue == SheetValue.PartiallyExpanded
                                }.first { it }
                            }
                            withFrameNanos { }
                            // Closed or opened again meanwhile, these bounds belong to another history
                            if (!vm.isShowing(event.aroundHistory)) return@launch
                            controller.fitBounds(
                                event.south,
                                event.west,
                                event.north,
                                event.east,
                                historyLayout.padding(density, currentLayoutDirection),
                            )
                        }
                    } else {
                        historyLayout.fit?.cancel()
                        controller.fitBounds(
                            event.south,
                            event.west,
                            event.north,
                            event.east,
                            NativeMapController.Padding(with(density) { 64.dp.roundToPx() }),
                        )
                    }

                    is NativeMapEvents.HistoryUnavailable -> scope.launch {
                        val text = when (event.reason) {
                            NativeMapEvents.HistoryUnavailable.Reason.NO_ACCESS -> R.string.map_history_no_access
                            NativeMapEvents.HistoryUnavailable.Reason.LIMIT_REACHED -> R.string.map_history_limit_reached
                            NativeMapEvents.HistoryUnavailable.Reason.FAILED -> R.string.map_history_failed
                        }
                        snackbarHostState.showSnackbar(context.getString(text))
                    }

                    NativeMapEvents.HistoryEmpty -> scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.map_history_empty))
                    }

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

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
        snackbarHostState = snackbarHostState,
    )

    val selectedHex = state?.selectedHex
    var lastDetails by remember { mutableStateOf<MapAircraftDetails?>(null) }
    // While the sheet slides away it keeps showing the aircraft it showed, and selecting the same
    // aircraft again keeps the full details until the server answers again. What the history showed
    // never stands in for the aircraft now, nor the other way round.
    val details = remember {
        derivedStateOf {
            val freshDetails = fullState.value?.details
            val last = lastDetails
            when {
                freshDetails == null -> last
                freshDetails.isPartial && last?.let {
                    !it.isPartial && it.hex == freshDetails.hex && it.isHistorical == freshDetails.isHistorical
                } == true -> last

                else -> freshDetails
            }
        }
    }
    // From a flow, so the screen itself doesn't read the details
    LaunchedEffect(Unit) {
        snapshotFlow { details.value }.collect { if (it != null) lastDetails = it }
    }
    val hasDetails by remember { derivedStateOf { details.value != null } }
    val detailsLoading by remember {
        derivedStateOf { details.value?.isPartial == true && fullState.value?.detailsLoading == true }
    }
    val sheetListState = rememberLazyListState()
    BackHandler(enabled = selectedHex != null && sheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.partialExpand() }
    }
    // Registered later, so it goes first: back from the history is back to the live map
    BackHandler(enabled = inHistory) { vm.closeHistory() }
    // The history needs the map, the sheet rests collapsed and at its top once it opens.
    // Not again when coming back to a history left open, the sheet stays as it was left.
    var historySheetSettled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(inHistory) {
        if (!inHistory) {
            historyLayout.fit?.cancel()
            historySheetSettled = false
            return@LaunchedEffect
        }
        if (historySheetSettled) return@LaunchedEffect
        sheetListState.scrollToItem(0)
        if (sheetState.currentValue != SheetValue.Hidden) sheetState.partialExpand()
        historySheetSettled = true
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Mascot(
                                size = 40.dp,
                                cropPadding = true,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            )
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
            val overlayStartPx = if (layoutDirection == LayoutDirection.Ltr) {
                overlayInsets.getLeft(density, layoutDirection)
            } else {
                overlayInsets.getRight(density, layoutDirection)
            }
            val overlayEndPx = if (layoutDirection == LayoutDirection.Ltr) {
                overlayInsets.getRight(density, layoutDirection)
            } else {
                overlayInsets.getLeft(density, layoutDirection)
            }
            // The history has follow beside its playback controls, share and deselect wait until it is left
            val showFloatingActions = selectedHex != null && hasDetails && !isSidebarOpen && !inHistory
            // The status keeps clear of the floating actions above the resting sheet
            val floatingActionsSpace = if (showFloatingActions && sheetState.targetValue != SheetValue.Expanded) {
                floatingRowHeight + with(density) { 8.dp.roundToPx() }
            } else {
                0
            }
            // The history's controls and timeline ride above the resting sheet
            val historyBarSpace = if (inHistory && historyLayout.scrubberHeight > 0) {
                historyLayout.scrubberHeight + with(density) { 8.dp.roundToPx() }
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
                        val shown = details.value ?: return@BottomSheetScaffold
                        val latest = fullState.value
                        val currentRoute = (routeDisplay.value as? NativeMapViewModel.RouteDisplay.Result)
                            ?.takeIf { it.hex == state?.selectedHex }
                        // A blocked request explains more than the aircraft missing from older answers
                        val note = if (latest?.inHistory == true) {
                            latest.historyNote?.let { stringResource(it.textRes) }
                        } else {
                            latest?.takeIf { shown.isPartial && !detailsLoading }?.let { current ->
                                unavailableText(current)
                                    ?: stringResource(R.string.watch_list_item_no_current_data_msg).takeIf { current.selectedMissing }
                            }
                        }
                        AircraftDetailsSheetContent(
                            details = shown,
                            loading = detailsLoading,
                            note = note,
                            route = currentRoute?.route,
                            routeProgress = currentRoute?.progress ?: RouteProgress.Unknown,
                            progress = sheetProgress,
                            topInset = cutoutTop,
                            maxPhotoHeight = (sheetLayoutHeight * 0.45f).roundToInt(),
                            onCollapsedHeight = { glancePeekPx = it },
                            listState = sheetListState,
                            watched = shown.hex.trim().uppercase() in watchedHexes,
                            seen = latest?.takeIf { it.selectedHex.equals(shown.hex, ignoreCase = true) }?.seen,
                            serverNow = vm::serverNowMillis,
                            onShowInSearch = vm::showInSearch,
                            onWatchAction = vm::onWatchAction,
                            onWatchWithNote = vm::onWatchWithNote,
                            onPhotoClick = { link ->
                                when {
                                    sheetState.currentValue == SheetValue.Expanded -> vm.onOpenUrl(link)
                                    !inHistory -> scope.launch { sheetState.expand() }
                                }
                            },
                            modifier = Modifier.windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Horizontal)),
                        )
                    },
                    sheetPeekHeight = with(density) { peekPx.toDp() },
                    sheetShape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
                    sheetDragHandle = null,
                    // A drag would cancel the hide that follows a deselection and leave the sheet up,
                    // and the history keeps it collapsed
                    sheetSwipeEnabled = selectedHex != null && !inHistory,
                    snackbarHost = {
                        // Snackbars appear above the resting sheet, clear of the floating actions or the history
                        val lift = if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                            val actionsSpace = if (showFloatingActions) floatingRowHeight + with(density) { 8.dp.roundToPx() } else 0
                            with(density) { (actionsSpace + historyBarSpace).toDp() }
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
                                lastSeen = current.lastSeenMark,
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
                        // The floating actions ride the sheet's edge while it rises, the rail stays clear of them until then
                        val railActionsSpace = if (showFloatingActions) floatingRowHeight + with(density) { 8.dp.roundToPx() } else 0
                        val freeBottomPx = if (sheetCoversEnd && sheetTop != null) {
                            (sheetTop - railActionsSpace - historyBarSpace).coerceAtMost(mapSize.height.toFloat())
                        } else {
                            mapSize.height.toFloat()
                        }
                        historyLayout.mapHeight = mapSize.height
                        historyLayout.coveredBottom = sheetTop
                            ?.let { (mapSize.height - (it - floatingActionsSpace - historyBarSpace)).roundToInt() }
                            ?.coerceAtLeast(0) ?: 0
                        historyLayout.coveredEnd = insetEndPx + with(density) { (16.dp + MapControlRailWidth).roundToPx() }
                        val railRoom = with(density) { (freeBottomPx - insetTopPx).toDp() } - 16.dp
                        val railMaxHeight = if (mapSize != IntSize.Zero) {
                            (railRoom - 16.dp).coerceAtLeast(0.dp)
                        } else {
                            Dp.Unspecified
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { mapSize = it }
                                // Unpadded like the map itself, the fit's padding is measured against it
                                .onGloballyPositioned { historyLayout.map = it }
                                .then(cutoutSafe)
                        ) {
                            HistoryShown(historyView) { history ->
                                HistoryDateBar(
                                    view = history,
                                    onPrevious = { vm.showHistoryDay(forward = false) },
                                    onNext = { vm.showHistoryDay(forward = true) },
                                    onClose = { vm.closeHistory() },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 16.dp, top = 16.dp, end = 16.dp + MapControlRailWidth + 8.dp)
                                        .widthIn(max = 420.dp)
                                        .onGloballyPositioned { bar ->
                                            val map = historyLayout.map?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                                            historyLayout.dateBarBottom =
                                                map.localPositionOf(bar, Offset(0f, bar.size.height.toFloat())).y.roundToInt()
                                        },
                                )
                            }
                            if (!inHistory) current?.let {
                                WithCap(fullState) { cap ->
                                    // A queued explanation for a chip that is gone would describe nothing on screen
                                    val capShown = cap != null
                                    DisposableEffect(capShown) { onDispose { if (capShown) capSnackbar?.cancel() } }
                                    MapFilterChips(
                                        militaryOnly = it.toggles.militaryOnly,
                                        isolating = it.isolating,
                                        pinnedCount = it.pinnedCount,
                                        cap = cap,
                                        onClearMilitaryOnly = { vm.toggleMilitaryOnly() },
                                        onClearIsolate = { vm.toggleIsolate() },
                                        onClearPinned = { vm.clearPinned() },
                                        onCapInfo = {
                                            if (cap != null) {
                                                capSnackbar?.cancel()
                                                capSnackbar = scope.launch { snackbarHostState.showSnackbar(cap.explanation) }
                                            }
                                        },
                                        // Keeps clear of the rail
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(start = 16.dp, top = 16.dp, end = 16.dp + MapControlRailWidth + 8.dp),
                                    )
                                }
                            }

                            MapControlRail(
                                isFullscreen = isFullscreen,
                                showAr = vm.hasRotationSensor,
                                onOptions = {
                                    vm.closeSidebar()
                                    optionsOpen = true
                                },
                                onAircraftList = if (inHistory) null else {
                                    { vm.toggleSidebar() }
                                },
                                onAr = { vm.goToAr() },
                                onFullscreen = { isFullscreen = !isFullscreen },
                                onMyLocation = { vm.goToMyLocation() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .heightIn(max = railMaxHeight),
                            )

                            // Rides above the aircraft sheet; an expanded sheet, or too little map above it, leaves no room
                            // for it
                            val statusBottomPx = sheetTop
                                ?.let { (it - floatingActionsSpace - historyBarSpace).coerceAtMost(mapSize.height.toFloat()) }
                                ?: mapSize.height.toFloat()
                            val statusRoom = with(density) { (statusBottomPx - insetTopPx).toDp() }
                            if (sheetState.targetValue != SheetValue.Expanded && statusRoom >= MIN_STATUS_ROOM) {
                                MapStatus(
                                    fullState = fullState,
                                    attempt = viewingAttempt,
                                    onRetry = { vm.retryViewing() },
                                    // Keeps clear of the rail's foot, on both sides to stay centered
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 16.dp + MapControlRailWidth + 8.dp)
                                        .padding(bottom = 24.dp)
                                        .offset { IntOffset(0, -(mapSize.height - statusBottomPx).roundToInt()) },
                                )
                            }

                            if (sheetTop != null) HistoryShown(historyView) { scrubbed ->
                                DisposableEffect(Unit) { onDispose { historyLayout.scrubberHeight = 0 } }
                                HistoryScrubber(
                                    view = scrubbed,
                                    follow = state?.toggles?.follow == true,
                                    onSeek = vm::seekHistory,
                                    onTogglePlaying = vm::toggleHistoryPlaying,
                                    onCycleSpeed = vm::cycleHistorySpeed,
                                    onToggleFollow = vm::toggleFollow,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 12.dp)
                                        .widthIn(max = BottomSheetDefaults.SheetMaxWidth - 24.dp)
                                        .fillMaxWidth()
                                        .onSizeChanged { historyLayout.scrubberHeight = it.height }
                                        .offset {
                                            val bottom = sheetTop - floatingActionsSpace - with(density) { 8.dp.roundToPx() }
                                            IntOffset(0, -(mapSize.height - bottom).roundToInt())
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

                // Above the whole scaffold, so they can sit on the sheet's photo as well as above the sheet
                if (showFloatingActions) {
                    SheetFloatingActions(
                        sheetTop = { runCatching { sheetState.requireOffset() }.getOrNull() },
                        contentScroll = {
                            if (sheetListState.firstVisibleItemIndex == 0) sheetListState.firstVisibleItemScrollOffset else Int.MAX_VALUE / 2
                        },
                        progress = sheetProgress,
                        safeTop = cutoutTop,
                        sheetMaxWidth = with(density) { BottomSheetDefaults.SheetMaxWidth.roundToPx() },
                        startInset = overlayStartPx,
                        endInset = overlayEndPx,
                        onRowHeight = { floatingRowHeight = it },
                        follow = state?.toggles?.follow == true,
                        onToggleFollow = { vm.toggleFollow() },
                        onShare = { details.value?.let { vm.shareAircraft(it) } },
                        onClose = { vm.deselect() },
                        history = state?.historyAction,
                        onHistory = { vm.openHistory() },
                    )
                }

                // Watch messages answer a tap on the sheet, so they cover its card rather than the map above it
                SnackbarHost(
                    watchSnackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Horizontal))
                        .then(
                            if (isFullscreen) {
                                Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            } else {
                                Modifier
                            }
                        ),
                )
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

// The filter chips with their margin, the status' touch height and its bottom margin
private val MIN_STATUS_ROOM = 16.dp + 48.dp + 48.dp + 24.dp
private const val HISTORY_FIT_WAIT = 2_000L
private const val UNDO_TIMEOUT_MS = 3_000L

/** The part of [NativeMapViewModel.State] the screen itself reads. */
private data class ScreenState(
    val tagline: String,
    val isPro: Boolean,
    val style: NativeMapStyle,
    val toggles: NativeMapViewModel.Toggles,
    val selectedHex: AircraftHex?,
    val myLocation: Pair<Double, Double>?,
    val pinnedCount: Int,
    val isolating: Boolean,
    val historyAction: NativeMapViewModel.HistoryAction?,
    val lastSeenMark: NativeMapViewModel.LastSeenMark?,
) {
    constructor(state: NativeMapViewModel.State) : this(
        tagline = state.tagline,
        isPro = state.isPro,
        style = state.style,
        toggles = state.toggles,
        selectedHex = state.selectedHex,
        myLocation = state.myLocation,
        pinnedCount = state.pinnedCount,
        isolating = state.isolating,
        historyAction = state.historyAction,
        lastSeenMark = state.lastSeenMark,
    )
}

/**
 * Where the history's overlays cover the map, in px, so its day is fitted between them, and the
 * pending fit, which closing the history or another camera move cancels.
 */
private class HistoryLayout {
    var dateBarBottom by mutableIntStateOf(0)
    var scrubberHeight by mutableIntStateOf(0)
    var coveredBottom = 0
    var coveredEnd = 0
    var mapHeight = 0
    var map: LayoutCoordinates? = null
    var fit: Job? = null

    val ready: Boolean get() = dateBarBottom > 0 && scrubberHeight > 0

    /** The rail sits at the end, which is the left in right-to-left layouts; the map's padding is not mirrored. */
    fun padding(density: Density, direction: LayoutDirection): NativeMapController.Padding {
        val margin = with(density) { 16.dp.roundToPx() }
        val top = dateBarBottom + margin
        val bottom = coveredBottom + margin
        // Too little room left between them: centred on the whole map is better than squeezed
        if (mapHeight - top - bottom < with(density) { 96.dp.roundToPx() }) return NativeMapController.Padding(margin)
        val end = coveredEnd + margin
        return if (direction == LayoutDirection.Rtl) {
            NativeMapController.Padding(left = end, top = top, right = margin, bottom = bottom)
        } else {
            NativeMapController.Padding(left = margin, top = top, right = end, bottom = bottom)
        }
    }
}

/** Its own scope reads the cursor, so a playback step recomposes the overlays and not the screen. */
@Composable
private fun BoxScope.HistoryShown(
    history: State<NativeMapViewModel.HistoryView?>,
    content: @Composable BoxScope.(NativeMapViewModel.HistoryView) -> Unit,
) {
    val view = history.value ?: return
    content(view)
}

private val NativeMapViewModel.HistoryNote.textRes: Int
    get() = when (this) {
        NativeMapViewModel.HistoryNote.NOT_SEEN -> R.string.map_history_not_seen
        NativeMapViewModel.HistoryNote.NOT_RECORDED -> R.string.map_history_not_recorded
        NativeMapViewModel.HistoryNote.OMITTED -> R.string.map_history_omitted
        NativeMapViewModel.HistoryNote.ESTIMATED -> R.string.map_history_estimated
    }

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

/**
 * Null while nothing is left out, or while no answer can come that zooming in would change. Read
 * in its own scope, a new answer recomposes only [content].
 */
@Composable
private fun WithCap(
    fullState: State<NativeMapViewModel.State?>,
    content: @Composable (cap: MapCapNote?) -> Unit,
) {
    val state = fullState.value
    val altitude = state?.capAltitude
    val cap = if (state?.capped == true && unavailableText(state) == null) {
        val shown = state.shownCount
        if (altitude != null) {
            MapCapNote(
                stringResource(R.string.map_cap_chip_altitude, "%,d".format(altitude.aboveFeet)),
                altitude.totalMatching
                    ?.let { stringResource(R.string.map_status_capped_altitude, altitude.shownCount, it) }
                    ?: stringResource(R.string.map_status_capped_altitude_unknown_total, altitude.shownCount),
            )
        } else {
            state.totalMatching
                ?.let { MapCapNote(stringResource(R.string.map_cap_chip, shown, it), stringResource(R.string.map_status_capped, shown, it)) }
                ?: MapCapNote(
                    stringResource(R.string.map_cap_chip_unknown_total, shown),
                    stringResource(R.string.map_status_capped_unknown_total, shown),
                )
        }
    } else {
        null
    }
    content(cap)
}

/**
 * Why the map shows no aircraft, only aging positions, or aircraft of the other filter. Failed
 * requests can be retried from it, [attempt] is true while a request is out.
 */
@Composable
private fun MapStatus(
    fullState: State<NativeMapViewModel.State?>,
    attempt: State<Boolean>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = fullState.value ?: return
    val text = unavailableText(state)
        ?: stringResource(R.string.map_status_filter_loading).takeIf { state.filterPending }
        ?: return
    // Restricted, revoked or used up, another request can't change it
    val retryable = state.waiting is AircraftRepo.ViewingState.Reason.Offline || (state.waiting == null && state.offline)
    val shape = RoundedCornerShape(16.dp)
    val color = Color.Black.copy(alpha = 0.6f)
    if (!retryable) {
        Box(
            modifier = modifier
                .widthIn(max = 360.dp)
                .background(color, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(text = text, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }
    val retrying = attempt.value
    Surface(
        onClick = onRetry,
        enabled = !retrying,
        shape = shape,
        color = color,
        contentColor = Color.White,
        modifier = modifier.widthIn(max = 360.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (retrying) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(1.dp)
                        .size(14.dp),
                )
            } else {
                Icon(
                    Icons.TwoTone.Refresh,
                    contentDescription = stringResource(R.string.common_refresh_action),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
