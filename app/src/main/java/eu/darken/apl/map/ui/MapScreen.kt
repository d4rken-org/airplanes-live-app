package eu.darken.apl.map.ui

import android.Manifest
import android.app.Activity
import android.view.WindowInsetsController
import android.webkit.WebView
import androidx.core.view.doOnLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ViewInAr
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Fullscreen
import androidx.compose.material.icons.twotone.FullscreenExit
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.automirrored.twotone.ViewSidebar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import eu.darken.apl.upgrade.ui.TierChip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import eu.darken.apl.R
import eu.darken.apl.common.compose.HideBottomNavBar
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.map.core.MapControl
import eu.darken.apl.map.core.MapHandler
import eu.darken.apl.map.core.MapUiConfig
import eu.darken.apl.map.core.MapOptions
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private val TAG = logTag("Map", "Screen")

@Composable
fun MapScreenHost(
    mapOptions: MapOptions? = null,
    mapHandlerFactory: MapHandler.Factory,
    vm: MapViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(mapOptions) {
        vm.init(mapOptions)
    }

    val state by vm.state.collectAsState(initial = null)
    val currentState = state ?: return

    val aircraftDetails by vm.aircraftDetails.collectAsState()
    val useNativePanel by vm.useNativePanel.collectAsState()
    val showHoverInfo by vm.showHoverInfo.collectAsState()
    val enabledOverlays by vm.enabledOverlays.collectAsState()
    val buttonStates by vm.buttonStates.collectAsState()
    val sidebarData by vm.sidebarData.collectAsState()
    val isSidebarOpen by vm.isSidebarOpen.collectAsState()
    val sidebarSort by vm.sidebarSort.collectAsState()
    val sidebarSortAscending by vm.sidebarSortAscending.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Location permission
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

    // Route display
    var currentRoute by remember { mutableStateOf<FlightRoute?>(null) }
    LaunchedEffect(Unit) {
        vm.routeDisplay
            .onEach { display ->
                if (!vm.useNativePanel.value) return@onEach
                currentRoute = when (display) {
                    is MapViewModel.RouteDisplay.Result -> display.route
                    else -> null
                }
            }
            .launchIn(this)
    }

    // Fullscreen state
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    HideBottomNavBar(hidden = isFullscreen)

    // Controls dropdown state
    var controlsExpanded by remember { mutableStateOf(false) }

    // Bottom sheet state
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
        snackbarHostState = snackbarHostState,
    )

    // WebView + MapHandler (created once, survives recomposition)
    var mapHandlerRef by remember { mutableStateOf<MapHandler?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Events handler
    LaunchedEffect(Unit) {
        vm.events
            .onEach { event ->
                when (event) {
                    MapEvents.RequestLocationPermission -> {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    is MapEvents.CenterOnLocation -> {
                        mapHandlerRef?.centerOnLocation(event.lat, event.lon)
                    }

                    MapEvents.LocationUnavailable -> {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.map_my_location_unavailable))
                        }
                    }

                    is MapEvents.WatchAdded -> {
                        val ac = event.watch.tracked.firstOrNull()
                        val text = context.getString(R.string.watch_item_x_added, ac?.registration ?: ac?.hex)
                        snackbarHostState.showSnackbar(text)
                    }

                    is MapEvents.SelectAircraftOnMap -> {
                        mapHandlerRef?.selectAircraft(event.hex)
                    }

                    MapEvents.ReloadMap -> {
                        mapHandlerRef?.forceReload()
                    }
                }
            }
            .launchIn(this)
    }

    // Handle aircraft details changes → show/hide sheet
    LaunchedEffect(aircraftDetails?.hex, useNativePanel) {
        if (!useNativePanel || aircraftDetails == null) {
            sheetState.hide()
        } else {
            sheetState.partialExpand()
        }
    }

    // Handle useNativePanel changes (reload webview)
    LaunchedEffect(Unit) {
        vm.useNativePanel
            .drop(1)
            .distinctUntilChanged()
            .onEach { enabled ->
                mapHandlerRef?.let { it.uiConfig = it.uiConfig.copy(useNativePanel = enabled) }
                vm.clearButtonStates()
                webViewRef?.reload()
            }
            .launchIn(this)
    }

    // Handle showHoverInfo changes (no reload needed)
    LaunchedEffect(Unit) {
        vm.showHoverInfo
            .drop(1)
            .distinctUntilChanged()
            .onEach { enabled -> mapHandlerRef?.applyHoverInfo(enabled) }
            .launchIn(this)
    }

    // Handle map layer changes
    LaunchedEffect(Unit) {
        vm.mapLayer
            .drop(1)
            .distinctUntilChanged()
            .onEach { layerKey -> mapHandlerRef?.applyMapLayer(layerKey) }
            .launchIn(this)
    }

    // Handle overlay changes
    LaunchedEffect(Unit) {
        vm.enabledOverlays
            .drop(1)
            .distinctUntilChanged()
            .onEach { keys -> mapHandlerRef?.applyOverlays(keys ?: emptySet()) }
            .launchIn(this)
    }

    // Immersive mode
    val activity = context as? Activity
    LaunchedEffect(isFullscreen) {
        activity?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.setDecorFitsSystemWindows(!isFullscreen)
                window.insetsController?.let {
                    if (isFullscreen) {
                        it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                        it.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        it.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    }
                }
            }
        }
    }

    // Restore system bars on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (isFullscreen) {
                activity?.window?.let { window ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        @Suppress("DEPRECATION")
                        window.setDecorFitsSystemWindows(true)
                        window.insetsController?.show(
                            android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                        )
                    }
                }
            }
        }
    }

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
                            Icon(
                                Icons.TwoTone.MyLocation,
                                contentDescription = stringResource(R.string.map_my_location_action),
                            )
                        }
                        IconButton(onClick = { vm.reset() }) {
                            Icon(
                                Icons.TwoTone.Refresh,
                                contentDescription = stringResource(R.string.common_reset_action),
                            )
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = when {
            !isFullscreen -> aplContentWindowInsets(hasBottomNav = true)
            // Pre-R the immersive block above can't hide the system bars, so keep their inset
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R ->
                aplContentWindowInsets(hasBottomNav = false)
            // tar1090 draws its own controls inside the WebView and Compose can't inset those,
            // so keep the cutout reserved while the native panel is off
            !useNativePanel -> WindowInsets.displayCutout
            // Bars are hidden: the map extends under any display cutout, the Compose overlays
            // are inset separately so they stay clear of it
            else -> WindowInsets(0, 0, 0, 0)
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            // Animate sheet corners: rounded when peeking, flat when fully expanded
            val sheetCornerRadius by animateDpAsState(
                targetValue = if (sheetState.currentValue == SheetValue.Expanded) 0.dp else 28.dp,
                label = "sheetCornerRadius",
            )

            // Must match the Scaffold branch above that drops the inset: only in that exact
            // state do the Compose overlays have to re-apply the cutout themselves
            val cutoutSafe = if (
                isFullscreen &&
                useNativePanel &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
            ) {
                Modifier.windowInsetsPadding(WindowInsets.displayCutout)
            } else {
                Modifier
            }

            // Map content with bottom sheet
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetContent = {
                    val details = aircraftDetails
                    if (details != null) {
                        AircraftDetailsSheetContent(
                            details = details,
                            route = currentRoute,
                            onClose = { scope.launch { sheetState.hide() } },
                            onCopyLink = { hex ->
                                vm.copyLink(hex)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.map_aircraft_details_link_copied)
                                    )
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
                    val lifecycleOwner = LocalLifecycleOwner.current

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).also { webView ->
                                webViewRef = webView
                                val uiConfig = MapUiConfig(useNativePanel = vm.useNativePanel.value, showHoverInfo = vm.showHoverInfo.value)
                                val handler = mapHandlerFactory.create(webView, uiConfig, vm.mapLayer.value, enabledOverlays ?: emptySet())
                                mapHandlerRef = handler

                                scope.launch {
                                    handler.events
                                        .onEach { event ->
                                            when (event) {
                                                is MapHandler.Event.OpenUrl -> vm.onOpenUrl(event.url)
                                                is MapHandler.Event.OptionsChanged -> vm.onOptionsUpdated(event.options)
                                                is MapHandler.Event.AircraftDetailsChanged -> vm.onAircraftDetailsChanged(event.details)
                                                MapHandler.Event.AircraftDeselected -> vm.onAircraftDeselected()
                                                is MapHandler.Event.ButtonStatesChanged -> vm.onButtonStatesChanged(event.jsonData)
                                                is MapHandler.Event.AircraftListChanged -> vm.onAircraftListChanged(event.data)
                                            }
                                        }
                                        .launchIn(this)
                                }

                                // Wait for Compose layout so WebView has non-zero dimensions for the globe page
                                webView.doOnLayout { handler.loadMap(currentState.options) }
                            }
                        },
                        update = { _ ->
                            // Skip before initial load completes (deferred via post above)
                            if (webViewRef?.url != null) {
                                mapHandlerRef?.loadMap(currentState.options)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // WebView lifecycle management
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().then(cutoutSafe)) {
                        // Fullscreen toggle + My Location buttons
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
                                    Icon(
                                        Icons.TwoTone.MyLocation,
                                        contentDescription = stringResource(R.string.map_my_location_action),
                                    )
                                }
                            }

                            if (vm.hasRotationSensor) {
                                FilledTonalIconButton(
                                    onClick = { vm.goToAr() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.ViewInAr,
                                        contentDescription = stringResource(R.string.ar_view_action),
                                    )
                                }
                            }
                        }

                        // Map controls + sidebar toggle
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Controls button + dropdown
                            Box {
                                FilledTonalIconButton(
                                    onClick = { controlsExpanded = true },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.TwoTone.Tune,
                                        contentDescription = stringResource(R.string.map_controls_action),
                                    )
                                }

                                DropdownMenu(
                                    expanded = controlsExpanded,
                                    onDismissRequest = { controlsExpanded = false },
                                ) {
                                    MapControl.entries.forEach { control ->
                                        val isActive = buttonStates[control.buttonId] == true
                                        val needsSelection = control.requiresSelection && aircraftDetails == null

                                        DropdownMenuItem(
                                            text = { Text(stringResource(control.labelRes)) },
                                            onClick = {
                                                when (control.type) {
                                                    MapControl.ControlType.ACTION -> {
                                                        mapHandlerRef?.executeToggle(control.buttonId)
                                                        controlsExpanded = false
                                                    }

                                                    MapControl.ControlType.TOGGLE -> {
                                                        mapHandlerRef?.executeToggle(control.buttonId)
                                                    }
                                                }
                                            },
                                            leadingIcon = if (control.type == MapControl.ControlType.TOGGLE && isActive) {
                                                {
                                                    Icon(
                                                        Icons.TwoTone.Check,
                                                        contentDescription = null,
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            enabled = !needsSelection,
                                        )
                                    }
                                }
                            }

                            // Sidebar toggle button (only when native panel enabled)
                            if (useNativePanel) {
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
                        }
                    }

                    // Sidebar overlay
                    MapSidebar(
                        visible = isSidebarOpen && useNativePanel,
                        sidebarData = sidebarData,
                        activeSort = sidebarSort,
                        sortAscending = sidebarSortAscending,
                        onSortToggle = { vm.toggleSort(it) },
                        onClose = { vm.closeSidebar() },
                        onAircraftClick = { hex -> vm.selectAircraftOnMap(hex) },
                        panelModifier = cutoutSafe,
                    )
                }
            }
        }
    }
}
