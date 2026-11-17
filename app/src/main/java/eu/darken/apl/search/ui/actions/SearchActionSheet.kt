package eu.darken.apl.search.ui.actions

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.isFinite
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.compose.SheetActionsAnchor
import eu.darken.apl.common.compose.SnackbarPresenter
import eu.darken.apl.common.compose.keepInVisibleWindow
import eu.darken.apl.common.compose.rememberCopyToClipboard
import eu.darken.apl.common.compose.rememberModalSheetProgress
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.flight.RouteProgress
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.core.aircraft.IcaoCountries
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.ui.AircraftDetailsSheetContent
import eu.darken.apl.map.ui.AircraftPlaceAction
import eu.darken.apl.watch.ui.showWatchAdded
import eu.darken.apl.watch.ui.showWatchLimitReached
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchActionSheetHost(
    hex: String,
    vm: SearchActionViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(hex) {
        vm.init(hex)
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val current = state
    val country = remember(hex) { IcaoCountries.countryName(hex) }
    val aircraft = current?.aircraft
    val details = remember(aircraft, country) {
        aircraft?.let { MapAircraftDetails.from(it, country) } ?: MapAircraftDetails.pending(hex, country)
    }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    // Shown inside the sheet, which has a window of its own, the screen's snackbars would be under its scrim
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbars = remember { SnackbarPresenter(snackbarHostState, scope) }
    val copyToClipboard = rememberCopyToClipboard(snackbars)
    val context = LocalContext.current
    val accessibilityManager = LocalAccessibilityManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.events.collect { event ->
                when (event) {
                    is SearchActionEvents.WatchAdded -> snackbars.showAction {
                        if (showWatchAdded(context, event.label, accessibilityManager)) vm.undoWatch(event.watchId)
                    }

                    is SearchActionEvents.WatchLimitReached -> snackbars.showAction {
                        if (showWatchLimitReached(context, event.error)) vm.goUpgrade()
                    }
                }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = { vm.navUp() },
        sheetState = sheetState,
        dragHandle = null,
    ) {
        val progress = rememberModalSheetProgress(sheetState)
        BoxWithConstraints(modifier = progress.modifier) {
            val maxPhotoHeight = if (maxHeight.isFinite) with(LocalDensity.current) { (maxHeight * 0.45f).roundToPx() } else 0
            AircraftDetailsSheetContent(
                details = details,
                loading = current == null,
                note = stringResource(R.string.watch_list_item_no_current_data_msg).takeIf { current != null && aircraft == null },
                route = current?.route,
                routeProgress = current?.routeProgress ?: RouteProgress.Unknown,
                heightAboveAirport = current?.heightAboveAirport,
                progress = progress,
                topInset = 0,
                maxPhotoHeight = maxPhotoHeight,
                onCollapsedHeight = {},
                watched = current?.watch != null,
                seen = current?.seen,
                serverNow = vm::serverNow,
                placeAction = remember(vm) { AircraftPlaceAction.ShowOnMap { vm.showMap() } },
                onWatchAction = { vm.onWatchAction() },
                onWatchWithNote = { vm.showWatch() },
                onPhotoClick = { link ->
                    if (sheetState.currentValue == SheetValue.Expanded) vm.openPhoto(link) else scope.launch { sheetState.expand() }
                },
                distanceInMeter = current?.distanceInMeter,
                onAirportClick = vm::showAirport,
                onTypeClick = vm::searchForType,
                onAirlineClick = vm::searchForAirline,
                onSquawkClick = vm::searchForSquawk,
                onCopy = copyToClipboard,
            )
            SheetActionsAnchor(
                sheetState = sheetState,
                label = details.callsign ?: details.registration ?: details.hex,
                onDismiss = { vm.navUp() },
            )
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .keepInVisibleWindow(WindowInsets.safeDrawing),
            )
        }
    }
}
