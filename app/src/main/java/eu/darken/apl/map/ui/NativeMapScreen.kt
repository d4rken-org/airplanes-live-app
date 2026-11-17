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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.airport.core.AirportTarget
import eu.darken.apl.airport.ui.AIRPORT_SHEET_LEADING_ITEMS
import eu.darken.apl.airport.ui.AirportDetailsSheetContent
import eu.darken.apl.airport.ui.AirportDetailsState
import eu.darken.apl.airport.ui.flights.AirportFlightsActions
import eu.darken.apl.airport.ui.flights.AirportFlightsUiState
import eu.darken.apl.common.compose.HideBottomNavBar
import eu.darken.apl.common.compose.SnackbarPresenter
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.compose.rememberCopyToClipboard
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.navigation.LocalCoveredBySheet
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.time.relativeResetText
import eu.darken.apl.common.units.LocalUnitFormatter
import eu.darken.apl.common.units.text
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.ui.update.UpdateRequiredCard
import eu.darken.apl.main.ui.update.rememberUpdateRequiredNotice
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.NativeMapStyle
import eu.darken.apl.map.core.overlay.OverlaySpec
import eu.darken.apl.watch.ui.RadiusStops
import eu.darken.apl.watch.ui.showWatchAdded
import eu.darken.apl.watch.ui.showWatchLimitReached
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    val listControls by vm.listControls.collectAsStateWithLifecycle()
    // Only the airport's sheet and the map's airport read them
    val airportSheet = vm.airportSheet.collectAsStateWithLifecycle()
    val airportWatchArea = vm.airportWatchArea.collectAsStateWithLifecycle()
    val airportFlights = vm.airportFlights.collectAsStateWithLifecycle()
    val shapes by vm.shapes.collectAsStateWithLifecycle()
    // Playback moves the cursor every frame, only the history's own overlays read it, see [HistoryShown]
    val historyView = vm.historyView.collectAsStateWithLifecycle()
    val viewingAttempt = vm.viewingAttempt.collectAsStateWithLifecycle()
    val watchedHexes by vm.watchedHexes.collectAsStateWithLifecycle()
    val mapWatches by vm.mapWatches.collectAsStateWithLifecycle()
    val watchZones by vm.watchZones.collectAsStateWithLifecycle()
    val watchMatches by vm.selectedWatchMatches.collectAsStateWithLifecycle()
    val inHistory by remember { derivedStateOf { historyView.value != null } }
    val updateNotice = rememberUpdateRequiredNotice()
    val areaDraft by vm.areaDraft.collectAsStateWithLifecycle()
    val areaMaxRadiusKm by vm.areaMaxRadiusKm.collectAsStateWithLifecycle()
    val distanceUnit = LocalUnitFormatter.current.distanceUnit
    val areaStops = remember(areaMaxRadiusKm, distanceUnit) { RadiusStops.forLimit(areaMaxRadiusKm, distanceUnit) }
    val areaRadius = areaDraft?.shownRadius(areaStops)
    // How far up from the map's bottom the area editor reaches, 0 while it isn't shown
    var areaCoveredPx by remember { mutableIntStateOf(0) }
    // Between the map's bottom and the window's, e.g. the bottom navigation
    var mapBottomGapPx by remember { mutableIntStateOf(0) }
    val rootView = LocalView.current.rootView

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Replaced by the next tap instead of queueing behind it
    var capSnackbar by remember { mutableStateOf<Job?>(null) }
    val watchSnackbarHostState = remember { SnackbarHostState() }
    val watchSnackbars = remember { SnackbarPresenter(watchSnackbarHostState, scope) }
    val copyToClipboard = rememberCopyToClipboard(watchSnackbars)
    val context = LocalContext.current
    val density = LocalDensity.current
    val controller = remember { NativeMapController() }
    val historyLayout = remember { HistoryLayout() }
    val currentLayoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val sheetRequests = remember { SheetRequests() }
    // Follow keeps the aircraft above it, read outside composition only
    var followInsetPx by remember { mutableIntStateOf(0) }
    // Waits for the sheet to rise, any newer camera move replaces it
    var followJump by remember { mutableStateOf<Job?>(null) }

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
        vm.events
            .onEach { event ->
                when (event) {
                    NativeMapEvents.RequestLocationPermission -> {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    NativeMapEvents.LocationUnavailable -> scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.map_my_location_unavailable))
                    }

                    is NativeMapEvents.WatchAdded -> watchSnackbars.showAction {
                        if (showWatchAdded(context, event.label, accessibilityManager)) vm.undoWatch(event.watchId)
                    }

                    is NativeMapEvents.WatchLimitReached -> watchSnackbars.showAction {
                        if (showWatchLimitReached(context, event.error)) vm.goUpgrade()
                    }

                    NativeMapEvents.LocationWatchNotAllowed -> watchSnackbars.showAction {
                        val result = watchSnackbarHostState.showSnackbar(
                            message = context.getString(R.string.watch_location_not_allowed_msg),
                            actionLabel = context.getString(R.string.common_upgrade_action),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.goUpgrade()
                    }

                    is NativeMapEvents.MoveCamera -> {
                        followJump?.cancel()
                        val jump = event.followJumpOf
                        if (jump != null) {
                            followJump = scope.launch {
                                // The jump's selection may not be composed yet, or its sheet still rising: the inset
                                // is its peek once it rests
                                withTimeoutOrNull(FOLLOW_JUMP_SHEET_WAIT) {
                                    snapshotFlow {
                                        state?.selection === jump && sheetState.currentValue != SheetValue.Hidden &&
                                                sheetState.targetValue == sheetState.currentValue
                                    }.first { it }
                                }
                                // The inset is written once the risen sheet is composed
                                withFrameNanos { }
                                // Deselected, replaced or no longer followed while it waited
                                if (!vm.isFollowJumpCurrent(jump)) return@launch
                                historyLayout.fit?.cancel()
                                controller.moveTo(event.camera, event.keepZoom, bottomInset = followInsetPx)
                            }
                        } else {
                            historyLayout.fit?.cancel()
                            controller.moveTo(event.camera, event.keepZoom)
                        }
                    }

                    is NativeMapEvents.FitBounds -> if (event.aroundHistory != null) {
                        followJump?.cancel()
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
                        followJump?.cancel()
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
                            NativeMapEvents.HistoryUnavailable.Reason.UPDATE_REQUIRED -> R.string.map_history_update_required
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

                    is NativeMapEvents.PeekSheet -> sheetRequests.request(event.selection, listPosition = null)

                    is NativeMapEvents.ExpandSheet -> sheetRequests.request(event.selection, event.listPosition)
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

    val selection = state?.selection
    val selectedHex = state?.selectedHex
    val airportSelected = selection is NativeMapViewModel.Selected.Airport
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
    // Like the aircraft's details, the airport's stay while the sheet slides away
    var lastAirport by remember { mutableStateOf<AirportDetailsState?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { airportSheet.value }.collect { if (it != null) lastAirport = it }
    }
    var lastAirportFlights by remember { mutableStateOf<AirportFlightsUiState?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { airportFlights.value }.collect { if (it != null) lastAirportFlights = it }
    }
    // Whichever took over the sheet drops what the other one left in it
    LaunchedEffect(selection) {
        when (selection) {
            is NativeMapViewModel.Selected.Airport -> lastDetails = null
            is NativeMapViewModel.Selected.Aircraft -> lastAirport = null
            null -> Unit
        }
    }
    val airportInSheet by remember {
        derivedStateOf {
            when (state?.selection) {
                is NativeMapViewModel.Selected.Airport -> true
                is NativeMapViewModel.Selected.Aircraft -> false
                null -> details.value == null && lastAirport != null
            }
        }
    }
    val detailsLoading by remember {
        derivedStateOf { details.value?.isPartial == true && fullState.value?.detailsLoading == true }
    }
    val sheetListState = rememberLazyListState()
    val shownHex by remember { derivedStateOf { details.value?.hex } }
    LaunchedEffect(shownHex) { sheetListState.scrollToItem(0) }
    val airportListState = rememberLazyListState()
    val airportFlightsActions = remember(vm) {
        AirportFlightsActions(
            onShowFlights = vm::showAirportFlights,
            onRefresh = vm::refreshAirportFlights,
            onShowMore = vm::showMoreAirportFlights,
            onSelectSection = vm::selectAirportFlightsSection,
            onRetry = vm::retryAirportFlights,
            onReload = vm::reloadAirportFlights,
            onOpenAircraft = { hex ->
                val position = SheetListPosition(
                    airportListState.firstVisibleItemIndex,
                    airportListState.firstVisibleItemScrollOffset,
                )
                vm.openFlightAircraft(hex, controller.camera(), position)
            },
        )
    }
    val shownAirport by remember { derivedStateOf { (airportSheet.value ?: lastAirport)?.target } }
    LaunchedEffect(shownAirport) {
        // Coming back to it, the list returns to where it was instead
        if (!sheetRequests.restoresList(shownAirport)) airportListState.scrollToItem(0)
    }
    // Back belongs to a sheet opened over the map, also before it has drawn its own window
    val coveredBySheet = LocalCoveredBySheet.current
    BackHandler(enabled = selection != null && !inHistory && !coveredBySheet) { vm.onBack() }
    // Registered later, so it goes first: back collapses an expanded sheet before it closes
    BackHandler(enabled = selection != null && sheetState.currentValue == SheetValue.Expanded && !coveredBySheet) {
        scope.launch { sheetState.partialExpand() }
    }
    // Registered later, so it goes first: back from the history is back to the live map
    BackHandler(enabled = inHistory && !coveredBySheet) { vm.closeHistory() }
    // Large enough, the list stays beside the map instead of covering it. A phone in landscape is wide enough,
    // but the map beside the list would be a strip under the app bar.
    val configuration = LocalConfiguration.current
    val listDocked = configuration.screenWidthDp.dp >= DOCKED_LIST_MIN_WIDTH &&
            configuration.screenHeightDp.dp >= DOCKED_LIST_MIN_HEIGHT
    val listOverlaid = isSidebarOpen && !listDocked
    // The overlaid list covers everything else, so back closes it first
    BackHandler(enabled = listOverlaid && !coveredBySheet) { vm.closeSidebar() }
    BackHandler(enabled = areaDraft != null && !coveredBySheet) { vm.cancelAreaDraft() }
    // Once per draft, not when coming back to one left open: the area in view above the editor
    var areaFitSession by rememberSaveable { mutableLongStateOf(-1L) }
    val currentAreaStops by rememberUpdatedState(areaStops)
    LaunchedEffect(areaDraft?.session) {
        val session = areaDraft?.session ?: return@LaunchedEffect
        if (session == areaFitSession) return@LaunchedEffect
        // The aircraft sheet makes way first, and the editor has to be measured
        withTimeoutOrNull(AREA_FIT_WAIT) {
            snapshotFlow {
                sheetState.currentValue == SheetValue.Hidden && sheetState.targetValue == SheetValue.Hidden && areaCoveredPx > 0
            }.first { it }
        } ?: return@LaunchedEffect
        withFrameNanos { }
        val draft = areaDraft ?: return@LaunchedEffect
        val radius = draft.shownRadius(currentAreaStops) ?: return@LaunchedEffect
        // Centered between the rail and its mirror, so either layout direction keeps clear of it
        val side = with(density) { (16.dp + MapControlRailWidth + 8.dp).roundToPx() }
        val top = with(density) { 64.dp.roundToPx() }
        // A short landscape map with the editor up leaves nothing to fit into
        if (historyLayout.mapHeight - top - areaCoveredPx <= 0) return@LaunchedEffect
        areaFitSession = session
        val ring = areaRing(draft.latitude, draft.longitude, radius.toDouble())
        controller.fitBounds(
            ring.south,
            ring.west,
            ring.north,
            ring.east,
            NativeMapController.Padding(left = side, top = top, right = side, bottom = areaCoveredPx),
        )
    }
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
    // Measured by the content shown, neither the aircraft nor the airport peeks at the other's glance
    var glancePeekPx by remember(airportInSheet) { mutableIntStateOf(0) }
    val minPeekPx = with(density) { 180.dp.roundToPx() }
    val peekPx = when {
        glancePeekPx == 0 || sheetLayoutHeight == 0 -> minPeekPx
        // Large fonts on a short screen must still leave room for the map
        else -> min(glancePeekPx, max(minPeekPx, (sheetLayoutHeight * 0.6f).roundToInt()))
    }
    // The peek and not where the sheet is, dragging or expanding the sheet leaves the followed aircraft in place
    val restingSheetInset = if (sheetState.currentValue != SheetValue.Hidden) peekPx else 0
    SideEffect { followInsetPx = restingSheetInset }
    // Set while a sheet opened over the map, e.g. the watch details, stands in for the aircraft's sheet: whether to expand it again
    var restoreExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // The peek grows while the details load, each change cancels the running settle, so every change settles again
    LaunchedEffect(selection, peekPx, coveredBySheet) {
        if (selection == null) {
            sheetState.hide()
            lastDetails = null
            lastAirport = null
        } else if (!coveredBySheet && restoreExpanded == null && sheetState.targetValue != SheetValue.Expanded) {
            // The sheet needs its content laid out before it can peek
            withFrameNanos { }
            if (restoreExpanded == null && sheetState.targetValue != SheetValue.Expanded && !sheetRequests.holds(selection)) {
                sheetState.partialExpand()
            }
        }
    }
    LaunchedEffect(selection, coveredBySheet) {
        if (selection == null) {
            restoreExpanded = null
            return@LaunchedEffect
        }
        if (coveredBySheet) {
            if (restoreExpanded == null) restoreExpanded = sheetState.targetValue == SheetValue.Expanded
            sheetState.hide()
            return@LaunchedEffect
        }
        val expand = restoreExpanded ?: return@LaunchedEffect
        withFrameNanos { }
        // Cleared first, an interrupted animation must not leave it set
        restoreExpanded = null
        // Requested since, the request places the sheet
        if (sheetRequests.holds(selection)) return@LaunchedEffect
        if (expand) sheetState.expand() else sheetState.partialExpand()
    }
    // After each composition: a request applies once its selection is shown, whether the event or the state came first
    val sheetRequest = sheetRequests.pending
    SideEffect {
        sheetRequests.shown = selection
        val request = sheetRequest ?: return@SideEffect
        if (selection !== request.selection) {
            // Replaced by another selection, also one that came before the request's own was ever shown
            if (selection !== request.shownBefore) sheetRequests.drop(request)
            return@SideEffect
        }
        if (coveredBySheet || request.job != null) return@SideEffect
        request.job = scope.launch {
            try {
                // Lands after the settle's frame, which leaves the sheet to a held request
                withFrameNanos { }
                coroutineScope {
                    val position = request.listPosition
                    val restoreTarget = (request.selection as? NativeMapViewModel.Selected.Airport)?.target
                    if (position != null && restoreTarget != null) launch {
                        // Until the sheet has settled expanded, the list's height follows its progress until then,
                        // and the list is laid out with its flights section and is as long as it was
                        val ready = withTimeoutOrNull(AIRPORT_LIST_RESTORE_WAIT) {
                            snapshotFlow {
                                sheetState.currentValue == SheetValue.Expanded &&
                                        sheetState.targetValue == SheetValue.Expanded &&
                                        shownAirport == restoreTarget &&
                                        airportFlights.value != null &&
                                        airportListState.layoutInfo.totalItemsCount >
                                        maxOf(position.index, AIRPORT_SHEET_LEADING_ITEMS)
                            }.first { it }
                        }
                        val count = airportListState.layoutInfo.totalItemsCount
                        when {
                            ready != null -> airportListState.scrollToItem(position.index, position.offset)
                            shownAirport == restoreTarget && count > 0 -> airportListState.scrollToItem(count - 1)
                        }
                    }
                    // Its own child, a drag interrupting the animation leaves the list's restore running
                    launch { if (position != null) sheetState.expand() else sheetState.partialExpand() }
                }
            } finally {
                sheetRequests.done(request)
            }
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
        if (sheetShown && restoreExpanded == null && vm.state.value?.selection != null) vm.deselect()
        sheetShown = false
    }
    // Decided here and not in the lazy item: the sheet lays that out also while collapsed or covered
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val airportFlightsVisible by remember(coveredBySheet, listOverlaid) {
        derivedStateOf {
            airportInSheet && airportSheet.value != null &&
                    sheetState.currentValue != SheetValue.Hidden &&
                    !coveredBySheet && !listOverlaid &&
                    lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        }
    }
    LaunchedEffect(airportFlightsVisible) { vm.setAirportFlightsVisible(airportFlightsVisible) }
    DisposableEffect(vm) { onDispose { vm.setAirportFlightsVisible(false) } }

    MapFullscreenEffect(isFullscreen)

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                MapTopBar(
                    isPro = state?.isPro == true,
                    tagline = state?.tagline ?: stringResource(R.string.map_page_label),
                    onTierClick = { vm.goUpgrade() },
                    onSettingsClick = { vm.goSettings() },
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
        Row(
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
            val screenInsets = if (avoidCutout) {
                WindowInsets.displayCutout
            } else {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            }
            val listPaneShown = isSidebarOpen && listDocked && !inHistory
            // The pane beside the map takes the end edge's insets
            val overlayInsets = if (listPaneShown) {
                screenInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            } else {
                screenInsets
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
            val showFloatingActions = ((selectedHex != null && hasDetails) || airportSelected) &&
                    !listOverlaid && !inHistory && !coveredBySheet && areaDraft == null
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

            val listActions = remember(vm, listDocked) {
                MapListActions(
                    onClose = { vm.closeSidebar() },
                    onQueryChange = { vm.setListQuery(it) },
                    onToggleWatchedOnly = { vm.toggleListWatchedOnly() },
                    onToggleMilitaryOnly = { vm.toggleMilitaryOnly() },
                    onToggleAircraftKind = { vm.toggleAircraftKind(it) },
                    onShowAllAircraftKinds = { vm.showAllAircraftKinds() },
                    onToggleIsolate = { vm.toggleIsolate() },
                    onSort = { vm.setListSort(it) },
                    onClearFilters = { vm.clearAllListFilters() },
                    onAircraftClick = { hex -> vm.onSidebarAircraftClicked(hex, docked = listDocked) },
                    onAircraftLongClick = { hex -> vm.onSidebarAircraftLongClicked(hex) },
                )
            }
            val listFilters = state?.let {
                MapListFilters(
                    militaryOnly = it.toggles.militaryOnly,
                    kinds = it.toggles.kinds,
                    isolate = it.toggles.isolate,
                    canIsolate = it.selectedHex != null || it.pinnedCount > 0,
                )
            } ?: MapListFilters()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { sheetLayoutHeight = it.height },
            ) {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetContent = {
                        if (airportInSheet) {
                            val airport = airportSheet.value ?: lastAirport ?: return@BottomSheetScaffold
                            // A different airport must not show the previous one's snapshot as its placeholder
                            key(airport.target) {
                                AirportDetailsSheetContent(
                                    display = airport.display,
                                    lookingUp = airport.lookingUp,
                                    watchArea = airportWatchArea.value,
                                    onShowOnMap = null,
                                    onWatchArea = vm::watchAirportArea,
                                    onOpenWebsite = vm::openAirportWebsite,
                                    onOpenWikipedia = vm::openAirportWikipedia,
                                    progress = sheetProgress,
                                    topInset = cutoutTop,
                                    maxPhotoHeight = (sheetLayoutHeight * 0.45f).roundToInt(),
                                    // A title that takes a second line once fully expanded must not move the peek
                                    onCollapsedHeight = { if (sheetProgress() < 1f) glancePeekPx = it },
                                    listState = airportListState,
                                    modifier = Modifier.windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Horizontal)),
                                    flights = if (airportSheet.value != null) airportFlights.value else lastAirportFlights,
                                    flightsActions = airportFlightsActions,
                                )
                            }
                            return@BottomSheetScaffold
                        }
                        val shown = details.value ?: return@BottomSheetScaffold
                        val latest = fullState.value
                        val currentRoute = (routeDisplay.value as? NativeMapViewModel.RouteDisplay.Result)
                            ?.takeIf { it.hex == state?.selectedHex }
                        val selection = latest?.takeIf { it.selectedHex.equals(shown.hex, ignoreCase = true) }
                        val lastPositionAt = selection?.let {
                            lastPositionNoteAt(
                                inHistory = it.inHistory,
                                selectedMissing = it.selectedMissing,
                                seen = it.seen,
                                partialDetails = shown.isPartial,
                                unavailable = unavailableText(it, vm::serverNowMillis) != null,
                            )
                        }
                        // A blocked request explains more than the aircraft missing from older answers
                        val note = if (latest?.inHistory == true) {
                            latest.historyNote?.let { stringResource(it.textRes) }
                        } else if (lastPositionAt != null) {
                            lastPositionNote(lastPositionAt, vm::serverNowMillis)
                        } else {
                            latest?.takeIf { shown.isPartial && !detailsLoading }?.let { current ->
                                unavailableText(current, vm::serverNowMillis)
                                    ?: stringResource(R.string.watch_list_item_no_current_data_msg).takeIf { current.selectedMissing }
                            }
                        }
                        AircraftDetailsSheetContent(
                            details = shown,
                            loading = detailsLoading,
                            note = note,
                            route = currentRoute?.route,
                            routeProgress = currentRoute?.progress ?: RouteProgress.Unknown,
                            heightAboveAirport = currentRoute?.heightAboveAirport,
                            progress = sheetProgress,
                            topInset = cutoutTop,
                            maxPhotoHeight = (sheetLayoutHeight * 0.45f).roundToInt(),
                            onCollapsedHeight = { glancePeekPx = it },
                            listState = sheetListState,
                            watched = shown.hex.trim().uppercase() in watchedHexes,
                            seen = selection?.seen?.takeIf { lastPositionAt == null },
                            serverNow = vm::serverNowMillis,
                            placeAction = remember(vm) { AircraftPlaceAction.ShowInSearch(vm::showInSearch) },
                            onWatchAction = vm::onWatchAction,
                            onWatchWithNote = vm::onWatchWithNote,
                            watchMatches = watchMatches.takeIf { !inHistory }.orEmpty(),
                            onOpenWatch = vm::openWatch,
                            onAirportClick = { vm.showAirport(it, controller.camera()) },
                            onTypeClick = vm::searchForType,
                            onAirlineClick = vm::searchForAirline,
                            onSquawkClick = vm::searchForSquawk,
                            onCopy = copyToClipboard,
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
                    sheetSwipeEnabled = selection != null && !inHistory,
                    snackbarHost = {
                        // Snackbars appear above the resting sheet, clear of the floating actions or the history
                        val lift = if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                            val actionsSpace = if (showFloatingActions) floatingRowHeight + with(density) { 8.dp.roundToPx() } else 0
                            with(density) { (actionsSpace + historyBarSpace).toDp() }
                        } else {
                            0.dp
                        }
                        SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = lift + with(density) { areaCoveredPx.toDp() }))
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
                                labelFields = current.toggles.labelFields,
                                militaryHighlight = current.toggles.militaryHighlight,
                                // A replay draws the past, the watches are about now
                                watches = mapWatches.takeIf { current.toggles.watchHighlight && !inHistory },
                                zones = watchZones.takeIf { current.toggles.watchHighlight && !inHistory }.orEmpty(),
                                pinned = current.pinned.takeIf { !inHistory }.orEmpty(),
                                follow = current.toggles.follow,
                                followInset = { followInsetPx },
                                myLocation = current.myLocation,
                                lastSeen = current.lastSeenMark,
                                airport = airportSheet.value?.best,
                                area = areaDraft?.let { draft ->
                                    areaRadius?.let { AreaCircle(draft.latitude, draft.longitude, it) }
                                },
                                // Like the watches, the overlays show the present
                                overlays = current.overlaySpecs.takeIf { !inHistory }.orEmpty(),
                                onCameraIdle = vm::onCameraIdle,
                                onAircraftTapped = vm::onAircraftTapped,
                                onAirportTapped = vm::onAirportTapped,
                                onMapTapped = vm::onMapTapped,
                                onMapLongPressed = vm::onMapLongPressed,
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
                        }.coerceAtMost((mapSize.height - areaCoveredPx).toFloat())
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
                                .onGloballyPositioned {
                                    historyLayout.map = it
                                    mapBottomGapPx = (rootView.height - it.boundsInWindow().bottom).roundToInt().coerceAtLeast(0)
                                }
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
                                WithCap(fullState, vm::serverNowMillis) { cap ->
                                    // A queued explanation for a chip that is gone would describe nothing on screen
                                    val capShown = cap != null
                                    DisposableEffect(capShown) { onDispose { if (capShown) capSnackbar?.cancel() } }
                                    MapFilterChips(
                                        militaryOnly = it.toggles.militaryOnly,
                                        kinds = it.toggles.kinds,
                                        isolating = it.isolating,
                                        pinnedCount = it.pinnedCount,
                                        query = listControls.query,
                                        watchedOnly = listControls.watchedOnly,
                                        cap = cap,
                                        onClearMilitaryOnly = { vm.toggleMilitaryOnly() },
                                        onClearKinds = { vm.showAllAircraftKinds() },
                                        onClearIsolate = { vm.toggleIsolate() },
                                        onClearPinned = { vm.clearPinned() },
                                        onClearQuery = { vm.setListQuery("") },
                                        onClearWatchedOnly = { vm.toggleListWatchedOnly() },
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
                                    if (listOverlaid) vm.closeSidebar()
                                    optionsOpen = true
                                },
                                onAircraftList = if (inHistory || areaDraft != null) null else {
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
                            val statusBottomPx = (
                                    sheetTop
                                        ?.let { (it - floatingActionsSpace - historyBarSpace).coerceAtMost(mapSize.height.toFloat()) }
                                        ?: mapSize.height.toFloat()
                                    ).coerceAtMost((mapSize.height - areaCoveredPx).toFloat())
                            val statusRoom = with(density) { (statusBottomPx - insetTopPx).toDp() }
                            if (sheetState.targetValue != SheetValue.Expanded && statusRoom >= MIN_STATUS_ROOM) {
                                // Keeps clear of the rail's foot, on both sides to stay centered
                                val statusModifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp + MapControlRailWidth + 8.dp)
                                    .padding(bottom = 24.dp)
                                    .offset { IntOffset(0, -(mapSize.height - statusBottomPx).roundToInt()) }
                                val notice = updateNotice
                                if (notice != null) {
                                    UpdateRequiredCard(
                                        notice = notice,
                                        modifier = statusModifier.widthIn(max = 360.dp),
                                    )
                                } else {
                                    MapStatus(
                                        fullState = fullState,
                                        attempt = viewingAttempt,
                                        serverNow = vm::serverNowMillis,
                                        onRetry = { vm.retryViewing() },
                                        modifier = statusModifier,
                                    )
                                }
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

                            val draft = areaDraft
                            if (draft != null) {
                                DisposableEffect(Unit) { onDispose { areaCoveredPx = 0 } }
                                HoldWindowForKeyboard()
                                // The keyboard, or in fullscreen the navigation bar, rises from the window's bottom, the map may end above it
                                val bottomInsetPx = maxOf(
                                    WindowInsets.ime.getBottom(density),
                                    if (isFullscreen) WindowInsets.navigationBars.getBottom(density) else 0,
                                )
                                val keyboardLiftPx = (bottomInsetPx - mapBottomGapPx).coerceAtLeast(0)
                                MapAreaEditor(
                                    draft = draft,
                                    stops = areaStops,
                                    radiusInMeters = areaRadius,
                                    maxRadiusKm = areaMaxRadiusKm,
                                    onLabelChange = vm::onAreaLabelChanged,
                                    onRadiusChange = vm::onAreaRadiusChanged,
                                    onBandChange = vm::onAreaBandChanged,
                                    onCancel = vm::cancelAreaDraft,
                                    onCreate = vm::createAreaWatch,
                                    onDelete = vm::deleteAreaWatch,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = with(density) { keyboardLiftPx.toDp() })
                                        .padding(12.dp)
                                        .widthIn(max = BottomSheetDefaults.SheetMaxWidth - 24.dp)
                                        .fillMaxWidth()
                                        .onGloballyPositioned { editor ->
                                            val map = historyLayout.map?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                                            val top = map.localPositionOf(editor, Offset.Zero).y.roundToInt()
                                            // With its margin, so what is fit above it doesn't touch it
                                            areaCoveredPx = (map.size.height - top + with(density) { 12.dp.roundToPx() }).coerceAtLeast(0)
                                        },
                                )
                            }
                        }
                    }
                }

                // Above the whole scaffold, so they can sit on the sheet's photo as well as above the sheet
                if (showFloatingActions) {
                    SheetFloatingActions(
                        sheetTop = { runCatching { sheetState.requireOffset() }.getOrNull() },
                        contentScroll = {
                            val list = if (airportSelected) airportListState else sheetListState
                            when {
                                list.firstVisibleItemIndex == 0 -> list.firstVisibleItemScrollOffset
                                else -> Int.MAX_VALUE / 2
                            }
                        },
                        progress = sheetProgress,
                        safeTop = cutoutTop,
                        sheetMaxWidth = with(density) { BottomSheetDefaults.SheetMaxWidth.roundToPx() },
                        startInset = overlayStartPx,
                        endInset = overlayEndPx,
                        onRowHeight = { floatingRowHeight = it },
                        // The airport has nothing to follow, share or look back on
                        follow = (state?.toggles?.follow == true).takeIf { !airportSelected },
                        onToggleFollow = { vm.toggleFollow() },
                        onShare = if (airportSelected) null else {
                            { details.value?.let { vm.shareAircraft(it) } }
                        },
                        onClose = { vm.deselect() },
                        history = state?.historyAction.takeIf { !airportSelected },
                        onHistory = { vm.openHistory() },
                        onCenter = if (airportSelected && airportSheet.value?.display?.hasPosition == true) {
                            vm::centerOnAirport
                        } else {
                            null
                        },
                    )
                }

                // Above the scaffold too, a selected aircraft's sheet would cover the list's lower rows
                MapSidebar(
                    visible = listOverlaid,
                    sidebarData = sidebarData,
                    controls = listControls,
                    filters = listFilters,
                    shapes = shapes,
                    selectedHex = selectedHex,
                    pinned = state?.pinned.orEmpty(),
                    actions = listActions,
                    // Its start is mid-screen, only its end and top can meet a cutout or a side navigation bar
                    panelModifier = Modifier.windowInsetsPadding(screenInsets.only(WindowInsetsSides.End + WindowInsetsSides.Top)),
                )

                // Watch messages answer a tap on the sheet, so they cover its card rather than the map above it
                SnackbarHost(
                    watchSnackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Above the area editor, whose answers these are while it is shown
                        .padding(bottom = with(density) { areaCoveredPx.toDp() })
                        .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Horizontal))
                        .then(
                            // The editor's height already clears the navigation bar
                            if (isFullscreen && areaCoveredPx == 0) {
                                Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            } else {
                                Modifier
                            }
                        ),
                )
            }

            if (listPaneShown) {
                MapListPane(
                    sidebarData = sidebarData,
                    controls = listControls,
                    filters = listFilters,
                    shapes = shapes,
                    selectedHex = selectedHex,
                    pinned = state?.pinned.orEmpty(),
                    actions = listActions,
                    modifier = Modifier.windowInsetsPadding(screenInsets.only(WindowInsetsSides.End + WindowInsetsSides.Top)),
                )
            }
        }
    }

    if (optionsOpen) {
        val current = state
        MapOptionsSheet(
            toggles = current?.toggles ?: NativeMapViewModel.Toggles(),
            overlays = current?.overlays.orEmpty(),
            onToggleLabels = { vm.toggleLabels() },
            onToggleLabelField = { vm.toggleLabelField(it) },
            onToggleAllTracks = { vm.toggleAllTracks() },
            onToggleMilitaryHighlight = { vm.toggleMilitaryHighlight() },
            onToggleWatchHighlight = { vm.toggleWatchHighlight() },
            onToggleOverlay = { vm.toggleOverlay(it) },
            onUpgrade = {
                optionsOpen = false
                vm.goUpgrade()
            },
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

// Material's expanded width and medium height classes, which leave the map 480dp beside the list
private val DOCKED_LIST_MIN_WIDTH = 840.dp
private val DOCKED_LIST_MIN_HEIGHT = 480.dp

private const val HISTORY_FIT_WAIT = 2_000L
private const val AREA_FIT_WAIT = 2_000L
private const val AIRPORT_LIST_RESTORE_WAIT = 2_000L
private const val FOLLOW_JUMP_SHEET_WAIT = 1_500L

/** The part of [NativeMapViewModel.State] the screen itself reads. */
private data class ScreenState(
    val tagline: String,
    val isPro: Boolean,
    val style: NativeMapStyle,
    val toggles: NativeMapViewModel.Toggles,
    val overlays: List<NativeMapViewModel.OverlayOption>,
    val overlaySpecs: List<OverlaySpec>,
    val selection: NativeMapViewModel.Selected?,
    val myLocation: Pair<Double, Double>?,
    val pinned: List<AircraftHex>,
    val isolating: Boolean,
    val historyAction: NativeMapViewModel.HistoryAction?,
    val lastSeenMark: NativeMapViewModel.LastSeenMark?,
) {
    val selectedHex: AircraftHex? get() = (selection as? NativeMapViewModel.Selected.Aircraft)?.hex
    val pinnedCount: Int get() = pinned.size

    constructor(state: NativeMapViewModel.State) : this(
        tagline = state.tagline,
        isPro = state.isPro,
        style = state.style,
        toggles = state.toggles,
        overlays = state.overlays,
        overlaySpecs = state.overlaySpecs,
        selection = state.selection,
        myLocation = state.myLocation,
        pinned = state.pinned,
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

/**
 * The latest [NativeMapEvents.PeekSheet] or [NativeMapEvents.ExpandSheet], held until the screen shows its
 * selection and the sheet got there. The events arrive apart from the state, either can come first.
 */
private class SheetRequests {
    var pending by mutableStateOf<SheetRequest?>(null)
        private set

    /** The selection the screen last composed. */
    var shown: NativeMapViewModel.Selected? = null

    fun request(selection: NativeMapViewModel.Selected, listPosition: SheetListPosition?) {
        pending?.job?.cancel()
        pending = SheetRequest(selection, listPosition, shownBefore = shown)
    }

    fun drop(request: SheetRequest) {
        request.job?.cancel()
        done(request)
    }

    fun done(request: SheetRequest) {
        if (pending === request) pending = null
    }

    fun holds(selection: NativeMapViewModel.Selected?): Boolean = selection != null && pending?.selection === selection

    fun restoresList(target: AirportTarget?): Boolean {
        val request = pending ?: return false
        return target != null && request.listPosition != null &&
                (request.selection as? NativeMapViewModel.Selected.Airport)?.target == target
    }
}

/** Expands with the airport's list at [listPosition], without one it peeks. */
private class SheetRequest(
    val selection: NativeMapViewModel.Selected,
    val listPosition: SheetListPosition?,
    val shownBefore: NativeMapViewModel.Selected?,
) {
    var job: Job? = null
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
private fun unavailableText(state: NativeMapViewModel.State, serverNow: () -> Long): String? = when {
    state.waiting is AircraftRepo.ViewingState.Reason.Exhausted -> {
        val resetsAt = state.waiting.resetsAt
        // Server time: the reset instant is the server's. The text counts whole minutes at its finest
        val now by produceState(initialValue = serverNow(), key1 = resetsAt) {
            while (true) {
                delay(60_000)
                value = serverNow()
            }
        }
        stringResource(
            R.string.map_status_allowance_used_up,
            relativeResetText(resetsAt, now, LocalConfiguration.current.locales[0]),
        )
    }
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
    serverNow: () -> Long,
    content: @Composable (cap: MapCapNote?) -> Unit,
) {
    val state = fullState.value
    val altitude = state?.capAltitude
    // A refused app gets no new answers, the counts are from before
    val cap = if (
        state?.capped == true &&
        state.waiting !is AircraftRepo.ViewingState.Reason.UpdateRequired &&
        unavailableText(state, serverNow) == null
    ) {
        val shown = state.shownCount
        if (altitude != null) {
            MapCapNote(
                stringResource(R.string.map_cap_chip_altitude, LocalUnitFormatter.current.altitudeAtMost(altitude.aboveFeet).text()),
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
 * Why the map shows no aircraft, only aging positions, aircraft of the other filter, or not yet
 * those of the view. Failed requests can be retried from it, [attempt] is true while a request is out.
 */
@Composable
private fun MapStatus(
    fullState: State<NativeMapViewModel.State?>,
    attempt: State<Boolean>,
    serverNow: () -> Long,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = fullState.value ?: return
    val text = unavailableText(state, serverNow)
        ?: stringResource(R.string.map_status_filter_loading).takeIf { state.filterPending || state.viewPending }
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
