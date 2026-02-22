package eu.darken.apl.map.ui

import android.Manifest
import android.app.Activity
import android.view.WindowInsetsController
import android.webkit.WebView
import androidx.core.view.doOnLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import eu.darken.apl.R
import eu.darken.apl.common.compose.BottomNavBar
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.flight.ui.RouteDisplay
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.map.core.MapAircraftDetails
import eu.darken.apl.map.core.MapControl
import eu.darken.apl.map.core.MapHandler
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

                    MapEvents.HomeMap -> {
                        mapHandlerRef?.clickHome()
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
                mapHandlerRef?.useNativePanel = enabled
                vm.clearButtonStates()
                webViewRef?.reload()
            }
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
                            Text(stringResource(R.string.app_name))
                            Text(
                                text = stringResource(R.string.map_page_label),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.reset() }) {
                            Icon(
                                painterResource(R.drawable.ic_refresh_24),
                                contentDescription = stringResource(R.string.common_reset_action),
                            )
                        }
                        IconButton(onClick = { vm.goToSettings() }) {
                            Icon(
                                painterResource(R.drawable.ic_settings_24),
                                contentDescription = stringResource(R.string.label_settings),
                            )
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = aplContentWindowInsets(hasBottomNav = !isFullscreen),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
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
                        )
                    }
                },
                sheetPeekHeight = 200.dp,
                sheetDragHandle = if (aircraftDetails != null) {
                    { BottomSheetDefaults.DragHandle() }
                } else null,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.weight(1f),
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                val lifecycleOwner = LocalLifecycleOwner.current

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).also { webView ->
                            webViewRef = webView
                            val handler = mapHandlerFactory.create(webView, vm.useNativePanel.value, vm.mapLayer.value)
                            mapHandlerRef = handler

                            scope.launch {
                                handler.events
                                    .onEach { event ->
                                        when (event) {
                                            MapHandler.Event.HomePressed -> vm.checkLocationPermission()
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

                // Fullscreen toggle button
                FilledTonalIconButton(
                    onClick = { isFullscreen = !isFullscreen },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(48.dp),
                ) {
                    Icon(
                        painterResource(
                            if (isFullscreen) R.drawable.ic_fullscreen_exit_24 else R.drawable.ic_fullscreen_24
                        ),
                        contentDescription = stringResource(R.string.common_fullscreen_action),
                    )
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
                                painterResource(R.drawable.ic_tune_24),
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
                                        when {
                                            control == MapControl.HOME -> {
                                                vm.homeMap()
                                                controlsExpanded = false
                                            }

                                            control.type == MapControl.ControlType.ACTION -> {
                                                mapHandlerRef?.executeToggle(control.buttonId)
                                                controlsExpanded = false
                                            }

                                            else -> {
                                                mapHandlerRef?.executeToggle(control.buttonId)
                                            }
                                        }
                                    },
                                    leadingIcon = if (control.type == MapControl.ControlType.TOGGLE && isActive) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
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
                                painterResource(R.drawable.ic_sidebar_list_24),
                                contentDescription = stringResource(R.string.map_sidebar_toggle_action),
                            )
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
                )
                }
            }

            // Bottom nav (hidden in fullscreen)
            if (!isFullscreen) {
                BottomNavBar(selectedTab = 0)
            }
        }
    }
}

@Composable
private fun AircraftDetailsSheetContent(
    details: MapAircraftDetails,
    route: FlightRoute?,
    onClose: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShowInSearch: (String) -> Unit,
    onAddWatch: (String) -> Unit,
    onThumbnailClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Peek content
        item(key = "peek") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
            ) {
                // Callsign + Hex + Copy + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = details.callsign ?: details.hex,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "#${details.hex}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { onCopyLink(details.hex) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_content_copy_24),
                            contentDescription = stringResource(R.string.map_aircraft_details_copy_link_action),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_close_24),
                            contentDescription = stringResource(R.string.common_close_action),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Subtitle: Registration · Type · Country
                val subtitleParts = listOfNotNull(details.registration, details.icaoType, details.country)
                val subtitle = subtitleParts.joinToString(" \u00b7 ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Type description
                val typeDesc = listOfNotNull(
                    details.typeLong,
                    details.typeDesc?.let { "($it)" },
                ).joinToString(" ")
                if (typeDesc.isNotBlank()) {
                    Text(
                        text = typeDesc,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // Operator
                if (!details.operator.isNullOrBlank()) {
                    Text(
                        text = details.operator,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Route
                if (route != null) {
                    RouteDisplay(
                        route = route,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }

        // Expanded content
        item(key = "expanded") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                // Photo + actions
                AircraftPhotoActions(
                    details = details,
                    onShowInSearch = onShowInSearch,
                    onAddWatch = onAddWatch,
                    onThumbnailClick = onThumbnailClick,
                )

                // Flight Data section
                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_flight_data),
                    rows = listOf(
                        DetailRow(
                            stringResource(R.string.common_altitude_label) to details.altitude,
                            stringResource(R.string.common_speed_label) to details.speed,
                        ),
                        DetailRow(
                            stringResource(R.string.common_squawk_label) to details.squawk,
                            stringResource(R.string.map_aircraft_details_track_label) to details.track,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_vert_rate_label) to details.vertRate,
                            stringResource(R.string.map_aircraft_details_position_label) to details.position,
                        ),
                    ),
                )

                // Navigation section
                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_navigation),
                    rows = listOf(
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_nav_altitude_label) to details.navAltitude,
                            stringResource(R.string.map_aircraft_details_nav_heading_label) to details.navHeading,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_nav_modes_label) to details.navModes,
                            stringResource(R.string.map_aircraft_details_qnh_label) to details.navQnh,
                        ),
                    ),
                )

                // Performance section
                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_performance),
                    rows = listOf(
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_tas_label) to details.tas,
                            stringResource(R.string.map_aircraft_details_ias_label) to details.ias,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_mach_label) to details.mach,
                            stringResource(R.string.map_aircraft_details_baro_rate_label) to details.baroRate,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_geom_rate_label) to details.geomRate,
                            stringResource(R.string.map_aircraft_details_wind_speed_label) to details.windSpeed?.let { ws ->
                                details.windDir?.let { wd -> "$ws / $wd" } ?: ws
                            },
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_temp_label) to details.temp,
                            null,
                        ),
                    ),
                )

                // Signal section
                DetailSection(
                    header = stringResource(R.string.map_aircraft_details_section_signal),
                    rows = listOf(
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_source_label) to details.source,
                            stringResource(R.string.map_aircraft_details_rssi_label) to details.rssi,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_msg_rate_label) to details.messageRate,
                            stringResource(R.string.map_aircraft_details_messages_label) to details.messageCount,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_last_seen_label) to details.seen,
                            stringResource(R.string.map_aircraft_details_adsb_version_label) to details.adsVersion,
                        ),
                        DetailRow(
                            stringResource(R.string.map_aircraft_details_category_label) to details.category,
                            stringResource(R.string.map_aircraft_details_flags_label) to details.dbFlags,
                        ),
                    ),
                )
            }
        }
    }
}

private data class DetailRow(
    val left: Pair<String, String?>,
    val right: Pair<String, String?>?,
)

@Composable
private fun DetailSection(
    header: String,
    rows: List<DetailRow>,
) {
    val visibleRows = rows.filter { row ->
        !row.left.second.isNullOrBlank() || row.right?.second?.isNotBlank() == true
    }
    if (visibleRows.isEmpty()) return

    Text(
        text = header,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )

    visibleRows.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            if (!row.left.second.isNullOrBlank()) {
                DetailCell(
                    label = row.left.first,
                    value = row.left.second!!,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            if (row.right != null && !row.right.second.isNullOrBlank()) {
                DetailCell(
                    label = row.right.first,
                    value = row.right.second!!,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AircraftPhotoActions(
    details: MapAircraftDetails,
    onShowInSearch: (String) -> Unit,
    onAddWatch: (String) -> Unit,
    onThumbnailClick: (String) -> Unit,
) {
    val hex = details.hex
    val photoUrl = details.photoUrl

    val safePhotoUrl = photoUrl?.takeIf { it.startsWith("https://") }
    if (!safePhotoUrl.isNullOrBlank()) {
        val photoLink = "https://www.planespotters.net/hex/$hex"
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(420f / 280f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onThumbnailClick(photoLink) },
            ) {
                AsyncImage(
                    model = safePhotoUrl,
                    contentDescription = stringResource(R.string.common_aircraft_photo_label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!details.photoCredit.isNullOrBlank()) {
                    Text(
                        text = "\u00a9 ${details.photoCredit}",
                        color = Color.White,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 2.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(
                    onClick = { onShowInSearch(hex) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.common_show_in_search),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                OutlinedButton(
                    onClick = { onAddWatch(hex) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.watch_list_watch_add_label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onShowInSearch(hex) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.common_show_in_search),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(
                onClick = { onAddWatch(hex) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.watch_list_watch_add_label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
